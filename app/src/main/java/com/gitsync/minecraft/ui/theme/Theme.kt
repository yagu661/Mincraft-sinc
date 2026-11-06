package com.gitsync.minecraft.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = GraphiteBackground,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldLight,
    surface = GraphiteSurface,
    onSurface = TextPrimary,
    background = GraphiteBackground,
    onBackground = TextPrimary,
    surfaceVariant = GraphiteCard,
    onSurfaceVariant = TextSecondary,
    outline = GraphiteCardBorder,
    error = RedstoneError,
    onError = TextPrimary
)

@Composable
fun MinecraftGitSyncTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
