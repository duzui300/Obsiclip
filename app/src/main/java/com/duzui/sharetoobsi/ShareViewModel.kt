package com.duzui.sharetoobsi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duzui.sharetoobsi.data.AppProfileEntity
import com.duzui.sharetoobsi.data.AppSettings
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
    val savedTargets: List<TargetEntity> = emptyList(),
    /** null is the inbox: whatever is being captured without naming a book first. */
    val selectedTargetId: Long? = null,
    /** Set once the user hand-edits the payload; cleared whenever an input changes. */
    val payloadOverride: String? = null,
) {
    val chosenTarget: TargetEntity? = savedTargets.firstOrNull { it.id == selectedTargetId }

    private val values: TemplateValues = TemplateValues(
        text = Cleanup.clean(raw, profile, settings.cleanup),
        title = chosenTarget?.name,
        author = chosenTarget?.author?.takeIf { it.isNotBlank() },
        year = chosenTarget?.year?.takeIf { it.isNotBlank() },
        tags = settings.tags.ifBlank { null },
        source = sourcePackage,
        date = LocalDate.now().toString(),
    )

    /** A user-written profile may carry its own output shape. */
    val effectiveTemplate: String = profile.template.ifBlank { settings.template }

    val payload: String = payloadOverride ?: Template.render(effectiveTemplate, values)

    val targetPath: String = chosenTarget?.path ?: settings.inboxPath

    /**
     * A heading that does not exist makes the Advanced URI silently write nothing, so the
     * inbox target is written without one rather than aimed at a guess.
     */
    val effectiveHeading: String? = chosenTarget?.heading?.takeIf { it.isNotBlank() }
}

class ShareViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer(application)
    private val targets = container.db.targets()
    private val history = container.db.history()
    private val outbox = container.db.outbox()
    private val profiles = container.db.profiles()
    private val appProfiles = container.db.appProfiles()

    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** A target chosen before the list finished loading, e.g. from a Direct Share shortcut. */
    private var pendingTargetId: Long? = null

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
            history.observeRecent().collect { rows -> _state.update { it.copy(history = rows) } }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { launchableApps(getApplication()) }.let { apps ->
                _state.update { it.copy(installedApps = apps) }
            }
        }
        viewModelScope.launch {
            targets.observeAll().collect { list ->
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
     * only if the user turned it on, and never over text already in hand.
     */
    fun adoptClipboardIfEnabled(force: Boolean = false) {
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            if (!force && !settings.autoReadClipboard) return@launch
            if (_state.value.raw.isNotBlank()) return@launch
            readFromClipboard(ClipboardReader.read(getApplication()), quiet = true)
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
    fun setInboxPath(value: String) = settings { it.copy(inboxPath = value) }
    fun setTags(value: String) = settings { it.copy(tags = value) }
    fun setMode(mode: WriteMode) = settings { it.copy(mode = mode) }
    fun setSilent(value: Boolean) = settings { it.copy(silent = value) }
    fun setAutoReadClipboard(value: Boolean) = settings { it.copy(autoReadClipboard = value) }
    fun setReturnToSource(value: Boolean) = settings { it.copy(returnToSource = value) }
    fun setCleanup(options: CleanupOptions) = settings { it.copy(cleanup = options) }
    fun setDefaultProfileId(value: String) = settings { it.copy(defaultProfileId = value) }

    /** The path a new target would get, so the add form can show it while the name is typed. */
    fun previewPath(name: String): String =
        Template.substitute(
            _state.value.settings.pathTemplate,
            TemplateValues(text = "", title = name.trim(), date = LocalDate.now().toString()),
        ).trim()

    // ---- targets -----------------------------------------------------------

    /**
     * Remembers a book (or any other destination) so the next share can be filed without
     * typing. Optionally writes its skeleton, which is the only way a brand new note gets
     * the `## Quotes worth keeping` section the next write needs to aim at.
     */
    fun addTarget(
        name: String,
        author: String,
        year: String,
        path: String,
        heading: String,
        createSkeleton: Boolean,
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _message.value = "先填书名"
            return
        }
        val resolvedPath = path.trim().ifBlank { previewPath(cleanName) }

        viewModelScope.launch {
            val id = targets.upsert(
                TargetEntity(
                    name = cleanName,
                    path = resolvedPath,
                    heading = heading.trim(),
                    author = author.trim(),
                    year = year.trim(),
                )
            )
            pendingTargetId = id
            _state.update { it.copy(selectedTargetId = id) }
            if (createSkeleton) {
                if (writeSkeleton(cleanName, resolvedPath, author.trim(), year.trim())) {
                    targets.markSeeded(id)
                    _message.value = "已添加「$cleanName」并写入骨架"
                }
            } else {
                _message.value = "已添加目标「$cleanName」"
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
                successCallback = if (current.settings.returnToSource) RETURN_CALLBACK else null,
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
}
