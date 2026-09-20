package no.mwmai.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwmai.music.ui.theme.Deck
import no.mwmai.music.ui.theme.Mono

/** One entry in a row's three-dot menu. */
data class RowAction(val label: String, val run: () -> Unit)

/** A playlist-editor line: number, title, artist, length, heart, menu. */
@Composable
fun TrackRow(
    number: Int,
    title: String,
    subtitle: String,
    trailing: String,
    current: Boolean,
    favorite: Boolean?,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    actions: List<RowAction>,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (current) Deck.Raised else Deck.Ink)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$number",
            color = if (current) Deck.Amber else Deck.PhosphorDim,
            fontFamily = Mono,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                title,
                color = if (current) Deck.Phosphor else Deck.Text,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = Deck.TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            trailing,
            color = Deck.TextDim,
            fontFamily = Mono,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
        if (favorite != null) {
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (favorite) Deck.Heart else Deck.Edge,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (actions.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Deck.TextDim)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                open = false
                                action.run()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Deck.TextDim,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
    )
}
