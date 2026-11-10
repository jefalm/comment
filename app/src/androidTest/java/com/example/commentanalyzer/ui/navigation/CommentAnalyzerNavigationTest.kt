package com.example.commentanalyzer.ui.navigation

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentAnalyzerNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backDispatcher: OnBackPressedDispatcher? = null

    @Test
    fun launchesAtHome() {
        setNavigationContent()
        composeRule.onNodeWithTag("destination_home").assertIsSelected()
        composeRule.onNodeWithText("Home content").assertIsDisplayed()
    }

    @Test
    fun topLevelDestinationsShowHonestUnavailableStates() {
        setNavigationContent()
        composeRule.onNodeWithTag("destination_comments").performClick()
        composeRule.onNodeWithTag("comments_unavailable").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("destination_analysis").performClick()
        composeRule.onNodeWithTag("analysis_unavailable").assertIsDisplayed()

        composeRule.onNodeWithTag("destination_settings").performClick()
        composeRule.onNodeWithTag("settings_unavailable").assertIsDisplayed()

        composeRule.onNodeWithTag("destination_home").performClick()
        composeRule.onNodeWithTag("destination_home").assertIsSelected()
    }

    @Test
    fun reselectionBackAndRecreationPreserveTopLevelNavigation() {
        val restorationTester = setNavigationContent()
        composeRule.onNodeWithTag("destination_comments").performClick()
        composeRule.onNodeWithTag("destination_comments").performClick()
        composeRule.onNodeWithTag("destination_comments").assertIsSelected()
        composeRule.runOnIdle {
            backDispatcher?.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("destination_home").assertIsSelected()

        composeRule.onNodeWithTag("destination_analysis").performClick()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("destination_analysis").assertIsSelected()
        composeRule.onNodeWithTag("analysis_unavailable").assertIsDisplayed()
    }

    private fun setNavigationContent(): StateRestorationTester {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            SideEffect { backDispatcher = dispatcher }
            MaterialTheme {
                CommentAnalyzerNavigation(homeContent = { Text("Home content") })
            }
        }
        return restorationTester
    }
}