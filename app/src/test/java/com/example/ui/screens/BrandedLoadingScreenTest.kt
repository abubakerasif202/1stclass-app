package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrandedLoadingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restorationStateShowsOfficialBrandedLoadingContent() {
        composeRule.setContent {
            MyApplicationTheme {
                BrandedLoadingScreen()
            }
        }

        composeRule.onNodeWithTag("branded_loading_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("company_logo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1st Class Express").assertIsDisplayed()
        composeRule.onNodeWithText("DRIVER APP").assertIsDisplayed()
    }
}
