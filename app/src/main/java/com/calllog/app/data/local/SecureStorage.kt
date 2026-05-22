package com.calllog.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "calllog_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ── Token ────────────────────────────────────────────────────────────────
    fun saveToken(token: String) = prefs.edit().putString("auth_token", token).apply()
    fun getToken(): String? = prefs.getString("auth_token", null)
    fun clearToken() = prefs.edit().remove("auth_token").apply()
    fun getAuthHeader(): String = "Bearer ${getToken() ?: ""}"

    // ── User Info ─────────────────────────────────────────────────────────────
    fun saveUserId(id: String) = prefs.edit().putString("user_id", id).apply()
    fun getUserId(): String? = prefs.getString("user_id", null)

    fun saveUserName(name: String) = prefs.edit().putString("user_name", name).apply()
    fun getUserName(): String? = prefs.getString("user_name", null)

    fun saveUserEmail(email: String) = prefs.edit().putString("user_email", email).apply()
    fun getUserEmail(): String? = prefs.getString("user_email", null)

    fun saveCodeType(type: String) = prefs.edit().putString("code_type", type).apply()
    fun getCodeType(): String? = prefs.getString("code_type", null)

    // ── Organization ──────────────────────────────────────────────────────────
    fun saveOrgId(id: String) = prefs.edit().putString("org_id", id).apply()
    fun getOrgId(): String? = prefs.getString("org_id", null)

    fun saveOrgName(name: String) = prefs.edit().putString("org_name", name).apply()
    fun getOrgName(): String? = prefs.getString("org_name", null)

    fun saveRole(role: String) = prefs.edit().putString("role", role).apply()
    fun getRole(): String? = prefs.getString("role", null)

    // ── Sync Config ───────────────────────────────────────────────────────────
    fun saveSyncInterval(minutes: Long) =
        prefs.edit().putLong("sync_interval_minutes", minutes).apply()
    fun getSyncInterval(): Long = prefs.getLong("sync_interval_minutes", 60L)

    fun saveMaxRecordsPerSync(max: Int) =
        prefs.edit().putInt("max_records_per_sync", max).apply()
    fun getMaxRecordsPerSync(): Int = prefs.getInt("max_records_per_sync", 5000)

    // ── Last Sync Time ────────────────────────────────────────────────────────
    fun saveLastSyncTime(timestamp: Long) =
        prefs.edit().putLong("last_sync_time", timestamp).apply()
    fun getLastSyncTime(): Long = prefs.getLong("last_sync_time", 0L)

    // ── First Sync Flag ───────────────────────────────────────────────────────
    fun isFirstSyncDone(): Boolean = prefs.getBoolean("first_sync_done", false)
    fun markFirstSyncDone() = prefs.edit().putBoolean("first_sync_done", true).apply()

    // ── Old Records Sync Checkpoint ───────────────────────────────────────────
    // First sync नंतर जुने records background मध्ये sync करतो
    // हा checkpoint जुन्या records साठी वेगळा ठेवतो
    fun saveOldRecordsSyncUpTo(timestamp: Long) =
        prefs.edit().putLong("old_records_sync_upto", timestamp).apply()
    fun getOldRecordsSyncUpTo(): Long =
        prefs.getLong("old_records_sync_upto", 0L)

    // ── Sync History (last 10) ────────────────────────────────────────────────
    // Format: JSON array string — "timestamp|status|synced|failed|message"
    fun saveSyncHistory(history: List<String>) {
        prefs.edit().putString("sync_history", history.joinToString("||")).apply()
    }
    fun getSyncHistory(): List<String> {
        val raw = prefs.getString("sync_history", "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("||")
    }
    fun addSyncHistoryEntry(entry: String) {
        val current = getSyncHistory().toMutableList()
        current.add(0, entry) // newest first
        saveSyncHistory(current.take(10)) // फक्त last 10
    }

    // ── SIM Registration ──────────────────────────────────────────────────────
    fun saveSim1Registered(registered: Boolean) =
        prefs.edit().putBoolean("sim1_registered", registered).apply()
    fun isSim1Registered(): Boolean = prefs.getBoolean("sim1_registered", false)

    fun saveSim2Registered(registered: Boolean) =
        prefs.edit().putBoolean("sim2_registered", registered).apply()
    fun isSim2Registered(): Boolean = prefs.getBoolean("sim2_registered", false)

    // ── UI Preferences ────────────────────────────────────────────────────────
    fun saveDarkMode(enabled: Boolean) = prefs.edit().putBoolean("dark_mode", enabled).apply()
    fun isDarkMode(): Boolean = prefs.getBoolean("dark_mode", false)

    // ── Auth Check ────────────────────────────────────────────────────────────
    fun isLoggedIn(): Boolean = getToken() != null

    // ── Logout ────────────────────────────────────────────────────────────────
    fun clearAll() = prefs.edit().clear().apply()
}
