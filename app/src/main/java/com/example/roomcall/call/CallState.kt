package com.example.roomcall.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallState { IDLE, CONNECTING, BUSY }
data class CallStatus(val state: CallState = CallState.IDLE, val detail: String = "", val ready: Boolean = false)

object CallStates {
    internal val receiverMutable = MutableStateFlow(CallStatus())
    internal val senderMutable = MutableStateFlow(CallStatus())
    val receiver = receiverMutable.asStateFlow()
    val sender = senderMutable.asStateFlow()
}