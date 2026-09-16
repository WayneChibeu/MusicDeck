package com.wayne.musicdeck.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.wayne.musicdeck.data.EQPreset
import org.json.JSONArray
import java.io.File

object EQPresetManager {

    private const val PREFS_NAME = "custom_eq_presets"
    private const val KEY_SAVED_PRESETS = "saved_presets_json"

    fun getSavedCustomPresets(context: Context): List<EQPreset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SAVED_PRESETS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<EQPreset>()
            for (i in 0 until arr.length()) {
                val preset = EQPreset.fromJson(arr.getString(i))
                if (preset != null) list.add(preset)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomPreset(context: Context, preset: EQPreset) {
        val current = getSavedCustomPresets(context).toMutableList()
        current.removeAll { it.name.equals(preset.name, ignoreCase = true) }
        current.add(0, preset)

        val arr = JSONArray()
        current.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_PRESETS, arr.toString())
            .apply()
    }

    fun deleteCustomPreset(context: Context, presetName: String) {
        val current = getSavedCustomPresets(context).toMutableList()
        current.removeAll { it.name.equals(presetName, ignoreCase = true) }

        val arr = JSONArray()
        current.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_PRESETS, arr.toString())
            .apply()
    }

    fun exportPresetToFile(context: Context, preset: EQPreset): File? {
        return try {
            val presetsDir = File(context.cacheDir, "presets")
            if (!presetsDir.exists()) presetsDir.mkdirs()

            val sanitized = preset.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(presetsDir, "$sanitized.deck")
            file.writeText(preset.toJson())
            file
        } catch (e: Exception) {
            null
        }
    }

    fun sharePreset(context: Context, preset: EQPreset) {
        val file = exportPresetToFile(context, preset)
        if (file == null) {
            Toast.makeText(context, "Failed to create .deck file", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "MusicDeck EQ Preset: ${preset.name}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Check out my custom sound curve '${preset.name}' for MusicDeck (.deck file)!"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share EQ Preset (${preset.name})")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share preset: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun importPresetFromUri(context: Context, uri: Uri): EQPreset? {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return null

            val preset = EQPreset.fromJson(content)
            if (preset != null) {
                saveCustomPreset(context, preset)
            }
            preset
        } catch (e: Exception) {
            null
        }
    }
}
