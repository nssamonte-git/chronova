package noel.chronova.feature.sync.presentation

/** The "you're back online" prompt, as the dialog shows it. */
data class SyncPromptUi(val pendingChanges: Int, val signedIn: Boolean)
