package com.kyoko412.vrcxcompanion.data

import com.kyoko412.vrcxcompanion.network.ApiFailure
import com.kyoko412.vrcxcompanion.network.Page

class PageAccumulator<T>(private val stableId: (T) -> String) {
    private val rows = LinkedHashMap<String, T>()
    private var accountId: String? = null
    val items: List<T> get() = rows.values.toList()
    var nextCursor: String? = null
        private set

    fun accept(page: Page<T>, expectedAccount: String, reset: Boolean = false) {
        if (page.accountId != expectedAccount) {
            clear()
            throw ApiFailure.AccountChanged
        }
        if (reset || accountId != expectedAccount) clear()
        accountId = expectedAccount
        page.items.forEach { rows.putIfAbsent(stableId(it), it) }
        nextCursor = page.nextCursor
    }

    fun clear() {
        rows.clear()
        accountId = null
        nextCursor = null
    }
}
