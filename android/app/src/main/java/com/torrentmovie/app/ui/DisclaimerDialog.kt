package com.torrentmovie.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Legal disclaimer") },
        text = {
            Text(
                "This app searches public torrent indexers and sends magnet links to your " +
                    "configured seedbox. You are responsible for complying with local laws " +
                    "and the terms of your seedbox provider. No media is hosted or played in this app.",
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("I understand") }
        },
    )
}
