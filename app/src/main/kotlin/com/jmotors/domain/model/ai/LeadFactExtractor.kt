package com.jmotors.domain.model.ai

/**
 * Pulls name / visa / budget / credit / category out of free-form Russian STT
 * so the funnel state machine stays in sync with what the user actually said.
 */
object LeadFactExtractor {

    private val NAME_STOPWORDS = setOf(
        "хочу", "ищу", "просто", "смотрю", "тут", "здесь", "привет", "пока",
        "машина", "байк", "кредит", "наличка", "да", "нет", "ладно",
    )

    fun absorb(
        utterance: String,
        profile: UserProfile,
        state: ChonikState,
    ): Pair<UserProfile, ChonikState> {
        val text = utterance.trim()
        if (text.isEmpty()) return profile to state

        var nextProfile = profile
        nextProfile = nextProfile.copy(name = nextProfile.name ?: extractName(text))
        nextProfile = nextProfile.copy(
            visaType = nextProfile.visaType ?: extractVisa(text),
            paymentMethod = nextProfile.paymentMethod ?: extractPayment(text),
            budget = nextProfile.budget ?: extractBudgetKrw(text),
            vehicleCategory = if (nextProfile.vehicleCategory == VehicleCategory.UNKNOWN) {
                extractCategory(text)
            } else {
                nextProfile.vehicleCategory
            },
            chosenModel = nextProfile.chosenModel ?: extractModelHint(text),
            isOfficiallyEmployed = nextProfile.isOfficiallyEmployed ?: extractEmployment(text),
        )
        val nextState = advance(state, nextProfile)
        return nextProfile to nextState
    }

    private fun advance(state: ChonikState, profile: UserProfile): ChonikState = when (state) {
        is ChonikState.Greeting ->
            if (!profile.name.isNullOrBlank()) ChonikState.CategorySelection else state
        ChonikState.CategorySelection ->
            if (profile.vehicleCategory != VehicleCategory.UNKNOWN) {
                ChonikState.ModelDiscussion(profile.chosenModel)
            } else {
                state
            }
        is ChonikState.ModelDiscussion -> {
            val withModel = if (!profile.chosenModel.isNullOrBlank()) {
                ChonikState.ModelDiscussion(profile.chosenModel)
            } else {
                state
            }
            val readyForMoney = profile.budget != null || profile.visaType != null ||
                profile.paymentMethod != null
            if (readyForMoney) ChonikState.FinancialQualification else withModel
        }
        ChonikState.FinancialQualification -> {
            val qualified = profile.budget != null &&
                profile.visaType != null &&
                profile.paymentMethod != null
            if (qualified) ChonikState.MarketCalculation else state
        }
        ChonikState.MarketCalculation,
        ChonikState.HandoverToOffice,
        -> state
    }

    private fun extractName(text: String): String? {
        val named = Regex(
            """(?:меня зовут|моё имя|мое имя|зови меня|\bя)\s+([А-ЯЁа-яёA-Za-z]{2,20})""",
            RegexOption.IGNORE_CASE,
        ).find(text)?.groupValues?.getOrNull(1)
        val candidate = named?.replaceFirstChar { it.uppercase() }
        return candidate?.takeIf { it.lowercase() !in NAME_STOPWORDS }
    }

    private fun extractVisa(text: String): VisaType? {
        val compact = text.uppercase().replace(" ", "")
        return when {
            compact.contains("F-4") || compact.contains("F4") -> VisaType.F4
            compact.contains("H-2") || compact.contains("H2") -> VisaType.H2
            compact.contains("E-9") || compact.contains("E9") -> VisaType.E9
            compact.contains("G-1") || compact.contains("G1") -> VisaType.G1
            else -> null
        }
    }

    private fun extractPayment(text: String): PaymentMethod? {
        val lower = text.lowercase()
        return when {
            lower.contains("кредит") || lower.contains("рассроч") || lower.contains("в долг") ->
                PaymentMethod.CREDIT
            lower.contains("налич") || lower.contains("кэш") || lower.contains("cash") ->
                PaymentMethod.CASH
            else -> null
        }
    }

    private fun extractCategory(text: String): VehicleCategory {
        val lower = text.lowercase()
        return when {
            listOf("байк", "мото", "скутер", "мотоцикл").any { it in lower } -> VehicleCategory.BIKES
            listOf("машин", "авто", "седан", "кроссовер", "джип").any { it in lower } ->
                VehicleCategory.CARS
            else -> VehicleCategory.UNKNOWN
        }
    }

    private fun extractEmployment(text: String): Boolean? {
        val lower = text.lowercase()
        return when {
            lower.contains("официальн") && (lower.contains("работ") || lower.contains("трудов")) -> true
            lower.contains("без трудовой") || lower.contains("неофициал") -> false
            else -> null
        }
    }

    private fun extractModelHint(text: String): String? {
        val match = Regex(
            """(?:хочу|рассмотрим|модель|машин[ау]|байк)\s+([A-Za-zА-Яа-яЁё0-9\-]{2,24})""",
            RegexOption.IGNORE_CASE,
        ).find(text) ?: return null
        val token = match.groupValues[1]
        return token.takeIf { it.lowercase() !in NAME_STOPWORDS && it.length >= 2 }
    }

    private fun extractBudgetKrw(text: String): Long? {
        val million = Regex(
            """(\d+(?:[.,]\d+)?)\s*(?:млн|миллион)""",
            RegexOption.IGNORE_CASE,
        ).find(text)
        if (million != null) {
            val n = million.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            return (n * 1_000_000).toLong()
        }
        val man = Regex("""(\d+)\s*(?:만|ман)""").find(text)
        if (man != null) {
            val n = man.groupValues[1].toLongOrNull() ?: return null
            return n * 10_000
        }
        val won = Regex(
            """(\d[\d\s]{2,})\s*(?:원|крв|krw|вон)""",
            RegexOption.IGNORE_CASE,
        ).find(text)
        if (won != null) {
            return won.groupValues[1].replace(" ", "").toLongOrNull()
        }
        return null
    }
}
