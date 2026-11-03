package org.openfactor.app

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NameDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun blankNamesCannotBeSaved() {
        var saved: String? = null
        compose.setContent {
            OpenFactorTheme { NameDialog("Rename Day", "Day 1", {}, { saved = it }) }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("   ")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.runOnIdle { assertNull(saved) }
    }

    @Test fun savingTrimsTheNameAndLeavesClosingToTheCaller() {
        var saved: String? = null
        var dismissed = false
        compose.setContent {
            OpenFactorTheme { NameDialog("Rename Day", "Day 1", { dismissed = true }, { saved = it }) }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("  Upper body  ")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals("Upper body", saved)
            assertFalse(dismissed)
        }
    }

    @Test fun cancellingDoesNotSaveTheDraft() {
        var saved: String? = null
        var dismissed = false
        compose.setContent {
            OpenFactorTheme { NameDialog("Rename Day", "Day 1", { dismissed = true }, { saved = it }) }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("Draft")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertNull(saved)
        }
    }
}
