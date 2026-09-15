package com.torrentmovie.app.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.torrentmovie.core.data.SearchFilterValidation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterSheet(
    minSeeds: Int?,
    maxSize: String?,
    onDismiss: () -> Unit,
    onApply: (Int?, String?) -> Unit,
) {
    var seedsText by remember(minSeeds) { mutableStateOf(minSeeds?.toString() ?: "") }
    var sizeText by remember(maxSize) { mutableStateOf(maxSize ?: "") }
    var seedsError by remember { mutableStateOf<String?>(null) }
    var sizeError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("Filters")
            OutlinedTextField(
                value = seedsText,
                onValueChange = {
                    seedsText = it
                    seedsError = null
                },
                label = { Text("Min seeds") },
                isError = seedsError != null,
                supportingText = seedsError?.let { { Text(it, color = Color.Red) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            OutlinedTextField(
                value = sizeText,
                onValueChange = {
                    sizeText = it
                    sizeError = null
                },
                label = { Text("Max size (e.g. 4GB)") },
                isError = sizeError != null,
                supportingText = sizeError?.let { { Text(it, color = Color.Red) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Button(
                onClick = {
                    val trimmed = seedsText.trim()
                    val parsedSeeds = if (trimmed.isEmpty()) null else trimmed.toIntOrNull()
                    if (trimmed.isNotEmpty() && parsedSeeds == null) {
                        seedsError = "Enter a whole number"
                        return@Button
                    }
                    if (parsedSeeds != null && parsedSeeds < 0) {
                        seedsError = "Must be 0 or greater"
                        return@Button
                    }
                    val trimmedSize = sizeText.trim()
                    if (trimmedSize.isNotEmpty() && !SearchFilterValidation.isValidMaxSize(trimmedSize)) {
                        sizeError = "Use a size like 4GB or 1.5 GiB (unit required)"
                        return@Button
                    }
                    onApply(parsedSeeds, trimmedSize.ifBlank { null })
                    onDismiss()
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Apply") }
        }
    }
}
