package com.joetr.basil.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.joetr.basil.di.createBasilAppGraph
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

import kotlinx.browser.window
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    MainScope().launch {
        try {
            val graph = createBasilAppGraph()
            ComposeViewport(viewportContainerId = "composeApp") {
                App(graph, initialWebPath = readWebPath())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (window.location.pathname.contains("auth-callback")) {
                window.history.replaceState(null, "", "/")
            }
            window.document.body?.innerHTML =
                "<pre style='padding:1rem;font-family:system-ui'>Failed to start Basil: ${e.message ?: e}</pre>"
        }
    }
}

private fun readWebPath(): String? = window.location.pathname.takeIf { it != "/" }
