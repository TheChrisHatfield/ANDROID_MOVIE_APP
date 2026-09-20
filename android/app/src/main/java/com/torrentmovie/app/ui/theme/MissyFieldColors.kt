package com.torrentmovie.app.ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/** Cream fields with dark ink text — readable on the Pantone red app background. */
@Composable
fun missyOutlinedTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Cream,
        unfocusedContainerColor = Cream,
        disabledContainerColor = Cream,
        focusedTextColor = InkOnRed,
        unfocusedTextColor = InkOnRed,
        focusedLabelColor = InkOnRed,
        unfocusedLabelColor = InkOnRed,
        cursorColor = InkOnRed,
        focusedBorderColor = InkOnRed,
        unfocusedBorderColor = InkOnRed,
    )
}
