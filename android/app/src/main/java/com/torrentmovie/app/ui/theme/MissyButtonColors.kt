package com.torrentmovie.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
/** Cream-filled controls for contrast on Pantone red chrome and white content (FR-039a). */
@Composable
fun missyFilledButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = Cream,
        contentColor = TextCharcoal,
        disabledContainerColor = Cream.copy(alpha = 0.45f),
        disabledContentColor = TextMuted,
    )
}

@Composable
fun seedboxSendButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = SeedboxActionBlue,
        contentColor = OnSeedboxAction,
        disabledContainerColor = SeedboxActionBlue.copy(alpha = 0.38f),
        disabledContentColor = OnSeedboxAction.copy(alpha = 0.70f),
    )
}

@Composable
fun missyFilterChipColors(): SelectableChipColors {
    return FilterChipDefaults.filterChipColors(
        containerColor = Cream,
        labelColor = TextCharcoal,
        iconColor = TextCharcoal,
        disabledContainerColor = Cream.copy(alpha = 0.45f),
        disabledLabelColor = TextMuted,
        disabledLeadingIconColor = TextMuted,
        selectedContainerColor = Cream,
        selectedLabelColor = TextCharcoal,
        selectedLeadingIconColor = TextCharcoal,
    )
}

@Composable
fun missyFilterChipBorder(selected: Boolean, enabled: Boolean = true): BorderStroke {
    return FilterChipDefaults.filterChipBorder(
        enabled = enabled,
        selected = selected,
        borderColor = DividerGray,
        selectedBorderColor = PantoneRed,
        disabledBorderColor = DividerGray.copy(alpha = 0.4f),
    )
}

@Composable
fun missyAssistChipColors(): ChipColors {
    return AssistChipDefaults.assistChipColors(
        containerColor = Cream,
        labelColor = TextCharcoal,
        disabledContainerColor = Cream.copy(alpha = 0.45f),
        disabledLabelColor = TextMuted,
    )
}
