package no.mwmai.music.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import no.mwmai.music.playback.PlayerState
import no.mwmai.music.ui.theme.Deck
import no.mwmai.music.ui.theme.Mono
import no.mwmai.music.util.formatTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The main window: display, seek bar, transport. Always on screen. */
@Composable
fun DeckPanel(
    state: PlayerState,
    isFavorite: Boolean,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Deck.Panel)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "MWM MUSIC",
                color = Deck.TextDim,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.weight(1f))
            Lamp("SHUF", state.shuffle)
            Spacer(Modifier.width(10.dp))
            Lamp(
                when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "REP 1"
                    else -> "REP"
                },
                state.repeatMode != Player.REPEAT_MODE_OFF,
            )
        }
        Spacer(Modifier.height(8.dp))
        Display(state)
        SeekBar(state, onSeek)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckButton(Icons.Rounded.Shuffle, "Shuffle", onShuffle, lit = state.shuffle, small = true)
            DeckButton(Icons.Rounded.SkipPrevious, "Previous", onPrevious)
            DeckButton(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.isPlaying) "Pause" else "Play",
                onTogglePlay,
                primary = true,
            )
            DeckButton(Icons.Rounded.Stop, "Stop", onStop)
            DeckButton(Icons.Rounded.SkipNext, "Next", onNext)
            DeckButton(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat",
                onRepeat,
                lit = state.repeatMode != Player.REPEAT_MODE_OFF,
                small = true,
            )
            DeckButton(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (isFavorite) "Remove from favorites" else "Add to favorites",
                onFavorite,
                tint = if (isFavorite) Deck.Heart else null,
                small = true,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Display(state: PlayerState) {
    val empty = state.index < 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Deck.Lcd)
            .border(1.dp, Deck.Edge, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0A100D)),
            contentAlignment = Alignment.Center,
        ) {
            if (state.art != null) {
                Image(
                    bitmap = state.art,
                    contentDescription = "Album cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(76.dp),
                )
            } else {
                Bars(moving = state.isPlaying, modifier = Modifier.size(width = 60.dp, height = 52.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatTime(state.positionMs),
                    color = Deck.Phosphor,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 30.sp,
                )
                Text(
                    "  / ${formatTime(state.durationMs)}",
                    color = Deck.PhosphorDim,
                    fontFamily = Mono,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Text(
                if (empty) "Pick a track to start" else "${state.index + 1}. ${state.title}",
                color = Deck.Phosphor,
                fontFamily = Mono,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
            Text(
                if (empty) "No ads. No account. Just your files."
                else listOf(state.artist, state.album).filter { it.isNotBlank() }.joinToString("  ·  "),
                color = Deck.PhosphorDim,
                fontFamily = Mono,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Decoration, not measurement: reading the real audio spectrum needs the
 * microphone permission, and a music player has no business asking for it.
 */
@Composable
private fun Bars(moving: Boolean, modifier: Modifier = Modifier) {
    val phase = if (moving) {
        val transition = rememberInfiniteTransition(label = "bars")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )
        value
    } else {
        null
    }
    Canvas(modifier) {
        val count = 9
        val gap = size.width * 0.04f
        val barWidth = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val level = if (phase == null) 0.08f
            else 0.18f + 0.78f * abs(sin(phase * (1 + i % 3) + i * 0.9f)) * (1f - abs(i - 4) * 0.08f)
            val h = size.height * level
            drawRect(
                color = if (level > 0.78f) Deck.Amber else Deck.Phosphor,
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
            )
        }
    }
}

@Composable
private fun SeekBar(state: PlayerState, onSeek: (Long) -> Unit) {
    // While a thumb is held the slider follows the finger, not the player.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    Slider(
        value = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onSeek((it * duration).toLong()) }
            dragging = null
        },
        enabled = state.durationMs > 0,
        colors = SliderDefaults.colors(
            thumbColor = Deck.Phosphor,
            activeTrackColor = Deck.Phosphor,
            inactiveTrackColor = Deck.Raised,
            disabledThumbColor = Deck.Edge,
            disabledActiveTrackColor = Deck.Edge,
            disabledInactiveTrackColor = Deck.Raised,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Seek" },
    )
}

@Composable
private fun Lamp(label: String, on: Boolean) {
    Text(
        label,
        color = if (on) Deck.Amber else Deck.Edge,
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun DeckButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    small: Boolean = false,
    lit: Boolean = false,
    tint: Color? = null,
) {
    val box = if (primary) 58.dp else if (small) 40.dp else 48.dp
    val shape = RoundedCornerShape(if (primary) 16.dp else 12.dp)
    Box(
        Modifier
            .size(box)
            .clip(shape)
            .background(if (primary) Deck.Phosphor else if (small) Color.Transparent else Deck.Raised)
            .then(if (primary || small) Modifier else Modifier.border(1.dp, Deck.Edge, shape))
            .pressable(label, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint ?: if (primary) Deck.Ink else if (lit) Deck.Amber else if (small) Deck.TextDim else Deck.Text,
            modifier = Modifier.size(if (primary) 32.dp else if (small) 22.dp else 26.dp),
        )
    }
}
