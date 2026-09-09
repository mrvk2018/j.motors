package com.jmotors.domain.model.ai

/**
 * Pulls name / visa / payment / down payment / showcase model out of free-form Russian STT
 * so the 4-stage funnel stays in sync with what the user actually said.
 */
object LeadFactExtractor {

    private val NAME_STOPWORDS = setOf(
        "хочу", "ищу", "просто", "смотрю", "тут", "здесь", "привет", "пока",
        "машина", "байк", "кредит", "наличка", "да", "нет", "ладно",
        "avante", "sonata", "аванте", "соната",
    )

    fun absorb(
        utterance: String,
        profile: UserProfile,
        state: ChonikState,
    ): Pair<UserProfile, ChonikState> {
        val text = utterance.trim()
        if (text.isEmpty()) return profile to state

        var next = profile
        next = next.copy(name = next.name ?: extractName(text))

        val showcase = ShowcaseCar.fromUtterance(text)
        if (showcase != null && next.carKey == null) {
            next = next.copy(
                chosenModel = showcase.displayName,
                carKey = showcase.key,
                vehicleCategory = VehicleCategory.CARS,
            )
        }

        next = next.copy(
            visaType = next.visaType ?: extractVisa(text),
            visaRaw = next.visaRaw ?: extractVisaRaw(text),
            paymentMethod = next.paymentMethod ?: extractPayment(text),
            budget = next.budget ?: extractBudgetKrw(text),
            downPaymentKrw = next.downPaymentKrw ?: extractDownPaymentKrw(text),
            vehicleCategory = if (next.vehicleCategory == VehicleCategory.UNKNOWN) {
                extractCategory(text)
            } else {
                next.vehicleCategory
            },
            chosenModel = next.chosenModel ?: extractModelHint(text),
            isOfficiallyEmployed = next.isOfficiallyEmployed ?: extractEmployment(text),
        )
        return next to advance(state, next)
    }

    private fun advance(state: ChonikState, profile: UserProfile): ChonikState = when (state) {
        is ChonikState.Greeting ->
            if (!profile.name.isNullOrBlank()) {
                ChonikState.ModelDiscussion(profile.chosenModel)
            } else {
                state
            }
        ChonikState.CategorySelection ->
            if (!profile.carKey.isNullOrBlank() || !profile.chosenModel.isNullOrBlank()) {
                ChonikState.ModelDiscussion(profile.chosenModel)
            } else {
                ChonikState.ModelDiscussion(profile.chosenModel)
            }
        is ChonikState.ModelDiscussion -> {
            val withModel = if (!profile.chosenModel.isNullOrBlank() || !profile.carKey.isNullOrBlank()) {
                ChonikState.ModelDiscussion(profile.chosenModel)
            } else {
                state
            }
            if (!profile.carKey.isNullOrBlank() && profile.paymentMethod != null) {
                if (isFinanciallyReady(profile)) {
                    ChonikState.HandoverToOffice
                } else {
                    ChonikState.FinancialQualification
                }
            } else if (!profile.carKey.isNullOrBlank()) {
                ChonikState.FinancialQualification
            } else {
                withModel
            }
        }
        ChonikState.FinancialQualification ->
            if (isFinanciallyReady(profile)) ChonikState.HandoverToOffice else state
        ChonikState.MarketCalculation ->
            if (isFinanciallyReady(profile)) ChonikState.HandoverToOffice else state
        ChonikState.HandoverToOffice -> state
    }

    /** Cash is enough. Credit needs a visa and a down payment. */
    private fun isFinanciallyReady(profile: UserProfile): Boolean = when (profile.paymentMethod) {
        PaymentMethod.CASH -> true
        PaymentMethod.CREDIT ->
            (!profile.visaRaw.isNullOrBlank() || profile.visaType != null) &&
                profile.downPaymentKrw != null
        null -> false
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
        val compact = text.uppercase().replace(" ", "").replace("-", "")
        return when {
            compact.contains("F4") -> VisaType.F4
            compact.contains("F5") -> VisaType.F5
            compact.contains("H2") -> VisaType.H2
            compact.contains("E7") -> VisaType.E7
            compact.contains("E9") -> VisaType.E9
            compact.contains("G1") -> VisaType.G1
            else -> null
        }
    }

    private fun extractVisaRaw(text: String): String? {
        val match = Regex(
            """\b([FHEGfheg]\s*-?\s*[0-9])\b""",
        ).find(text) ?: return null
        val letter = match.groupValues[1].first { it.isLetter() }.uppercaseChar()
        val digit = match.groupValues[1].first { it.isDigit() }
        return "$letter-$digit"
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
            listOf("машин", "авто", "седан", "кроссовер", "джип", "avante", "sonata", "santa")
                .any { it in lower } -> VehicleCategory.CARS
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
        ShowcaseCar.fromUtterance(text)?.let { return it.displayName }
        val match = Regex(
            """(?:хочу|рассмотрим|модель|машин[ау])\s+([A-Za-zА-Яа-яЁё0-9\-]{2,24})""",
            RegexOption.IGNORE_CASE,
        ).find(text) ?: return null
        val token = match.groupValues[1]
        return token.takeIf { it.lowercase() !in NAME_STOPWORDS && it.length >= 2 }
    }

    private fun extractDownPaymentKrw(text: String): Long? {
        val lower = text.lowercase()
        val talksAboutDeposit = listOf("взнос", "первоначальн", "депозит", "다운").any { it in lower }
        if (!talksAboutDeposit) return null
        return extractBudgetKrw(text)
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
