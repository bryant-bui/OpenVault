package io.openvault.app.service.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import java.util.Locale

enum class DetectedFieldKind {
    NONE,
    USERNAME,
    PASSWORD,
    OTP,
}

data class ParsedAutofillRequest(
    val packageName: String?,
    val webDomain: String?,
    val usernameIds: List<AutofillId>,
    val passwordIds: List<AutofillId>,
    val otpIds: List<AutofillId>,
    val capturedUsername: String? = null,
    val capturedPassword: String? = null,
) {
    val allTargetIds: List<AutofillId>
        get() = (usernameIds + passwordIds + otpIds).distinct()

    val hasAnyTarget: Boolean
        get() = usernameIds.isNotEmpty() || passwordIds.isNotEmpty() || otpIds.isNotEmpty()
}

/**
 * Pure, testable field classifier + AssistStructure tree parser for Android AutofillService.
 */
object AutofillStructureParser {

    private val PASSWORD_TOKENS = listOf("password", "passwd", "passcode", "pwd", "matkhau", "mật khẩu")
    private val USERNAME_TOKENS = listOf("email", "e-mail", "username", "user_name", "userid", "login", "account", "tài khoản", "đăng nhập")
    private val OTP_TOKENS = listOf("otp", "totp", "2fa", "mfa", "one-time-code", "verification", "authcode", "xác thực")
    private val IGNORE_TOKENS = listOf("search", "query", "find", "tìm kiếm", "filter", "comment")

    fun classifyFieldMetadata(
        autofillHints: List<String>,
        htmlTag: String?,
        htmlType: String?,
        htmlAutocomplete: String?,
        htmlNameOrId: String?,
        idEntry: String?,
        hintText: String?,
        inputType: Int,
        isEditable: Boolean,
    ): DetectedFieldKind {
        // Explicit W3C / Android autofill hints have top priority
        val lowerHints = autofillHints.map { it.lowercase(Locale.ROOT) }
        val lowerAc = htmlAutocomplete?.lowercase(Locale.ROOT).orEmpty()

        if (lowerHints.any { it.contains("otp") || it == "smsotpcode" || it == "2facode" } ||
            lowerAc.contains("one-time-code")
        ) {
            return DetectedFieldKind.OTP
        }

        if (lowerHints.any { it == View.AUTOFILL_HINT_PASSWORD || it == "newpassword" || it.contains("password") } ||
            lowerAc.contains("current-password") ||
            lowerAc.contains("new-password") ||
            htmlType.equals("password", ignoreCase = true)
        ) {
            return DetectedFieldKind.PASSWORD
        }

        if (lowerHints.any {
                it == View.AUTOFILL_HINT_USERNAME ||
                    it == View.AUTOFILL_HINT_EMAIL_ADDRESS ||
                    it.contains("username") ||
                    it.contains("email")
            } ||
            lowerAc.contains("username") ||
            lowerAc == "email" ||
            htmlType.equals("email", ignoreCase = true)
        ) {
            return DetectedFieldKind.USERNAME
        }

        // Check Android InputType password variations
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            ) {
                return DetectedFieldKind.PASSWORD
            }
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            ) {
                return DetectedFieldKind.USERNAME
            }
        } else if (inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) {
            return DetectedFieldKind.PASSWORD
        }

        val isWebInput = htmlTag.equals("input", ignoreCase = true)
        if (!isEditable && !isWebInput) return DetectedFieldKind.NONE

        val combinedText = listOfNotNull(htmlNameOrId, idEntry, hintText)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        if (combinedText.isBlank()) return DetectedFieldKind.NONE
        if (IGNORE_TOKENS.any { combinedText.contains(it) }) return DetectedFieldKind.NONE

        if (OTP_TOKENS.any { combinedText.contains(it) }) return DetectedFieldKind.OTP
        if (PASSWORD_TOKENS.any { combinedText.contains(it) }) return DetectedFieldKind.PASSWORD
        if (USERNAME_TOKENS.any { combinedText.contains(it) }) return DetectedFieldKind.USERNAME

        return DetectedFieldKind.NONE
    }

    fun parse(structure: AssistStructure): ParsedAutofillRequest {
        val packageName = structure.activityComponent?.packageName
        var webDomain: String? = null
        val usernameIds = mutableListOf<AutofillId>()
        val passwordIds = mutableListOf<AutofillId>()
        val otpIds = mutableListOf<AutofillId>()
        var capturedUsername: String? = null
        var capturedPassword: String? = null

        fun walk(node: AssistStructure.ViewNode) {
            if (webDomain.isNullOrBlank() && !node.webDomain.isNullOrBlank()) {
                webDomain = node.webDomain
            }

            val autofillId = node.autofillId
            if (autofillId != null) {
                val htmlInfo = node.htmlInfo
                val attrs = htmlInfo?.attributes.orEmpty().associate {
                    it.first.lowercase(Locale.ROOT) to it.second
                }
                val htmlTag = htmlInfo?.tag
                val htmlType = attrs["type"]
                val htmlAc = attrs["autocomplete"]
                val htmlNameOrId = listOfNotNull(attrs["name"], attrs["id"], attrs["placeholder"])
                    .joinToString(" ")
                    .takeIf { it.isNotBlank() }

                val className = node.className.orEmpty()
                val isEditable = className.contains("EditText", ignoreCase = true) ||
                    node.autofillType == View.AUTOFILL_TYPE_TEXT ||
                    htmlTag.equals("input", ignoreCase = true)

                val kind = classifyFieldMetadata(
                    autofillHints = node.autofillHints?.toList().orEmpty(),
                    htmlTag = htmlTag,
                    htmlType = htmlType,
                    htmlAutocomplete = htmlAc,
                    htmlNameOrId = htmlNameOrId,
                    idEntry = node.idEntry,
                    hintText = node.hint,
                    inputType = node.inputType,
                    isEditable = isEditable,
                )

                val currentText = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                    ?: node.text?.toString()

                when (kind) {
                    DetectedFieldKind.USERNAME -> {
                        usernameIds += autofillId
                        if (capturedUsername.isNullOrBlank() && !currentText.isNullOrBlank()) {
                            capturedUsername = currentText.trim()
                        }
                    }
                    DetectedFieldKind.PASSWORD -> {
                        passwordIds += autofillId
                        if (capturedPassword.isNullOrBlank() && !currentText.isNullOrBlank()) {
                            capturedPassword = currentText
                        }
                    }
                    DetectedFieldKind.OTP -> {
                        otpIds += autofillId
                    }
                    DetectedFieldKind.NONE -> Unit
                }
            }

            for (i in 0 until node.childCount) {
                walk(node.getChildAt(i))
            }
        }

        for (i in 0 until structure.windowNodeCount) {
            structure.getWindowNodeAt(i).rootViewNode?.let { walk(it) }
        }

        return ParsedAutofillRequest(
            packageName = packageName,
            webDomain = webDomain,
            usernameIds = usernameIds.distinct(),
            passwordIds = passwordIds.distinct(),
            otpIds = otpIds.distinct(),
            capturedUsername = capturedUsername,
            capturedPassword = capturedPassword,
        )
    }
}
