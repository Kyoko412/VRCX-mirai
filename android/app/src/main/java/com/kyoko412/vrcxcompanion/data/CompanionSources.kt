package com.kyoko412.vrcxcompanion.data

import com.kyoko412.vrcxcompanion.network.BioChangeDto
import com.kyoko412.vrcxcompanion.network.EncounterPage
import com.kyoko412.vrcxcompanion.network.FriendDto
import com.kyoko412.vrcxcompanion.network.Page
import com.kyoko412.vrcxcompanion.network.VisitDto
import com.kyoko412.vrcxcompanion.network.GameLocationDto
import com.kyoko412.vrcxcompanion.network.StatusDto

interface FriendsSource {
    suspend fun friends(search: String?, cursor: String?): Page<FriendDto>
}

interface HistorySource {
    suspend fun worldVisits(friendId: String, cursor: String?): Page<VisitDto>
    suspend fun encounters(friendId: String, cursor: String?): EncounterPage
    suspend fun bioHistory(friendId: String, cursor: String?): Page<BioChangeDto>
}

interface ConnectionSource {
    suspend fun status(): StatusDto
    suspend fun gameLog(cursor: String?): Page<GameLocationDto>
}
