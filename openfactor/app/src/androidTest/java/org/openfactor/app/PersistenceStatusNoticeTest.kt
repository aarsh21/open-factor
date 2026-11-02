package org.openfactor.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PersistenceStatusNoticeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyFailuresTakeSpaceAndRetryUsesTheSuppliedOwner() {
        val state = MutableStateFlow<PersistenceState>(PersistenceState.Saved)
        var requests = 0
        compose.setContent { OpenFactorTheme { PersistenceStatusNotice(state,
            onRetry = { requests++; state.value = PersistenceState.Saved }, onDismiss = {}) } }
        compose.onNodeWithText("Retry save").assertDoesNotExist()
        compose.runOnIdle { state.value = PersistenceState.Pending }
        compose.onNodeWithText("Retry save").assertDoesNotExist()
        compose.runOnIdle { state.value = PersistenceState.Failed(true) }
        compose.onNodeWithText("Your latest edits aren't saved. Free up storage and retry.").assertIsDisplayed()
        compose.onNodeWithText("Retry save").performClick()
        compose.onNodeWithText("Retry save").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun rejectedSynchronousChangesDoNotClaimToBeAcceptedUnsavedEdits() {
        val state = MutableStateFlow<PersistenceState>(PersistenceState.Failed(false))
        compose.setContent { OpenFactorTheme { PersistenceStatusNotice(state,
            onRetry = {}, onDismiss = { state.value = PersistenceState.Saved }) } }
        compose.onNodeWithText("The last change couldn't be saved. Please try that action again.").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText("Dismiss").assertDoesNotExist()
    }
}
