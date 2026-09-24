package com.kyoko412.vrcxcompanion.ui

import com.kyoko412.vrcxcompanion.data.HistorySource
import com.kyoko412.vrcxcompanion.network.BioChangeDto
import com.kyoko412.vrcxcompanion.network.EncounterPage
import com.kyoko412.vrcxcompanion.network.Page
import com.kyoko412.vrcxcompanion.network.VisitDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val timestamp = "2026-09-24T02:00:00Z"
    private class FakeHistorySource : HistorySource {
        var delayFirst = false
        override suspend fun worldVisits(friendId: String, cursor: String?): Page<VisitDto> {
            if (delayFirst && friendId == "usr_first") delay(1_000)
            return Page("usr_me", listOf(VisitDto("gps:$friendId", "wrld_a", "World", "wrld_a:1",
                "2026-09-24T02:00:00Z", null, null, "2026-09-24T02:00:00Z", 1)), null)
        }
        override suspend fun encounters(friendId: String, cursor: String?) =
            EncounterPage("usr_me", 0, 0, emptyList(), null)
        override suspend fun bioHistory(friendId: String, cursor: String?) =
            Page("usr_me", listOf(BioChangeDto(1, "旧", "第一行\n第二行", "2026-09-24T02:00:00Z")), null)
    }

    @Test fun opensByStableIdAndSwitchesTabsWithoutLosingMultilineBio() = runTest {
        val model = HistoryViewModel(FakeHistorySource(), scope = this)
        model.open("usr_friend")
        advanceUntilIdle()
        assertEquals("usr_friend", model.state.value.friendId)
        assertEquals("gps:usr_friend", model.state.value.visits.single().eventKey)
        assertNull(model.state.value.visits.single().exitedAt)
        model.selectTab(HistoryTab.Bio)
        advanceUntilIdle()
        assertEquals("第一行\n第二行", model.state.value.bios.single().bio)
    }

    @Test fun oldFriendResponseCannotReplaceNewFriendSelection() = runTest {
        val source = FakeHistorySource().also { it.delayFirst = true }
        val model = HistoryViewModel(source, scope = this)
        model.open("usr_first")
        runCurrent()
        model.open("usr_second")
        advanceUntilIdle()
        assertEquals("usr_second", model.state.value.friendId)
        assertEquals("gps:usr_second", model.state.value.visits.single().eventKey)
    }
}
