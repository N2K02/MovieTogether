package com.movietogether.app.watch

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.URI

/**
 * Firebase is a discoverability directory ONLY. It never uploads or relays video bytes.
 * The movie still streams from the host's embedded HTTP server; CGNAT remains a limitation.
 *
 * Requires a registered Android Firebase app for com.movietogether.app, enabled anonymous auth,
 * a Realtime Database and matching per-room security rules.
 */
object FirebaseRoomDirectory {
    private const val PROJECT_ID = "movietogether-45988"
    private const val ROOM_LIFETIME_MS = 4L * 60L * 60L * 1000L
    private val validCode = Regex("[A-Za-z0-9_-]{32}")
    val status = MutableStateFlow("Firebase not configured")
    val invitation = MutableStateFlow<String?>(null)

    private var publishedRoom: DatabaseReference? = null
    private var generation: Long = 0L

    fun isAvailable(context: Context): Boolean =
        firebase(context) != null

    private fun firebase(context: Context): FirebaseApp? {
        val app = runCatching {
            FirebaseApp.initializeApp(context.applicationContext) ?: FirebaseApp.getInstance()
        }.getOrNull()
        if (app == null) {
            status.value = "Firebase Android configuration not installed; direct links still work"
            return null
        }
        if (app.options.projectId != PROJECT_ID || app.options.databaseUrl.isNullOrBlank()) {
            status.value = "Firebase project/database configuration does not match MovieTogether"
            return null
        }
        return app
    }

    private fun withAuthenticatedRoomDatabase(
        context: Context,
        success: (DatabaseReference, String) -> Unit,
        failure: (String) -> Unit,
    ) {
        val app = firebase(context)
        if (app == null) {
            failure(status.value)
            return
        }
        val db = runCatching { FirebaseDatabase.getInstance(app).reference.child("rooms") }
            .getOrElse {
                failure("Realtime Database is not enabled or its URL is missing")
                return
            }
        val auth = FirebaseAuth.getInstance(app)
        val uid = auth.currentUser?.uid
        if (uid != null) {
            success(db, uid)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener {
                val userId = it.user?.uid
                if (userId.isNullOrBlank()) failure("Anonymous authentication returned no user")
                else success(db, userId)
            }
            .addOnFailureListener { failure("Firebase anonymous sign-in failed: ${it.localizedMessage}") }
    }

    /** Publish an unguessable room code to resolve the host's current address. */
    fun publish(context: Context, address: RoomAddress, movieTitle: String) {
        val requestGeneration = ++generation
        // A refreshed invitation invalidates the previous room code.
        val previous = publishedRoom
        publishedRoom = null
        previous?.removeValue()
        invitation.value = null
        status.value = "Registering room with Firebase…"
        withAuthenticatedRoomDatabase(context, { root, uid ->
            if (generation != requestGeneration) return@withAuthenticatedRoomDatabase
            val code = RoomAddress.newToken().take(32)
            val ref = root.child(code)
            val record = mapOf(
                "ownerUid" to uid,
                "host" to address.host,
                "port" to address.port,
                "mediaToken" to address.token,
                "title" to movieTitle.take(120),
                "expiresAt" to (System.currentTimeMillis() + ROOM_LIFETIME_MS),
            )
            ref.setValue(record)
                .addOnSuccessListener {
                    if (generation != requestGeneration) {
                        ref.removeValue()
                        return@addOnSuccessListener
                    }
                    publishedRoom = ref
                    // Server cannot guarantee disconnection cleanup after every process death:
                    // expiry in both rules and read validation is the fallback.
                    ref.onDisconnect().removeValue()
                    invitation.value = "movietogether://join?room=$code"
                    status.value = "Firebase room registered; video still needs direct reachability"
                }
                .addOnFailureListener {
                    if (generation == requestGeneration) status.value =
                        "Firebase room could not be registered: ${it.localizedMessage}"
                }
        }, { error ->
            if (generation == requestGeneration) status.value = error
        })
    }

    /** Prevent stale room code pointing to a stopped local video server. */
    fun endHosting() {
        generation++
        invitation.value = null
        val old = publishedRoom
        publishedRoom = null
        old?.removeValue()
        status.value = "Room ended"
    }

    fun resolve(context: Context, code: String, result: (Result<RoomAddress>) -> Unit) {
        if (!validCode.matches(code)) {
            result(Result.failure(IllegalArgumentException("Invalid Firebase room invitation")))
            return
        }
        withAuthenticatedRoomDatabase(context, { root, _ ->
            root.child(code).get()
                .addOnSuccessListener { snapshot ->
                    val expiry = snapshot.child("expiresAt").getValue(Long::class.java) ?: 0L
                    if (!snapshot.exists() || expiry < System.currentTimeMillis()) {
                        result(Result.failure(IllegalStateException("Room expired or host offline")))
                    } else {
                        val address = runCatching {
                            RoomAddress(
                                requireNotNull(snapshot.child("host").getValue(String::class.java)),
                                requireNotNull(snapshot.child("port").getValue(Long::class.java)).toInt(),
                                requireNotNull(snapshot.child("mediaToken").getValue(String::class.java)),
                            )
                        }
                        result(address)
                    }
                }
                .addOnFailureListener {
                    result(Result.failure(IllegalStateException("Cannot look up Firebase room: ${it.localizedMessage}")))
                }
        }, { error -> result(Result.failure(IllegalStateException(error))) })
    }

    /** Accept either a code or the custom-scheme Firebase room link. */
    fun extractCode(input: String): String? {
        val trimmed = input.trim()
        if (validCode.matches(trimmed)) return trimmed
        return runCatching {
            val uri = URI(trimmed)
            if (uri.scheme != "movietogether" || uri.host != "join") return null
            uri.rawQuery.orEmpty().split('&')
                .mapNotNull {
                    val pieces = it.split('=', limit = 2)
                    if (pieces.size == 2) pieces[0] to pieces[1] else null
                }.toMap()["room"]?.takeIf(validCode::matches)
        }.getOrNull()
    }
}
