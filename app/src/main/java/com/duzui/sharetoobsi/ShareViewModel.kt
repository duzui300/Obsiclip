package com.duzui.sharetoobsi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.duzui.sharetoobsi.domain.Cleanup
import com.duzui.sharetoobsi.domain.CleanupOptions
import com.duzui.sharetoobsi.domain.Defaults
import com.duzui.sharetoobsi.domain.SourceProfile
import com.duzui.sharetoobsi.domain.SourceProfiles
import com.duzui.sharetoobsi.domain.Template
import com.duzui.sharetoobsi.domain.TemplateValues
import com.duzui.sharetoobsi.domain.WriteMode
import com.duzui.sharetoobsi.domain.WriteRequest
import com.duzui.sharetoobsi.send.ObsidianSender
import com.duzui.sharetoobsi.send.SendOutcome
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The derived strings are computed once per instance rather than in `get()`, so a state
 * change costs one pass of the pipeline instead of one per recomposition.
 */
data class ShareUiState(
    val raw: String = "",
    val sourcePackage: String? = null,
    val profile: SourceProfile = SourceProfiles.GENERIC,
    val options: CleanupOptions = CleanupOptions(),
    val vault: String = Defaults.VAULT,
    val heading: String = Defaults.HEADING,
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val bookYear: String = "",
    val tags: String = Defaults.TAGS,
    val template: String = Defaults.TEMPLATE,
    val pathTemplate: String = Defaults.BOOK_PATH_TEMPLATE,
    val inboxPath: String = Defaults.INBOX_NOTE,
    val mode: WriteMode = WriteMode.ADVANCED,
    /** Set once the user hand-edits the payload; cleared when any input changes. */
    val payloadOverride: String? = null,
) {
    val values: TemplateValues = TemplateValues(
        text = Cleanup.clean(raw, profile, options),
        title = bookTitle.ifBlank { null },
        author = bookAuthor.ifBlank { null },
        year = bookYear.ifBlank { null },
        tags = tags.ifBlank { null },
        source = sourcePackage,
        date = LocalDate.now().toString(),
    )

    val payload: String = payloadOverride ?: Template.render(template, values)

    val targetsBook: Boolean = bookTitle.isNotBlank()

    val targetPath: String =
        if (targetsBook) Template.substitute(pathTemplate, values).trim() else inboxPath

    /**
     * A missing heading makes the Advanced URI silently write nothing, so the inbox note
     * is written without one — it has no quotes section to aim at.
     */
    val effectiveHeading: String? = if (targetsBook) heading.takeIf { it.isNotBlank() } else null
}

class ShareViewModel(application: Application) : AndroidViewModel(application) {

    private val sender = ObsidianSender(application)

    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onShare(share: IncomingShare?) {
        if (share == null || share.text.isBlank()) return
        edit {
            it.copy(
                raw = share.text,
                sourcePackage = share.sourcePackage,
                profile = SourceProfiles.forPackage(share.sourcePackage),
            )
        }
    }

    fun setProfile(profile: SourceProfile) = edit { it.copy(profile = profile) }
    fun setBookTitle(value: String) = edit { it.copy(bookTitle = value) }
    fun setBookAuthor(value: String) = edit { it.copy(bookAuthor = value) }
    fun setBookYear(value: String) = edit { it.copy(bookYear = value) }
    fun setTags(value: String) = edit { it.copy(tags = value) }
    fun setMode(mode: WriteMode) = edit { it.copy(mode = mode) }

    /** A hand edit wins until some other input changes, which is what [edit] undoes. */
    fun editPayload(value: String) = _state.update { it.copy(payloadOverride = value) }

    fun regenerate() = _state.update { it.copy(payloadOverride = null) }

    fun messageShown() {
        _message.value = null
    }

    fun send() {
        val current = _state.value
        if (current.raw.isBlank()) {
            _message.value = "没有可写入的内容"
            return
        }

        val outcome = sender.send(
            WriteRequest(
                vault = current.vault,
                filePath = current.targetPath,
                heading = current.effectiveHeading,
                content = current.payload,
                mode = current.mode,
            )
        )

        _message.value = when (outcome) {
            is SendOutcome.Dispatched ->
                if (outcome.viaClipboard) "已发送，正文走剪贴板" else "已发送"
            SendOutcome.NoObsidian -> "没找到 Obsidian，可能没装或已停用"
            is SendOutcome.Failed -> "发送失败：${outcome.message}"
        }
    }

    private fun edit(transform: (ShareUiState) -> ShareUiState) =
        _state.update { transform(it).copy(payloadOverride = null) }
}
