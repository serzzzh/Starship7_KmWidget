package com.starship7.kmwidget

enum class OverlayLineType    { BATTERY, FUEL, TEXT, SUM }
enum class OverlayLineDisplay { KM, LITERS, PERCENT, NONE }

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
        const val FUEL_TANK_LITERS = 50f
    }
}

/**
 * Одна запись настроек (кнопка «+»).
 * Содержит текст, сумму, батарею, топливо — каждое со своим стилем и размером.
 */
data class OverlayEntry(
    val textEnabled: Boolean               = false,
    val textValue: String                  = "",
    val textSizeSp: Int                    = OverlayLine.SIZE_M,

    val sumEnabled: Boolean                = false,
    val sumSizeSp: Int                     = OverlayLine.SIZE_M,

    val batteryEnabled: Boolean            = false,
    val batteryDisplay: OverlayLineDisplay = OverlayLineDisplay.PERCENT,
    val batterySizeSp: Int                 = OverlayLine.SIZE_M,

    val fuelEnabled: Boolean               = false,
    val fuelDisplay: OverlayLineDisplay    = OverlayLineDisplay.KM,
    val fuelSizeSp: Int                    = OverlayLine.SIZE_M
)
