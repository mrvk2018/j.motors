package com.jmotors.domain.model.ai

/**
 * Lead profile collected by Чоник during the qualification funnel.
 * Fields stay null/UNKNOWN until the matching dialogue step fills them in.
 */
data class UserProfile(
    val name: String? = null,
    val vehicleCategory: VehicleCategory = VehicleCategory.UNKNOWN,
    val chosenModel: String? = null,
    /** Budget in KRW, as stated by the user. */
    val budget: Long? = null,
    val paymentMethod: PaymentMethod? = null,
    val visaType: VisaType? = null,
    val isOfficiallyEmployed: Boolean? = null,
)

enum class VehicleCategory {
    CARS,
    BIKES,
    UNKNOWN,
}

enum class PaymentMethod {
    CASH,
    CREDIT,
}

/** Visa types relevant to Russian-speaking expats in Korea (J Motors lead form). */
enum class VisaType {
    F4,
    H2,
    E9,
    G1,
}
