package com.zer0state.zzthat.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * The draft title and body, labeled "Not a code yet." (FR-024). It holds no
 * code words, is never shared, and is never resolved. Kept until mint or discard.
 */
class DraftStore(private val store: DataStore<Preferences>) {
    data class Draft(val title: String, val body: String)

    suspend fun draft(): Draft? {
        val preferences = store.data.first()
        val title = preferences[TITLE]
        val body = preferences[BODY]
        if (title == null && body == null) return null
        return Draft(title.orEmpty(), body.orEmpty())
    }

    /** A draft with only blank text is no draft. */
    suspend fun save(title: String, body: String) {
        if (title.isBlank() && body.isBlank()) return discard()
        store.edit {
            it[TITLE] = title
            it[BODY] = body
        }
    }

    suspend fun discard() {
        store.edit {
            it.remove(TITLE)
            it.remove(BODY)
        }
    }

    private companion object {
        val TITLE = stringPreferencesKey("draft.title")
        val BODY = stringPreferencesKey("draft.body")
    }
}

/** The last canonical text, so resolve can be retried (runtime.md, Storage). Kept until the person clears it. */
class LastCanonicalStore(private val store: DataStore<Preferences>) {
    suspend fun value(): String? = store.data.first()[KEY]

    suspend fun set(canonical: String?) {
        store.edit { if (canonical == null) it.remove(KEY) else it[KEY] = canonical }
    }

    private companion object {
        val KEY = stringPreferencesKey("resolve.last_canonical")
    }
}

/** The provider of the signed-in account, so Account can show it offline (runtime.md, Screens: Account). Not a secret. */
class ProviderStore(private val store: DataStore<Preferences>) {
    suspend fun value(): String? = store.data.first()[KEY]

    suspend fun set(provider: String?) {
        store.edit { if (provider == null) it.remove(KEY) else it[KEY] = provider }
    }

    private companion object {
        val KEY = stringPreferencesKey("account.provider")
    }
}
