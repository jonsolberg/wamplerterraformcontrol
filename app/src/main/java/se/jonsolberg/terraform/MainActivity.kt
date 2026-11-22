package se.jonsolberg.terraform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import se.jonsolberg.terraform.ui.AboutScreen
import se.jonsolberg.terraform.ui.TerraformTheme
import se.jonsolberg.terraform.ui.PedalScreen
import se.jonsolberg.terraform.ui.PresetsScreen
import se.jonsolberg.terraform.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TerraformTheme {
                App(viewModel())
            }
        }
        if (savedInstanceState == null) UpdateChecker.start(this)
    }
}

@Composable
private fun App(vm: TerraformViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("Pedal") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Presets") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Info, null) }, label = { Text("About") })
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> PedalScreen(vm, onOpenSettings = { tab = 2 })
                1 -> PresetsScreen(vm, onOpenPreset = { tab = 0 })
                2 -> SettingsScreen(vm)
                else -> AboutScreen()
            }
        }
    }

    vm.saveStep?.let { step -> SaveGuide(vm, step) }
}

@Composable
private fun SaveGuide(vm: TerraformViewModel, step: SaveStep) {
    val n = vm.displayNumber(vm.saveTarget)
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Save preset $n into the pedal") },
        text = {
            Column {
                when (step) {
                    SaveStep.SENDING -> {
                        Text("Sending the sound to the pedal…")
                        LinearProgressIndicator(Modifier.padding(top = 16.dp))
                    }
                    SaveStep.HOLD_PRESET -> Text(
                        "1. On the pedal, hold PRESET / SAVE until the preset LEDs start blinking.\n\n" +
                            "Then tap “LEDs are blinking”."
                    )
                    SaveStep.CONFIRM -> Text(
                        "2. The app sent Program Change for slot $n.\n\n" +
                            "Now hold PRESET / SAVE again until the LEDs stop blinking to confirm the save."
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                SaveStep.SENDING -> Unit
                SaveStep.HOLD_PRESET -> TextButton(onClick = vm::saveLedsBlinking) { Text("LEDs are blinking") }
                SaveStep.CONFIRM -> TextButton(onClick = { vm.finishSave(true) }) { Text("Saved") }
            }
        },
        dismissButton = { TextButton(onClick = vm::cancelSave) { Text("Cancel") } },
    )
}
