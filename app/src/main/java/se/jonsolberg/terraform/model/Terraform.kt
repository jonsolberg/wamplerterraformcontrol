package se.jonsolberg.terraform.model

import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.roundToInt

/** MIDI CC numbers from the Terraform firmware addendum (October 2023). */
object Cc {
    const val BYPASS = 1            // 0 = off, 1-127 = on
    const val PROGRAM = 2           // 0-10, see Terraform.EFFECTS
    const val RATE = 3
    const val DEPTH = 4
    const val VARIABLE = 5
    const val BLEND = 6             // "Output Mix" – meaning depends on the effect
    const val VOLUME = 7            // 0 = -6 dB, 63 = 0 dB, 127 = +6 dB
    const val PHASER_FEEDBACK = 8   // 0 script, 63 block, 127 beyond
    const val ROTARY_SPEED = 9      // 0 = fast, 1-127 = slow
    const val SUBDIVISION = 11      // 0-14
    const val RATE_MS_HIGH = 12     // mod rate period in ms, high 7 bits
    const val RATE_MS_LOW = 13      // low 7 bits, send after HIGH
    const val TAP = 81              // value 0
    const val PRESET_DOWN = 82
    const val PRESET_UP = 83
    const val TAP_SWITCH_MODE = 84  // 0 = tap tempo, 1-127 = preset increment
    const val ROUTING = 85          // 0 = normal (stereo), 1-127 = pre/post (mono)
    const val CLOCK_ENABLE = 99     // 0 = ignore MIDI clock, 1-127 = follow
    const val EXPRESSION = 100
}

enum class BlendKind { MIX, ROTARY, TREMOLO_PHASE, CROSSOVER }

data class Effect(
    val name: String,
    /** Abbreviation printed next to the selector on the pedal. */
    val short: String,
    val rate: String,
    val depth: String,
    val variable: String,
    val blend: String,
    val blendKind: BlendKind = BlendKind.MIX,
    /** Default position in PRE/POST mode (true = before the drive/amp). */
    val pre: Boolean,
)

object Terraform {
    const val PRESET_COUNT = 128

    /** In CC 2 order, which is also clockwise round the selector starting bottom-left. */
    val EFFECTS = listOf(
        Effect("Auto Wah", "A.WAH", "LFO speed", "Filter range", "Q (resonance)", "Mix", pre = true),
        Effect("Envelope Filter", "ENVE", "Attack / release", "Sensitivity", "Q (resonance)", "Mix", pre = true),
        Effect("Flanger", "FLNG", "Speed", "Depth (max = through-zero)", "Feedback", "Mix", pre = true),
        Effect("Phaser", "PHAS", "Speed", "Filter depth", "Tone", "Mix", pre = true),
        Effect("Uni-Vibe", "U.VIB", "Speed", "Filter depth", "Wave shape", "Mix", pre = true),
        Effect("Rotary", "RTRY", "Horn max speed", "Woofer max speed", "Ramp speed", "Woofer ↔ horn", BlendKind.ROTARY, pre = false),
        Effect("Auto Swell", "SWEL", "Attack time", "Modulation", "Pick sensitivity", "Mix", pre = false),
        Effect("Tremolo", "TREM", "Speed", "Depth", "Wave shape", "Stereo phase", BlendKind.TREMOLO_PHASE, pre = false),
        Effect("Harmonic Tremolo", "HARM", "Speed", "Depth", "Width", "Crossover", BlendKind.CROSSOVER, pre = false),
        Effect("Chorus", "CHOR", "Speed", "Depth", "Tone", "Mix (max = vibrato)", pre = false),
        Effect("Dimension", "DIME", "Speed", "Depth", "Low end", "Mix", pre = false),
    )

    const val PHASER = 3
    const val ROTARY = 5

    val SUBDIVISIONS = listOf(
        "♩ Quarter", "Dotted eighth", "Eighth", "Sixteenth", "Half", "Whole", "Dotted quarter",
        "Quarter triplet", "Dotted sixteenth", "Dotted half", "Dotted whole", "Sixteenth triplet",
        "Eighth triplet", "Half triplet", "Whole triplet",
    )

    /** Subdivisions 0-7 can also be picked on the pedal itself. */
    const val PANEL_SUBDIVISIONS = 8

    fun phaserFeedbackName(v: Int) = when {
        v < 32 -> "Script"
        v < 96 -> "Block"
        else -> "Beyond"
    }
}

fun pct(v: Int): Int = (v * 100f / 127f).roundToInt()

fun volumeDb(v: Int): Float = if (v <= 63) -6f + 6f * v / 63f else 6f * (v - 63) / 64f

fun formatVolume(v: Int): String {
    val db = (volumeDb(v) * 10).roundToInt() / 10f
    return (if (db > 0) "+" else "") + "$db dB"
}

fun formatBlend(kind: BlendKind, v: Int): String = when (kind) {
    BlendKind.MIX -> when {
        v == 63 -> "50 / 50"
        v < 63 -> "${100 - (v * 50 / 63)}% dry"
        else -> "${50 + ((v - 63) * 50 / 64)}% wet"
    }
    BlendKind.ROTARY -> "${pct(v)}% horn"
    BlendKind.TREMOLO_PHASE -> "${(v * 180f / 127f).roundToInt()}°"
    BlendKind.CROSSOVER -> {
        val hz = if (v <= 63) 100.0 * 7.0.pow(v / 63.0) else 700.0 * (2500.0 / 700.0).pow((v - 63) / 64.0)
        "${(hz / 10).roundToInt() * 10} Hz"
    }
}

/** Every setting of one Terraform sound. */
data class TerraSound(
    val program: Int = 9,
    val rate: Int = 48,
    val depth: Int = 64,
    val variable: Int = 64,
    val blend: Int = 63,
    val volume: Int = 63,
    val phaserFeedback: Int = 63,
    val rotarySlow: Boolean = true,
    val subdivision: Int = 0,
    val prePost: Boolean = false,
    /** Mod-rate period set by tempo (CC 12/13), or null when the RATE knob decides. */
    val tempoMs: Int? = null,
) {
    val effect: Effect get() = Terraform.EFFECTS[program]

    /** Program first: since the 2023 firmware a program change keeps the knob positions. */
    fun ccMessages(): List<Pair<Int, Int>> = buildList {
        add(Cc.PROGRAM to program)
        add(Cc.RATE to rate)
        add(Cc.DEPTH to depth)
        add(Cc.VARIABLE to variable)
        add(Cc.BLEND to blend)
        add(Cc.VOLUME to volume)
        if (program == Terraform.PHASER) add(Cc.PHASER_FEEDBACK to phaserFeedback)
        if (program == Terraform.ROTARY) add(Cc.ROTARY_SPEED to if (rotarySlow) 127 else 0)
        add(Cc.SUBDIVISION to subdivision)
        add(Cc.ROUTING to if (prePost) 127 else 0)
        tempoMs?.let { ms ->
            add(Cc.RATE_MS_HIGH to (ms / 128).coerceIn(0, 15))
            add(Cc.RATE_MS_LOW to ms % 128)
        }
    }

    fun withCc(cc: Int, raw: Int): TerraSound {
        val v = raw.coerceIn(0, 127)
        return when (cc) {
            Cc.PROGRAM -> if (v in Terraform.EFFECTS.indices) copy(program = v) else this
            // Turning RATE takes over from a tempo-set rate, as on the pedal.
            Cc.RATE -> copy(rate = v, tempoMs = null)
            Cc.DEPTH -> copy(depth = v)
            Cc.VARIABLE -> copy(variable = v)
            Cc.BLEND -> copy(blend = v)
            Cc.VOLUME -> copy(volume = v)
            Cc.PHASER_FEEDBACK -> copy(phaserFeedback = v)
            Cc.ROTARY_SPEED -> copy(rotarySlow = v > 0)
            Cc.SUBDIVISION -> if (v in Terraform.SUBDIVISIONS.indices) copy(subdivision = v) else this
            Cc.ROUTING -> copy(prePost = v > 0)
            else -> this
        }
    }

    fun summary(): String {
        val e = effect
        val tempo = tempoMs?.let { " | ${60000 / it} BPM" } ?: ""
        return "${e.name} | Rate ${pct(rate)} · Depth ${pct(depth)} · ${formatBlend(e.blendKind, blend)}$tempo"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("program", program)
        .put("effect", effect.name)
        .put("rate", rate)
        .put("depth", depth)
        .put("variable", variable)
        .put("blend", blend)
        .put("volume", volume)
        .put("phaserFeedback", phaserFeedback)
        .put("rotarySlow", rotarySlow)
        .put("subdivision", subdivision)
        .put("prePost", prePost)
        .put("tempoMs", tempoMs ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): TerraSound {
            val d = TerraSound()
            return TerraSound(
                program = o.optInt("program", d.program).coerceIn(0, Terraform.EFFECTS.size - 1),
                rate = o.optInt("rate", d.rate).coerceIn(0, 127),
                depth = o.optInt("depth", d.depth).coerceIn(0, 127),
                variable = o.optInt("variable", d.variable).coerceIn(0, 127),
                blend = o.optInt("blend", d.blend).coerceIn(0, 127),
                volume = o.optInt("volume", d.volume).coerceIn(0, 127),
                phaserFeedback = o.optInt("phaserFeedback", d.phaserFeedback).coerceIn(0, 127),
                rotarySlow = o.optBoolean("rotarySlow", d.rotarySlow),
                subdivision = o.optInt("subdivision", 0).coerceIn(0, Terraform.SUBDIVISIONS.size - 1),
                prePost = o.optBoolean("prePost", false),
                tempoMs = if (o.isNull("tempoMs")) null else o.optInt("tempoMs").takeIf { it in MIN_TEMPO_MS..MAX_TEMPO_MS },
            )
        }

        /** CC 12/13 carry 4 + 7 bits, so the longest period is 2047 ms. */
        const val MIN_TEMPO_MS = 100
        const val MAX_TEMPO_MS = 2047
    }
}

enum class SyncState { EMPTY, EDITED, SYNCED }

data class Preset(
    val number: Int,
    val name: String = "",
    val sound: TerraSound? = null,
    val sync: SyncState = SyncState.EMPTY,
)
