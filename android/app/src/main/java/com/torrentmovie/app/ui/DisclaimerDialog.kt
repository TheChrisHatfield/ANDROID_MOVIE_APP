package com.torrentmovie.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.torrentmovie.app.R
import com.torrentmovie.app.ui.theme.PantoneRed
import com.torrentmovie.app.ui.theme.missyFilledButtonColors

@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit,
) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.missy_portrait),
                    contentDescription = "Missy",
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .widthIn(max = 220.dp)
                        .fillMaxWidth(0.65f),
                    contentScale = ContentScale.Fit,
                )
                Surface(
                    color = PantoneRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.missy_wordmark),
                        contentDescription = "Missy's Movies",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Legal disclaimer",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        text = "This app searches public torrent indexers and sends magnet links to your " +
                            "configured seedbox. You are responsible for complying with local laws " +
                            "and the terms of your seedbox provider. No media is hosted or played in this app.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = missyFilledButtonColors(),
                    ) {
                        Text("I understand")
                    }
                }
            }
        }
    }
}
