package com.duzui.sharetoobsi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duzui.sharetoobsi.data.AppProfileEntity
import com.duzui.sharetoobsi.data.AppSettings
import com.duzui.sharetoobsi.data.BookEntity
import com.duzui.sharetoobsi.data.FormatEntity
import com.duzui.sharetoobsi.data.HistoryEntity
import com.duzui.sharetoobsi.data.OutboxEntity
import com.duzui.sharetoobsi.data.ProfileEntity
import com.duzui.sharetoobsi.data.TargetEntity
import com.duzui.sharetoobsi.domain.Cleanup
import com.duzui.sharetoobsi.domain.CleanupOptions
import com.duzui.sharetoobsi.domain.Defaults
import com.duzui.sharetoobsi.domain.SourceProfile
import com.duzui.sharetoobsi.domain.SourceProfiles
import com.duzui.sharetoobsi.domain.Template
import com.duzui.sharetoobsi.domain.TemplateValues
import com.duzui.sharetoobsi.domain.WriteMode
import com.duzui.sharetoobsi.domain.WriteRequest
import com.duzui.sharetoobsi.domain.userProfile
import com.duzui.sharetoobsi.domain.userProfileId
import com.duzui.sharetoobsi.send.SendOutcome
import com.duzui.sharetoobsi.send.ShareShortcuts
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Obsidian calls this back once it has handled the URI, if the user asked to be returned. */
const val RETURN_CALLBACK = "sharetoobsi://return"

/**
 * Derived values are computed once at construction rather than in `get()`, so one state
 * change costs one pass of the pipeline instead of one per recomposition.
 */
data class ShareUiState(
    val raw: String = "",
    /** Where the text came from, for display: a share, the clipboard, or nothing yet. */
    val origin: String = "",
    val sourcePackage: String? = null,
    val profile: SourceProfile = SourceProfiles.GENERIC,
    val settings: AppSettings = AppSettings(),
    /** Built-ins plus whatever the user has written. */
    val availableProfiles: List<SourceProfile> = SourceProfiles.ALL,
    val userProfiles: List<ProfileEntity> = emptyList(),
    /** Source package to profile id. */
    val appMappings: Map<String, String> = emptyMap(),
    val installedApps: List<AppEntry> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    /** Named output formats a target can pick from. */
    val formats: List<FormatEntity> = emptyList(),
    val savedTargets: List<TargetEntity> = emptyList(),
    /** null falls back to the first target, so there is always somewhere to write. */
    val selectedTargetId: Long? = null,
    /** Books being read, kept separately from where their quotes go. */
    val savedBooks: List<BookEntity> = emptyList(),
    /** null falls back to the first book. */
    val selectedBookId: Long? = null,
    /** Set once the user hand-edits the payload; cleared whenever an input changes. */
    val payloadOverride: String? = null,
) {
    /**
     * An explicit selection first, then the user's chosen default, then whatever sorts
     * first — so there is always somewhere to write.
     */
    val chosenTarget: TargetEntity? =
        savedTargets.firstOrNull { it.id == selectedTargetId }
            ?: savedTargets.firstOrNull { it.id == settings.defaultTargetId }
            ?: savedTargets.firstOrNull()

    /** Metadata only; falls back to the first book so a capture still has an attribution. */
    val chosenBook: BookEntity? =
        savedBooks.firstOrNull { it.id == selectedBookId } ?: savedBooks.firstOrNull()

    private val chosenFormat: FormatEntity? =
        formats.firstOrNull { it.id == chosenTarget?.formatId }

    private val values: TemplateValues = TemplateValues(
        text = Cleanup.clean(raw, profile, settings.cleanup),
        // What is being read comes from the book; where it goes comes from the target.
        title = chosenBook?.title?.takeIf { it.isNotBlank() },
        author = chosenBook?.author?.takeIf { it.isNotBlank() },
        year = chosenBook?.year?.takeIf { it.isNotBlank() },
        tags = settings.tags.ifBlank { null },
        source = sourcePackage,
        date = LocalDate.now().toString(),
    )

    /**
     * Output shape, most specific first: the format the target picked, then the rule set's
     * own template, then the default. The target wins because it knows the vault's
     * convention for that note; the rule set only knows the shape of what comes in.
     */
    val effectiveTemplate: String =
        chosenFormat?.template ?: profile.template.ifBlank { settings.template }

    val payload: String = payloadOverride ?: Template.render(effectiveTemplate, values)

    /** The target's path, filled from the book — one target serves every book. */
    val targetPath: String = chosenTarget
        ?.let { Template.substitute(it.path, values).trim() }
        ?: Defaults.INBOX_NOTE

    /**
     * A heading that does not exist makes the Advanced URI silently write nothing, so a
     * target with no heading is written without one rather than aimed at a guess.
     */
    val effectiveHeading: String? = chosenTarget?.heading?.takeIf { it.isNotBlank() }
}

/** What the add/edit target form collects. */
data class TargetDraft(
    val rowId: Long? = null,
    val name: String = "",
    val path: String = "",
    val heading: String = "",
    val formatId: Long? = null,
)

/** What the add/edit book form collects. */
data class BookDraft(
    val rowId: Long? = null,
    val title: String = "",
    val author: String = "",
    val year: String = "",
    val createSkeleton: Boolean = false,
)

class ShareViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer(application)
    private val targets = container.db.targets()
    private val bookDao = container.db.books()
    private val history = container.db.history()
    private val outbox = container.db.outbox()
    private val profiles = container.db.profiles()
    private val appProfiles = container.db.appProfiles()
    private val formatDao = container.db.formats()

    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** A target chosen before the list finished loading, e.g. from a Direct Share shortcut. */
    private var pendingTargetId: Long? = null

    /** Same, for a book selected before the list has loaded. */
    private var pendingBookId: Long? = null

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                // Settings reshape the payload, so a hand edit no longer applies.
                _state.update { it.copy(settings = settings, payloadOverride = null) }
            }
        }
        viewModelScope.launch {
            profiles.observeAll().collect { rows ->
                _state.update {
                    it.copy(
                        userProfiles = rows,
                        availableProfiles = SourceProfiles.ALL + rows.map { row ->
                            userProfile(row.id, row.name, row.lineRules, row.inlineRules, row.template)
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            appProfiles.observeAll().collect { rows ->
                _state.update { it.copy(appMappings = rows.associate { row -> row.packageName to row.profileId }) }
            }
        }
        viewModelScope.launch {
            bookDao.observeAll().collect { list ->
                _state.update { current ->
                    val wanted = pendingBookId ?: current.selectedBookId
                    current.copy(
                        savedBooks = list,
                        selectedBookId = wanted?.takeIf { id -> list.any { it.id == id } },
                    )
                }
            }
        }
        viewModelScope.launch {
            history.observeRecent().collect { rows -> _state.update { it.copy(history = rows) } }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { launchableApps(getApplication()) }.let { apps ->
                _state.update { it.copy(installedApps = apps) }
            }
        }
        viewModelScope.launch {
            formatDao.observeAll().collect { rows ->
                if (!_state.value.settings.presetFormatsSeeded) {
                    // Offered once, then they are the user's to edit or delete.
                    container.settings.update { it.copy(presetFormatsSeeded = true) }
                    if (rows.isEmpty()) {
                        Defaults.PRESET_FORMATS.forEachIndexed { index, preset ->
                            formatDao.upsert(
                                FormatEntity(
                                    name = preset.first,
                                    template = preset.second,
                                    sortOrder = index,
                                )
                            )
                        }
                        return@collect
                    }
                }
                _state.update { it.copy(formats = rows) }
            }
        }
        viewModelScope.launch {
            targets.observeAll().collect { list ->
                if (!_state.value.settings.defaultTargetSeeded) {
                    // Offered once, not restored when missing: from here the inbox is an
                    // ordinary target — reorderable, editable, removable — so putting it
                    // back after the user deleted it would be overriding them.
                    container.settings.update { it.copy(defaultTargetSeeded = true) }
                    if (list.none { it.path == Defaults.INBOX_NOTE }) {
                        targets.upsert(
                            TargetEntity(
                                name = Defaults.INBOX_NAME,
                                path = Defaults.INBOX_NOTE,
                                // Ahead of existing targets, which all default to 0.
                                sortOrder = -1,
                            )
                        )
                        return@collect
                    }
                }
                _state.update { current ->
                    // A target deleted since it was chosen, or chosen before the list
                    // loaded, must not stay selected pointing at nothing.
                    val wanted = pendingTargetId ?: current.selectedTargetId
                    current.copy(
                        savedTargets = list,
                        selectedTargetId = wanted?.takeIf { id -> list.any { it.id == id } },
                    )
                }
                ShareShortcuts.sync(getApplication(), list)
            }
        }
    }

    // ---- intake ------------------------------------------------------------

    fun onShare(share: IncomingShare?) {
        if (share == null || share.text.isBlank()) return
        _state.update { current ->
            current.copy(
                raw = share.text,
                origin = "分享自 ${share.sourcePackage ?: "未知来源"}",
                sourcePackage = share.sourcePackage,
                profile = resolveProfile(current, share.sourcePackage),
                payloadOverride = null,
            )
        }
    }

    /** An explicit app mapping wins; otherwise the app's own profile, then the default. */
    private fun resolveProfile(state: ShareUiState, packageName: String?): SourceProfile {
        state.appMappings[packageName]?.let { mapped ->
            state.availableProfiles.firstOrNull { it.id == mapped }?.let { return it }
        }
        val detected = SourceProfiles.forPackage(packageName)
        if (detected !== SourceProfiles.GENERIC) return detected
        return state.availableProfiles.firstOrNull { it.id == state.settings.defaultProfileId }
            ?: SourceProfiles.GENERIC
    }

    /**
     * The escape hatch for shares that are too long to go through a share sheet: select in
     * the reading app, Copy, and read it here.
     */
    fun readFromClipboard(text: String?, quiet: Boolean = false) {
        if (text.isNullOrBlank()) {
            if (!quiet) _message.value = "剪贴板里没有文字"
            return
        }
        _state.update {
            it.copy(raw = text, origin = "剪贴板", sourcePackage = null, payloadOverride = null)
        }
    }

    /**
     * Opened directly rather than from a share — from the launcher or the quick settings
     * tile. [force] is for the tile, which exists for exactly this; otherwise it happens
     * only if the user turned it on.
     *
     * It always overwrites what is on screen. The app stays alive after a write, so
     * anything left over is from last time and is stale by definition — refusing to
     * replace it would mean the second capture of a session never arrived.
     */
    fun adoptClipboardIfEnabled(force: Boolean = false) {
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            if (!force && !settings.autoReadClipboard) return@launch

            // Android serves the clipboard only to the focused app, and on a cold start
            // focus lands a few frames after this composition runs. A single attempt
            // therefore reads nothing and the capture silently does not happen — which is
            // exactly what made the tile look broken. Retry briefly instead.
            repeat(CLIPBOARD_ATTEMPTS) { attempt ->
                val text = ClipboardReader.read(getApplication())
                if (!text.isNullOrBlank()) {
                    readFromClipboard(text, quiet = true)
                    return@launch
                }
                if (attempt < CLIPBOARD_ATTEMPTS - 1) delay(CLIPBOARD_RETRY_MS)
            }
        }
    }

    // ---- inputs ------------------------------------------------------------

    fun setProfile(profile: SourceProfile) = edit { it.copy(profile = profile) }

    /** Also used by a Direct Share shortcut, which arrives before the list is loaded. */
    fun selectTarget(id: Long?) {
        pendingTargetId = id
        edit { it.copy(selectedTargetId = id) }
    }

    fun editPayload(value: String) = _state.update { it.copy(payloadOverride = value) }
    fun regenerate() = _state.update { it.copy(payloadOverride = null) }

    fun setVault(value: String) = settings { it.copy(vault = value) }
    fun setHeading(value: String) = settings { it.copy(heading = value) }
    fun setTemplate(value: String) = settings { it.copy(template = value) }
    fun setPathTemplate(value: String) = settings { it.copy(pathTemplate = value) }
    fun setTags(value: String) = settings { it.copy(tags = value) }
    fun setMode(mode: WriteMode) = settings { it.copy(mode = mode) }
    fun setSilent(value: Boolean) = settings { it.copy(silent = value) }
    fun setAutoReadClipboard(value: Boolean) = settings { it.copy(autoReadClipboard = value) }
    fun setReturnToSource(value: Boolean) = settings { it.copy(returnToSource = value) }
    fun setCleanup(options: CleanupOptions) = settings { it.copy(cleanup = options) }
    fun setDefaultProfileId(value: String) = settings { it.copy(defaultProfileId = value) }
    fun setDefaultTargetId(value: Long?) = settings { it.copy(defaultTargetId = value) }

    /** Where a book's note would land, given the target currently chosen. */
    fun previewBookPath(title: String): String {
        val current = _state.value
        val target = current.chosenTarget ?: return Defaults.INBOX_NOTE
        return Template.substitute(
            target.path,
            TemplateValues(text = "", title = title.trim(), date = LocalDate.now().toString()),
        ).trim()
    }

    // ---- books -------------------------------------------------------------

    /**
     * Metadata for something being read. Kept apart from targets so a book is described
     * once and can be filed by whichever destination suits it.
     */
    fun saveBook(draft: BookDraft) {
        val title = draft.title.trim()
        if (title.isEmpty()) {
            _message.value = "先填书名"
            return
        }
        viewModelScope.launch {
            val existing = draft.rowId?.let { id ->
                bookDao.observeAll().first().firstOrNull { it.id == id }
            }
            val id = bookDao.upsert(
                BookEntity(
                    id = draft.rowId ?: 0,
                    title = title,
                    author = draft.author.trim(),
                    year = draft.year.trim(),
                    sortOrder = existing?.sortOrder ?: _state.value.savedBooks.size,
                )
            )
            pendingBookId = id
            _state.update { it.copy(selectedBookId = id) }

            if (draft.createSkeleton) {
                val path = previewBookPath(title)
                if (writeSkeleton(title, path, draft.author.trim(), draft.year.trim())) {
                    _message.value = "已保存「$title」并写入骨架"
                }
            } else {
                _message.value = if (existing == null) "已添加「$title」" else "已保存「$title」"
            }
        }
    }

    fun selectBook(id: Long?) {
        pendingBookId = id
        edit { it.copy(selectedBookId = id) }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch { bookDao.delete(book.id) }
        if (pendingBookId == book.id) pendingBookId = null
        _state.update {
            if (it.selectedBookId == book.id) it.copy(selectedBookId = null) else it
        }
    }

    fun moveBook(fromIndex: Int, toIndex: Int) {
        val list = _state.value.savedBooks.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        list.add(toIndex, list.removeAt(fromIndex))
        viewModelScope.launch {
            list.forEachIndexed { index, book ->
                if (book.sortOrder != index) bookDao.upsert(book.copy(sortOrder = index))
            }
        }
    }

    // ---- targets -----------------------------------------------------------

    /**
     * Remembers a book (or any other destination) so the next share can be filed without
     * typing. Optionally writes its skeleton, which is the only way a brand new note gets
     * the `## Quotes worth keeping` section the next write needs to aim at.
     *
     * Create and edit share this path, so editing keeps `seeded` — otherwise a book whose
     * skeleton is already written would look new again.
     */
    fun saveTarget(draft: TargetDraft) {
        val name = draft.name.trim()
        if (name.isEmpty()) {
            _message.value = "给这个目标起个名字"
            return
        }
        val path = draft.path.trim().ifBlank { _state.value.settings.pathTemplate }
        if (path.isBlank()) {
            _message.value = "填一下笔记路径"
            return
        }

        viewModelScope.launch {
            val existing = draft.rowId?.let { id -> targets.all().firstOrNull { it.id == id } }
            val id = targets.upsert(
                TargetEntity(
                    id = draft.rowId ?: 0,
                    name = name,
                    path = path,
                    heading = draft.heading.trim(),
                    formatId = draft.formatId,
                    sortOrder = existing?.sortOrder ?: _state.value.savedTargets.size,
                )
            )
            pendingTargetId = id
            _state.update { it.copy(selectedTargetId = id) }
            _message.value = if (existing == null) "已添加目标「$name」" else "已保存「$name」"
        }
    }

    /**
     * Moves a target one slot and persists the whole order, so the chip row and the
     * settings list cannot drift apart — they read the same column.
     */
    fun moveTarget(fromIndex: Int, toIndex: Int) {
        val list = _state.value.savedTargets.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        list.add(toIndex, list.removeAt(fromIndex))
        viewModelScope.launch {
            list.forEachIndexed { index, target ->
                if (target.sortOrder != index) targets.upsert(target.copy(sortOrder = index))
            }
        }
    }

    fun deleteTarget(target: TargetEntity) {
        viewModelScope.launch { targets.delete(target.id) }
        if (pendingTargetId == target.id) pendingTargetId = null
        _state.update {
            if (it.selectedTargetId == target.id) it.copy(selectedTargetId = null) else it
        }
    }

    // ---- output formats ----------------------------------------------------

    fun saveFormat(rowId: Long?, name: String, template: String) {
        if (name.isBlank()) {
            _message.value = "给这个格式起个名字"
            return
        }
        viewModelScope.launch {
            formatDao.upsert(
                FormatEntity(
                    id = rowId ?: 0,
                    name = name.trim(),
                    template = template,
                    sortOrder = _state.value.formats.size,
                )
            )
            _message.value = "已保存格式「$name」"
        }
    }

    fun deleteFormat(row: FormatEntity) {
        viewModelScope.launch {
            formatDao.delete(row.id)
            // Targets pointing at it fall back to the default rather than to nothing.
            _state.value.savedTargets
                .filter { it.formatId == row.id }
                .forEach { targets.upsert(it.copy(formatId = null)) }
        }
    }

    // ---- user profiles -----------------------------------------------------

    fun saveUserProfile(
        rowId: Long?,
        name: String,
        lineRules: String,
        inlineRules: String,
        template: String,
    ) {
        if (name.isBlank()) {
            _message.value = "给这个规则起个名字"
            return
        }
        viewModelScope.launch {
            val id = profiles.upsert(
                ProfileEntity(
                    id = rowId ?: 0,
                    name = name.trim(),
                    lineRules = lineRules,
                    inlineRules = inlineRules,
                    template = template.trim(),
                )
            )
            // Keep the just-edited profile selected so its effect is visible immediately.
            val assembled = SourceProfiles.ALL + profiles.observeAll().first().map { row ->
                userProfile(row.id, row.name, row.lineRules, row.inlineRules, row.template)
            }
            _state.update { current ->
                val edited = assembled.firstOrNull { it.id == userProfileId(id) }
                current.copy(profile = edited ?: current.profile, payloadOverride = null)
            }
            _message.value = "已保存规则「$name」"
        }
    }

    fun deleteUserProfile(row: ProfileEntity) {
        viewModelScope.launch {
            profiles.delete(row.id)
            // Mappings pointing at it would resolve to nothing, so clear them with it.
            _state.value.appMappings
                .filterValues { it == userProfileId(row.id) }
                .keys
                .forEach { appProfiles.delete(it) }
        }
    }

    // ---- per-app mapping ---------------------------------------------------

    fun setAppMapping(packageName: String, profileId: String) {
        viewModelScope.launch { appProfiles.upsert(AppProfileEntity(packageName, profileId)) }
    }

    fun clearAppMapping(packageName: String) {
        viewModelScope.launch { appProfiles.delete(packageName) }
    }

    // ---- history -----------------------------------------------------------

    fun resend(entry: HistoryEntity) {
        val outcome = container.sender.send(
            WriteRequest(
                vault = _state.value.settings.vault,
                filePath = entry.targetPath,
                heading = entry.heading,
                content = entry.payload,
                mode = _state.value.settings.mode,
            )
        )
        _message.value = when (outcome) {
            is SendOutcome.Dispatched -> "已重发到 ${entry.targetPath}"
            SendOutcome.NoObsidian -> "没找到 Obsidian"
            is SendOutcome.Failed -> "重发失败：${outcome.message}"
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            _state.value.history.forEach { history.delete(it.id) }
            _message.value = "已清空发送历史"
        }
    }

    fun messageShown() {
        _message.value = null
    }

    // ---- sending -----------------------------------------------------------

    fun send() {
        val current = _state.value
        if (current.raw.isBlank()) {
            _message.value = "没有可写入的内容"
            return
        }

        // Obsidian always comes to the foreground: a URI can only be handled by starting
        // the app, so the plugin's silent mode can only mean "don't open the note". Quiet
        // therefore has to mean "take control straight back" as well, which is what the
        // callback is for — it fires after Obsidian has finished, and starting our
        // activity is itself what returns us to the front.
        val wantsControlBack = current.settings.silent || current.settings.returnToSource
        if (current.settings.returnToSource) {
            current.sourcePackage?.let { container.pendingReturn.remember(it) }
        }

        val outcome = container.sender.send(
            WriteRequest(
                vault = current.settings.vault,
                filePath = current.targetPath,
                heading = current.effectiveHeading,
                content = current.payload,
                mode = current.settings.mode,
                silent = current.settings.silent,
                successCallback = if (wantsControlBack) RETURN_CALLBACK else null,
            )
        )

        record(current, outcome)

        _message.value = when (outcome) {
            is SendOutcome.Dispatched ->
                if (outcome.viaClipboard) "已发送，正文走剪贴板" else "已发送"
            SendOutcome.NoObsidian -> "没找到 Obsidian，可能没装或已停用；已存入待发队列"
            is SendOutcome.Failed -> "发送失败：${outcome.message}；已存入待发队列"
        }
    }

    /** Retries only ever happen while the app is on screen — see the note on the outbox. */
    fun retryOutbox() {
        viewModelScope.launch {
            val queued = outbox.observeAll().first()
            if (queued.isEmpty()) {
                _message.value = "待发队列是空的"
                return@launch
            }
            var sent = 0
            var failed = 0
            queued.forEach { entry ->
                val outcome = container.sender.send(
                    WriteRequest(
                        vault = entry.vault,
                        filePath = entry.filePath,
                        heading = entry.heading,
                        content = entry.payload,
                        mode = runCatching { WriteMode.valueOf(entry.mode) }
                            .getOrDefault(WriteMode.ADVANCED),
                    )
                )
                if (outcome is SendOutcome.Dispatched) {
                    outbox.delete(entry)
                    sent++
                } else {
                    outbox.recordFailure(entry.id, outcome.toString())
                    failed++
                }
            }
            _message.value = "重发完成：成功 $sent，仍失败 $failed"
        }
    }

    // ---- plumbing ----------------------------------------------------------

    /**
     * Written through the official URI on purpose: it has no heading parameter, so it works
     * on a note that does not exist yet and cannot no-op the way a missing heading does.
     */
    private fun writeSkeleton(name: String, path: String, author: String, year: String): Boolean {
        val skeleton = Defaults.bookSkeleton(
            title = name,
            author = author.ifBlank { null },
            year = year.ifBlank { null },
            date = LocalDate.now().toString(),
        )
        val outcome = container.sender.send(
            WriteRequest(
                vault = _state.value.settings.vault,
                filePath = path,
                heading = null,
                content = skeleton,
                mode = WriteMode.OFFICIAL,
            )
        )
        return when (outcome) {
            is SendOutcome.Dispatched -> true
            SendOutcome.NoObsidian -> {
                _message.value = "没找到 Obsidian，骨架没写"
                false
            }
            is SendOutcome.Failed -> {
                _message.value = "骨架写入失败：${outcome.message}"
                false
            }
        }
    }

    private fun record(state: ShareUiState, outcome: SendOutcome) {
        viewModelScope.launch {
            history.insert(
                HistoryEntity(
                    createdAt = System.currentTimeMillis(),
                    targetPath = state.targetPath,
                    heading = state.effectiveHeading,
                    payload = state.payload,
                    outcome = outcome::class.simpleName ?: "Unknown",
                    viaClipboard = (outcome as? SendOutcome.Dispatched)?.viaClipboard == true,
                    detail = (outcome as? SendOutcome.Failed)?.message.orEmpty(),
                )
            )
            if (outcome !is SendOutcome.Dispatched) {
                outbox.insert(
                    OutboxEntity(
                        createdAt = System.currentTimeMillis(),
                        vault = state.settings.vault,
                        filePath = state.targetPath,
                        heading = state.effectiveHeading,
                        payload = state.payload,
                        mode = state.settings.mode.name,
                        lastError = outcome.toString(),
                    )
                )
            }
        }
    }

    /** Any input change invalidates a hand-edited payload. */
    private fun edit(transform: (ShareUiState) -> ShareUiState) =
        _state.update { transform(it).copy(payloadOverride = null) }

    private fun settings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    private companion object {
        /**
         * Android only serves the clipboard to the focused app. On a cold start focus
         * arrives a few frames after the first composition, so the read is retried rather
         * than treating the first empty answer as "nothing to capture".
         */
        const val CLIPBOARD_ATTEMPTS = 6
        const val CLIPBOARD_RETRY_MS = 250L
    }
}
