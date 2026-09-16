package com.chesspoints.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.chesspoints.app.ui.theme.ChessPointsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessPointsApp()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChessPointsAppPreview() {
    ChessPointsTheme {
        ChessPointsApp()
    }
}
