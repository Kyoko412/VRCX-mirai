package com.kyoko412.vrcxcompanion.data

import android.content.Context
import com.kyoko412.vrcxcompanion.network.ApiFailure
import com.kyoko412.vrcxcompanion.network.BioChangeDto
import com.kyoko412.vrcxcompanion.network.CompanionApi
import com.kyoko412.vrcxcompanion.network.EncounterDto
import com.kyoko412.vrcxcompanion.network.EncounterPage
import com.kyoko412.vrcxcompanion.network.FriendDto
import com.kyoko412.vrcxcompanion.network.GameLocationDto
import com.kyoko412.vrcxcompanion.network.Page
import com.kyoko412.vrcxcompanion.network.PinnedClientFactory
import com.kyoko412.vrcxcompanion.network.StatusDto
import com.kyoko412.vrcxcompanion.network.VisitDto
import com.kyoko412.vrcxcompanion.pairing.PairingStore
import com.kyoko412.vrcxcompanion.pairing.SavedPairing

class CompanionRepository(private val store: PairingStore, private var api: CompanionApi) : FriendsSource, HistorySource, ConnectionSource {
    private var pairing: SavedPairing? = store.load()
    var accountId: String? = null
        private set
    private val friendPages = mutableMapOf<String, PageAccumulator<FriendDto>>()
    private val visitPages = mutableMapOf<String, PageAccumulator<VisitDto>>()
    private val encounterPages = mutableMapOf<String, PageAccumulator<EncounterDto>>()
    private val bioPages = mutableMapOf<String, PageAccumulator<BioChangeDto>>()
    private val gamePages = PageAccumulator<GameLocationDto> { it.id.toString() }

    private fun token(): String = pairing?.token ?: throw ApiFailure.Unauthorized

    private suspend fun <T> read(block: suspend () -> T): T = try { block() }
    catch (error: ApiFailure) {
        clearPages()
        if (error == ApiFailure.Unauthorized || error == ApiFailure.Revoked) {
            pairing = null
            store.clear()
        }
        throw error
    }

    override suspend fun status(): StatusDto = read {
        val next = api.status(token())
        if (accountId != null && accountId != next.accountId) {
            clearPages()
            accountId = next.accountId
            throw ApiFailure.AccountChanged
        }
        accountId = next.accountId
        next
    }

    private suspend fun expectedAccount(): String = accountId ?: status().accountId

    private fun checkAccount(actual: String, expected: String) {
        if (actual != expected) {
            clearPages()
            throw ApiFailure.AccountChanged
        }
    }

    override suspend fun friends(search: String?, cursor: String?): Page<FriendDto> = read {
        val expected = expectedAccount()
        val page = api.friends(token(), search, cursor)
        checkAccount(page.accountId, expected)
        friendPages.getOrPut(search.orEmpty()) { PageAccumulator { it.id } }
            .accept(page, expected, reset = cursor == null)
        page
    }

    override suspend fun worldVisits(friendId: String, cursor: String?): Page<VisitDto> = read {
        val expected = expectedAccount()
        val page = api.worldVisits(token(), friendId, cursor)
        checkAccount(page.accountId, expected)
        visitPages.getOrPut(friendId) { PageAccumulator { it.eventKey } }
            .accept(page, expected, reset = cursor == null)
        page
    }

    override suspend fun encounters(friendId: String, cursor: String?): EncounterPage = read {
        val expected = expectedAccount()
        val page = api.encounters(token(), friendId, cursor)
        checkAccount(page.accountId, expected)
        encounterPages.getOrPut(friendId) { PageAccumulator { it.visitKey } }
            .accept(Page(page.accountId, page.items, page.nextCursor), expected, reset = cursor == null)
        page
    }

    override suspend fun bioHistory(friendId: String, cursor: String?): Page<BioChangeDto> = read {
        val expected = expectedAccount()
        val page = api.bioHistory(token(), friendId, cursor)
        checkAccount(page.accountId, expected)
        bioPages.getOrPut(friendId) { PageAccumulator { it.id.toString() } }
            .accept(page, expected, reset = cursor == null)
        page
    }

    override suspend fun gameLog(cursor: String?): Page<GameLocationDto> = read {
        val expected = expectedAccount()
        val page = api.gameLog(token(), cursor)
        checkAccount(page.accountId, expected)
        gamePages.accept(page, expected, reset = cursor == null)
        page
    }

    fun friendItems(search: String?): List<FriendDto> = friendPages[search.orEmpty()]?.items.orEmpty()
    fun visitItems(friendId: String): List<VisitDto> = visitPages[friendId]?.items.orEmpty()
    fun encounterItems(friendId: String): List<EncounterDto> = encounterPages[friendId]?.items.orEmpty()
    fun bioItems(friendId: String): List<BioChangeDto> = bioPages[friendId]?.items.orEmpty()
    fun gameItems(): List<GameLocationDto> = gamePages.items

    fun clearPages() {
        friendPages.clear()
        visitPages.clear()
        encounterPages.clear()
        bioPages.clear()
        gamePages.clear()
    }

    fun unpair() {
        pairing = null
        accountId = null
        clearPages()
        store.clear()
    }

    fun updateAddress(address: String, port: Int, pin: String) {
        val previous = pairing ?: throw ApiFailure.Unauthorized
        if (pin != previous.spkiSha256) throw ApiFailure.TlsMismatch
        val updated = previous.copy(address = address, port = port)
        val endpoint = updated.endpoint()
        api = CompanionApi(endpoint, PinnedClientFactory.create(endpoint))
        store.save(updated)
        pairing = updated
        clearPages()
    }

    companion object {
        fun forAndroid(context: Context): CompanionRepository? {
            val store = PairingStore.forAndroid(context)
            val pairing = store.load() ?: return null
            val endpoint = pairing.endpoint()
            return CompanionRepository(store, CompanionApi(endpoint, PinnedClientFactory.create(endpoint)))
        }
    }
}
