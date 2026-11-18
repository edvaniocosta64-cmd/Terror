package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.GameSave
import com.example.ui.theme.BoneParchment
import com.example.ui.theme.CrimsonBlood
import com.example.ui.theme.FogMist
import com.example.ui.theme.LanternAmber
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.viewmodel.SavesUiState
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SaveSlotsScreen(
    savesState: SavesUiState,
    onLoadSave: (GameSave) -> Unit,
    onDeleteSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("saves_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = BoneParchment,
                    )
                }
                Text(
                    text = "SALVAMENTOS NA NUVEM",
                    style = MaterialTheme.typography.headlineMedium,
                    color = BoneParchment,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (savesState) {
                is SavesUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = CrimsonBlood)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Carregando seus jogos salvos...", color = FogMist)
                    }
                }
                is SavesUiState.Error -> {
                    Text(
                        text = savesState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is SavesUiState.Success -> {
                    if (savesState.saves.isEmpty()) {
                        Surface(
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ObsidianOutline, RoundedCornerShape(14.dp)),
                        ) {
                            Text(
                                text = "Nenhum progresso salvo ainda. Inicie o Modo História e salve pelo menu de pausa!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = FogMist,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(savesState.saves, key = { it.id }) { save ->
                                SaveSlotCard(
                                    save = save,
                                    onLoad = { onLoadSave(save) },
                                    onDelete = { onDeleteSave(save.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveSlotCard(
    save: GameSave,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateLabel = save.updatedAt?.toDate()?.let { formatter.format(it) } ?: "Recente"
    val mins = save.playTimeSeconds / 60
    val secs = save.playTimeSeconds % 60

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianOutline, RoundedCornerShape(14.dp))
            .testTag("save_card_${save.id}"),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = save.slotName,
                    style = MaterialTheme.typography.labelSmall,
                    color = LanternAmber,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = save.chapterTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = BoneParchment,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Vida: ${save.health}% • Tempo: %02d:%02d • $dateLabel".format(mins, secs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FogMist,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_save_${save.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Apagar salvamento",
                        tint = FogMist,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onLoad,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonBlood),
                    modifier = Modifier.testTag("load_save_${save.id}"),
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Carregar")
                }
            }
        }
    }
}
