package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AppFont = FontFamily.SansSerif

private fun premiumTextStyle(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Float
) = TextStyle(
    fontFamily = AppFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

val Typography = Typography(
    displaySmall = premiumTextStyle(FontWeight.Bold, 36, 42, -0.7f),
    headlineLarge = premiumTextStyle(FontWeight.Bold, 32, 38, -0.6f),
    headlineMedium = premiumTextStyle(FontWeight.Bold, 28, 34, -0.4f),
    headlineSmall = premiumTextStyle(FontWeight.SemiBold, 24, 30, -0.2f),
    titleLarge = premiumTextStyle(FontWeight.SemiBold, 22, 28, -0.1f),
    titleMedium = premiumTextStyle(FontWeight.SemiBold, 16, 22, 0f),
    titleSmall = premiumTextStyle(FontWeight.SemiBold, 14, 20, 0.1f),
    bodyLarge = premiumTextStyle(FontWeight.Normal, 16, 24, 0.1f),
    bodyMedium = premiumTextStyle(FontWeight.Normal, 14, 21, 0.1f),
    bodySmall = premiumTextStyle(FontWeight.Normal, 12, 18, 0.2f),
    labelLarge = premiumTextStyle(FontWeight.SemiBold, 14, 20, 0.1f),
    labelMedium = premiumTextStyle(FontWeight.SemiBold, 12, 16, 0.3f),
    labelSmall = premiumTextStyle(FontWeight.Medium, 11, 16, 0.4f)
)
