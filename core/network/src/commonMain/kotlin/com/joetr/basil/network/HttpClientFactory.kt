package com.joetr.basil.network

import io.ktor.client.HttpClient

public expect fun createPlatformHttpClient(): HttpClient

public fun createBasilHttpClient(): HttpClient = createPlatformHttpClient()
