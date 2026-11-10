package com.cecapi.app.core.voice

/**
 * Ready-made ways of talking for different ages, so nobody has to tune speed, pitch and "tú" or "usted" by hand:
 * a child gets a friendlier, slightly slower voice; an older person a clearly slower one addressed as "usted".
 * It only changes how the assistant sounds and speaks; it never asks for or stores an age.
 */
enum class VoiceProfile(val rate: Float, val pitch: Float, val address: AddressStyle, val announcement: String) {
    CHILD(
        0.9f, 1.15f, AddressStyle.TU,
        "Perfil de niño activado. Hablaré un poco más despacio, con una voz más amable, y te hablaré de tú.",
    ),
    NORMAL(
        1.0f, 1.0f, AddressStyle.TU,
        "Perfil normal activado. Hablaré a velocidad normal y te hablaré de tú.",
    ),
    SENIOR(
        0.8f, 1.0f, AddressStyle.USTED,
        "Perfil de persona mayor activado. Hablaré más despacio y le hablaré de usted.",
    ),
}
