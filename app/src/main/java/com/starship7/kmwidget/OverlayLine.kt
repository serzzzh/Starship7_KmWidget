package com.starship7.kmwidget

enum class OverlayLineType    { BATTERY, FUEL }
enum class OverlayLineDisplay { KM, PERCENT }

data class OverlayLine(
    val type: OverlayLineType       = OverlayLineType.BATTERY,
    val display: OverlayLineDisplay = OverlayLineDisplay.KM,
    val sizeSp: Int                 = LINE_SIZE_M
) {
    companion object {
        // Размеры дополнительных строк
        const val LINE_SIZE_S  = 12
        const val LINE_SIZE_M  = 16
        const val LINE_SIZE_L  = 20
        const val LINE_SIZE_XL = 26

        // Размеры строки "Запас хода"
        const val RANGE_SIZE_S  = 20
        const val RANGE_SIZE_M  = 28
        const val RANGE_SIZE_L  = 36
        const val RANGE_SIZE_XL = 46
    }
}
