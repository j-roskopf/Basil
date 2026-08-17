package com.joetr.basil.app

import com.joetr.basil.platform.ShareIntentHolder

@Suppress("unused")
public fun setPendingShareUrl(url: String) {
    ShareIntentHolder.pendingUrl = url
}

@Suppress("unused")
public fun setPendingSharedRecipeToken(token: String) {
    ShareIntentHolder.pendingSharedToken = token
}
