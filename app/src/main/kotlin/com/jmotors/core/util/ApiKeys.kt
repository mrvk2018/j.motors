package com.jmotors.core.util

import com.jmotors.BuildConfig

/**
 * Runtime secrets from [BuildConfig], filled at compile time from `local.properties`.
 * Never put a real Gemini key in source control.
 */
object ApiKeys {
    val GEMINI_API_KEY: String get() = BuildConfig.GEMINI_API_KEY
}
