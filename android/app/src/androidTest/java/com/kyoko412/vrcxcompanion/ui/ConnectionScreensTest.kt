package com.kyoko412.vrcxcompanion.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kyoko412.vrcxcompanion.network.GameLocationDto
import org.junit.Rule
import org.junit.Test

class ConnectionScreensTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun gameLogExplainsOwnershipAndShowsOnlyCurrentRows() {
        val record = GameLocationDto(1, "2026-09-24T02:00:00Z", "wrld_a:1", "wrld_a", "World", 60_000)
        composeRule.setContent {
            GameLogScreen(ConnectionUiState(connection = ConnectionState.Ready, records = listOf(record)),
                onBack = {}, onRefresh = {}, onLoadMore = {})
        }
        composeRule.onNodeWithText("仅显示已确认属于当前账号的记录；旧日志可能缺失").assertExists()
        composeRule.onNodeWithText("World").assertExists()
    }

    @Test fun failedReadShowsRecoveryStateWithoutOldWorld() {
        composeRule.setContent {
            GameLogScreen(ConnectionUiState(connection = ConnectionState.Offline),
                onBack = {}, onRefresh = {}, onLoadMore = {})
        }
        composeRule.onNodeWithText(ConnectionState.Offline.label).assertExists()
        composeRule.onNodeWithText("World").assertDoesNotExist()
    }
}
