package com.jmotors.domain.model.ai

/**
 * Dialogue state machine for Чоник.
 * The funnel is ordered, but phrasing inside each state stays variable.
 */
sealed class ChonikState {

    /** Step 1 — introduction and name check. */
    data class Greeting(
        val openingLine: String,
    ) : ChonikState()

    /** Step 2 — cars 🚗 or bikes 🏍️. */
    data object CategorySelection : ChonikState()

    /** Step 3 — Korean domestic-market pros/cons and alternatives. */
    data class ModelDiscussion(
        val modelName: String? = null,
    ) : ChonikState()

    /** Step 4 — budget, cash/credit, visa, employment. */
    data object FinancialQualification : ChonikState()

    /** Step 5 — 7% registration tax, insurance, inspection date. */
    data object MarketCalculation : ChonikState()

    /** Step 6 — send the lead to the Suwon office. */
    data object HandoverToOffice : ChonikState()
}
