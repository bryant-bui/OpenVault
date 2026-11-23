package io.openvault.app.service.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.InlinePresentation
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.v1.InlineSuggestionUi
import io.openvault.app.R
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.core.crypto.VaultItem

object AutofillDatasetBuilder {

    fun createDropdownPresentation(
        context: Context,
        title: String,
        subtitle: String,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.item_autofill_suggestion).apply {
        setTextViewText(R.id.autofill_title, title)
        setTextViewText(R.id.autofill_subtitle, subtitle)
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    fun createInlinePresentation(
        context: Context,
        spec: InlinePresentationSpec,
        title: String,
        subtitle: String,
        requestCode: Int,
    ): InlinePresentation? = runCatching {
        val attribution = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val content = InlineSuggestionUi.newContentBuilder(attribution)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setStartIcon(Icon.createWithResource(context, R.drawable.ic_openvault_logo))
            .build()
        InlinePresentation(content.slice, spec, false)
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun buildCredentialDataset(
        context: Context,
        item: VaultItem,
        usernameIds: List<AutofillId>,
        passwordIds: List<AutofillId>,
        otpIds: List<AutofillId>,
        inlineSpec: InlinePresentationSpec? = null,
        requestCode: Int = 100,
    ): Dataset? {
        val login = item.login ?: return null
        val subtitle = login.username.ifBlank { item.primarySubtitle }
        val dropdown = createDropdownPresentation(context, item.name, subtitle)
        val inline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlineSpec != null) {
            createInlinePresentation(context, inlineSpec, item.name, subtitle, requestCode)
        } else {
            null
        }

        val builder = Dataset.Builder(dropdown)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inline != null) {
            builder.setInlinePresentation(inline)
        }

        var populated = false
        if (login.username.isNotBlank()) {
            for (id in usernameIds) {
                builder.setValue(id, AutofillValue.forText(login.username))
                populated = true
            }
        }
        if (login.password.isNotBlank()) {
            for (id in passwordIds) {
                builder.setValue(id, AutofillValue.forText(login.password))
                populated = true
            }
        }
        val totpSecret = login.totp
        if (!totpSecret.isNullOrBlank() && otpIds.isNotEmpty()) {
            val code = TotpEngine.generateFromInput(totpSecret)?.code
            if (!code.isNullOrBlank()) {
                for (id in otpIds) {
                    builder.setValue(id, AutofillValue.forText(code))
                    populated = true
                }
            }
        }

        return if (populated) builder.build() else null
    }
}
