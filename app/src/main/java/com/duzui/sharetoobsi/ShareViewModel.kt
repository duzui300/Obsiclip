package com.duzui.sharetoobsi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duzui.sharetoobsi.data.AppSettings
import com.duzui.sharetoobsi.data.BookEntity
import com.duzui.sharetoobsi.data.HistoryEntity
import com.duzui.sharetoobsi.data.OutboxEntity
import com.duzui.sharetoobsi.data.TargetEntity
import com.duzui.sharetoobsi.domain.Cleanup
import com.duzui.sharetoobsi.domain.CleanupOptions
import com.duzui.sharetoobsi.domain.Defaults
import com.duzui.sharetoobsi.domain.SourceProfile
import com.duzui.sharetoobsi.domain.SourceProfiles
import com.duzui.sharetoobsi.domain.ShareParser
import com.duzui.sharetoobsi.domain.Template
import com.duzui.sharetoobsi.domain.TemplateValues
import com.duzui.sharetoobsi.domain.WriteMode
import com.duzui.sharetoobsi.domain.WriteRequest
import com.duzui.sharetoobsi.send.SendOutcome
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Obsidian calls this back once it has handled the URI, if the user asked to be returned. */
const val RETURN_CALLBACK = "sharetoobsi://return"

/**
 * Derived values are computed once at construction rather than in `get()`, so one state
 * change costs one pass of the pipeline instead of one per recomposition.
 */
data class ShareUiState(
    val raw: String = "",
    val sourcePackage: String? = null,
    val profile: SourceProfile = SourceProfiles.GENERIC,
    val settings: AppSettings = AppSettings(),
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val bookYear: String = "",
    /** True when the book name above was read out of the shared text rather than typed. */
    val hintApplied: Boolean = false,
    /** False until a Book skeleton is known to have been written for this title. */
    val bookSeeded: Boolean = false,
    /** null means "follow the current book, falling back to the inbox". */
    val selectedTargetId: Long? = null,
    val savedTargets: List<TargetEntity> = emptyList(),
    /** Set once the user hand-edits the payload; cleared whenever an input changes. */
    val payloadOverride: String? = null,
) {
    private val values: TemplateValues = TemplateValues(
        text = Cleanup.clean(raw, profile, settings.cleanup),
        title = bookTitle.ifBlank { null },
        author = bookAuthor.ifBlank { null },
        year = bookYear.ifBlank { null },
        tags = settings.tags.ifBlank { null },
        source = sourcePackage,
        date = LocalDate.now().toString(),
    )

    val payload: String = payloadOverride ?: Template.render(settings.template, values)

    val chosenTarget: TargetEntity? = savedTargets.firstOrNull { it.id == selectedTargetId }

    val targetsBook: Boolean = chosenTarget == null && bookTitle.isNotBlank()

    val bookPath: String = Template.substitute(settings.pathTemplate, values).trim()

    val targetPath: String = when {
        chosenTarget != null -> chosenTarget.path
        targetsBook -> bookPath
        else -> settings.inboxPath
    }

    /**
     * A heading that does not exist makes the Advanced URI silently write nothing, so a
     * target with no heading is written without one rather than aimed at a guess.
     */
    val effectiveHeading: String? = when {
        chosenTarget != null -> chosenTarget.heading.ifBlank { null }
        targetsBook -> settings.heading.ifBlank { null }
        else -> null
    }
}

class ShareViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer(application)
    private val books = container.db.books()
    private val targets = container.db.targets()
    private val history = container.db.history()
    private val outbox = container.db.outbox()

    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                // Settings reshape the payload, so a hand edit no longer applies.
                _state.update { it.copy(settings = settings, payloadOverride = null) }
            }
        }
        viewModelScope.launch {
            targets.observeAll().collect { list -> _state.update { it.copy(savedTargets = list) } }
        }
        viewModelScope.launch {
            // Pick up where the last session left off, once, and only if nothing is set yet.
            val recent = books.observeMostRecent().first()
            if (recent != null) {
                _state.update {
                    if (it.bookTitle.isBlank()) {
                        it.copy(
                            bookTitle = recent.title,
                            bookAuthor = recent.author,
                            bookYear = recent.year,
                            bookSeeded = recent.seeded,
                        )
                    } else {
                        it
                    }
                }
            }
        }
    }

    // ---- incoming share ----------------------------------------------------

    fun onShare(share: IncomingShare?) {
        if (share == null || share.text.isBlank()) return
        val detected = SourceProfiles.forPackage(share.sourcePackage)

        // Kindle and friends name the book in a preamble. Using it is what makes sharing
        // from a *different* book just work — that is precisely when the target changes.
        val hint = ShareParser.extract(share.text)

        _state.update { current ->
            current.copy(
                raw = share.text,
                sourcePackage = share.sourcePackage,
                // Nothing recognised the source, so fall back to the configured default.
                profile = if (detected === SourceProfiles.GENERIC) {
                    SourceProfiles.byId(current.settings.defaultProfileId)
                } else {
                    detected
                },
                // A recognised title replaces the author too, so a stale author from the
                // previous book cannot leak into the new one's attribution line.
                bookTitle = hint.title ?: current.bookTitle,
                bookAuthor = if (hint.title != null) hint.author.orEmpty() else current.bookAuthor,
                hintApplied = hint.title != null,
                payloadOverride = null,
            )
        }

        if (hint.title != null) rememberBook(_state.value)
    }

    // ---- inputs ------------------------------------------------------------

    fun setProfile(profile: SourceProfile) = edit { it.copy(profile = profile) }

    fun setBookTitle(value: String) = editBook { it.copy(bookTitle = value) }
    fun setBookAuthor(value: String) = editBook { it.copy(bookAuthor = value) }
    fun setBookYear(value: String) = editBook { it.copy(bookYear = value) }

    fun selectTarget(id: Long?) = edit { it.copy(selectedTargetId = id) }

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
    fun setReturnToSource(value: Boolean) = settings { it.copy(returnToSource = value) }
    fun setCleanup(options: CleanupOptions) = settings { it.copy(cleanup = options) }

    fun saveTarget(name: String, path: String, heading: String) {
        if (name.isBlank() || path.isBlank()) return
        viewModelScope.launch {
            targets.upsert(TargetEntity(name = name.trim(), path = path.trim(), heading = heading.trim()))
        }
    }

    fun deleteTarget(target: TargetEntity) {
        viewModelScope.launch { targets.delete(target.id) }
        _state.update {
            if (it.selectedTargetId == target.id) it.copy(selectedTargetId = null) else it
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

        rememberBook(current)
        record(current, outcome)

        _message.value = when (outcome) {
            is SendOutcome.Dispatched ->
                if (outcome.viaClipboard) "已发送，正文走剪贴板" else "已发送"
            SendOutcome.NoObsidian -> "没找到 Obsidian，可能没装或已停用；已存入待发队列"
            is SendOutcome.Failed -> "发送失败：${outcome.message}；已存入待发队列"
        }
    }

    /**
     * Writes the Book skeleton the vault's own template produces.
     *
     * Deliberately goes through the official URI: it takes no heading, so it works on a
     * note that does not exist yet and cannot silently no-op the way a missing heading
     * does. The skeleton ends with `## Quotes worth keeping`, so the heading is in place
     * for every send after this one.
     *
     * Explicit rather than automatic on purpose — on a note that already exists the
     * official URI appends, which would duplicate the frontmatter and the heading.
     */
    fun createSkeleton() {
        val current = _state.value
        val title = current.bookTitle.trim()
        if (title.isEmpty()) {
            _message.value = "先填书名"
            return
        }

        val skeleton = Defaults.bookSkeleton(
            title = title,
            author = current.bookAuthor.trim().ifBlank { null },
            year = current.bookYear.trim().ifBlank { null },
            date = LocalDate.now().toString(),
        )

        val outcome = container.sender.send(
            WriteRequest(
                vault = current.settings.vault,
                filePath = current.bookPath,
                heading = null,
                content = skeleton,
                mode = WriteMode.OFFICIAL,
                silent = true,
            )
        )

        when (outcome) {
            is SendOutcome.Dispatched -> {
                viewModelScope.launch {
                    books.upsert(
                        BookEntity(
                            title = title,
                            author = current.bookAuthor.trim(),
                            year = current.bookYear.trim(),
                            seeded = true,
                            lastUsedAt = System.currentTimeMillis(),
                        )
                    )
                    _state.update {
                        if (it.bookTitle.trim() == title) it.copy(bookSeeded = true) else it
                    }
                }
                _message.value = "已写入书目骨架：${current.bookPath}"
            }
            SendOutcome.NoObsidian -> _message.value = "没找到 Obsidian，可能没装或已停用"
            is SendOutcome.Failed -> _message.value = "写入骨架失败：${outcome.message}"
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
                        mode = runCatching { WriteMode.valueOf(entry.mode) }.getOrDefault(WriteMode.ADVANCED),
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

    private fun rememberBook(state: ShareUiState) {
        val title = state.bookTitle.trim()
        if (title.isEmpty()) return
        viewModelScope.launch {
            // Preserve `seeded`: REPLACE would otherwise wipe the fact that the skeleton is written.
            val seeded = books.find(title)?.seeded ?: false
            books.upsert(
                BookEntity(
                    title = title,
                    author = state.bookAuthor.trim(),
                    year = state.bookYear.trim(),
                    seeded = seeded,
                    lastUsedAt = System.currentTimeMillis(),
                )
            )
            _state.update {
                if (it.bookTitle.trim() == title) it.copy(bookSeeded = seeded) else it
            }
        }
    }

    private fun record(state: ShareUiState, outcome: SendOutcome) {
        viewModelScope.launch {
            history.insert(
                HistoryEntity(
                    createdAt = System.currentTimeMillis(),
                    targetPath = state.targetPath,
                    payload = state.payload,
                    outcome = outcome::class.simpleName ?: "Unknown",
                    viaClipboard = (outcome as? SendOutcome.Dispatched)?.viaClipboard == true,
                    detail = (outcome as? SendOutcome.Failed)?.message.orEmpty(),
                )
            )
            // Only a genuine dispatch failure can be retried; NoObsidian can too, later.
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

    private fun editBook(transform: (ShareUiState) -> ShareUiState) {
        edit(transform)
        rememberBook(_state.value)
    }

    private fun settings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }
}
