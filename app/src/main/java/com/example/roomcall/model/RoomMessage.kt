package com.example.roomcall.model

data class RoomMessage(
    val title: String,
    val speechText: String
)

val defaultMessages = listOf(
    RoomMessage(
        title = "응, 괜찮아",
        speechText = "응, 괜찮아"
    ),
    RoomMessage(
        title = "주희야, 조용히 좀 해",
        speechText = "주희야, 조용히 좀 해"
    ),
    RoomMessage(
        title = "주희야, 밥 먹자",
        speechText = "주희야, 밥 먹자"
    )

)