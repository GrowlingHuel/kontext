package com.kontext.ui.story

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kontext.data.local.StoryLoader
import com.kontext.ui.theme.KontextTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class StoryActivity : ComponentActivity() {

    @Inject
    lateinit var storyLoader: StoryLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KontextTheme {
                StoryScreen(
                    storyLoader = storyLoader,
                    onFinished = { finish() }
                )
            }
        }
    }
}
