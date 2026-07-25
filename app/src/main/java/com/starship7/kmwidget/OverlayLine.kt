package com.starship7.kmwidget

enum class OverlayLineType    { BATTERY, FUEL }
enum class OverlayLineDisplay { KM, LITERS, PERCENT }

/** Одна отображаемая строка (внутренняя). */
data class OverlayLine(
    val type: OverlayLineType       = OverlayLineType.BATTERY,
    val display: OverlayLineDisplay = OverlayLineDisplay.PERCENT,
    val sizeSp: Int                 = SIZE_M
) {
    companion object {
        // Единая шкала размеров для всех строк оверлея
        const val SIZE_S  = 14
        const val SIZE_M  = 20
        const val SIZE_L  = 28
        const val SIZE_XL = 38

        // Объём бака Geely Galaxy Starship 7 (для перевода % → литры)
        const val FUEL_TANK_LITERS = 55f
    }
}

/**
 * Одна запись настроек (кнопка «+»).
 * Содержит батарею и/или топливо — каждое со своим стилем и размером.
 * Если [combineLine] = true и оба включены — выводятся на ОДНОЙ строке.
 */
data class OverlayEntry(
    val batteryEnabled: Boolean            = true,
    val batteryDisplay: OverlayLineDisplay = OverlayLineDisplay.PERCENT,
    val batterySizeSp: Int                 = OverlayLine.SIZE_M,

    val fuelEnabled: Boolean               = true,
    val fuelDisplay: OverlayLineDisplay    = OverlayLineDisplay.KM,
    val fuelSizeSp: Int                    = OverlayLine.SIZE_M,

    val combineLine: Boolean               = true   // оба на одной строке
) {
    /** Развернуть в список строк (для раздельного рендеринга). */
    fun toLines(): List<OverlayLine> = buildList {
        if (batteryEnabled) add(OverlayLine(OverlayLineType.BATTERY, batteryDisplay, batterySizeSp))
        if (fuelEnabled)    add(OverlayLine(OverlayLineType.FUEL,    fuelDisplay,    fuelSizeSp))
    }
}
