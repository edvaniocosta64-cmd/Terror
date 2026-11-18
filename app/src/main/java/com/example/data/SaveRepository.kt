package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

class SaveRepository(
    private val db: FirebaseFirestore?,
    private val auth: FirebaseAuth? = null,
    private val localFilesDir: File? = null,
) {
    constructor(context: Context) : this(
        db = if (FirebaseApp.getApps(context.applicationContext).isNotEmpty()) {
            FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
        } else {
            null
        },
        auth = if (FirebaseApp.getApps(context.applicationContext).isNotEmpty()) {
            Firebase.auth
        } else {
            null
        },
        localFilesDir = context.applicationContext.filesDir,
    )

    private val localSavesFlow = MutableStateFlow<List<GameSave>>(loadLocalSavesFromDisk())

    private fun savesCollection(userId: String) =
        requireNotNull(db).collection("users").document(userId).collection("saves")

    fun observeSaves(userId: String): Flow<List<GameSave>> {
        if (db == null || auth == null || userId == OFFLINE_USER_ID) {
            return localSavesFlow
        }
        require(userId.isNotBlank()) { "userId must not be blank" }
        return savesCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    GameSave::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt ?: it.createdAt }
            }
    }

    suspend fun saveGame(
        userId: String,
        slotId: String,
        save: GameSave,
    ): Result<Unit> {
        if (db == null || auth == null || userId == OFFLINE_USER_ID) {
            return saveLocally(slotId, save)
        }
        val docPath = "users/$userId/saves/$slotId"
        return try {
            val uid = auth.currentUser?.uid
                ?: error("Você precisa estar conectado com o Google para salvar.")
            check(uid == userId) { "Usuário autenticado divergente." }
            require(VALID_ID_REGEX.matches(slotId)) { "Slot ID inválido: $slotId" }

            val docRef = savesCollection(userId).document(slotId)
            val existing = docRef.get().await()

            val sanitizedSlotName = save.slotName.trim().ifBlank { "Slot" }.take(60)
            val sanitizedChapter = save.chapterTitle.trim().ifBlank { "Modo História" }.take(100)
            val sanitizedPhase = StoryPhase.fromRaw(save.storyPhase).name
            val clampedX = save.playerX.coerceIn(-200.0, 200.0)
            val clampedY = save.playerY.coerceIn(-50.0, 50.0)
            val clampedZ = save.playerZ.coerceIn(-200.0, 200.0)
            val clampedYaw = save.playerYaw.coerceIn(-100.0, 100.0)
            val clampedHealth = save.health.coerceIn(0L, 100L)
            val clampedBattery = save.battery.coerceIn(0L, 100L)
            val clampedTime = save.playTimeSeconds.coerceIn(0L, 9_999_999L)
            val unlocked = save.unlockedDoors.distinct().take(32)
            val collected = save.collectedItems.distinct().take(32)
            val inventory = save.inventoryItems.distinct().take(16)
            val notes = save.readNotes.distinct().take(16)

            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "slotName" to sanitizedSlotName,
                        "chapterTitle" to sanitizedChapter,
                        "storyPhase" to sanitizedPhase,
                        "playerX" to clampedX,
                        "playerY" to clampedY,
                        "playerZ" to clampedZ,
                        "playerYaw" to clampedYaw,
                        "health" to clampedHealth,
                        "battery" to clampedBattery,
                        "flashlightOn" to save.flashlightOn,
                        "unlockedDoors" to unlocked,
                        "collectedItems" to collected,
                        "inventoryItems" to inventory,
                        "readNotes" to notes,
                        "playTimeSeconds" to clampedTime,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "slotName" to sanitizedSlotName,
                        "chapterTitle" to sanitizedChapter,
                        "storyPhase" to sanitizedPhase,
                        "playerX" to clampedX,
                        "playerY" to clampedY,
                        "playerZ" to clampedZ,
                        "playerYaw" to clampedYaw,
                        "health" to clampedHealth,
                        "battery" to clampedBattery,
                        "flashlightOn" to save.flashlightOn,
                        "unlockedDoors" to unlocked,
                        "collectedItems" to collected,
                        "inventoryItems" to inventory,
                        "readNotes" to notes,
                        "playTimeSeconds" to clampedTime,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSave(userId: String, slotId: String): Result<Unit> {
        if (db == null || auth == null || userId == OFFLINE_USER_ID) {
            return deleteLocally(slotId)
        }
        val docPath = "users/$userId/saves/$slotId"
        return try {
            val uid = auth.currentUser?.uid
                ?: error("Você precisa estar conectado com o Google para apagar.")
            check(uid == userId) { "Usuário autenticado divergente." }
            require(VALID_ID_REGEX.matches(slotId)) { "Slot ID inválido: $slotId" }
            savesCollection(userId).document(slotId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun saveLocally(slotId: String, save: GameSave): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val now = Timestamp(Date())
            val sanitized = save.copy(
                id = slotId,
                userId = OFFLINE_USER_ID,
                slotName = save.slotName.trim().ifBlank { "Slot" }.take(60),
                chapterTitle = save.chapterTitle.trim().ifBlank { "Modo História" }.take(100),
                storyPhase = StoryPhase.fromRaw(save.storyPhase).name,
                updatedAt = now,
                createdAt = save.createdAt ?: now,
            )
            val updated = (localSavesFlow.value.filterNot { it.id == slotId } + sanitized)
                .sortedByDescending { it.updatedAt ?: it.createdAt }
            writeLocalSavesToDisk(updated)
            localSavesFlow.value = updated
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deleteLocally(slotId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updated = localSavesFlow.value.filterNot { it.id == slotId }
            writeLocalSavesToDisk(updated)
            localSavesFlow.value = updated
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadLocalSavesFromDisk(): List<GameSave> {
        val file = localFilesDir?.let { File(it, LOCAL_SAVES_FILENAME) } ?: return emptyList()
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { idx ->
                val obj = arr.getJSONObject(idx)
                val ts = Timestamp(Date(obj.optLong("updatedAtMs", System.currentTimeMillis())))
                GameSave(
                    id = obj.optString("id", "slot_1"),
                    userId = OFFLINE_USER_ID,
                    slotName = obj.optString("slotName", "Slot"),
                    chapterTitle = obj.optString("chapterTitle", "Modo História"),
                    storyPhase = obj.optString("storyPhase", StoryPhase.INTRO_ROAD.name),
                    playerX = obj.optDouble("playerX", 0.0),
                    playerY = obj.optDouble("playerY", 1.65),
                    playerZ = obj.optDouble("playerZ", 24.0),
                    playerYaw = obj.optDouble("playerYaw", 0.0),
                    health = obj.optLong("health", 100L),
                    battery = obj.optLong("battery", 100L),
                    flashlightOn = obj.optBoolean("flashlightOn", false),
                    unlockedDoors = obj.optJSONArray("unlockedDoors").toStringList(),
                    collectedItems = obj.optJSONArray("collectedItems").toStringList(),
                    inventoryItems = obj.optJSONArray("inventoryItems").toStringList(),
                    readNotes = obj.optJSONArray("readNotes").toStringList(),
                    playTimeSeconds = obj.optLong("playTimeSeconds", 0L),
                    createdAt = ts,
                    updatedAt = ts,
                )
            }.sortedByDescending { it.updatedAt ?: it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeLocalSavesToDisk(saves: List<GameSave>) {
        val dir = localFilesDir ?: return
        val arr = JSONArray()
        for (s in saves) {
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("slotName", s.slotName)
                    put("chapterTitle", s.chapterTitle)
                    put("storyPhase", s.storyPhase)
                    put("playerX", s.playerX)
                    put("playerY", s.playerY)
                    put("playerZ", s.playerZ)
                    put("playerYaw", s.playerYaw)
                    put("health", s.health)
                    put("battery", s.battery)
                    put("flashlightOn", s.flashlightOn)
                    put("unlockedDoors", JSONArray(s.unlockedDoors))
                    put("collectedItems", JSONArray(s.collectedItems))
                    put("inventoryItems", JSONArray(s.inventoryItems))
                    put("readNotes", JSONArray(s.readNotes))
                    put("playTimeSeconds", s.playTimeSeconds)
                    put("updatedAtMs", s.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
                }
            )
        }
        File(dir, LOCAL_SAVES_FILENAME).writeText(arr.toString())
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { i -> optString(i) }.filter { it.isNotBlank() }
    }

    companion object {
        const val OFFLINE_USER_ID = "offline_local_player"
        private const val LOCAL_SAVES_FILENAME = "darkness_local_saves.json"
        private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
    }
}
