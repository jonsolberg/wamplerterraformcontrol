package se.jonsolberg.terraform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import se.jonsolberg.terraform.TerraformViewModel
import se.jonsolberg.terraform.model.Cc
import se.jonsolberg.terraform.model.SyncState
import se.jonsolberg.terraform.model.Terraform
import se.jonsolberg.terraform.model.formatBlend
import se.jonsolberg.terraform.model.formatVolume
import se.jonsolberg.terraform.model.pct
import kotlin.math.roundToInt

@Composable
fun PedalScreen(vm: TerraformViewModel, onOpenSettings: () -> Unit) {
    val connected by vm.midi.connected.collectAsState()
    var showStore by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val knob = when {
            wide -> minOf(76.dp, maxHeight / 6.5f)
            maxWidth < 360.dp -> 50.dp
            else -> 58.dp
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.1f).verticalScroll(rememberScrollState())) {
                    PedalFace(vm, knob, onStore = { showStore = true })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConnectionBanner(connected, onOpenSettings)
                    PresetBar(vm, onStore = { showStore = true })
                    KnobGuide(vm)
                    LiveEditPanel(vm)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConnectionBanner(connected, onOpenSettings)
                PresetBar(vm, onStore = { showStore = true })
                PedalFace(vm, knob, onStore = { showStore = true })
                KnobGuide(vm)
                LiveEditPanel(vm)
            }
        }
    }

    if (showStore) StoreDialog(vm, onDismiss = { showStore = false })
}

@Composable
private fun ConnectionBanner(connected: String?, onOpenSettings: () -> Unit) {
    if (connected != null) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF5A2A22), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Led(LedRed, on = true, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            "Not connected to a MIDI interface — tap to choose one",
            fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PedalFace(vm: TerraformViewModel, knob: Dp, onStore: () -> Unit) {
    val s = vm.sound
    val e = s.effect
    val shape = RoundedCornerShape(16.dp)

    // Bypass LED: off/red when bypassed; blue (green for a PRE effect in PRE/POST mode) when on,
    // with red added when the sound differs from the stored preset.
    val pre = s.prePost && e.pre
    val bypassColor = when {
        !vm.engaged -> LedRed
        vm.isEdited -> if (pre) LedYellow else LedPurple
        pre -> LedGreen
        else -> LedBlue
    }
    val bypassOn = vm.engaged || vm.isEdited

    // TAP LED flashes on every beat of the known tempo.
    var beat by remember { mutableStateOf(false) }
    val bpm = vm.ledBpm
    LaunchedEffect(bpm) {
        beat = false
        if (bpm == null) return@LaunchedEffect
        val period = (60000L / bpm).coerceAtLeast(100)
        while (true) {
            beat = true
            delay(90)
            beat = false
            delay(period - 90)
        }
    }
    val tapColor = if (vm.tapSwitchPresets) LedGreen else LedBlue
    val tapOn = if (vm.tapSwitchPresets) true else beat || bpm == null

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, shape)
            .clip(shape),
    ) {
        // ---- navy control panel ----
        Column(Modifier.fillMaxWidth().navyPanel().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                RoutingToggle(s.prePost, vm::setPrePost)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                EffectSelector(
                    Terraform.EFFECTS.map { it.short }, s.program, vm::setProgram,
                    Modifier.weight(1.75f).height(knob * 2.35f),
                )
                Column(Modifier.weight(3f)) {
                    Row(Modifier.fillMaxWidth()) {
                        PedalKnob("RATE", s.rate, { vm.setValue(Cc.RATE, it) }, Modifier.weight(1f), knob)
                        PedalKnob("DEPTH", s.depth, { vm.setValue(Cc.DEPTH, it) }, Modifier.weight(1f), knob)
                        PedalKnob(
                            "BLEND", s.blend, { vm.setValue(Cc.BLEND, it) }, Modifier.weight(1f), knob,
                            format = { formatBlend(e.blendKind, it) },
                        )
                    }
                    // VARIABLE and VOLUME sit between the top knobs, a row lower.
                    Row(Modifier.fillMaxWidth().offset(y = -knob * 0.08f)) {
                        Spacer(Modifier.weight(0.5f))
                        PedalKnob("VARIABLE", s.variable, { vm.setValue(Cc.VARIABLE, it) }, Modifier.weight(1f), knob)
                        PedalKnob(
                            "VOLUME", s.volume, { vm.setValue(Cc.VOLUME, it) }, Modifier.weight(1f), knob,
                            format = ::formatVolume,
                        )
                        Spacer(Modifier.weight(0.5f))
                    }
                }
            }
        }
        // ---- pale blue body with footswitches ----
        Column(
            Modifier.fillMaxWidth().wireframeBody().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                e.name.uppercase().toList().joinToString(" "),
                color = BodyText, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1,
            )
            Text(
                if (s.prePost) (if (e.pre) "pre/post · pre" else "pre/post · post") else "normal · stereo",
                color = BodyText.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(knob * 0.35f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Footswitch("BYPASS", bypassColor, bypassOn, vm::toggleBypass, switchSize = knob * 1.05f)
                }
                PresetButton(
                    litLed = vm.current % 4,
                    ledColor = if ((vm.current / 4) % 2 == 0) LedBlue else LedGreen,
                    onTap = { vm.step(1) },
                    onLongPress = onStore,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Footswitch("TAP TEMPO", tapColor, tapOn, vm::tapFootswitch, ledLeft = true, switchSize = knob * 1.05f)
                }
            }
        }
    }
}

@Composable
private fun PresetBar(vm: TerraformViewModel, onStore: () -> Unit) {
    val p = vm.currentPreset
    val connected by vm.midi.connected.collectAsState()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.step(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous preset")
                }
                Box(
                    Modifier
                        .background(Color(0xFF0B0B0C), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF333336), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "%03d".format(vm.displayNumber(vm.current)),
                        color = LedBlue, fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = { vm.step(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next preset")
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        p.name.ifEmpty { if (p.sound == null) "Empty slot" else "Untitled" },
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val (txt, col) = when {
                        vm.isEdited -> "Modified" to SyncAmber
                        p.sync == SyncState.SYNCED -> "In the pedal" to SyncGreen
                        p.sync == SyncState.EDITED -> "Only in the app" to SyncAmber
                        else -> "Empty" to Color.Gray
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(col, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(txt, fontSize = 12.sp, color = col)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStore, modifier = Modifier.weight(1f)) { Text("Store") }
                if (vm.isEdited) {
                    OutlinedButton(onClick = vm::revert, modifier = Modifier.weight(1f)) { Text("Revert") }
                } else if (p.sound != null && p.sync != SyncState.SYNCED) {
                    OutlinedButton(
                        onClick = { vm.beginSaveToPedal(vm.current) },
                        enabled = connected != null,
                        modifier = Modifier.weight(1f),
                    ) { Text("To pedal") }
                }
                OutlinedButton(onClick = vm::sendAll, modifier = Modifier.weight(1f)) { Text("Send all") }
            }
        }
    }
}

/** What each knob does on the selected effect – it changes from effect to effect. */
@Composable
private fun KnobGuide(vm: TerraformViewModel) {
    val s = vm.sound
    val e = s.effect
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${e.name} knobs", style = MaterialTheme.typography.titleSmall)
            GuideRow("RATE", e.rate, s.tempoMs?.let { "${60000 / it} BPM (tempo)" } ?: "${pct(s.rate)}")
            GuideRow("DEPTH", e.depth, "${pct(s.depth)}")
            GuideRow("VARIABLE", e.variable, "${pct(s.variable)}")
            GuideRow("BLEND", e.blend, formatBlend(e.blendKind, s.blend))
            GuideRow("VOLUME", "Output level", formatVolume(s.volume))
            Text(
                "Drag a knob up/right to turn it, double-tap to centre. Tap or drag on the dial to pick an effect. " +
                    "Hold PRESET / SAVE to store.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuideRow(knob: String, what: String, value: String) {
    Row {
        Text(knob, Modifier.width(78.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(what, Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveEditPanel(vm: TerraformViewModel) {
    val s = vm.sound
    var bpmText by remember(s.tempoMs) { mutableStateOf(s.tempoMs?.let { "${60000 / it}" } ?: (vm.tappedBpm?.toString() ?: "")) }
    var subMenu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Live edit", style = MaterialTheme.typography.titleSmall)

            if (s.program == Terraform.PHASER) {
                ChoiceRow("Phaser feedback", listOf("Script", "Block", "Beyond"), when {
                    s.phaserFeedback < 32 -> 0
                    s.phaserFeedback < 96 -> 1
                    else -> 2
                }) { vm.setValue(Cc.PHASER_FEEDBACK, listOf(0, 63, 127)[it]) }
            }
            if (s.program == Terraform.ROTARY) {
                ChoiceRow("Rotary speed (TAP ramps between them)", listOf("Slow (chorale)", "Fast (tremolo)"), if (s.rotarySlow) 0 else 1) {
                    vm.setRotarySlow(it == 0)
                }
            }

            ChoiceRow("Routing", listOf("Normal (stereo)", "Pre/post (mono)"), if (s.prePost) 1 else 0) { vm.setPrePost(it == 1) }

            Text("Tap subdivision", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                OutlinedButton(onClick = { subMenu = true }) {
                    Text(Terraform.SUBDIVISIONS[s.subdivision])
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = subMenu, onDismissRequest = { subMenu = false }) {
                    Terraform.SUBDIVISIONS.forEachIndexed { i, name ->
                        DropdownMenuItem(
                            text = { Text(name + if (i >= Terraform.PANEL_SUBDIVISIONS) "  (MIDI only)" else "") },
                            onClick = {
                                vm.setSubdivision(i)
                                subMenu = false
                            },
                        )
                    }
                }
            }

            Text("Tempo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    bpmText, { bpmText = it.filter(Char::isDigit).take(3) },
                    label = { Text("BPM") }, singleLine = true, modifier = Modifier.width(96.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(
                    onClick = { bpmText.toIntOrNull()?.takeIf { it in 30..600 }?.let(vm::setTempoBpm) },
                    enabled = bpmText.toIntOrNull()?.let { it in 30..600 } == true,
                ) { Text("Set") }
                if (s.tempoMs != null) TextButton(onClick = vm::clearTempo) { Text("Use RATE knob") }
            }
            Text(
                s.tempoMs?.let { "Modulation period ${it} ms (${"%.2f".format(1000f / it)} Hz), saved with the preset." }
                    ?: (vm.tappedBpm?.let { "Last tapped: $it BPM" } ?: "Sets the modulation period over MIDI (30–600 BPM)."),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Expression pedal: ${pct(vm.expression)}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = vm.expression.toFloat(),
                onValueChange = { vm.sendExpression(it.roundToInt()) },
                valueRange = 0f..127f,
            )
            Text(
                "Moves whichever knob is assigned to the expression input on the pedal (heel → toe range set on the pedal).",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o ->
                FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(o, fontSize = 12.sp) })
            }
        }
    }
}

@Composable
private fun StoreDialog(vm: TerraformViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(vm.currentPreset.name) }
    val connected by vm.midi.connected.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Store preset ${vm.displayNumber(vm.current)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true)
                Text(
                    "Stores the sound in the app. “Store + pedal” then walks you through saving it into the pedal.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    vm.storeCurrent(name)
                    onDismiss()
                }) { Text("Store") }
                TextButton(
                    enabled = connected != null,
                    onClick = {
                        vm.storeCurrent(name)
                        vm.beginSaveToPedal(vm.current)
                        onDismiss()
                    },
                ) { Text("Store + pedal") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
