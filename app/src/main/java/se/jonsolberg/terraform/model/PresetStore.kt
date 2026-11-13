package se.jonsolberg.terraform.model

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists the 128-slot preset library as JSON in app storage. */
class PresetStore(private val file: File) {

    fun load(): List<Preset> {
        if (!file.exists()) return defaults()
        return try {
            readPresets(JSONObject(file.readText()).getJSONArray("presets"))
        } catch (e: Exception) {
            defaults()
        }
    }

    fun save(presets: List<Preset>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONObject().put("version", 1).put("presets", presetArray(presets)).toString())
        tmp.renameTo(file)
    }

    companion object {
        const val DEVICE = "terraform"
        const val APP = "Terraform Control"

        private fun presetArray(presets: List<Preset>): JSONArray {
            val arr = JSONArray()
            presets.filter { it.sound != null || it.name.isNotEmpty() }.forEach { p ->
                val o = JSONObject().put("number", p.number).put("name", p.name).put("sync", p.sync.name)
                p.sound?.let { o.put("sound", it.toJson()) }
                arr.put(o)
            }
            return arr
        }

        private fun readPresets(arr: JSONArray): List<Preset> {
            val list = MutableList(Terraform.PRESET_COUNT) { Preset(it) }
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val n = o.optInt("number", -1)
                if (n !in 0 until Terraform.PRESET_COUNT) continue
                val sound = o.optJSONObject("sound")?.let { TerraSound.fromJson(it) }
                val sync = runCatching { SyncState.valueOf(o.optString("sync")) }.getOrNull() ?: SyncState.EDITED
                list[n] = Preset(n, o.optString("name"), sound, if (sound == null) SyncState.EMPTY else if (sync == SyncState.EMPTY) SyncState.EDITED else sync)
            }
            return list
        }

        /** Full backup: every preset (with slot number and sync state) plus the app settings. */
        fun backupJson(presets: List<Preset>, settings: JSONObject): String = JSONObject()
            .put("app", APP)
            .put("type", "backup")
            .put("version", 1)
            .put("device", DEVICE)
            .put("settings", settings)
            .put("presets", presetArray(presets))
            .toString(2)

        fun parseBackup(text: String): Pair<List<Preset>, JSONObject?> {
            val root = JSONObject(text)
            require(root.optString("device") == DEVICE) { "Not a Terraform Control file." }
            require(root.optString("type") == "backup") { "This is a preset export, not a backup – use Import on the Presets tab." }
            return readPresets(root.optJSONArray("presets") ?: JSONArray()) to root.optJSONObject("settings")
        }

        /** Selected presets, without slot numbers, for sharing. */
        fun exportJson(presets: List<Preset>): String {
            val arr = JSONArray()
            presets.filter { it.sound != null }.forEach { p ->
                arr.put(JSONObject().put("name", p.name).put("sound", p.sound!!.toJson()))
            }
            return JSONObject()
                .put("app", APP)
                .put("type", "presets")
                .put("version", 1)
                .put("device", DEVICE)
                .put("presets", arr)
                .toString(2)
        }

        fun importJson(text: String): List<Pair<String, TerraSound>> {
            val root = JSONObject(text)
            require(root.optString("device") == DEVICE) { "Not a Terraform Control file." }
            val arr = root.optJSONArray("presets") ?: throw IllegalArgumentException("No presets found in file.")
            val out = mutableListOf<Pair<String, TerraSound>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val s = o.optJSONObject("sound") ?: continue
                out += o.optString("name") to TerraSound.fromJson(s)
            }
            return out
        }

        fun defaults(): List<Preset> {
            val list = MutableList(Terraform.PRESET_COUNT) { Preset(it) }
            // program, rate, depth, variable, blend, volume
            val seeds = listOf(
                "Lush Chorus" to TerraSound(9, 40, 70, 60, 63, 63),
                "Leslie Slow" to TerraSound(5, 70, 60, 64, 80, 63, rotarySlow = true),
                "Surf Trem" to TerraSound(7, 75, 90, 40, 0, 66),
                "Machine Vibe" to TerraSound(4, 45, 85, 64, 63, 63),
                "Funk Envelope" to TerraSound(1, 50, 75, 90, 110, 63),
                "Jet Flange" to TerraSound(2, 20, 110, 85, 50, 63),
            )
            seeds.forEachIndexed { i, (name, s) -> list[i] = Preset(i, name, s, SyncState.EDITED) }
            return list
        }
    }
}
