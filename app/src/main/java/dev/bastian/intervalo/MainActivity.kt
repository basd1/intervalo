package dev.bastian.intervalo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.bastian.intervalo.ui.IntervaloNavHost
import dev.bastian.intervalo.ui.theme.IntervaloTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IntervaloTheme {
                IntervaloNavHost()
            }
        }
    }
}
