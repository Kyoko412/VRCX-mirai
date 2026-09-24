package com.kyoko412.vrcxcompanion.ui

import com.kyoko412.vrcxcompanion.data.FriendsSource
import com.kyoko412.vrcxcompanion.network.FriendDto
import com.kyoko412.vrcxcompanion.network.Page
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {
    @Test fun searchesNamesAndPreservesUserIdAcrossRenameAndPagination() = runTest {
        val source = object : FriendsSource {
            override suspend fun friends(search: String?, cursor: String?): Page<FriendDto> =
                if (cursor == null) Page("usr_me", listOf(FriendDto("usr_a", "旧名字")), "next")
                else Page("usr_me", listOf(FriendDto("usr_b", "新名字")), null)
        }
        val model = FriendsViewModel(source, scope = this)
        model.search("名字")
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("usr_a", "usr_b"), model.state.value.items.map { it.id })
        assertEquals("名字", model.state.value.search)
    }
}
