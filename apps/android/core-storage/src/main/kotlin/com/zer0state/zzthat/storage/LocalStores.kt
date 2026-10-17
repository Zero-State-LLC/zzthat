package com.zer0state.zzthat.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

/**
 * The Terms version the person accepted on this device (design/UX.md, Terms;
 * zzThis D-2026-10-13-04). Client-only: never sent, and the server neither
 * stores nor checks it. Account deletion clears it, so the next account on
 * the device sees the Terms step again.
 */
class TermsStore(private val store: DataStore<Preferences>) {
    suspend fun acceptedVersion(): String? = store.data.first()[KEY]

    suspend fun accept(version: String) {
        store.edit { it[KEY] = version }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("terms.accepted_version")
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

/**
 * The codes the person hid on this device (FR-035, zzThis RM-101). A hidden
 * code still resolves, and Resolve shows the hidden state without its title
 * or body. The list never leaves the device: hiding and unhiding make no
 * server call. Account deletion and the Account screen reset clear it.
 *
 * The creators the person blocked (FR-035, zzThis RM-112) are a second list:
 * the opaque Resolve `creator` references, never an account id. A code whose
 * creator is blocked resolves to the blocked state. Blocking and unblocking
 * make no server call either.
 *
 * Each list is one string set under its own key, and [clear] removes both.
 */
class HiddenStore(private val store: DataStore<Preferences>) {
    suspend fun isHidden(canonical: String): Boolean = canonical in members(CODES)

    suspend fun hide(canonical: String) = add(CODES, canonical)

    suspend fun unhide(canonical: String) = remove(CODES, canonical)

    suspend fun isBlocked(creator: String): Boolean = creator in members(CREATORS)

    suspend fun block(creator: String) = add(CREATORS, creator)

    suspend fun unblock(creator: String) = remove(CREATORS, creator)

    /** Every list this store keeps. */
    suspend fun clear() {
        store.edit { preferences -> LISTS.forEach { preferences.remove(it) } }
    }

    private suspend fun members(key: Preferences.Key<Set<String>>): Set<String> = store.data.first()[key].orEmpty()

    private suspend fun add(key: Preferences.Key<Set<String>>, value: String) {
        store.edit { it[key] = it[key].orEmpty() + value }
    }

    private suspend fun remove(key: Preferences.Key<Set<String>>, value: String) {
        store.edit {
            val left = it[key].orEmpty() - value
            if (left.isEmpty()) it.remove(key) else it[key] = left
        }
    }

    private companion object {
        val CODES = stringSetPreferencesKey("hide.codes")
        val CREATORS = stringSetPreferencesKey("hide.creators")
        val LISTS = listOf(CODES, CREATORS)
    }
}

/**
 * Saved (FR-050, zzThis RM-115): the canonical codes the person chose to
 * save, newest first. Canonical text only: no title, no body, no thumbnail,
 * and no photo. The list never leaves the device, and saving, removing, and
 * clearing make no server call. Account deletion and the Account screen's
 * Clear saved codes empty it.
 */
class SavedStore(private val store: DataStore<Preferences>) {
    /** The saved codes, newest first. */
    suspend fun list(): List<String> = decode(store.data.first()[CODES])

    suspend fun isSaved(canonical: String): Boolean = canonical in list()

    /** Saves [canonical] as the newest entry. Saving it again moves it to the top. */
    suspend fun save(canonical: String) {
        store.edit { it[CODES] = encode(listOf(canonical) + (decode(it[CODES]) - canonical)) }
    }

    suspend fun unsave(canonical: String) {
        store.edit {
            val left = decode(it[CODES]) - canonical
            if (left.isEmpty()) it.remove(CODES) else it[CODES] = encode(left)
        }
    }

    suspend fun clear() {
        store.edit { it.remove(CODES) }
    }

    private companion object {
        /** One string, a canonical code per line. A canonical code never holds a line break. */
        val CODES = stringPreferencesKey("saved.codes")
        const val SEPARATOR = "\n"

        fun decode(value: String?): List<String> = value?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

        fun encode(codes: List<String>): String = codes.joinToString(SEPARATOR)
    }
}
