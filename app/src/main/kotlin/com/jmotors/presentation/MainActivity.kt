package com.jmotors.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.jmotors.presentation.ar.ArShowroomScreen

/**
 * MVP entry: AR showroom with Чоник's emotional sphere over ARCore planes.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ArShowroomScreen()
            }
        }
    }
}
