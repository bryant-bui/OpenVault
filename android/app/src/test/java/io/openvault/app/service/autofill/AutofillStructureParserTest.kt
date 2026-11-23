package io.openvault.app.service.autofill

import android.text.InputType
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test

class AutofillStructureParserTest {

    @Test
    fun detectsExplicitAutofillHints() {
        assertEquals(
            DetectedFieldKind.USERNAME,
            AutofillStructureParser.classifyFieldMetadata(
                autofillHints = listOf(View.AUTOFILL_HINT_EMAIL_ADDRESS),
                htmlTag = null,
                htmlType = null,
                htmlAutocomplete = null,
                htmlNameOrId = null,
                idEntry = null,
                hintText = null,
                inputType = InputType.TYPE_CLASS_TEXT,
                isEditable = true,
            ),
        )

        assertEquals(
            DetectedFieldKind.PASSWORD,
            AutofillStructureParser.classifyFieldMetadata(
                autofillHints = listOf(View.AUTOFILL_HINT_PASSWORD),
                htmlTag = null,
                htmlType = null,
                htmlAutocomplete = null,
                htmlNameOrId = null,
                idEntry = null,
                hintText = null,
                inputType = InputType.TYPE_CLASS_TEXT,
                isEditable = true,
            ),
        )

        assertEquals(
            DetectedFieldKind.OTP,
            AutofillStructureParser.classifyFieldMetadata(
                autofillHints = emptyList(),
                htmlTag = "input",
                htmlType = "text",
                htmlAutocomplete = "one-time-code",
                htmlNameOrId = "totp",
                idEntry = null,
                hintText = "6-digit code",
                inputType = InputType.TYPE_CLASS_NUMBER,
                isEditable = true,
            ),
        )
    }

    @Test
    fun ignoresSearchBarsAndDetectsVietnameseLoginFields() {
        assertEquals(
            DetectedFieldKind.NONE,
            AutofillStructureParser.classifyFieldMetadata(
                autofillHints = emptyList(),
                htmlTag = "input",
                htmlType = "text",
                htmlAutocomplete = null,
                htmlNameOrId = "search_user",
                idEntry = "search_bar",
                hintText = "Tìm kiếm tài khoản",
                inputType = InputType.TYPE_CLASS_TEXT,
                isEditable = true,
            ),
        )

        assertEquals(
            DetectedFieldKind.PASSWORD,
            AutofillStructureParser.classifyFieldMetadata(
                autofillHints = emptyList(),
                htmlTag = null,
                htmlType = null,
                htmlAutocomplete = null,
                htmlNameOrId = null,
                idEntry = "edt_pass",
                hintText = "Nhập mật khẩu",
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                isEditable = true,
            ),
        )
    }
}
