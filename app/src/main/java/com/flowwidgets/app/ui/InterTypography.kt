package com.flowwidgets.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.flowwidgets.app.R

private val inter =
    FontFamily(
        Font(R.font.inter_light, FontWeight.Light),
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

internal fun interTypography(): Typography =
    Typography().let { base ->
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = inter),
            displayMedium = base.displayMedium.copy(fontFamily = inter),
            displaySmall = base.displaySmall.copy(fontFamily = inter),
            headlineLarge = base.headlineLarge.copy(fontFamily = inter),
            headlineMedium = base.headlineMedium.copy(fontFamily = inter),
            headlineSmall = base.headlineSmall.copy(fontFamily = inter),
            titleLarge = base.titleLarge.copy(fontFamily = inter),
            titleMedium = base.titleMedium.copy(fontFamily = inter),
            titleSmall = base.titleSmall.copy(fontFamily = inter),
            bodyLarge = base.bodyLarge.copy(fontFamily = inter),
            bodyMedium = base.bodyMedium.copy(fontFamily = inter),
            bodySmall = base.bodySmall.copy(fontFamily = inter),
            labelLarge = base.labelLarge.copy(fontFamily = inter),
            labelMedium = base.labelMedium.copy(fontFamily = inter),
            labelSmall = base.labelSmall.copy(fontFamily = inter),
        )
    }
