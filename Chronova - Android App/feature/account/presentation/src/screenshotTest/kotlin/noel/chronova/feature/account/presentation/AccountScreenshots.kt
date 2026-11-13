package noel.chronova.feature.account.presentation

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDateTime
import java.time.ZoneOffset
import noel.chronova.core.designsystem.ChronovaScreenshots
import noel.chronova.core.designsystem.ChronovaTheme
import noel.chronova.core.domain.sync.SyncFailure
import noel.chronova.core.ui.sync.SyncPhase

/** A fixed "now", so "last synced 10 minutes ago" doesn't change between runs. */
private val now = LocalDateTime.of(2026, 11, 13, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

@Composable
private fun Account(page: AccountPageUi, onBack: (() -> Unit)? = {}) = ChronovaTheme {
    AccountContent(page = page, actions = AccountActions(), onBack = onBack, now = { now })
}

/** The sign-in page that opens the app (no way back, with the logo). */
@PreviewTest
@ChronovaScreenshots
@Composable
fun AccountSignIn() = Account(AccountPageUi(form = AccountForm(email = "ana@example.com", password = "secret-pass")), onBack = null)

/** Creating an account, refused because it already exists. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun AccountCreateWithError() = Account(
    AccountPageUi(form = AccountForm(mode = AccountMode.SIGN_UP, email = "ana@example.com", password = "Secret-pass-1", authProblem = AuthProblemUi.ACCOUNT_EXISTS)),
)

/** Confirming a new account with the emailed code. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun AccountConfirmCode() = Account(
    AccountPageUi(form = AccountForm(mode = AccountMode.CONFIRM, email = "ana@example.com", code = "1234", notice = AccountNotice.CODE_SENT)),
)

/** Signed in, with the last sync failed for lack of a connection and two changes waiting. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun AccountSignedInSyncFailed() = Account(
    AccountPageUi(auth = AccountAuthUi.SignedIn("ana@example.com"), pending = 2, syncPhase = SyncPhase.Failed(SyncFailure.OFFLINE), lastSyncedAt = now - 10 * 60_000L),
)

/** Signing out with unsynced changes asks first. */
@PreviewTest
@ChronovaScreenshots
@Composable
fun AccountSignOutDialog() = Account(
    AccountPageUi(auth = AccountAuthUi.SignedIn("ana@example.com"), pending = 2, dialog = AccountDialog.SignOut(unsynced = 2), lastSyncedAt = now - 10 * 60_000L),
)
