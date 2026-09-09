package com.jmotors.domain.model.ai

/**
 * Three MVP inventory cars shown in the XREAL showroom.
 * [key] matches `assets/models/cars/{key}.glb` and `[SET_CAR:{key}]`.
 */
enum class ShowcaseCar(
    val key: String,
    val displayName: String,
) {
    AVANTE("avante", "Avante"),
    SONATA("sonata", "Sonata"),
    SANTA_FE("santa_fe", "Santa Fe"),
    ;

    companion object {
        fun fromTag(raw: String?): ShowcaseCar? {
            val slug = raw.orEmpty().trim().lowercase().replace('-', '_').replace(' ', '_')
            return entries.find { it.key == slug }
                ?: when (slug) {
                    "elantra", "avante_n" -> AVANTE
                    "santafe" -> SANTA_FE
                    else -> null
                }
        }

        fun fromUtterance(text: String): ShowcaseCar? {
            val lower = text.lowercase()
            return when {
                listOf("avante", "аванте", "элантра", "elantra").any { it in lower } -> AVANTE
                listOf("sonata", "соната").any { it in lower } -> SONATA
                listOf("santa fe", "santa_fe", "santa-fe", "santafe", "санта фе", "сантафе", "санта-фе")
                    .any { it in lower } -> SANTA_FE
                else -> null
            }
        }
    }
}
