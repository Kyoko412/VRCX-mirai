package com.kyoko412.vrcxcompanion.network

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiModelsTest {
    @Test fun parsesFriendPage() {
        val page = companionJson.decodeFromString<Page<FriendDto>>(
            """{"accountId":"usr_me","items":[{"id":"usr_a","displayName":"A"}],"nextCursor":null}"""
        )
        assertEquals("usr_a", page.items.single().id)
    }

    @Test fun rejectsUnsupportedStatusMajor() {
        assertThrows(UnsupportedApiVersion::class.java) {
            parseStatus("""{"apiVersion":2,"accountId":"usr_me","computerName":"PC","syncState":"ready"}""")
        }
    }
}
