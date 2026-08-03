package com.joetr.basil.data.image

/**
 * Firebase download-token URLs are used directly (revocable, not TTL-signed).
 * This resolver is a pass-through for http(s) URLs and local models.
 */
public class SignedImageUrlResolver {
    public suspend fun resolve(model: Any?): Any? = when (model) {
        null -> null
        is String -> resolveString(model)
        else -> model
    }

    public suspend fun resolveString(value: String): String = value
}
