package noel.chronova.feature.account.domain

/**
 * The shared demo account that "Try demo" on the sign-in page signs into (DECISIONS, 2026-11-15).
 * Its login is public on purpose and committed by the user's choice: it is not a secret, and
 * anyone who opens the app can use it. Everyone who tries the demo shares its data.
 */
object DemoAccount {
    const val EMAIL = "test@chronova.com"
    const val PASSWORD = "Password123456"

    fun isDemo(email: String): Boolean = email.trim().equals(EMAIL, ignoreCase = true)
}
