package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.GameSave
import com.example.ui.theme.BoneParchment
import com.example.ui.theme.CrimsonBlood
import com.example.ui.theme.FogMist
import com.example.ui.theme.LanternAmber
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.SavesUiState

@Composable
fun MainMenuScreen(
    savesState: SavesUiState,
    userEmail: String?,
    onNewGame: () -> Unit,
    onContinueSave: (GameSave) -> Unit,
    onOpenSaves: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val latestSave = (savesState as? SavesUiState.Success)?.saves?.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        Image(
            painter = painterResource(id = R.drawable.horror_manor_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.65f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ObsidianVoid.copy(alpha = 0.94f),
                            ObsidianVoid.copy(alpha = 0.68f),
                            ObsidianVoid.copy(alpha = 0.35f),
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top bar with player identity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = ObsidianSurface.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, ObsidianOutline, RoundedCornerShape(8.dp)),
                ) {
                    Text(
                        text = "CAPÍTULO 1 • A CASA DA ESTRADA",
                        style = MaterialTheme.typography.labelSmall,
                        color = LanternAmber,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                TextButton(
                    onClick = { signOutPlayer(credentialManager, scope) },
                    modifier = Modifier.testTag("sign_out_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sair da conta",
                        tint = FogMist,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = userEmail?.substringBefore("@") ?: "Sair",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FogMist,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Title + Menu Actions
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
            ) {
                Text(
                    text = "PROJECT:",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CrimsonBlood,
                )
                Text(
                    text = "DARKNESS",
                    style = MaterialTheme.typography.displayLarge,
                    color = BoneParchment,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Uma estrada esquecida. Uma porta que se tranca atrás de você. Sobreviva em 3D à escuridão.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FogMist,
                )

                Spacer(modifier = Modifier.height(28.dp))

                if (latestSave != null) {
                    MenuPrimaryButton(
                        text = "Continuar (${latestSave.chapterTitle})",
                        icon = Icons.Filled.Replay,
                        testTag = "continue_game_button",
                        onClick = { onContinueSave(latestSave) },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                MenuPrimaryButton(
                    text = "Novo Jogo • Modo História",
                    icon = Icons.Filled.PlayArrow,
                    testTag = "new_game_button",
                    isPrimaryHighlight = latestSave == null,
                    onClick = onNewGame,
                )

                Spacer(modifier = Modifier.height(12.dp))

                MenuSecondaryButton(
                    text = "Carregar Salvamento",
                    icon = Icons.Filled.FolderOpen,
                    testTag = "load_saves_button",
                    onClick = onOpenSaves,
                )

                Spacer(modifier = Modifier.height(12.dp))

                MenuSecondaryButton(
                    text = "Configurações (FPS & Controles)",
                    icon = Icons.Filled.Settings,
                    testTag = "settings_button",
                    onClick = onOpenSettings,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Renderização OpenGL ES 3D • Suporte 30/60/90/120 FPS com Escala Automática",
                style = MaterialTheme.typography.labelSmall,
                color = FogMist.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun MenuPrimaryButton(
    text: String,
    icon: ImageVector,
    testTag: String,
    isPrimaryHighlight: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimaryHighlight) CrimsonBlood else ObsidianSurface.copy(alpha = 0.92f),
            contentColor = BoneParchment,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag(testTag),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MenuSecondaryButton(
    text: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ObsidianSurface.copy(alpha = 0.75f),
            contentColor = BoneParchment,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = LanternAmber, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
