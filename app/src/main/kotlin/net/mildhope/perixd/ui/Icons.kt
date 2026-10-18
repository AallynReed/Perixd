package net.mildhope.perixd.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object PerixdIcons {
    val Drop = icon("Drop", "M12,2c-5.33,4.55 -8,8.48 -8,11.8c0,4.98 3.8,8.2 8,8.2s8,-3.22 8,-8.2C20,10.48 17.33,6.55 12,2z")
    val Chart = icon("Chart", "M5,9.2h3V19H5V9.2zM10.6,5h2.8v14h-2.8V5zM16.2,13H19v6h-2.8V13z")
    val Pill = icon("Pill", "M4.22,11.29l7.07,-7.07c2.34,-2.34 6.14,-2.34 8.49,0c2.34,2.34 2.34,6.14 0,8.49l-7.07,7.07c-2.34,2.34 -6.14,2.34 -8.49,0C1.88,17.44 1.88,13.64 4.22,11.29z")
    val Minus = icon("Minus", "M19,13H5v-2h14v2z")
}

private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
    .build()
