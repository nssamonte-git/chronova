package noel.chronova.feature.account.presentation

import android.Manifest
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import noel.chronova.core.designsystem.ChronovaSpacing
import noel.chronova.core.designsystem.R as DesignR
import noel.chronova.core.designsystem.numeric
import noel.chronova.core.model.ReminderLead
import noel.chronova.core.model.ThemeMode
import noel.chronova.core.ui.R as CoreUiR
import noel.chronova.core.ui.components.BannerKind
import noel.chronova.core.ui.components.ChoiceChips
import noel.chronova.core.ui.components.ChronovaCard
import noel.chronova.core.ui.components.ChronovaTopBar
import noel.chronova.core.ui.components.ChronovaTopBarTitle
import noel.chronova.core.ui.components.ConfirmDialog
import noel.chronova.core.ui.components.DestructiveTextButton
import noel.chronova.core.ui.components.InlineBanner
import noel.chronova.core.ui.components.SectionHeader
import noel.chronova.core.ui.sync.SyncPhase
import noel.chronova.core.ui.sync.SyncUi
import noel.chronova.core.ui.sync.syncFailureText
import noel.chronova.core.ui.sync.syncStatusText

/** Sign up, confirm, sign in and sign out for sync. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
/** [onBack] null: the sign-in page that opens the app (no way back, the app's name and logo). */
fun AccountScreen(viewModel: AccountViewModel, onBack: (() -> Unit)?) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val sessionEnded by viewModel.sessionEnded.collectAsStateWithLifecycle()
    val pending by viewModel.pendingCount.collectAsStateWithLifecycle()
    val phase by viewModel.syncPhase.collectAsStateWithLifecycle()
    val deleteFailure by viewModel.deleteFailure.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val reminderLead by viewModel.reminderLead.collectAsStateWithLifecycle()
    AccountContent(
        page = AccountPageUi(authState, form, dialog, sessionEnded, pending, phase, deleteFailure, viewModel.lastSyncedAt(), themeMode, reminderLead),
        actions = remember(viewModel) {
            AccountActions(
                onEmailChange = viewModel::onEmailChange,
                onCodeChange = viewModel::onCodeChange,
                onPasswordChange = viewModel::onPasswordChange,
                onRememberChange = viewModel::onRememberChange,
                onSubmit = viewModel::submit,
                onSetMode = viewModel::setMode,
                onResendCode = viewModel::resendCode,
                onSyncNow = { viewModel.syncNow() },
                onRequestSignOut = viewModel::requestSignOut,
                onRequestDeleteAccount = viewModel::requestDeleteAccount,
                onConfirmDialog = viewModel::confirmDialog,
                onDismissDialog = viewModel::dismissDialog,
                onThemeModeChange = viewModel::setThemeMode,
                onReminderLeadChange = viewModel::setReminderLead,
            )
        },
        onBack = onBack,
    )
}

/** The account screen without its ViewModel. [onBack] null: the sign-in page that opens the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountContent(
    page: AccountPageUi,
    actions: AccountActions,
    onBack: (() -> Unit)?,
    /** The time "last synced 5 minutes ago" is counted from; fixed in screenshots. */
    now: () -> Long = System::currentTimeMillis,
) {
    page.dialog?.let { AccountConfirmDialog(it, onConfirm = actions.onConfirmDialog, onDismiss = actions.onDismissDialog) }
    Scaffold(
        topBar = {
            ChronovaTopBar(
                title = { ChronovaTopBarTitle(stringResource(if (onBack == null) CoreUiR.string.app_name else R.string.account_title)) },
                onBack = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    // Cards reach nearer the edges than a form's fields do.
                    .padding(if (page.auth is AccountAuthUi.SignedIn) ChronovaSpacing.screenGutter else ChronovaSpacing.sheetGutter),
                verticalArrangement = Arrangement.spacedBy(if (page.auth is AccountAuthUi.SignedIn) ChronovaSpacing.m else ChronovaSpacing.l),
            ) {
                if (onBack == null) {
                    Image(
                        painterResource(DesignR.drawable.brand_splash_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .align(Alignment.CenterHorizontally),
                    )
                }
                when (val state = page.auth) {
                    AccountAuthUi.NotConfigured -> Explanation(
                        stringResource(if (onBack == null) R.string.account_no_server else R.string.account_not_configured),
                    )
                    AccountAuthUi.Checking -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    is AccountAuthUi.SignedIn -> SignedIn(state.email, page, actions, now)
                    AccountAuthUi.SignedOut -> SignInForm(page.form, page.sessionEnded, actions, required = onBack == null)
                }
            }
        }
    }
}

@Composable
private fun Explanation(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SignedIn(email: String, page: AccountPageUi, actions: AccountActions, now: () -> Long) {
    val phase = page.syncPhase
    // Who is signed in, and the way out.
    AccountCard {
        Text(stringResource(R.string.account_signed_in_as, email), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = actions.onRequestSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_sign_out))
        }
    }
    // Sync: how it works, where it stands, and the button.
    AccountCard {
        SectionHeader(stringResource(R.string.account_sync))
        Explanation(stringResource(R.string.account_signed_in_body))
        val ui = SyncUi(configured = true, signedIn = true, pending = page.pending, phase = phase)
        val status = syncStatusText(ui) ?: lastSyncedText(page.lastSyncedAt, now())
        if (phase is SyncPhase.Failed) {
            InlineBanner(status, kind = BannerKind.Error)
        } else {
            Text(status, style = MaterialTheme.typography.bodyMedium.numeric(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = actions.onSyncNow, enabled = phase != SyncPhase.Syncing, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(CoreUiR.string.sync_now))
        }
    }
    AccountCard { ThemeChoice(page.themeMode, actions.onThemeModeChange) }
    AccountCard { ReminderChoice(page.reminderLead, actions.onReminderLeadChange) }
    // Apart from the cards: it is not a setting, and it can't be undone.
    DestructiveTextButton(stringResource(R.string.account_delete), actions.onRequestDeleteAccount, Modifier.fillMaxWidth())
    page.deleteFailure?.let {
        InlineBanner(stringResource(R.string.account_delete_failed, syncFailureText(it)), kind = BannerKind.Error)
    }
}

/** One group of the signed-in page: a card with its content in a column. */
@Composable
private fun AccountCard(content: @Composable ColumnScope.() -> Unit) {
    ChronovaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ChronovaSpacing.l), verticalArrangement = Arrangement.spacedBy(ChronovaSpacing.m), content = content)
    }
}

/**
 * When task reminders come. Choosing one asks for the notification permission (Android 13+)
 * if it isn't granted; without it the page says reminders can't show.
 */
@Composable
private fun ReminderChoice(selected: ReminderLead, onSelect: (ReminderLead) -> Unit) {
    val context = LocalContext.current
    // Previews and screenshots have no activity to ask from, nor notification settings to read.
    val preview = LocalInspectionMode.current
    var allowed by remember { mutableStateOf(preview || NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val ask: ((String) -> Unit)? = if (preview) {
        null
    } else {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }
        launcher::launch
    }
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.account_reminders))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(reminderLabel(selected), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                ReminderLead.entries.forEach { lead ->
                    DropdownMenuItem(
                        text = { Text(reminderLabel(lead)) },
                        onClick = {
                            open = false
                            onSelect(lead)
                            if (lead != ReminderLead.OFF && !allowed && Build.VERSION.SDK_INT >= 33) {
                                ask?.invoke(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                }
            }
        }
        if (selected != ReminderLead.OFF && !allowed) {
            InlineBanner(stringResource(R.string.account_reminders_blocked), kind = BannerKind.Warning)
        }
    }
}

@Composable
private fun reminderLabel(lead: ReminderLead): String = stringResource(
    when (lead) {
        ReminderLead.OFF -> R.string.reminders_off
        ReminderLead.AT_START -> R.string.reminders_at_start
        ReminderLead.MINUTES_15 -> R.string.reminders_15_minutes
        ReminderLead.HOUR_1 -> R.string.reminders_1_hour
        ReminderLead.DAY_1 -> R.string.reminders_1_day
    },
)

/** System / Light / Dark for this device. */
@Composable
private fun ThemeChoice(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ChronovaSpacing.xs)) {
        SectionHeader(stringResource(R.string.account_theme))
        ChoiceChips(
            options = ThemeMode.entries,
            selected = selected,
            label = { mode ->
                stringResource(
                    when (mode) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    },
                )
            },
            onSelect = onSelect,
            fillWidth = true,
        )
    }
}

@Composable
private fun lastSyncedText(at: Long?, now: Long): String =
    if (at == null) {
        stringResource(R.string.sync_never)
    } else {
        stringResource(
            R.string.sync_last,
            DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS).toString(),
        )
    }

@Composable
private fun AccountConfirmDialog(dialog: AccountDialog, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, body, confirm) = when (dialog) {
        is AccountDialog.SignOut -> Triple(
            stringResource(R.string.account_sign_out_title),
            if (dialog.unsynced > 0) {
                pluralStringResource(R.plurals.account_sign_out_unsynced, dialog.unsynced, dialog.unsynced)
            } else {
                stringResource(R.string.account_sign_out_body)
            },
            stringResource(R.string.account_sign_out),
        )
        AccountDialog.DeleteAccount -> Triple(
            stringResource(R.string.account_delete_title),
            stringResource(R.string.account_delete_body),
            stringResource(R.string.account_delete_confirm),
        )
    }
    ConfirmDialog(title = title, text = body, confirmLabel = confirm, onConfirm = onConfirm, onDismiss = onDismiss, destructive = true)
}

@Composable
private fun SignInForm(form: AccountForm, sessionEnded: Boolean, actions: AccountActions, required: Boolean) {
    val title = when (form.mode) {
        AccountMode.SIGN_IN -> R.string.account_sign_in
        AccountMode.SIGN_UP -> R.string.account_create
        AccountMode.CONFIRM -> R.string.account_confirm
        AccountMode.FORGOT -> R.string.account_forgot_title
        AccountMode.RESET -> R.string.account_reset_title
    }
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
    if (required && sessionEnded && form.mode == AccountMode.SIGN_IN) {
        InlineBanner(stringResource(CoreUiR.string.account_session_ended), kind = BannerKind.Warning)
    }
    Explanation(
        stringResource(
            when (form.mode) {
                AccountMode.CONFIRM -> R.string.account_confirm_body
                AccountMode.FORGOT -> R.string.account_forgot_body
                AccountMode.RESET -> R.string.account_reset_body
                else -> if (required) R.string.account_required else R.string.account_why
            },
        ),
    )
    val needsCode = form.mode == AccountMode.CONFIRM || form.mode == AccountMode.RESET
    val needsPassword = form.mode != AccountMode.CONFIRM && form.mode != AccountMode.FORGOT
    val choosingPassword = form.mode == AccountMode.SIGN_UP || form.mode == AccountMode.RESET

    OutlinedTextField(
        value = form.email,
        onValueChange = actions.onEmailChange,
        label = { Text(stringResource(R.string.account_email)) },
        singleLine = true,
        enabled = !needsCode && !form.busy,
        isError = form.formProblem == FormProblem.INVALID_EMAIL,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.EmailAddress + ContentType.Username },
    )
    if (needsCode) {
        OutlinedTextField(
            value = form.code,
            onValueChange = actions.onCodeChange,
            label = { Text(stringResource(R.string.account_code)) },
            singleLine = true,
            enabled = !form.busy,
            isError = form.formProblem == FormProblem.MISSING_CODE,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = if (needsPassword) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.SmsOtpCode },
        )
    }
    if (needsPassword) {
        // Hidden by default; the eye shows what was typed, to check it before signing in.
        var showPassword by rememberSaveable { mutableStateOf(false) }
        OutlinedTextField(
            value = form.password,
            onValueChange = actions.onPasswordChange,
            label = { Text(stringResource(if (form.mode == AccountMode.RESET) R.string.account_new_password else R.string.account_password)) },
            supportingText = if (choosingPassword) {
                { Text(pluralStringResource(R.plurals.account_password_rules, AccountViewModel.MIN_PASSWORD, AccountViewModel.MIN_PASSWORD)) }
            } else {
                null
            },
            singleLine = true,
            enabled = !form.busy,
            isError = form.formProblem == FormProblem.SHORT_PASSWORD,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        painterResource(if (showPassword) DesignR.drawable.ic_visibility_off else DesignR.drawable.ic_visibility),
                        contentDescription = stringResource(if (showPassword) R.string.account_hide_password else R.string.account_show_password),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentType = if (choosingPassword) ContentType.NewPassword else ContentType.Password
                },
        )
    }
    if (form.mode == AccountMode.SIGN_IN) {
        // The whole row toggles; unticked, the session lasts only while the app runs.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = form.remember, enabled = !form.busy, role = Role.Checkbox, onValueChange = actions.onRememberChange),
        ) {
            Checkbox(checked = form.remember, onCheckedChange = null, enabled = !form.busy)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.account_remember_me), style = MaterialTheme.typography.bodyLarge)
        }
    }

    val message = form.formProblem?.let { formProblemText(it) } ?: form.authProblem?.let { authProblemText(it) }
    message?.let { InlineBanner(it, kind = BannerKind.Error) }
    form.notice?.let {
        InlineBanner(stringResource(if (it == AccountNotice.CODE_SENT) R.string.account_code_sent else R.string.account_reset_code_sent))
    }

    Button(onClick = actions.onSubmit, enabled = !form.busy, modifier = Modifier.fillMaxWidth()) {
        if (form.busy) {
            CircularProgressIndicator(Modifier.padding(2.dp).widthIn(max = 20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(title))
        }
    }
    when (form.mode) {
        AccountMode.SIGN_IN -> {
            TextButton(onClick = { actions.onSetMode(AccountMode.SIGN_UP) }, enabled = !form.busy) {
                Text(stringResource(R.string.account_no_account))
            }
            TextButton(onClick = { actions.onSetMode(AccountMode.FORGOT) }, enabled = !form.busy) {
                Text(stringResource(R.string.account_forgot))
            }
        }
        AccountMode.FORGOT, AccountMode.RESET -> TextButton(onClick = { actions.onSetMode(AccountMode.SIGN_IN) }, enabled = !form.busy) {
            Text(stringResource(R.string.account_back_to_sign_in))
        }
        AccountMode.SIGN_UP -> TextButton(onClick = { actions.onSetMode(AccountMode.SIGN_IN) }, enabled = !form.busy) {
            Text(stringResource(R.string.account_have_account))
        }
        AccountMode.CONFIRM -> TextButton(onClick = actions.onResendCode, enabled = !form.busy) {
            Text(stringResource(R.string.account_resend))
        }
    }
}

@Composable
private fun formProblemText(problem: FormProblem): String = when (problem) {
    FormProblem.INVALID_EMAIL -> stringResource(R.string.account_problem_email)
    FormProblem.SHORT_PASSWORD ->
        pluralStringResource(R.plurals.account_problem_password, AccountViewModel.MIN_PASSWORD, AccountViewModel.MIN_PASSWORD)
    FormProblem.MISSING_CODE ->
        pluralStringResource(R.plurals.account_problem_code, AccountViewModel.CODE_LENGTH, AccountViewModel.CODE_LENGTH)
}

@Composable
private fun authProblemText(problem: AuthProblemUi): String = stringResource(
    when (problem) {
        AuthProblemUi.WRONG_CREDENTIALS -> R.string.auth_wrong_credentials
        AuthProblemUi.ACCOUNT_EXISTS -> R.string.auth_account_exists
        AuthProblemUi.WEAK_PASSWORD -> R.string.auth_weak_password
        AuthProblemUi.WRONG_CODE -> R.string.auth_wrong_code
        AuthProblemUi.ACCOUNT_DISABLED -> R.string.auth_account_disabled
        AuthProblemUi.CODE_EXPIRED -> R.string.auth_code_expired
        AuthProblemUi.TOO_MANY_ATTEMPTS -> R.string.auth_too_many
        AuthProblemUi.NETWORK -> R.string.auth_network
        AuthProblemUi.UNKNOWN -> R.string.auth_unknown
    },
)
