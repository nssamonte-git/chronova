package noel.chronova.app.presentation.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.text.format.DateUtils
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import noel.chronova.app.R
import noel.chronova.app.auth.AuthProblem
import noel.chronova.app.auth.AuthState
import noel.chronova.app.data.sync.SyncStatus
import noel.chronova.app.presentation.sync.SyncUi
import noel.chronova.app.presentation.sync.syncFailureText
import noel.chronova.app.presentation.sync.syncStatusText

/** Sign up, confirm, sign in and sign out for sync. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(viewModel: AccountViewModel, onBack: () -> Unit) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    dialog?.let { AccountConfirmDialog(it, onConfirm = viewModel::confirmDialog, onDismiss = viewModel::dismissDialog) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val state = authState) {
                    AuthState.NotConfigured -> Explanation(stringResource(R.string.account_not_configured))
                    AuthState.Checking -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    is AuthState.SignedIn -> SignedIn(state.email, viewModel)
                    AuthState.SignedOut -> SignInForm(form, viewModel)
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
private fun SignedIn(email: String, viewModel: AccountViewModel) {
    val pending by viewModel.pendingCount.collectAsStateWithLifecycle()
    val status by viewModel.syncStatus.collectAsStateWithLifecycle()
    val deleteFailure by viewModel.deleteFailure.collectAsStateWithLifecycle()
    Text(stringResource(R.string.account_signed_in_as, email), style = MaterialTheme.typography.titleMedium)
    Explanation(stringResource(R.string.account_signed_in_body))

    val ui = SyncUi(configured = true, signedIn = true, pending = pending, status = status)
    Text(
        syncStatusText(ui) ?: lastSyncedText(viewModel.lastSyncedAt()),
        style = MaterialTheme.typography.bodyMedium,
        color = if (status is SyncStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = viewModel::syncNow, enabled = status != SyncStatus.Syncing, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sync_now))
    }
    OutlinedButton(onClick = viewModel::requestSignOut, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_sign_out))
    }
    TextButton(onClick = viewModel::requestDeleteAccount, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error)
    }
    deleteFailure?.let {
        Text(
            stringResource(R.string.account_delete_failed, syncFailureText(it)),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun lastSyncedText(at: Long?): String =
    if (at == null) {
        stringResource(R.string.sync_never)
    } else {
        stringResource(
            R.string.sync_last,
            DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SignInForm(form: AccountForm, viewModel: AccountViewModel) {
    val title = when (form.mode) {
        AccountMode.SIGN_IN -> R.string.account_sign_in
        AccountMode.SIGN_UP -> R.string.account_create
        AccountMode.CONFIRM -> R.string.account_confirm
    }
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
    Explanation(
        stringResource(if (form.mode == AccountMode.CONFIRM) R.string.account_confirm_body else R.string.account_why),
    )

    OutlinedTextField(
        value = form.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text(stringResource(R.string.account_email)) },
        singleLine = true,
        enabled = form.mode != AccountMode.CONFIRM && !form.busy,
        isError = form.formProblem == FormProblem.INVALID_EMAIL,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.EmailAddress + ContentType.Username },
    )
    if (form.mode == AccountMode.CONFIRM) {
        OutlinedTextField(
            value = form.code,
            onValueChange = viewModel::onCodeChange,
            label = { Text(stringResource(R.string.account_code)) },
            singleLine = true,
            enabled = !form.busy,
            isError = form.formProblem == FormProblem.MISSING_CODE,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.SmsOtpCode },
        )
    } else {
        OutlinedTextField(
            value = form.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(stringResource(R.string.account_password)) },
            supportingText = if (form.mode == AccountMode.SIGN_UP) {
                { Text(pluralStringResource(R.plurals.account_password_rules, AccountViewModel.MIN_PASSWORD, AccountViewModel.MIN_PASSWORD)) }
            } else {
                null
            },
            singleLine = true,
            enabled = !form.busy,
            isError = form.formProblem == FormProblem.SHORT_PASSWORD,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentType = if (form.mode == AccountMode.SIGN_UP) ContentType.NewPassword else ContentType.Password
                },
        )
    }

    val message = form.formProblem?.let { formProblemText(it) } ?: form.authProblem?.let { authProblemText(it) }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    form.notice?.let {
        Text(
            stringResource(if (it == AccountNotice.CODE_SENT) R.string.account_code_sent else R.string.account_confirmed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    Button(onClick = viewModel::submit, enabled = !form.busy, modifier = Modifier.fillMaxWidth()) {
        if (form.busy) {
            CircularProgressIndicator(Modifier.padding(2.dp).widthIn(max = 20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(title))
        }
    }
    when (form.mode) {
        AccountMode.SIGN_IN -> TextButton(onClick = { viewModel.setMode(AccountMode.SIGN_UP) }, enabled = !form.busy) {
            Text(stringResource(R.string.account_no_account))
        }
        AccountMode.SIGN_UP -> TextButton(onClick = { viewModel.setMode(AccountMode.SIGN_IN) }, enabled = !form.busy) {
            Text(stringResource(R.string.account_have_account))
        }
        AccountMode.CONFIRM -> TextButton(onClick = viewModel::resendCode, enabled = !form.busy) {
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
private fun authProblemText(problem: AuthProblem): String = stringResource(
    when (problem) {
        AuthProblem.WRONG_CREDENTIALS -> R.string.auth_wrong_credentials
        AuthProblem.ACCOUNT_EXISTS -> R.string.auth_account_exists
        AuthProblem.WEAK_PASSWORD -> R.string.auth_weak_password
        AuthProblem.WRONG_CODE -> R.string.auth_wrong_code
        AuthProblem.CODE_EXPIRED -> R.string.auth_code_expired
        AuthProblem.TOO_MANY_ATTEMPTS -> R.string.auth_too_many
        AuthProblem.NETWORK -> R.string.auth_network
        AuthProblem.UNKNOWN -> R.string.auth_unknown
    },
)
