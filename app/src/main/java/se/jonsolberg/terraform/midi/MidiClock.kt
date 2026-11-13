package se.jonsolberg.terraform.midi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.LockSupport

/** Sends MIDI clock (24 pulses per quarter note) on its own thread. */
class MidiClock(private val midi: MidiController) {

    @Volatile
    var bpm: Int = 120

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    @Volatile
    private var thread: Thread? = null

    fun start() {
        if (thread != null) return
        val t = Thread({
            midi.sendRealtime(START)
            var next = System.nanoTime()
            while (thread === Thread.currentThread()) {
                if (!midi.sendRealtime(CLOCK)) break
                next += 60_000_000_000L / (bpm.coerceIn(30, 300) * 24L)
                val wait = next - System.nanoTime()
                if (wait > 0) LockSupport.parkNanos(wait) else next = System.nanoTime()
            }
            midi.sendRealtime(STOP)
            if (thread === Thread.currentThread()) thread = null
            _running.value = false
        }, "midi-clock")
        t.priority = Thread.MAX_PRIORITY
        thread = t
        _running.value = true
        t.start()
    }

    fun stop() {
        val t = thread ?: return
        thread = null
        LockSupport.unpark(t)
    }

    companion object {
        const val CLOCK = 0xF8
        const val START = 0xFA
        const val STOP = 0xFC
    }
}
