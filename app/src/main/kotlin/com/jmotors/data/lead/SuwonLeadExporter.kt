package com.jmotors.data.lead

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.jmotors.domain.model.ai.ClientProfile
import java.io.File

/**
 * Writes a Suwon-office lead JSON under app filesDir. No network yet.
 */
object SuwonLeadExporter {

    private const val TAG = "JMotors"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun export(context: Context, profile: ClientProfile): File {
        val dir = File(context.filesDir, "leads").apply { mkdirs() }
        val file = File(dir, "suwon_${profile.capturedAtEpochMs}.json")
        val json = gson.toJson(profile)
        file.writeText(json)
        Log.i(TAG, "Suwon lead JSON → ${file.absolutePath}\n$json")
        return file
    }
}
