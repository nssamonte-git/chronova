package noel.chronova.feature.sync.presentation

/** The "you're back online" (or "changes on another device") prompt, as the dialog shows it. */
data class SyncPromptUi(val pendingChanges: Int, val signedIn: Boolean, val fromOtherDevice: Boolean = false)
