package com.dormantxxy.glassmail.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val categoriesEnabled: Boolean = true,
    val remoteImagesEnabled: Boolean = false,
    val senderAvatarsEnabled: Boolean = true,
    val confirmDelete: Boolean = true,
    val undoSendSeconds: Int = 8,
    val selectedAccountId: String = "",
    val compactRows: Boolean = false
)

private val Context.dataStore by preferencesDataStore("glassmail_preferences")

class AppPreferences(private val context: Context) {
    val flow: Flow<UserPreferences> = context.dataStore.data.map { p ->
        UserPreferences(
            themeMode = runCatching { ThemeMode.valueOf(p[THEME] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
            categoriesEnabled = p[CATEGORIES] ?: true,
            remoteImagesEnabled = p[REMOTE_IMAGES] ?: false,
            senderAvatarsEnabled = p[AVATARS] ?: true,
            confirmDelete = p[CONFIRM_DELETE] ?: true,
            undoSendSeconds = p[UNDO_SEND] ?: 8,
            selectedAccountId = p[SELECTED_ACCOUNT] ?: "",
            compactRows = p[COMPACT_ROWS] ?: false
        )
    }

    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[THEME] = value.name }
    suspend fun setCategories(value: Boolean) = context.dataStore.edit { it[CATEGORIES] = value }
    suspend fun setRemoteImages(value: Boolean) = context.dataStore.edit { it[REMOTE_IMAGES] = value }
    suspend fun setSelectedAccount(value: String) = context.dataStore.edit { it[SELECTED_ACCOUNT] = value }
    suspend fun setCompactRows(value: Boolean) = context.dataStore.edit { it[COMPACT_ROWS] = value }
    suspend fun setConfirmDelete(value: Boolean) = context.dataStore.edit { it[CONFIRM_DELETE] = value }
    suspend fun setUndoSendSeconds(value: Int) = context.dataStore.edit { it[UNDO_SEND] = value.coerceIn(0, 30) }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val CATEGORIES = booleanPreferencesKey("categories")
        val REMOTE_IMAGES = booleanPreferencesKey("remote_images")
        val AVATARS = booleanPreferencesKey("avatars")
        val CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        val UNDO_SEND = intPreferencesKey("undo_send")
        val SELECTED_ACCOUNT = stringPreferencesKey("selected_account")
        val COMPACT_ROWS = booleanPreferencesKey("compact_rows")
    }
}
