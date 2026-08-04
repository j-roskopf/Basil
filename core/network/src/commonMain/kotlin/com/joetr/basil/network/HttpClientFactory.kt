package com.joetr.basil.network

import io.ktor.client.HttpClient

public expect fun createPlatformHttpClient(): HttpClient

/** API client for JSON endpoints; non-2xx responses throw. */
public fun createBasilHttpClient(): HttpClient = createPlatformHttpClient()

/** Binary/image client for Coil and image staging; failures return to the caller. */
public expect fun createBasilImageHttpClient(): HttpClient
