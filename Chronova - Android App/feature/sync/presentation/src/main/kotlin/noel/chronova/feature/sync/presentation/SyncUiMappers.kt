package noel.chronova.feature.sync.presentation

import noel.chronova.feature.sync.domain.SyncOverview
import noel.chronova.core.domain.sync.SyncPrompt
import noel.chronova.core.ui.sync.SyncUi
import noel.chronova.core.ui.sync.toSyncPhase

// Domain to screen: the project list's sync line and the reconnect prompt.

internal fun SyncOverview.toUi() = SyncUi(configured = configured, signedIn = signedIn, pending = pending, phase = status.toSyncPhase())

internal fun SyncPrompt.toUi() = SyncPromptUi(pendingChanges = pendingChanges, signedIn = signedIn)
