package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// BLACK & YELLOW PALETTE (60:30:10 Design Ratio)
// ==========================================
// 60% Dominant (Backdrops & Card Surface)
val CalmBackground = Color(0xFF0F172A) // Deep charcoal / dark black backdrop
val CalmSurface = Color(0xFF1E293B)    // Smooth dark slate for card surface

// 30% Structural & Secondary (Typography, Borders, Inactive elements)
val CalmSecondary = Color(0xFF94A3B8)  // Silver slate gray
val CalmTextPrimary = Color(0xFFF8FAFC) // Crisp white for primary text
val CalmTextSecondary = Color(0xFF94A3B8) // Muted slate gray for description text
val CalmBorder = Color(0xFF334155)      // Dark slate border dividers

// 10% Accent (Interactive buttons, call-to-actions, badges, brand indicators)
val CalmPrimary = Color(0xFFFACC15)    // Vivid Golden Yellow (Accent)
val CalmPrimaryDark = Color(0xFFEAB308) // Darker variant for clicks/shadows
val CalmAccentLight = Color(0xFFFEF08A) // Light yellow for container overlays
val CalmAccentText = Color(0xFF854D0E)  // Contrast dark gold text for light yellow tags

// Legacy Premium Elegant Palette mapping
val PremiumMidnight = CalmSecondary
val PremiumGold = CalmPrimary
val PremiumGoldDark = CalmPrimaryDark
val PremiumLightGold = CalmBackground
val PremiumSektorBg = CalmAccentLight
val PremiumSektorText = CalmAccentText

val PremiumBg = CalmBackground
val PremiumSurface = CalmSurface
val PremiumBorder = CalmBorder

val PremiumTextPrimary = CalmTextPrimary
val PremiumTextSecondary = CalmTextSecondary

val PremiumErrorBg = Color(0xFFFFDAD6)
val PremiumOnError = Color(0xFF93000A)
val PremiumOnErrorContainerText = Color(0xFF410002)

// Legacy compatibility mapping
val MiningYellow = CalmPrimary
val MiningYellowDark = CalmPrimaryDark
val MiningDarkSlate = CalmTextPrimary
val MiningSlate = CalmTextSecondary
val MiningGrey = CalmTextSecondary
val MiningLightBg = CalmBackground
val MiningSurface = CalmSurface

// Professional mappings
val ProfessionalTeal = CalmPrimary
val ProfessionalTealDark = CalmPrimaryDark
val ProfessionalLightTeal = Color(0xFFFDE047) // Light yellow for dark mode
val ProfessionalSektorBg = CalmAccentLight
val ProfessionalSektorText = CalmAccentText
val ProfessionalBg = CalmBackground
val ProfessionalSurface = CalmSurface
val ProfessionalBorder = CalmBorder
val ProfessionalTextPrimary = CalmTextPrimary
val ProfessionalTextSecondary = CalmTextSecondary
val ProfessionalErrorBg = PremiumErrorBg
val ProfessionalOnError = PremiumOnError
val ProfessionalOnErrorContainerText = PremiumOnErrorContainerText
