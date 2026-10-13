package com.charvis.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CharvisScreen(vm: CharvisViewModel = viewModel()) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.connect() }

    val accent = when (vm.state) {
        CharvisState.DISCONNECTED -> Color(0xFF4A5A78)
        CharvisState.CONNECTING -> Color(0xFFFFB74D)
        CharvisState.LISTENING -> Color(0xFF26C6DA)
        CharvisState.SPEAKING -> Color(0xFFB388FF)
    }

    val label = when (vm.state) {
        CharvisState.DISCONNECTED -> "टैप करो, चार्विस जागेगा"
        CharvisState.CONNECTING -> "जुड़ रहा हूँ..."
        CharvisState.LISTENING -> "सुन रहा हूँ"
        CharvisState.SPEAKING -> "बोल रहा हूँ"
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val scale = if (vm.state == CharvisState.DISCONNECTED) 1f else pulse

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CHARVIS",
                color = Color(0xFFE0E7FF),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "सेटिंग्स", tint = Color(0xFFB0BEC5))
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(280.dp)
                    .scale(scale)
                    .background(
                        Brush.radialGradient(listOf(accent.copy(alpha = 0.40f), Color.Transparent)),
                        CircleShape
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(160.dp)
                        .background(
                            Brush.verticalGradient(listOf(accent.copy(alpha = 0.9f), Color(0xFF0A0E1A))),
                            CircleShape
                        )
                        .border(2.dp, accent, CircleShape)
                        .clickable {
                            if (vm.state != CharvisState.DISCONNECTED) {
                                vm.toggle()
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) vm.connect()
                                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "माइक",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(text = label, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)

            vm.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = it,
                    color = Color(0xFFFF8A80),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentKey = vm.settings.apiKey,
            onSave = { key ->
                vm.settings.apiKey = key
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun SettingsDialog(
    currentKey: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyText by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1320),
        title = { Text("Gemini API Key", color = Color(0xFFE0E7FF)) },
        text = {
            Column {
                Text(
                    "अपनी Gemini API key यहाँ पेस्ट करो। Link पेस्ट करोगे तो भी मैं key खुद निकाल लूँगा।",
                    color = Color(0xFF90A4AE),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("AIza...") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(keyText) }) { Text("सेव करो") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("रद्द करो") }
        }
    )
}
