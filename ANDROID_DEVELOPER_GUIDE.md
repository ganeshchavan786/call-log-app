# CallLog SaaS — Android Mobile App Integration Guide

> **Complete guide for Android developers to integrate CallLog SaaS APIs.**
> This document covers authentication, SIM registration, call log syncing,
> and all implementation details needed to build the Android companion app.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Authentication Flow](#authentication-flow)
5. [API Reference](#api-reference)
6. [Android Implementation](#android-implementation)
7. [Error Handling](#error-handling)
8. [Security Best Practices](#security-best-practices)
9. [Testing](#testing)

---

## 1. Overview

The CallLog SaaS Mobile API allows Android apps to:
- Authenticate users using **Email + Password + Unique Code**
- Register device SIM cards (SIM 1 and SIM 2)
- Sync call logs from the device to the server
- Check sync status and history

### Base URL
```
Production:  https://your-domain.com/api/mobile
Development: http://10.0.2.2:3000/api/mobile  (Android Emulator)
             http://192.168.x.x:3000/api/mobile (Real Device on same WiFi)
```

### How Data Ownership Works
Every API call is automatically linked to the correct user and organization.
The server identifies ownership through the **JWT token** — no manual org ID needed.

```
User Login
    ↓
Server generates JWT Token
    ↓ Token contains:
        - userId       → Who is syncing
        - organizationId → Which company's data
        - role         → What permissions they have
    ↓
Every Sync Request uses this Token
    ↓
Server automatically knows:
    - Which Organization → data isolation guaranteed
    - Which Employee     → call log ownership
    - Which SIM          → joined from RegisteredSIM table
```

---

## 2. Architecture

### Data Flow Diagram

```
Android App                    CallLog Server
    │                               │
    │  1. POST /verify              │
    │  (email + password + code) ──→│
    │                               │ Verify credentials
    │                               │ Generate JWT (userId + orgId + role)
    │←── token + org + SIMs ────────│
    │                               │
    │  Save token securely          │
    │  (EncryptedSharedPreferences) │
    │                               │
    │  2. POST /register-sim        │
    │  (simSlot + phoneNumber) ────→│
    │                               │ Link SIM to user + org
    │←── SIM registered ────────────│
    │                               │
    │  3. Read Android Call Logs    │
    │  (CallLog.Calls ContentProvider)
    │                               │
    │  4. POST /sync                │
    │  (records array) ────────────→│
    │                               │ Decode token → get userId + orgId
    │                               │ Lookup RegisteredSIM → get SIM number
    │                               │ Insert CallLogs with full ownership
    │←── sync result + ownership ───│
    │                               │
    │  5. GET /status               │
    │  (check last sync) ──────────→│
    │←── status + SIM info ─────────│
```

### Token Payload (Decoded)
```json
{
  "userId": "clx1234abcd",
  "email": "rahul@company.com",
  "organizationId": "clx5678efgh",
  "role": "MEMBER",
  "iat": 1234567890,
  "exp": 1235567890
}
```
> The server reads `organizationId` from this token on every request.
> The Android app never needs to send orgId manually.

---

## 3. Prerequisites

### Android Permissions Required
Add these to `AndroidManifest.xml`:

```xml
<!-- Required: Read call logs from device -->
<uses-permission android:name="android.permission.READ_CALL_LOG" />

<!-- Required: Get SIM phone number -->
<uses-permission android:name="android.permission.READ_PHONE_STATE" />

<!-- Required: API calls -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Optional: Background sync -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

### Dependencies (build.gradle app level)
```gradle
dependencies {
    // HTTP Client
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // Secure Storage
    implementation 'androidx.security:security-crypto:1.1.0-alpha06'

    // WorkManager (background sync)
    implementation 'androidx.work:work-runtime-ktx:2.9.0'

    // ViewModel
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
}
```

---

## 4. Authentication Flow

### Step-by-Step Flow

```
Step 1: User opens app
    → Check if token exists in EncryptedSharedPreferences
    → If yes → go to Home Screen
    → If no  → go to Login Screen

Step 2: Login Screen
    → User enters: Email + Password + Unique Code
    → Unique Code is found in the Web Dashboard
    → POST /api/mobile/verify

Step 3: Login Success
    → Save token securely (EncryptedSharedPreferences)
    → Save organizationId, userId, userName
    → Save registeredSIMs list
    → Navigate to Home Screen

Step 4: Request SIM Permission
    → android.permission.READ_PHONE_STATE
    → android.permission.READ_CALL_LOG

Step 5: Register SIM (if not already registered)
    → Read SIM phone number from TelephonyManager
    → POST /api/mobile/register-sim for SIM_1
    → POST /api/mobile/register-sim for SIM_2 (if dual SIM)

Step 6: Start Background Sync
    → Schedule WorkManager job (every 60 minutes)
    → On each run: read new call logs → POST /api/mobile/sync
```

---

## 5. API Reference

---

### API 1: Verify (Login)

**Endpoint**
```
POST /api/mobile/verify
Content-Type: application/json
```

**Purpose**
Authenticate the user with Email, Password, and Unique Code.
The Unique Code is found in the Web Dashboard under Settings.
Without the correct Unique Code, login will be rejected even if
email and password are correct. This is an extra security layer.

**Request Body**
```json
{
  "email": "rahul@company.com",
  "password": "Admin1234",
  "uniqueCode": "EMP-7341"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| email | string | ✅ Yes | User's registered email |
| password | string | ✅ Yes | User's password (min 8 chars) |
| uniqueCode | string | ✅ Yes | Found in Web Dashboard → Settings. Format: OWN-XXXX (owner) or EMP-XXXX (employee) |

**Success Response (200)**
```json
{
  "success": true,
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiJ...",

  "identity": {
    "userId": "clx1234abcd",
    "userName": "Rahul Sharma",
    "userEmail": "rahul@company.com",
    "avatarUrl": null,
    "uniqueCode": "EMP-7341",
    "codeType": "EMPLOYEE"
  },

  "organization": {
    "id": "clx5678efgh",
    "name": "ABC Travels Pvt Ltd",
    "slug": "abc-travels",
    "logoUrl": null,
    "timezone": "Asia/Kolkata",
    "role": "MEMBER"
  },

  "registeredSIMs": [
    {
      "simSlot": "SIM_1",
      "phoneNumber": "+919876543210",
      "deviceName": "Samsung Galaxy S23",
      "isActive": true,
      "lastSyncAt": "2024-01-15T10:30:00Z",
      "totalSynced": 1250
    },
    {
      "simSlot": "SIM_2",
      "phoneNumber": "+919123456789",
      "deviceName": "Samsung Galaxy S23",
      "isActive": true,
      "lastSyncAt": null,
      "totalSynced": 0
    }
  ],

  "syncConfig": {
    "maxRecordsPerSync": 5000,
    "syncIntervalMinutes": 60,
    "allowedSIMSlots": ["SIM_1", "SIM_2"],
    "apiVersion": "v1"
  }
}
```

**What to save after login:**
```
token              → EncryptedSharedPreferences (use for all API calls)
identity.userId    → SharedPreferences
identity.userName  → SharedPreferences (show in UI)
organization.id    → SharedPreferences (for reference only)
organization.name  → SharedPreferences (show in UI)
registeredSIMs     → SharedPreferences as JSON
syncConfig         → SharedPreferences
```

**Error Responses**

| Status | Error Code | Meaning | Action |
|---|---|---|---|
| 400 | VALIDATION_ERROR | Missing fields | Show field validation errors |
| 401 | INVALID_CREDENTIALS | Wrong email/password | Show "Invalid credentials" |
| 401 | INVALID_CODE | Wrong unique code | Show "Invalid code. Check Dashboard." |
| 403 | NO_ORGANIZATION | User not in any org | Contact admin |
| 500 | INTERNAL_ERROR | Server error | Retry after delay |

---

### API 2: Register SIM

**Endpoint**
```
POST /api/mobile/register-sim
Content-Type: application/json
Authorization: Bearer <token>
```

**Purpose**
Register the physical SIM card's phone number with the server.
This links the SIM to the logged-in employee and their organization.
Once registered, every call log synced from this SIM slot will
automatically carry the SIM's own phone number in server records.
Call this once after login, and again if the user changes their SIM.

**Request Body**
```json
{
  "simSlot": "SIM_1",
  "phoneNumber": "+919876543210",
  "deviceName": "Samsung Galaxy S23"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| simSlot | string | ✅ Yes | "SIM_1" or "SIM_2" |
| phoneNumber | string | ✅ Yes | SIM's own phone number. Read from TelephonyManager. Format: +91XXXXXXXXXX |
| deviceName | string | ❌ No | Device model name. Read from Build.MODEL |

**How to read SIM number on Android:**
```kotlin
val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

// SIM 1
val sim1Number = telephonyManager.line1Number  // May be null on some devices

// SIM 2 (Dual SIM)
val subscriptionManager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) 
    as SubscriptionManager
val subscriptions = subscriptionManager.activeSubscriptionInfoList
subscriptions?.forEach { subInfo ->
    val simSlot = if (subInfo.simSlotIndex == 0) "SIM_1" else "SIM_2"
    val phoneNumber = subInfo.number  // May be null on some devices
}
```

> **Note:** On many Android devices, `getLine1Number()` returns null
> due to carrier restrictions. In that case, ask the user to enter
> their phone number manually during setup.

**Success Response (200)**
```json
{
  "success": true,
  "data": {
    "id": "clxsim123",
    "simSlot": "SIM_1",
    "phoneNumber": "+919876543210",
    "deviceName": "Samsung Galaxy S23",
    "isActive": true,
    "lastSyncAt": null,
    "totalSynced": 0
  },
  "message": "SIM 1 successfully registered"
}
```

**Error Responses**

| Status | Error Code | Meaning | Action |
|---|---|---|---|
| 400 | VALIDATION_ERROR | Invalid phone number or simSlot | Fix input |
| 401 | UNAUTHORIZED | Token missing or expired | Re-login |
| 500 | INTERNAL_ERROR | Server error | Retry |

---

### API 3: Sync Call Logs

**Endpoint**
```
POST /api/mobile/sync
Content-Type: application/json
Authorization: Bearer <token>
```

**Purpose**
Push call log records from the Android device to the server.
The server automatically identifies which organization and employee
the data belongs to using the JWT token. The server also joins
the SIM's own phone number from the RegisteredSIM table.
Send up to 5000 records per request. For larger batches, split
into multiple requests.

**Request Body**
```json
{
  "simSlot": "SIM_1",
  "records": [
    {
      "mobileNumber": "9876543210",
      "contactName": "Rahul Sharma",
      "callType": "INCOMING",
      "date": "2024-01-15T10:30:00Z",
      "duration": 120,
      "simSlot": "SIM_1",
      "deviceName": "Samsung Galaxy S23"
    },
    {
      "mobileNumber": "9123456789",
      "contactName": null,
      "callType": "MISSED",
      "date": "2024-01-15T11:45:00Z",
      "duration": 0,
      "simSlot": "SIM_1",
      "deviceName": "Samsung Galaxy S23"
    },
    {
      "mobileNumber": "8877665544",
      "contactName": "Priya Patel",
      "callType": "OUTGOING",
      "date": "2024-01-15T14:20:00Z",
      "duration": 480,
      "simSlot": "SIM_1",
      "deviceName": "Samsung Galaxy S23"
    }
  ]
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| simSlot | string | ✅ Yes | Which SIM these records are from: "SIM_1" or "SIM_2" |
| records | array | ✅ Yes | Array of call log objects. Min 1, Max 5000 |
| records[].mobileNumber | string | ✅ Yes | The other party's phone number (who called or was called) |
| records[].contactName | string | ❌ No | Contact name from phone book. null if unknown |
| records[].callType | string | ✅ Yes | "INCOMING", "OUTGOING", or "MISSED" |
| records[].date | string | ✅ Yes | ISO 8601 datetime: "2024-01-15T10:30:00Z" |
| records[].duration | integer | ✅ Yes | Call duration in seconds. 0 for missed calls |
| records[].simSlot | string | ❌ No | Same as top-level simSlot. Included per record for clarity |
| records[].deviceName | string | ❌ No | Device model. Read from android.os.Build.MODEL |

**How to read call logs on Android:**
```kotlin
fun readCallLogs(context: Context, lastSyncTime: Long): List<CallRecord> {
    val records = mutableListOf<CallRecord>()
    val uri = CallLog.Calls.CONTENT_URI

    val projection = arrayOf(
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.PHONE_ACCOUNT_ID
    )

    // Only fetch records newer than last sync
    val selection = "${CallLog.Calls.DATE} > ?"
    val selectionArgs = arrayOf(lastSyncTime.toString())

    context.contentResolver.query(uri, projection, selection, selectionArgs, 
        "${CallLog.Calls.DATE} DESC")?.use { cursor ->
        while (cursor.moveToNext()) {
            val type = when (cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE))) {
                CallLog.Calls.INCOMING_TYPE  -> "INCOMING"
                CallLog.Calls.OUTGOING_TYPE  -> "OUTGOING"
                CallLog.Calls.MISSED_TYPE    -> "MISSED"
                else -> "INCOMING"
            }

            val dateMillis = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE))
            val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(dateMillis))

            records.add(CallRecord(
                mobileNumber = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER)) ?: "",
                contactName  = cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)),
                callType     = type,
                date         = isoDate,
                duration     = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.DURATION)),
                simSlot      = "SIM_1", // Determine from PHONE_ACCOUNT_ID
                deviceName   = Build.MODEL
            ))
        }
    }
    return records
}
```

**How to determine SIM slot from call log:**
```kotlin
fun getSimSlotFromAccountId(accountId: String?): String {
    // accountId typically contains subscription ID
    // Map subscription ID to SIM slot
    val subscriptionManager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) 
        as SubscriptionManager
    val subscriptions = subscriptionManager.activeSubscriptionInfoList
    
    subscriptions?.forEach { subInfo ->
        if (subInfo.iccId == accountId || subInfo.subscriptionId.toString() == accountId) {
            return if (subInfo.simSlotIndex == 0) "SIM_1" else "SIM_2"
        }
    }
    return "SIM_1" // Default to SIM_1 if cannot determine
}
```

**Success Response (200)**
```json
{
  "success": true,
  "sync": {
    "batchId": "clxbatch789",
    "totalRows": 3,
    "successRows": 3,
    "failedRows": 0,
    "syncedAt": "2024-01-15T15:00:00Z"
  },
  "ownership": {
    "organization": {
      "id": "clx5678efgh",
      "name": "ABC Travels Pvt Ltd"
    },
    "employee": {
      "id": "clx1234abcd",
      "name": "Rahul Sharma",
      "email": "rahul@company.com",
      "uniqueCode": "EMP-7341",
      "codeType": "EMPLOYEE"
    },
    "sim": {
      "slot": "SIM_1",
      "ownNumber": "+919876543210",
      "deviceName": "Samsung Galaxy S23"
    }
  },
  "message": "3 records successfully synced"
}
```

> **Important:** The `ownership` object in the response confirms exactly
> which organization, employee, and SIM the data was saved under.
> Log this for debugging. If `sim.ownNumber` is null, it means the
> SIM was not registered yet — call `/register-sim` first.

**Error Responses**

| Status | Error Code | Meaning | Action |
|---|---|---|---|
| 400 | VALIDATION_ERROR | Invalid record format | Check date format and required fields |
| 401 | UNAUTHORIZED | Token expired | Re-login and retry |
| 403 | NO_ORGANIZATION | Org not found | Contact admin |
| 500 | INTERNAL_ERROR | Server error | Retry with exponential backoff |

---

### API 4: Sync Status

**Endpoint**
```
GET /api/mobile/status
Authorization: Bearer <token>
```

**Purpose**
Check the current sync status, registered SIMs, and total records
synced. Use this on app startup to show the user their sync history
and to verify that SIMs are registered correctly.

**Request**
```
No request body needed.
Token is enough — server identifies everything from it.
```

**Success Response (200)**
```json
{
  "success": true,
  "data": {
    "user": {
      "id": "clx1234abcd",
      "name": "Rahul Sharma",
      "email": "rahul@company.com",
      "uniqueCode": "EMP-7341",
      "codeType": "EMPLOYEE"
    },
    "organization": {
      "id": "clx5678efgh",
      "name": "ABC Travels Pvt Ltd",
      "role": "MEMBER"
    },
    "sims": [
      {
        "simSlot": "SIM_1",
        "phoneNumber": "+919876543210",
        "deviceName": "Samsung Galaxy S23",
        "isActive": true,
        "lastSyncAt": "2024-01-15T10:30:00Z",
        "totalSynced": 1250
      },
      {
        "simSlot": "SIM_2",
        "phoneNumber": "+919123456789",
        "deviceName": "Samsung Galaxy S23",
        "isActive": true,
        "lastSyncAt": null,
        "totalSynced": 0
      }
    ],
    "lastSync": {
      "id": "clxbatch789",
      "status": "COMPLETED",
      "successRows": 50,
      "failedRows": 0,
      "completedAt": "2024-01-15T10:30:00Z"
    },
    "totalCallLogsSynced": 1250
  }
}
```

---

## 6. Android Implementation

### 6.1 Project Structure

```
app/
├── data/
│   ├── api/
│   │   ├── CallLogApiService.kt     (Retrofit interface)
│   │   ├── ApiClient.kt             (Retrofit setup)
│   │   └── models/
│   │       ├── VerifyRequest.kt
│   │       ├── VerifyResponse.kt
│   │       ├── RegisterSimRequest.kt
│   │       ├── SyncRequest.kt
│   │       └── SyncResponse.kt
│   ├── local/
│   │   ├── SecureStorage.kt         (EncryptedSharedPreferences)
│   │   └── CallLogReader.kt         (Read from Android CallLog)
│   └── repository/
│       └── CallLogRepository.kt
├── domain/
│   └── SyncCallLogsUseCase.kt
├── ui/
│   ├── login/
│   │   ├── LoginActivity.kt
│   │   └── LoginViewModel.kt
│   └── home/
│       ├── HomeActivity.kt
│       └── HomeViewModel.kt
├── worker/
│   └── CallLogSyncWorker.kt         (Background sync)
└── utils/
    └── PermissionHelper.kt
```

---

### 6.2 Data Models (Kotlin)

```kotlin
// data/api/models/VerifyRequest.kt
data class VerifyRequest(
    val email: String,
    val password: String,
    val uniqueCode: String
)

// data/api/models/VerifyResponse.kt
data class VerifyResponse(
    val success: Boolean,
    val token: String?,
    val identity: Identity?,
    val organization: Organization?,
    val registeredSIMs: List<RegisteredSIM>?,
    val syncConfig: SyncConfig?,
    val error: String?,
    val message: String?
) {
    data class Identity(
        val userId: String,
        val userName: String,
        val userEmail: String,
        val uniqueCode: String,
        val codeType: String  // "OWNER" or "EMPLOYEE"
    )
    data class Organization(
        val id: String,
        val name: String,
        val slug: String,
        val timezone: String,
        val role: String
    )
    data class RegisteredSIM(
        val simSlot: String,
        val phoneNumber: String?,
        val deviceName: String?,
        val isActive: Boolean,
        val lastSyncAt: String?,
        val totalSynced: Int
    )
    data class SyncConfig(
        val maxRecordsPerSync: Int,
        val syncIntervalMinutes: Long,
        val allowedSIMSlots: List<String>
    )
}

// data/api/models/RegisterSimRequest.kt
data class RegisterSimRequest(
    val simSlot: String,       // "SIM_1" or "SIM_2"
    val phoneNumber: String,
    val deviceName: String?
)

// data/api/models/SyncRequest.kt
data class SyncRequest(
    val simSlot: String,
    val records: List<CallRecord>
) {
    data class CallRecord(
        val mobileNumber: String,
        val contactName: String?,
        val callType: String,   // "INCOMING", "OUTGOING", "MISSED"
        val date: String,       // ISO 8601: "2024-01-15T10:30:00Z"
        val duration: Int,      // seconds
        val simSlot: String,
        val deviceName: String?
    )
}

// data/api/models/SyncResponse.kt
data class SyncResponse(
    val success: Boolean,
    val sync: SyncResult?,
    val ownership: Ownership?,
    val message: String?,
    val error: String?
) {
    data class SyncResult(
        val batchId: String,
        val totalRows: Int,
        val successRows: Int,
        val failedRows: Int,
        val syncedAt: String
    )
    data class Ownership(
        val organization: OrgInfo,
        val employee: EmployeeInfo,
        val sim: SimInfo
    )
    data class OrgInfo(val id: String, val name: String)
    data class EmployeeInfo(
        val id: String,
        val name: String,
        val email: String,
        val uniqueCode: String
    )
    data class SimInfo(
        val slot: String,
        val ownNumber: String?,   // SIM's own phone number
        val deviceName: String?
    )
}
```

---

### 6.3 Retrofit API Service

```kotlin
// data/api/CallLogApiService.kt
import retrofit2.Response
import retrofit2.http.*

interface CallLogApiService {

    @POST("verify")
    suspend fun verify(
        @Body request: VerifyRequest
    ): Response<VerifyResponse>

    @POST("register-sim")
    suspend fun registerSim(
        @Header("Authorization") token: String,
        @Body request: RegisterSimRequest
    ): Response<ApiResponse>

    @GET("register-sim")
    suspend fun getRegisteredSims(
        @Header("Authorization") token: String
    ): Response<ApiResponse>

    @POST("sync")
    suspend fun syncCallLogs(
        @Header("Authorization") token: String,
        @Body request: SyncRequest
    ): Response<SyncResponse>

    @GET("status")
    suspend fun getStatus(
        @Header("Authorization") token: String
    ): Response<ApiResponse>
}
```

---

### 6.4 API Client Setup

```kotlin
// data/api/ApiClient.kt
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Change this to your server URL
    private const val BASE_URL = "http://10.0.2.2:3000/api/mobile/"
    // For real device on same WiFi: "http://192.168.1.100:3000/api/mobile/"
    // For production: "https://your-domain.com/api/mobile/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG)
            HttpLoggingInterceptor.Level.BODY
        else
            HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: CallLogApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CallLogApiService::class.java)
}
```

---

### 6.5 Secure Token Storage

```kotlin
// data/local/SecureStorage.kt
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

    // Token
    fun saveToken(token: String) = prefs.edit().putString("auth_token", token).apply()
    fun getToken(): String? = prefs.getString("auth_token", null)
    fun clearToken() = prefs.edit().remove("auth_token").apply()

    // Auth header helper
    fun getAuthHeader(): String = "Bearer ${getToken() ?: ""}"

    // User info
    fun saveUserId(id: String) = prefs.edit().putString("user_id", id).apply()
    fun getUserId(): String? = prefs.getString("user_id", null)

    fun saveUserName(name: String) = prefs.edit().putString("user_name", name).apply()
    fun getUserName(): String? = prefs.getString("user_name", null)

    // Organization
    fun saveOrgId(id: String) = prefs.edit().putString("org_id", id).apply()
    fun getOrgId(): String? = prefs.getString("org_id", null)

    fun saveOrgName(name: String) = prefs.edit().putString("org_name", name).apply()
    fun getOrgName(): String? = prefs.getString("org_name", null)

    // Last sync timestamp (to avoid re-syncing old records)
    fun saveLastSyncTime(timestamp: Long) =
        prefs.edit().putLong("last_sync_time", timestamp).apply()
    fun getLastSyncTime(): Long = prefs.getLong("last_sync_time", 0L)

    // SIM registration status
    fun saveSim1Registered(registered: Boolean) =
        prefs.edit().putBoolean("sim1_registered", registered).apply()
    fun isSim1Registered(): Boolean = prefs.getBoolean("sim1_registered", false)

    fun saveSim2Registered(registered: Boolean) =
        prefs.edit().putBoolean("sim2_registered", registered).apply()
    fun isSim2Registered(): Boolean = prefs.getBoolean("sim2_registered", false)

    // Sync interval from server config
    fun saveSyncInterval(minutes: Long) =
        prefs.edit().putLong("sync_interval_minutes", minutes).apply()
    fun getSyncInterval(): Long = prefs.getLong("sync_interval_minutes", 60L)

    fun isLoggedIn(): Boolean = getToken() != null

    fun clearAll() = prefs.edit().clear().apply()
}
```

---

### 6.6 Login Implementation

```kotlin
// ui/login/LoginViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val apiClient: CallLogApiService,
    private val storage: SecureStorage
) : ViewModel() {

    sealed class LoginState {
        object Idle : LoginState()
        object Loading : LoginState()
        object Success : LoginState()
        data class Error(val message: String) : LoginState()
    }

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state

    fun login(email: String, password: String, uniqueCode: String) {
        viewModelScope.launch {
            _state.value = LoginState.Loading

            try {
                val response = apiClient.verify(
                    VerifyRequest(
                        email = email.trim(),
                        password = password,
                        uniqueCode = uniqueCode.trim().uppercase()
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!

                    // Save all data securely
                    body.token?.let { storage.saveToken(it) }
                    body.identity?.let {
                        storage.saveUserId(it.userId)
                        storage.saveUserName(it.userName)
                    }
                    body.organization?.let {
                        storage.saveOrgId(it.id)
                        storage.saveOrgName(it.name)
                    }
                    body.syncConfig?.let {
                        storage.saveSyncInterval(it.syncIntervalMinutes)
                    }

                    // Check which SIMs are already registered
                    body.registeredSIMs?.forEach { sim ->
                        when (sim.simSlot) {
                            "SIM_1" -> storage.saveSim1Registered(true)
                            "SIM_2" -> storage.saveSim2Registered(true)
                        }
                    }

                    _state.value = LoginState.Success

                } else {
                    val errorBody = response.errorBody()?.string()
                    val message = when (response.code()) {
                        401 -> "Invalid credentials or wrong code"
                        403 -> "Account not linked to any organization"
                        else -> "Login failed. Please try again."
                    }
                    _state.value = LoginState.Error(message)
                }

            } catch (e: Exception) {
                _state.value = LoginState.Error("Network error. Check your connection.")
            }
        }
    }
}
```

---

### 6.7 SIM Registration

```kotlin
// data/local/SimHelper.kt
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

class SimHelper(private val context: Context) {

    data class SimInfo(
        val slot: String,          // "SIM_1" or "SIM_2"
        val phoneNumber: String?,  // May be null on some devices
        val deviceName: String
    )

    fun getActiveSimCards(): List<SimInfo> {
        val result = mutableListOf<SimInfo>()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subscriptionManager = context.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE
            ) as SubscriptionManager

            val subscriptions = subscriptionManager.activeSubscriptionInfoList ?: return result

            subscriptions.forEach { subInfo ->
                val slotIndex = subInfo.simSlotIndex
                val simSlot = if (slotIndex == 0) "SIM_1" else "SIM_2"

                // Phone number may be null — that is okay
                val phoneNumber = subInfo.number?.takeIf { it.isNotBlank() }

                result.add(SimInfo(simSlot, phoneNumber, deviceName))
            }
        } else {
            // Single SIM fallback
            val telephonyManager = context.getSystemService(
                Context.TELEPHONY_SERVICE
            ) as TelephonyManager
            val number = telephonyManager.line1Number?.takeIf { it.isNotBlank() }
            result.add(SimInfo("SIM_1", number, deviceName))
        }

        return result
    }
}

// Call this after login to register SIMs
suspend fun registerAllSims(
    simHelper: SimHelper,
    apiService: CallLogApiService,
    storage: SecureStorage
) {
    val sims = simHelper.getActiveSimCards()

    sims.forEach { sim ->
        // Ask user for number if not available automatically
        val phoneNumber = sim.phoneNumber ?: return@forEach // Skip if null — user will enter manually

        val response = apiService.registerSim(
            token = storage.getAuthHeader(),
            request = RegisterSimRequest(
                simSlot = sim.slot,
                phoneNumber = phoneNumber,
                deviceName = sim.deviceName
            )
        )

        if (response.isSuccessful) {
            when (sim.slot) {
                "SIM_1" -> storage.saveSim1Registered(true)
                "SIM_2" -> storage.saveSim2Registered(true)
            }
        }
    }
}
```

---

### 6.8 Call Log Reader

```kotlin
// data/local/CallLogReader.kt
import android.content.Context
import android.os.Build
import android.provider.CallLog
import java.text.SimpleDateFormat
import java.util.*

class CallLogReader(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun readNewCallLogs(lastSyncTime: Long, simSlot: String): List<SyncRequest.CallRecord> {
        val records = mutableListOf<SyncRequest.CallRecord>()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        val uri = CallLog.Calls.CONTENT_URI
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        // Only fetch records newer than last sync
        val selection = "${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(lastSyncTime.toString())
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val number = cursor.getString(
                        cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                    ) ?: continue

                    if (number.isBlank()) continue

                    val callTypeInt = cursor.getInt(
                        cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                    )
                    val callType = when (callTypeInt) {
                        CallLog.Calls.INCOMING_TYPE  -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE  -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE    -> "MISSED"
                        else -> "INCOMING"
                    }

                    val dateMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                    )
                    val isoDate = dateFormat.format(Date(dateMillis))

                    val duration = cursor.getInt(
                        cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                    )

                    val contactName = cursor.getString(
                        cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                    )?.takeIf { it.isNotBlank() }

                    records.add(
                        SyncRequest.CallRecord(
                            mobileNumber = number.replace(" ", "").replace("-", ""),
                            contactName  = contactName,
                            callType     = callType,
                            date         = isoDate,
                            duration     = duration,
                            simSlot      = simSlot,
                            deviceName   = deviceName
                        )
                    )
                }
            }

        return records
    }
}
```

---

### 6.9 Background Sync Worker

```kotlin
// worker/CallLogSyncWorker.kt
import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class CallLogSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val storage = SecureStorage(applicationContext)

        // Skip if not logged in
        if (!storage.isLoggedIn()) return Result.success()

        val token = storage.getAuthHeader()
        val lastSyncTime = storage.getLastSyncTime()

        val apiService = ApiClient.service
        val callLogReader = CallLogReader(applicationContext)

        try {
            // Sync SIM 1
            if (storage.isSim1Registered()) {
                val records = callLogReader.readNewCallLogs(lastSyncTime, "SIM_1")
                if (records.isNotEmpty()) {
                    syncRecords(apiService, token, "SIM_1", records, storage)
                }
            }

            // Sync SIM 2
            if (storage.isSim2Registered()) {
                val records = callLogReader.readNewCallLogs(lastSyncTime, "SIM_2")
                if (records.isNotEmpty()) {
                    syncRecords(apiService, token, "SIM_2", records, storage)
                }
            }

            // Update last sync time
            storage.saveLastSyncTime(System.currentTimeMillis())

            return Result.success()

        } catch (e: Exception) {
            // Retry on failure (max 3 retries with exponential backoff)
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun syncRecords(
        apiService: CallLogApiService,
        token: String,
        simSlot: String,
        records: List<SyncRequest.CallRecord>,
        storage: SecureStorage
    ) {
        // Split into chunks of 5000
        records.chunked(5000).forEach { chunk ->
            val response = apiService.syncCallLogs(
                token = token,
                request = SyncRequest(simSlot = simSlot, records = chunk)
            )

            if (response.isSuccessful) {
                val syncResult = response.body()?.sync
                // Log success for debugging
                android.util.Log.d("CallLogSync",
                    "Synced ${syncResult?.successRows} records for $simSlot")
            } else if (response.code() == 401) {
                // Token expired — clear and re-login
                storage.clearAll()
            }
        }
    }

    companion object {
        private const val WORK_NAME = "CallLogSyncWork"

        fun schedule(context: Context, intervalMinutes: Long = 60) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<CallLogSyncWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                15, TimeUnit.MINUTES  // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
```

---

### 6.10 App Startup (MainActivity)

```kotlin
// Start everything in MainActivity or Application class
class MainActivity : AppCompatActivity() {

    private lateinit var storage: SecureStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = SecureStorage(this)

        if (!storage.isLoggedIn()) {
            // Go to login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Already logged in — check status and start sync
        setContentView(R.layout.activity_main)

        // Schedule background sync
        val syncInterval = storage.getSyncInterval()
        CallLogSyncWorker.schedule(this, syncInterval)

        // Request permissions if not granted
        requestCallLogPermissions()
    }

    private fun requestCallLogPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_PHONE_STATE
        )

        val notGranted = permissions.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            requestPermissions(notGranted.toTypedArray(), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val allGranted = grantResults.all {
                it == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                // Register SIMs and start first sync
                lifecycleScope.launch {
                    val simHelper = SimHelper(this@MainActivity)
                    registerAllSims(simHelper, ApiClient.service, storage)
                    CallLogSyncWorker.schedule(this@MainActivity)
                }
            }
        }
    }
}
```

---

## 7. Error Handling

### Complete Error Code Reference

| HTTP Status | Error Code | Meaning | Android Action |
|---|---|---|---|
| 400 | VALIDATION_ERROR | Invalid request format | Show field errors to user |
| 401 | UNAUTHORIZED | Token missing | Redirect to login |
| 401 | INVALID_CREDENTIALS | Wrong email/password | Show error message |
| 401 | INVALID_CODE | Wrong unique code | Show "Check Dashboard for correct code" |
| 403 | FORBIDDEN | No permission | Show "Contact your admin" |
| 403 | NO_ORGANIZATION | Not linked to org | Show "Contact your admin" |
| 404 | NOT_FOUND | Resource not found | Log and skip |
| 409 | EMAIL_EXISTS | Email already registered | Show "Email already exists" |
| 500 | INTERNAL_ERROR | Server error | Retry with backoff |

### Token Expiry Handling

```kotlin
// In your API calls, always check for 401
if (response.code() == 401) {
    // Token expired or invalid
    storage.clearAll()  // Clear saved data
    // Redirect to login screen
    val intent = Intent(context, LoginActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    context.startActivity(intent)
}
```

---

## 8. Security Best Practices

### DO ✅
- Store token in `EncryptedSharedPreferences` (never plain SharedPreferences)
- Use HTTPS in production
- Clear all stored data on logout
- Validate server SSL certificate in production
- Send token in `Authorization: Bearer` header only
- Check token expiry and re-login when needed

### DO NOT ❌
- Never hardcode the token in code
- Never log the token in production logs
- Never store the token in plain text
- Never send the token as a URL query parameter
- Never share the unique code with other users

---

## 9. Testing

### Test with Demo Credentials
```
Base URL: http://10.0.2.2:3000/api/mobile/  (Emulator)
          http://192.168.x.x:3000/api/mobile/ (Real device)

Owner Account:
  email:      admin@demo.com
  password:   Admin1234
  uniqueCode: (Check Web Dashboard after login)

Employee Account:
  email:      member1@demo.com
  password:   Admin1234
  uniqueCode: (Check Web Dashboard after login)
```

### Test API with cURL

**Login:**
```bash
curl -X POST http://localhost:3000/api/mobile/verify \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@demo.com","password":"Admin1234","uniqueCode":"OWN-XXXX"}'
```

**Register SIM:**
```bash
curl -X POST http://localhost:3000/api/mobile/register-sim \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"simSlot":"SIM_1","phoneNumber":"+919876543210","deviceName":"Test Device"}'
```

**Sync Call Logs:**
```bash
curl -X POST http://localhost:3000/api/mobile/sync \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "simSlot": "SIM_1",
    "records": [
      {
        "mobileNumber": "9876543210",
        "contactName": "Test Contact",
        "callType": "INCOMING",
        "date": "2024-01-15T10:30:00Z",
        "duration": 120,
        "simSlot": "SIM_1",
        "deviceName": "Test Device"
      }
    ]
  }'
```

**Check Status:**
```bash
curl http://localhost:3000/api/mobile/status \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Test with Swagger UI
```
Open: http://localhost:3000/docs
1. Use POST /api/v1/auth/login first (web login)
2. Copy the token from response
3. Click "Authorize" → paste "Bearer YOUR_TOKEN"
4. Test all Mobile App APIs from the "Mobile App" section
```

---

## Quick Reference Card

```
┌─────────────────────────────────────────────────────┐
│           CallLog SaaS Mobile API                   │
│                                                     │
│  Base: http://YOUR-SERVER/api/mobile/               │
│                                                     │
│  1. LOGIN                                           │
│     POST /verify                                    │
│     Body: { email, password, uniqueCode }           │
│     → Save: token, orgId, userName, syncConfig      │
│                                                     │
│  2. REGISTER SIM (once per SIM)                     │
│     POST /register-sim                              │
│     Auth: Bearer <token>                            │
│     Body: { simSlot, phoneNumber, deviceName }      │
│                                                     │
│  3. SYNC (every 60 minutes)                         │
│     POST /sync                                      │
│     Auth: Bearer <token>                            │
│     Body: { simSlot, records: [...] }               │
│     Max: 5000 records per request                   │
│                                                     │
│  4. STATUS CHECK                                    │
│     GET /status                                     │
│     Auth: Bearer <token>                            │
│                                                     │
│  Error 401 → Re-login required                      │
│  Token expires in 7 days                            │
└─────────────────────────────────────────────────────┘
```
