package com.joetr.basil.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.joetr.basil.di.createBasilAppGraph
import com.joetr.basil.platform.AndroidContextHolder
import com.joetr.basil.platform.ShareIntentHolder
import com.joetr.basil.platform.registerLifecycleObserver
import kotlinx.coroutines.runBlocking

public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkWindow = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.decorView.setBackgroundColor(
            if (darkWindow) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF"),
        )
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        AndroidContextHolder.application = applicationContext
        AndroidContextHolder.activity = this
        handleShareIntent(intent)
        val graph = runBlocking { createBasilAppGraph() }
        registerLifecycleObserver()
        setContent { App(graph) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = text.lines().firstOrNull { it.startsWith("http://") || it.startsWith("https://") } ?: text.trim()
        if (url.isNotBlank()) ShareIntentHolder.pendingUrl = url
    }
}
