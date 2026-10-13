package com.charvis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF05070D),
                    surface = Color(0xFF0E1320)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF05070D)) {
                    CharvisScreen()
                }
            }
        }
    }
}
