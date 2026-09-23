package com.torrentmovie.app.ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Rounded gray search/settings fields on white content (reference UI). */
@Composable
fun missyOutlinedTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = SearchFieldGray,
        unfocusedContainerColor = SearchFieldGray,
        disabledContainerColor = SearchFieldGray,
        focusedTextColor = TextCharcoal,
        unfocusedTextColor = TextCharcoal,
        focusedLabelColor = TextMuted,
        unfocusedLabelColor = TextMuted,
        focusedPlaceholderColor = TextMuted,
        unfocusedPlaceholderColor = TextMuted,
        cursorColor = PantoneRed,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
    )
}
