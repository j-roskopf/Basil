package com.joetr.basil.data.image

/** Storage object path: `users/{uid}/{recipeId}.jpg` */
public fun recipeStoragePath(ownerId: String, recipeId: String): String =
    "users/$ownerId/$recipeId.jpg"

public fun isRemoteHttpUrl(value: String?): Boolean =
    value?.startsWith("http://") == true || value?.startsWith("https://") == true
