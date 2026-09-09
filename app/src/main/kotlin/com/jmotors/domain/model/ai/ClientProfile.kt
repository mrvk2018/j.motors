package com.jmotors.domain.model.ai

/**
 * Snapshot of the qualification funnel, serialized as JSON for the Suwon office.
 */
data class ClientProfile(
    val name: String? = null,
    val model: String? = null,
    val carKey: String? = null,
    val paymentMethod: String? = null,
    val visa: String? = null,
    val downPaymentKrw: Long? = null,
    val office: String = "Suwon",
    val showroom: String = "Incheon",
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
) {
    companion object {
        fun from(profile: UserProfile, nowMs: Long = System.currentTimeMillis()): ClientProfile =
            ClientProfile(
                name = profile.name,
                model = profile.chosenModel,
                carKey = profile.carKey,
                paymentMethod = profile.paymentMethod?.name,
                visa = profile.visaRaw ?: profile.visaType?.wireName(),
                downPaymentKrw = profile.downPaymentKrw,
                capturedAtEpochMs = nowMs,
            )
    }
}

private fun VisaType.wireName(): String = when (this) {
    VisaType.F4 -> "F-4"
    VisaType.F5 -> "F-5"
    VisaType.H2 -> "H-2"
    VisaType.E7 -> "E-7"
    VisaType.E9 -> "E-9"
    VisaType.G1 -> "G-1"
}
