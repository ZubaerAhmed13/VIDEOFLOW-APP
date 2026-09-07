package com.videoflow.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState as composeCollectAsState
import kotlinx.coroutines.flow.StateFlow

/** Same-package bridge keeps editor source concise while retaining lifecycle-safe Compose collection. */
@Composable
fun <T> StateFlow<T>.collectAsState(): State<T> = this.composeCollectAsState()
