package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SaveRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun savesCollection(userId: String) =
        db.collection("users").document(userId).collection("saves")

    fun observeSaves(userId: String): Flow<List<GameSave>> {
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
        val docPath = "users/$userId/saves/$slotId"
        return try {
            val uid = auth.currentUser?.uid
                ?: error("Você precisa estar conectado com o Google.")
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

    companion object {
        private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
    }
}
