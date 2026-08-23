package org.openmomentum.app.model

enum class NoiseMode(val displayName: String) {
    ANC("ANC"),
    BALANCED("Balanced"),
    TRANSPARENCY("Transparency"),
    ADAPTIVE("Adaptive ANC"),
    OFF("Noise control off"),
    UNKNOWN("Unknown");

    companion object {
        fun resolve(ancEnabled: Boolean, adaptiveEnabled: Boolean, level: Int): NoiseMode = when {
            !ancEnabled -> OFF
            adaptiveEnabled -> ADAPTIVE
            level <= 33 -> ANC
            level >= 67 -> TRANSPARENCY
            else -> BALANCED
        }
    }
}

data class HeadphoneState(
    val reachable: Boolean = false,
    val batteryPercent: Int? = null,
    val noiseMode: NoiseMode = NoiseMode.UNKNOWN,
    val transparencyLevel: Int? = null,
    val updatedAtMillis: Long = 0L,
    val error: String? = null,
)
