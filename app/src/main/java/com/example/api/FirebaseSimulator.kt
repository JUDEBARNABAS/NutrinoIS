package com.example.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class FieldAgent(
    val uid: String,
    val name: String,
    val email: String,
    val profilePhotoUrl: String = "https://lh3.googleusercontent.com/a/default-user=s96-c",
    val idToken: String
)

object FirebaseSimulator {
    private const val TAG = "FirebaseSimulator"
    private const val PREFS_NAME = "firebase_agent_prefs"
    private const val KEY_UID = "agent_uid"
    private const val KEY_NAME = "agent_name"
    private const val KEY_EMAIL = "agent_email"
    private const val KEY_TOKEN = "agent_token"

    private val _currentAgent = MutableStateFlow<FieldAgent?>(null)
    val currentAgent = _currentAgent.asStateFlow()

    private val _firestoreSyncStatus = MutableStateFlow("Firestore Connected (Local-First Sync Enabled)")
    val firestoreSyncStatus = _firestoreSyncStatus.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<String>>(emptyList())
    val syncLogs = _syncLogs.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getString(KEY_UID, null)
        if (uid != null) {
            _currentAgent.value = FieldAgent(
                uid = uid,
                name = prefs.getString(KEY_NAME, "Field Agent") ?: "Field Agent",
                email = prefs.getString(KEY_EMAIL, "agent@agroscope.org") ?: "agent@agroscope.org",
                idToken = prefs.getString(KEY_TOKEN, "mock-google-id-token") ?: "mock-google-id-token"
            )
            addSyncLog("Restored Google Sign-In session for: ${prefs.getString(KEY_EMAIL, "")}")
        }
    }

    fun loginWithGoogleCredential(context: Context, name: String, email: String, onComplete: () -> Unit) {
        val mockUid = "gg-" + (1000000..9999999).random().toString()
        val mockToken = "google-id-token-" + (10000..99999).random().toString()
        val agent = FieldAgent(
            uid = mockUid,
            name = name.ifBlank { "Field Agent Alpha" },
            email = email.ifBlank { "alpha@farming.com" },
            idToken = mockToken
        )

        // Save session
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_UID, agent.uid)
            putString(KEY_NAME, agent.name)
            putString(KEY_EMAIL, agent.email)
            putString(KEY_TOKEN, agent.idToken)
            apply()
        }

        _currentAgent.value = agent
        addSyncLog("Google Authenticated: ${agent.name} (${agent.email}) securely identified via Firebase Auth.")
        onComplete()
    }

    fun logoutGoogle(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        _currentAgent.value = null
        addSyncLog("Firebase Auth: User logged out successfully.")
    }

    fun addSyncLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        _syncLogs.value = (listOf("[$timestamp] $message") + _syncLogs.value).take(40)
    }

    /**
     * Simulates sending a record to Firestore db
     */
    fun syncToFirestore(collection: String, documentId: String, data: Map<String, Any>) {
        val jsonRepr = JSONObject(data).toString(2)
        addSyncLog("Firestore Sync Upload: Collection '$collection' Document ID '$documentId'")
        Log.d(TAG, "Sync to Firestore collection: $collection, doc: $documentId. Content:\n$jsonRepr")
        
        // Push sync status metrics
        _firestoreSyncStatus.value = "Firestore Upload Synced Document #$documentId"
    }
}
