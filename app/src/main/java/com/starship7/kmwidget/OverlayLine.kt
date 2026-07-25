package com.starship7.kmwidget

enum class OverlayLineType    { BATTERY, FUEL }
enum class OverlayLineDisplay { KM, PERCENT }

/** Одна отображаемая строка (внутреннее использование). */
data class OverlayLine(
    val type: OverlayLineType       = OverlayLineType.BATTERY,
    val display: OverlayLineDisplay = OverlayLineDisplay.KM,
    val sizeSp: Int                 = OverlayLine.LINE_SIZE_M
) {
    companion object {
        const val LINE_SIZE_S  = 12
        const val LINE_SIZE_M  = 16
        const val LINE_SIZE_L  = 20
        const val LINE_SIZE_XL = 26

        const val RANGE_SIZE_S  = 20
        const val RANGE_SIZE_M  = 28
        const val RANGE_SIZE_L  = 36
        const val RANGE_SIZE_XL = 46
    }
}

/**
 * Одна запись настроек, добавляемая пользователем через «+».
 * Может включать батарею и/или топливо — у каждого свои км/% и размер.
 */
data class OverlayEntry(
    val batteryEnabled: Boolean         = true,
    val batteryDisplay: OverlayLineDisplay = OverlayLineDisplay.KM,
    val batterySizeSp: Int              = OverlayLine.LINE_SIZE_M,
    val fuelEnabled: Boolean            = true,
    val fuelDisplay: OverlayLineDisplay = OverlayLineDisplay.KM,
    val fuelSizeSp: Int                 = OverlayLine.LINE_SIZE_M
) {
    /** Развернуть в список строк для рендеринга */
    fun toLines(): List<OverlayLine> = buildList {
        if (batteryEnabled) add(OverlayLine(OverlayLineType.BATTERY, batteryDisplay, batterySizeSp))
        if (fuelEnabled)    add(OverlayLine(OverlayLineType.FUEL,    fuelDisplay,    fuelSizeSp))
    }
}
