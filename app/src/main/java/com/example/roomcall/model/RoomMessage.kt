package com.example.roomcall.model

data class RoomMessage(
    val title: String,
    val speechText: String
)

val defaultMessages = listOf(
    RoomMessage("응, 괜찮아", "응, 괜찮아"),
    RoomMessage("주희야, 조용히 좀 해", "주희야, 조용히 좀 해"),
    RoomMessage("주희야, 밥 먹자", "주희야, 밥 먹자")
)
