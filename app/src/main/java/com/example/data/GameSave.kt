package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class StoryPhase(val chapterTitle: String) {
    INTRO_ROAD("Prólogo: A Estrada na Noite"),
    PORCH_DIALOGUE("Na Entrada da Casa"),
    INSIDE_MYSTERY("Dentro da Casa Antiga"),
    GRANNYS_PURSUIT("A Verdade da Vovó"),
    ESCAPED("Fuga Concluída");

    companion object {
        fun fromRaw(raw: String?): StoryPhase {
            return entries.firstOrNull { it.name == raw } ?: INTRO_ROAD
        }
    }
}

@Keep
@IgnoreExtraProperties
data class GameSave(
    @DocumentId val id: String = "",
    val userId: String = "",
    val slotName: String = "",
    val chapterTitle: String = "",
    val storyPhase: String = StoryPhase.INTRO_ROAD.name,
    val playerX: Double = 0.0,
    val playerY: Double = 1.65,
    val playerZ: Double = 24.0,
    val playerYaw: Double = 0.0,
    val health: Long = 100L,
    val battery: Long = 100L,
    val flashlightOn: Boolean = false,
    val unlockedDoors: List<String> = emptyList(),
    val collectedItems: List<String> = emptyList(),
    val inventoryItems: List<String> = emptyList(),
    val readNotes: List<String> = emptyList(),
    val playTimeSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val resolvedPhase: StoryPhase
        get() = StoryPhase.fromRaw(storyPhase)
}
