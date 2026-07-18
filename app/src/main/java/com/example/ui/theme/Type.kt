package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AppFont = FontFamily.SansSerif

val Typography = Typography(
    displaySmall = TextStyle(AppFont, FontWeight.Bold, 36.sp, 42.sp, (-0.7).sp),
    headlineLarge = TextStyle(AppFont, FontWeight.Bold, 32.sp, 38.sp, (-0.6).sp),
    headlineMedium = TextStyle(AppFont, FontWeight.Bold, 28.sp, 34.sp, (-0.4).sp),
    headlineSmall = TextStyle(AppFont, FontWeight.SemiBold, 24.sp, 30.sp, (-0.2).sp),
    titleLarge = TextStyle(AppFont, FontWeight.SemiBold, 22.sp, 28.sp, (-0.1).sp),
    titleMedium = TextStyle(AppFont, FontWeight.SemiBold, 16.sp, 22.sp, 0.sp),
    titleSmall = TextStyle(AppFont, FontWeight.SemiBold, 14.sp, 20.sp, 0.1.sp),
    bodyLarge = TextStyle(AppFont, FontWeight.Normal, 16.sp, 24.sp, 0.1.sp),
    bodyMedium = TextStyle(AppFont, FontWeight.Normal, 14.sp, 21.sp, 0.1.sp),
    bodySmall = TextStyle(AppFont, FontWeight.Normal, 12.sp, 18.sp, 0.2.sp),
    labelLarge = TextStyle(AppFont, FontWeight.SemiBold, 14.sp, 20.sp, 0.1.sp),
    labelMedium = TextStyle(AppFont, FontWeight.SemiBold, 12.sp, 16.sp, 0.3.sp),
    labelSmall = TextStyle(AppFont, FontWeight.Medium, 11.sp, 16.sp, 0.4.sp)
)
