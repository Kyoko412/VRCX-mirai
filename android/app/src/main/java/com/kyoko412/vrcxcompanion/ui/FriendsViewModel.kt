package com.kyoko412.vrcxcompanion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyoko412.vrcxcompanion.data.FriendsSource
import com.kyoko412.vrcxcompanion.data.PageAccumulator
import com.kyoko412.vrcxcompanion.network.ApiFailure
import com.kyoko412.vrcxcompanion.network.FriendDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FriendsUiState(
    val search: String = "",
    val items: List<FriendDto> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val error: ApiFailure? = null
)

class FriendsViewModel(private val source: FriendsSource, private val scope: CoroutineScope? = null) : ViewModel() {
    private val mutableState = MutableStateFlow(FriendsUiState())
    val state = mutableState.asStateFlow()
    private val accumulator = PageAccumulator<FriendDto> { it.id }
    private var request: Job? = null

    fun search(value: String) {
        request?.cancel()
        accumulator.clear()
        val query = value.take(100)
        mutableState.value = FriendsUiState(search = query)
        load(null)
    }

    fun refresh() = search(mutableState.value.search)

    fun clear() {
        request?.cancel()
        accumulator.clear()
        mutableState.value = FriendsUiState(search = mutableState.value.search)
    }

    fun loadMore() {
        if (mutableState.value.loading) return
        mutableState.value.nextCursor?.let(::load)
    }

    private fun load(cursor: String?) {
        val query = mutableState.value.search
        request = (scope ?: viewModelScope).launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val page = source.friends(query, cursor)
                if (mutableState.value.search != query) return@launch
                accumulator.accept(page, page.accountId, reset = cursor == null)
                mutableState.value = mutableState.value.copy(items = accumulator.items,
                    nextCursor = accumulator.nextCursor, loading = false)
            } catch (error: CancellationException) { throw error }
              catch (error: ApiFailure) {
                accumulator.clear()
                mutableState.value = mutableState.value.copy(items = emptyList(), nextCursor = null,
                    loading = false, error = error)
              }
        }
    }
}
