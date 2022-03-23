package com.example.demoble

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.demoble.ui.theme.DemoBLETheme

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    DemoBLETheme {
        Greeting("Android")
    }
}


@Composable
fun Greeting2(name: String) {
    Text(text = "Hello $name!", color = Color.Black)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview2() {
    DemoBLETheme {
        Greeting2("Android")
    }
}