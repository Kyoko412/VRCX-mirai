package com.kyoko412.vrcxcompanion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyoko412.vrcxcompanion.data.HistorySource
import com.kyoko412.vrcxcompanion.data.PageAccumulator
import com.kyoko412.vrcxcompanion.network.ApiFailure
import com.kyoko412.vrcxcompanion.network.BioChangeDto
import com.kyoko412.vrcxcompanion.network.EncounterDto
import com.kyoko412.vrcxcompanion.network.Page
import com.kyoko412.vrcxcompanion.network.VisitDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HistoryTab { Visits, Encounters, Bio }

data class HistoryUiState(
    val friendId: String? = null,
    val tab: HistoryTab = HistoryTab.Visits,
    val visits: List<VisitDto> = emptyList(),
    val encounters: List<EncounterDto> = emptyList(),
    val bios: List<BioChangeDto> = emptyList(),
    val nextCursor: String? = null,
    val qualifiedCount: Int = 0,
    val unknownCount: Int = 0,
    val loading: Boolean = false,
    val error: ApiFailure? = null
)

class HistoryViewModel(private val source: HistorySource, private val scope: CoroutineScope? = null) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state = mutableState.asStateFlow()
    private val visitPages = PageAccumulator<VisitDto> { it.eventKey }
    private val encounterPages = PageAccumulator<EncounterDto> { it.visitKey }
    private val bioPages = PageAccumulator<BioChangeDto> { it.id.toString() }
    private val cursors = mutableMapOf<HistoryTab, String?>()
    private var request: Job? = null

    fun open(friendId: String) {
        request?.cancel()
        visitPages.clear(); encounterPages.clear(); bioPages.clear(); cursors.clear()
        mutableState.value = HistoryUiState(friendId = friendId)
        load(null)
    }

    fun selectTab(tab: HistoryTab) {
        if (mutableState.value.tab == tab) return
        request?.cancel()
        mutableState.value = mutableState.value.copy(tab = tab, nextCursor = cursors[tab], loading = false)
        if (!cursors.containsKey(tab)) load(null)
    }

    fun refresh() {
        request?.cancel()
        load(null)
    }

    fun loadMore() {
        if (mutableState.value.loading) return
        mutableState.value.nextCursor?.let(::load)
    }

    private fun load(cursor: String?) {
        val friendId = mutableState.value.friendId ?: return
        val tab = mutableState.value.tab
        request = (scope ?: viewModelScope).launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                when (tab) {
                    HistoryTab.Visits -> {
                        val page = source.worldVisits(friendId, cursor)
                        if (mutableState.value.friendId != friendId || mutableState.value.tab != tab) return@launch
                        visitPages.accept(page, page.accountId, reset = cursor == null)
                        cursors[tab] = page.nextCursor
                        mutableState.value = mutableState.value.copy(visits = visitPages.items,
                            nextCursor = page.nextCursor, loading = false)
                    }
                    HistoryTab.Encounters -> {
                        val page = source.encounters(friendId, cursor)
                        if (mutableState.value.friendId != friendId || mutableState.value.tab != tab) return@launch
                        encounterPages.accept(Page(page.accountId, page.items, page.nextCursor),
                            page.accountId, reset = cursor == null)
                        cursors[tab] = page.nextCursor
                        mutableState.value = mutableState.value.copy(encounters = encounterPages.items,
                            qualifiedCount = page.qualifiedCount, unknownCount = page.unknownCount,
                            nextCursor = page.nextCursor, loading = false)
                    }
                    HistoryTab.Bio -> {
                        val page = source.bioHistory(friendId, cursor)
                        if (mutableState.value.friendId != friendId || mutableState.value.tab != tab) return@launch
                        bioPages.accept(page, page.accountId, reset = cursor == null)
                        cursors[tab] = page.nextCursor
                        mutableState.value = mutableState.value.copy(bios = bioPages.items,
                            nextCursor = page.nextCursor, loading = false)
                    }
                }
            } catch (error: CancellationException) { throw error }
              catch (error: ApiFailure) {
                visitPages.clear(); encounterPages.clear(); bioPages.clear(); cursors.clear()
                mutableState.value = mutableState.value.copy(visits = emptyList(), encounters = emptyList(),
                    bios = emptyList(), nextCursor = null, loading = false, error = error)
              }
        }
    }
}
