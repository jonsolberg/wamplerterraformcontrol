package se.jonsolberg.terraform

import android.app.Application
import android.content.Context
import android.media.midi.MidiDeviceInfo
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import se.jonsolberg.terraform.midi.BleMidiDevice
import se.jonsolberg.terraform.midi.BleMidiScanner
import se.jonsolberg.terraform.midi.MidiClock
import se.jonsolberg.terraform.midi.MidiController
import se.jonsolberg.terraform.model.Cc
import se.jonsolberg.terraform.model.Preset
import se.jonsolberg.terraform.model.PresetStore
import se.jonsolberg.terraform.model.SyncState
import se.jonsolberg.terraform.model.TerraSound
import se.jonsolberg.terraform.model.Terraform
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Steps of the guided "save into the pedal" flow. */
enum class SaveStep { SENDING, HOLD_PRESET, CONFIRM }

class TerraformViewModel(app: Application) : AndroidViewModel(app) {

    val midi = MidiController(app)
    val ble = BleMidiScanner(app)
    private val store = PresetStore(File(app.filesDir, "presets.json"))
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val clock = MidiClock(midi)

    // ---- pedal state (the pedal has no MIDI out, so this is what the app last sent) ----
    var sound by mutableStateOf(TerraSound())
        private set
    var engaged by mutableStateOf(true)
        private set
    var presets by mutableStateOf(store.load())
        private set
    var current by mutableStateOf(prefs.getInt("current", 0).coerceIn(0, Terraform.PRESET_COUNT - 1))
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    /** Tempo from tapping the TAP footswitch in the app, in BPM. */
    var tappedBpm by mutableStateOf<Int?>(null)
        private set
    var expression by mutableStateOf(0)
        private set
    /** Non-null while the guided save into the pedal is running. */
    var saveStep by mutableStateOf<SaveStep?>(null)
        private set
    var saveTarget by mutableStateOf(0)
        private set
    val log = mutableStateListOf<String>()

    // ---- settings ----
    var midiChannel by mutableStateOf(prefs.getInt("channel", 0))   // 0..15
        private set
    var oneBased by mutableStateOf(prefs.getBoolean("oneBased", true))
        private set
    /** Right footswitch: false = tap tempo, true = preset increment (CC 84). */
    var tapSwitchPresets by mutableStateOf(prefs.getBoolean("tapSwitchPresets", false))
        private set
    var followClock by mutableStateOf(prefs.getBoolean("followClock", false))
        private set
    var clockBpm by mutableStateOf(prefs.getInt("clockBpm", 120))
        private set
    var logOutgoing by mutableStateOf(false)

    private var lastDevice: String? = prefs.getString("lastDevice", null)
    private var job: Job? = null
    private val taps = ArrayDeque<Long>()

    val currentPreset: Preset get() = presets[current]
    val isEdited: Boolean get() = currentPreset.sound != null && currentPreset.sound != sound
    val isConnected: Boolean get() = midi.connected.value != null

    /** Tempo shown by the TAP LED: a tempo-set rate wins, then the last tapped tempo. */
    val ledBpm: Int?
        get() = sound.tempoMs?.let { 60000 / it } ?: tappedBpm

    init {
        midi.onLog = ::addLog
        midi.onControlChange = { ch, cc, v -> addLog("← ch${ch + 1} CC $cc = $v") }
        midi.onProgramChange = { ch, p -> addLog("← ch${ch + 1} PC $p") }
        midi.onDevicesChanged = ::autoConnect
        midi.start()
        currentPreset.sound?.let { sound = it }
        clock.bpm = clockBpm
        autoConnect()
    }

    override fun onCleared() {
        clock.stop()
        ble.stop()
        midi.stop()
    }

    fun displayNumber(n: Int) = n + if (oneBased) 1 else 0

    // ---------------------------------------------------------------- connection
    private fun autoConnect() {
        if (isConnected) return
        val target = midi.devices.value.firstOrNull { MidiController.nameOf(it) == lastDevice } ?: return
        connect(target)
    }

    fun connect(info: MidiDeviceInfo) {
        rememberDevice(MidiController.nameOf(info))
        midi.connect(info)
    }

    fun connectBle(d: BleMidiDevice) {
        ble.stop()
        rememberDevice(d.name)
        midi.connectBluetooth(d.device, d.name)
    }

    fun disconnect() {
        clock.stop()
        rememberDevice(null)
        midi.disconnect()
    }

    private fun rememberDevice(name: String?) {
        lastDevice = name
        prefs.edit().putString("lastDevice", name).apply()
    }

    // ---------------------------------------------------------------- knobs & switches
    private fun cc(n: Int, v: Int) {
        val ok = midi.sendCc(midiChannel, n, v)
        if (logOutgoing && ok) addLog("→ ch${midiChannel + 1} CC $n = $v")
    }

    private fun program(n: Int) {
        val ok = midi.sendProgram(midiChannel, n)
        if (logOutgoing && ok) addLog("→ ch${midiChannel + 1} PC $n")
    }

    fun setValue(ccNumber: Int, value: Int) {
        val next = sound.withCc(ccNumber, value)
        if (next == sound) return
        sound = next
        cc(ccNumber, value)
    }

    fun setProgram(p: Int) = setValue(Cc.PROGRAM, p)
    fun setRotarySlow(slow: Boolean) = setValue(Cc.ROTARY_SPEED, if (slow) 127 else 0)
    fun setPrePost(prePost: Boolean) = setValue(Cc.ROUTING, if (prePost) 127 else 0)
    fun setSubdivision(i: Int) = setValue(Cc.SUBDIVISION, i)

    /** Sets the modulation period in ms (CC 12 high, CC 13 low), e.g. from a tempo in BPM. */
    fun setTempoBpm(bpm: Int) {
        val ms = (60000f / bpm).roundToInt().coerceIn(TerraSound.MIN_TEMPO_MS, TerraSound.MAX_TEMPO_MS)
        sound = sound.copy(tempoMs = ms)
        cc(Cc.RATE_MS_HIGH, ms / 128)
        cc(Cc.RATE_MS_LOW, ms % 128)
    }

    fun clearTempo() {
        sound = sound.copy(tempoMs = null)
        cc(Cc.RATE, sound.rate)
    }

    fun setExpression(v: Int) {
        if (v == expression) return
        expression = v
        cc(Cc.EXPRESSION, v)
    }

    fun toggleBypass() {
        engaged = !engaged
        cc(Cc.BYPASS, if (engaged) 127 else 0)
    }

    /**
     * Right footswitch. In tap mode it taps the tempo (on Rotary this ramps between slow and
     * fast, like on the pedal); in preset mode it steps to the next preset.
     */
    fun tapFootswitch() {
        if (tapSwitchPresets) {
            step(1)
            return
        }
        cc(Cc.TAP, 0)
        if (sound.program == Terraform.ROTARY) {
            sound = sound.copy(rotarySlow = !sound.rotarySlow)
            return
        }
        val now = SystemClock.uptimeMillis()
        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
        taps.addLast(now)
        while (taps.size > 4) taps.removeFirst()
        if (taps.size >= 2) {
            val avg = (taps.last() - taps.first()).toFloat() / (taps.size - 1)
            tappedBpm = (60000f / avg).roundToInt()
            // A tapped tempo replaces a tempo set from the app.
            if (sound.tempoMs != null) sound = sound.copy(tempoMs = null)
        }
    }

    fun sendAll() {
        viewModelScope.launch { sendAllNow(sound) }
    }

    private suspend fun sendAllNow(s: TerraSound) {
        for ((c, v) in s.ccMessages()) {
            cc(c, v)
            delay(12)
        }
    }

    fun revert() {
        currentPreset.sound?.let {
            sound = it
            sendAll()
        }
    }

    // ---------------------------------------------------------------- presets
    /** Loads a slot: presets stored in the pedal are recalled by Program Change, others are sent as CCs. */
    fun select(n: Int) {
        if (n !in 0 until Terraform.PRESET_COUNT) return
        current = n
        prefs.edit().putInt("current", n).apply()
        val p = presets[n]
        when (p.sync) {
            SyncState.SYNCED -> {
                sound = p.sound!!
                program(n)
            }
            SyncState.EDITED -> {
                sound = p.sound!!
                sendAll()
            }
            SyncState.EMPTY -> Unit // keep the current sound so it can be stored here
        }
    }

    fun step(delta: Int) = select((current + delta + Terraform.PRESET_COUNT) % Terraform.PRESET_COUNT)

    private fun update(n: Int, transform: (Preset) -> Preset) {
        presets = presets.toMutableList().also { it[n] = transform(it[n]) }
        store.save(presets)
    }

    fun storeCurrent(name: String) {
        val s = sound
        update(current) { it.copy(name = name.trim(), sound = s, sync = SyncState.EDITED) }
    }

    /** Marks a slot as stored in the pedal without writing (it was saved on the pedal by hand). */
    fun markSynced(n: Int, synced: Boolean) = update(n) {
        if (it.sound == null) it else it.copy(sync = if (synced) SyncState.SYNCED else SyncState.EDITED)
    }

    fun rename(n: Int, name: String) = update(n) { it.copy(name = name.trim()) }

    fun reset(numbers: Collection<Int>) {
        presets = presets.toMutableList().also { l -> numbers.forEach { l[it] = Preset(it) } }
        store.save(presets)
    }

    fun copy(numbers: List<Int>, dest: Int): Int {
        val sources = numbers.sorted().map { presets[it] }
        val list = presets.toMutableList()
        var copied = 0
        for ((i, src) in sources.withIndex()) {
            val target = dest + i
            if (target >= Terraform.PRESET_COUNT) break
            list[target] = src.copy(number = target, sync = if (src.sound == null) SyncState.EMPTY else SyncState.EDITED)
            copied++
        }
        presets = list
        store.save(presets)
        return copied
    }

    fun exportJson(numbers: Collection<Int>): String = PresetStore.exportJson(numbers.sorted().map { presets[it] })

    fun importJson(text: String, startAt: Int): Int {
        val items = PresetStore.importJson(text)
        val list = presets.toMutableList()
        var count = 0
        for ((i, item) in items.withIndex()) {
            val target = startAt + i
            if (target >= Terraform.PRESET_COUNT) break
            list[target] = Preset(target, item.first, item.second, SyncState.EDITED)
            count++
        }
        presets = list
        store.save(presets)
        return count
    }

    fun exportBackup(): String = PresetStore.backupJson(
        presets,
        JSONObject()
            .put("midiChannel", midiChannel)
            .put("oneBased", oneBased)
            .put("tapSwitchPresets", tapSwitchPresets)
            .put("followClock", followClock)
            .put("clockBpm", clockBpm),
    )

    fun importBackup(text: String): Int {
        val (list, st) = PresetStore.parseBackup(text)
        presets = list
        store.save(presets)
        st?.let {
            if (it.has("midiChannel")) updateChannel(it.getInt("midiChannel"))
            if (it.has("oneBased")) updateOneBased(it.getBoolean("oneBased"))
            if (it.has("clockBpm")) updateClockBpm(it.getInt("clockBpm"))
            // Pedal-side settings are only stored here; they are sent when changed in Settings.
            if (it.has("tapSwitchPresets")) {
                tapSwitchPresets = it.getBoolean("tapSwitchPresets")
                prefs.edit().putBoolean("tapSwitchPresets", tapSwitchPresets).apply()
            }
            if (it.has("followClock")) {
                followClock = it.getBoolean("followClock")
                prefs.edit().putBoolean("followClock", followClock).apply()
            }
        }
        presets[current].sound?.let { sound = it }
        return presets.count { it.sound != null }
    }

    // ---------------------------------------------------------------- saving into the pedal
    /**
     * The Terraform can't be put in save mode over MIDI, so saving is guided:
     * the app sends the sound, the player holds PRESET/SAVE until the LEDs blink,
     * the app then sends the Program Change that picks the slot, and the player
     * holds PRESET/SAVE again to confirm.
     */
    fun beginSaveToPedal(n: Int) {
        if (!isConnected || job?.isActive == true) return
        val s = presets[n].sound ?: return
        saveTarget = n
        saveStep = SaveStep.SENDING
        job = viewModelScope.launch {
            current = n
            prefs.edit().putInt("current", n).apply()
            sound = s
            if (!engaged) toggleBypass()
            sendAllNow(s)
            delay(300)
            saveStep = SaveStep.HOLD_PRESET
        }
    }

    /** The player says the preset LEDs are blinking: send the slot number. */
    fun saveLedsBlinking() {
        program(saveTarget)
        addLog("Sent PC $saveTarget for preset ${displayNumber(saveTarget)}")
        saveStep = SaveStep.CONFIRM
    }

    fun finishSave(success: Boolean) {
        if (success) {
            update(saveTarget) { it.copy(sync = SyncState.SYNCED) }
            addLog("Preset ${displayNumber(saveTarget)} saved in the pedal")
        }
        saveStep = null
    }

    fun cancelSave() {
        job?.cancel()
        saveStep = null
        busy = null
    }

    // ---------------------------------------------------------------- MIDI clock
    fun toggleClock() {
        if (clock.running.value) clock.stop() else if (isConnected) clock.start()
    }

    fun updateClockBpm(bpm: Int) {
        clockBpm = bpm.coerceIn(30, 300)
        clock.bpm = clockBpm
        prefs.edit().putInt("clockBpm", clockBpm).apply()
    }

    // ---------------------------------------------------------------- log
    private fun addLog(line: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        log.add(0, "$t  $line")
        while (log.size > 300) log.removeAt(log.size - 1)
    }

    fun clearLog() = log.clear()

    // ---------------------------------------------------------------- settings
    fun updateChannel(ch: Int) {
        midiChannel = ch.coerceIn(0, 15)
        prefs.edit().putInt("channel", midiChannel).apply()
    }

    fun updateOneBased(b: Boolean) {
        oneBased = b
        prefs.edit().putBoolean("oneBased", b).apply()
    }

    fun updateTapSwitchPresets(b: Boolean) {
        tapSwitchPresets = b
        prefs.edit().putBoolean("tapSwitchPresets", b).apply()
        cc(Cc.TAP_SWITCH_MODE, if (b) 127 else 0)
    }

    fun updateFollowClock(b: Boolean) {
        followClock = b
        prefs.edit().putBoolean("followClock", b).apply()
        cc(Cc.CLOCK_ENABLE, if (b) 127 else 0)
    }

    /** Re-sends the pedal-wide settings kept in the app (footswitch mode, clock follow). */
    fun sendPedalSettings() {
        cc(Cc.TAP_SWITCH_MODE, if (tapSwitchPresets) 127 else 0)
        cc(Cc.CLOCK_ENABLE, if (followClock) 127 else 0)
    }

    fun testBypass() {
        viewModelScope.launch {
            toggleBypass()
            delay(600)
            toggleBypass()
        }
    }
}
