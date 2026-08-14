package au.com.firstclassexpress.driver.ui.theme

import androidx.compose.ui.graphics.Color

// 1st Class Express Brand Colors
val BrandBlack = Color(0xFF111111)
val BrandDarkGray = Color(0xFF1C1B1F)
val BrandGold = Color(0xFFD4AF37)
val BrandLightGold = Color(0xFFF3E5AB)
val BrandRed = Color(0xFFD32F2F)
val BrandLightRed = Color(0xFFEF5350)
val BrandWhite = Color(0xFFF8F8F8)
val BrandGray = Color(0xFF757575)

val PrimaryDark = BrandGold
val OnPrimaryDark = BrandBlack
val PrimaryContainerDark = BrandGold.copy(alpha = 0.2f)
val OnPrimaryContainerDark = BrandLightGold

val SecondaryDark = BrandRed
val OnSecondaryDark = BrandWhite
val SecondaryContainerDark = BrandRed.copy(alpha = 0.2f)
val OnSecondaryContainerDark = BrandLightRed

val BackgroundDark = BrandBlack
val SurfaceDark = BrandDarkGray
val OnBackgroundDark = BrandWhite
val OnSurfaceDark = BrandWhite

val ErrorDark = BrandRed
val OnErrorDark = BrandWhite

// Light theme equivalents (though we prefer dark for this app, we'll provide both)
val PrimaryLight = BrandBlack
val OnPrimaryLight = BrandGold
val PrimaryContainerLight = BrandGold
val OnPrimaryContainerLight = BrandBlack

val SecondaryLight = BrandRed
val OnSecondaryLight = BrandWhite
val SecondaryContainerLight = BrandRed.copy(alpha = 0.1f)
val OnSecondaryContainerLight = BrandRed

val BackgroundLight = BrandWhite
val SurfaceLight = Color.White
val OnBackgroundLight = BrandBlack
val OnSurfaceLight = BrandBlack

val ErrorLight = BrandRed
val OnErrorLight = BrandWhite
