package id.flufclip.player

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The APK contains a bundled bold face; its regular face is a downloadable font.
internal val YouTubeSansBold = FontFamily(Font(R.font.youtube_sans_bold, FontWeight.Bold))

internal val FlufPlayTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontFamily = YouTubeSansBold,
            fontWeight = FontWeight.Bold
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp
        ),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp
        ),
        titleSmall = base.titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.5.sp,
            lineHeight = 19.sp
        ),
        bodySmall = base.bodySmall.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    )
}
