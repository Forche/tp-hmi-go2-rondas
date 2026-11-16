package ar.edu.uade.go2rondas.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Estilos de texto del Figma. Roboto es la fuente del sistema en Android (FontFamily.Default).
//   Titulo/Pantalla -> titleLarge     Titulo/Seccion -> titleMedium
//   Cuerpo/Base     -> bodyMedium     Cuerpo/Fuerte  -> labelLarge
//   Etiqueta        -> labelMedium    Caption        -> bodySmall
//   Valor/Grande    -> headlineSmall

private fun style(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp
)

val Typography = Typography(
    titleLarge = style(22, 28, FontWeight.Normal),
    titleMedium = style(16, 24, FontWeight.Medium),
    bodyMedium = style(14, 20, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    bodySmall = style(12, 16, FontWeight.Normal),
    headlineSmall = style(22, 28, FontWeight.Medium)
)
