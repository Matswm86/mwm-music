package no.mwmai.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** A clickable that announces itself as a named button to screen readers. */
fun Modifier.pressable(label: String, onClick: () -> Unit): Modifier =
    clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
