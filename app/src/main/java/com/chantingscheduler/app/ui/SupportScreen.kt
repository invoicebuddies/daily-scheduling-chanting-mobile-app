package com.chantingscheduler.app.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chantingscheduler.app.BuildConfig
import com.chantingscheduler.app.R
import com.chantingscheduler.app.ui.theme.UrbanistFamily
import java.util.Locale

private const val KOFI_URL = "https://ko-fi.com/xia0da"

/**
 * Support & feedback: a zero-pressure tip card (gated on
 * [BuildConfig.SHOW_TIP_LINK]) and a feedback form that hands off to the
 * user's own email app — no backend, no network, nothing sent silently.
 */
@Composable
fun SupportScreen(
    showTipLink: Boolean,
    scheduleCount: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Text(
                stringResource(R.string.support_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(20.dp))

        if (showTipLink) {
            TipCard()
            Spacer(Modifier.height(16.dp))
        }

        FeedbackCard(scheduleCount = scheduleCount)

        Spacer(Modifier.height(24.dp))
    }
}

/** Warm, optional, never repeated elsewhere in the app (no-nag rule). */
@Composable
private fun TipCard() {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.support_tip_title),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = UrbanistFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.support_tip_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)))
                    }
                },
                shape = RoundedCornerShape(14.dp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.support_tip_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackCard(scheduleCount: Int) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var topic by remember { mutableStateOf(FeedbackComposer.Topic.BUG) }
    var topicMenuOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(true) }
    var showDiagnosticsPreview by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }

    // Gathered once per composition; exactly the fields the preview shows.
    val diagnostics = remember(scheduleCount) { gatherDiagnostics(context, scheduleCount) }

    val emailValid = email.isBlank() ||
        android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val messageValid = message.trim().length in MESSAGE_MIN..MESSAGE_MAX
    val canSend = messageValid && emailValid

    val composedBody = {
        FeedbackComposer.body(
            topic = topic,
            message = message,
            replyEmail = email,
            includeDiagnostics = includeDiagnostics,
            diagnostics = diagnostics,
        )
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.support_feedback_title),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = UrbanistFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))

            // Topic dropdown.
            ExposedDropdownMenuBox(
                expanded = topicMenuOpen,
                onExpandedChange = { topicMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = stringResource(topicLabelRes(topic)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.feedback_topic_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = topicMenuOpen) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = topicMenuOpen,
                    onDismissRequest = { topicMenuOpen = false },
                ) {
                    FeedbackComposer.Topic.entries.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(stringResource(topicLabelRes(entry))) },
                            onClick = {
                                topic = entry
                                topicMenuOpen = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = message,
                onValueChange = { if (it.length <= MESSAGE_MAX) message = it },
                label = { Text(stringResource(R.string.feedback_message_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.feedback_char_count,
                            message.length,
                            MESSAGE_MAX,
                        ),
                    )
                },
                isError = message.isNotBlank() && !messageValid,
                minLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.feedback_email_label)) },
                supportingText = {
                    if (email.isNotBlank() && !emailValid) {
                        Text(stringResource(R.string.feedback_email_invalid))
                    }
                },
                isError = email.isNotBlank() && !emailValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // Diagnostics opt-in with an exact preview of what would be sent.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = includeDiagnostics,
                    onCheckedChange = { includeDiagnostics = it },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.feedback_include_diagnostics),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = { showDiagnosticsPreview = !showDiagnosticsPreview }) {
                        Text(
                            stringResource(
                                if (showDiagnosticsPreview) {
                                    R.string.feedback_preview_hide
                                } else {
                                    R.string.feedback_preview_show
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Icon(
                            if (showDiagnosticsPreview) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (showDiagnosticsPreview) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        diagnosticsPreviewText(diagnostics),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(FeedbackComposer.RECIPIENT))
                        putExtra(Intent.EXTRA_SUBJECT, FeedbackComposer.SUBJECT)
                        putExtra(Intent.EXTRA_TEXT, composedBody())
                    }
                    val resolvable = intent.resolveActivity(context.packageManager) != null
                    if (resolvable) {
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            showCopyDialog = true
                        }
                    } else {
                        showCopyDialog = true
                    }
                },
                enabled = canSend,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    stringResource(R.string.feedback_send),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.feedback_send_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showCopyDialog) {
        AlertDialog(
            onDismissRequest = { showCopyDialog = false },
            title = { Text(stringResource(R.string.feedback_no_email_title)) },
            text = {
                Text(stringResource(R.string.feedback_no_email_body, FeedbackComposer.RECIPIENT))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(composedBody()))
                        showCopyDialog = false
                    },
                ) {
                    Text(stringResource(R.string.feedback_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCopyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun gatherDiagnostics(
    context: Context,
    scheduleCount: Int,
): FeedbackComposer.Diagnostics {
    val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != false
    val batteryExempt = context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
    val fullScreenAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
    return FeedbackComposer.Diagnostics(
        appVersion = BuildConfig.VERSION_NAME,
        androidVersion = Build.VERSION.RELEASE ?: "unknown",
        deviceManufacturerModel = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" "),
        appLanguage = Locale.getDefault().displayName,
        exactAlarmsAllowed = exactAllowed,
        batteryOptimisationExempt = batteryExempt,
        fullScreenIntentAllowed = fullScreenAllowed,
        scheduleCount = scheduleCount,
    )
}

/** Exactly the block [FeedbackComposer.body] appends when the tick is on. */
private fun diagnosticsPreviewText(d: FeedbackComposer.Diagnostics): String = buildString {
    appendLine("App version: ${d.appVersion}")
    appendLine("Android version: ${d.androidVersion}")
    appendLine("Device: ${d.deviceManufacturerModel}")
    appendLine("App language: ${d.appLanguage}")
    appendLine("Exact alarms: ${if (d.exactAlarmsAllowed) "Allowed" else "Not allowed"}")
    appendLine(
        "Battery optimisation: " + if (d.batteryOptimisationExempt) "Unrestricted" else "Restricted",
    )
    appendLine("Full-screen alarms: ${if (d.fullScreenIntentAllowed) "Allowed" else "Not allowed"}")
    append("Schedules: ${d.scheduleCount}")
}

private fun topicLabelRes(topic: FeedbackComposer.Topic): Int = when (topic) {
    FeedbackComposer.Topic.BUG -> R.string.feedback_topic_bug
    FeedbackComposer.Topic.FEATURE -> R.string.feedback_topic_feature
    FeedbackComposer.Topic.QUESTION -> R.string.feedback_topic_question
    FeedbackComposer.Topic.TRANSLATION -> R.string.feedback_topic_translation
    FeedbackComposer.Topic.OTHER -> R.string.feedback_topic_other
}

private const val MESSAGE_MIN = 10
private const val MESSAGE_MAX = 2000
