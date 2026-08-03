package com.joetr.basil.data.image

public const val LOCAL_IMAGE_SCHEME: String = "local-image"

public fun localImageModel(localImageId: String): String = "$LOCAL_IMAGE_SCHEME://$localImageId"
