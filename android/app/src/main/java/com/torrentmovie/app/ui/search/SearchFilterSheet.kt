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
    maxSeeds: Int?,
    maxSize: String?,
    onDismiss: () -> Unit,
    onApply: (minSeeds: Int?, maxSeeds: Int?, maxSize: String?) -> Unit,
) {
    var minSeedsText by remember(minSeeds) { mutableStateOf(minSeeds?.toString() ?: "") }
    var maxSeedsText by remember(maxSeeds) { mutableStateOf(maxSeeds?.toString() ?: "") }
    var sizeText by remember(maxSize) { mutableStateOf(maxSize ?: "") }
    var minSeedsError by remember { mutableStateOf<String?>(null) }
    var maxSeedsError by remember { mutableStateOf<String?>(null) }
    var sizeError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("Filters")
            OutlinedTextField(
                value = minSeedsText,
                onValueChange = {
                    minSeedsText = it
                    minSeedsError = null
                },
                label = { Text("Min seeds") },
                isError = minSeedsError != null,
                supportingText = minSeedsError?.let { { Text(it, color = Color.Red) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            OutlinedTextField(
                value = maxSeedsText,
                onValueChange = {
                    maxSeedsText = it
                    maxSeedsError = null
                },
                label = { Text("Max seeds") },
                isError = maxSeedsError != null,
                supportingText = maxSeedsError?.let { { Text(it, color = Color.Red) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
                    val minTrimmed = minSeedsText.trim()
                    val parsedMin = if (minTrimmed.isEmpty()) null else minTrimmed.toIntOrNull()
                    if (minTrimmed.isNotEmpty() && parsedMin == null) {
                        minSeedsError = "Enter a whole number"
                        return@Button
                    }
                    if (parsedMin != null && parsedMin < 0) {
                        minSeedsError = "Must be 0 or greater"
                        return@Button
                    }
                    val maxTrimmed = maxSeedsText.trim()
                    val parsedMax = if (maxTrimmed.isEmpty()) null else maxTrimmed.toIntOrNull()
                    if (maxTrimmed.isNotEmpty() && parsedMax == null) {
                        maxSeedsError = "Enter a whole number"
                        return@Button
                    }
                    if (parsedMax != null && parsedMax < 0) {
                        maxSeedsError = "Must be 0 or greater"
                        return@Button
                    }
                    if (parsedMin != null && parsedMax != null && parsedMin > parsedMax) {
                        maxSeedsError = "Must be >= min seeds"
                        return@Button
                    }
                    val trimmedSize = sizeText.trim()
                    if (trimmedSize.isNotEmpty() && !SearchFilterValidation.isValidMaxSize(trimmedSize)) {
                        sizeError = "Use a size like 4GB or 1.5 GiB (unit required)"
                        return@Button
                    }
                    onApply(parsedMin, parsedMax, trimmedSize.ifBlank { null })
                    onDismiss()
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Apply") }
        }
    }
}
