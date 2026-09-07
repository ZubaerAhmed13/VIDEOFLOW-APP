package com.videoflow.app.ui.editor

fun android.content.Context.hostActivity(): android.app.Activity? = when(this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> if(baseContext !== this) baseContext.hostActivity() else null
    else -> null
}
