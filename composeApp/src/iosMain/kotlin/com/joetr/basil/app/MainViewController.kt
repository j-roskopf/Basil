package com.joetr.basil.app

import androidx.compose.ui.window.ComposeUIViewController
import com.joetr.basil.di.createBasilAppGraph
import kotlinx.coroutines.runBlocking

@Suppress("unused")
public fun MainViewController() = ComposeUIViewController {
    val graph = runBlocking { createBasilAppGraph() }
    App(graph)
}
