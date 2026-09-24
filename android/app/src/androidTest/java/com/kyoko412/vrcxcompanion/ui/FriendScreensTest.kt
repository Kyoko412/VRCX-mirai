package com.kyoko412.vrcxcompanion.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kyoko412.vrcxcompanion.network.VisitDto
import org.junit.Rule
import org.junit.Test

class FriendScreensTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun unknownExitAndEmptyBioRemainExplicit() {
        val visit = VisitDto("gps:1", "wrld_a", "World", "wrld_a:1",
            "2026-09-24T02:00:00Z", null, null, "2026-09-24T02:00:00Z", 1)
        composeRule.setContent {
            FriendDetailScreen("好友", HistoryUiState(friendId = "usr_friend", visits = listOf(visit)),
                onBack = {}, onTab = {}, onLoadMore = {}, onRefresh = {})
        }
        composeRule.onNodeWithText("离开时间未观察到").assertExists()
        composeRule.runOnIdle { }
    }
}
