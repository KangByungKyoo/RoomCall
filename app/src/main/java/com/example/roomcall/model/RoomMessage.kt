package com.example.roomcall.model

data class RoomMessage(
    val title: String,
    val speechText: String
)

val defaultMessages = listOf(
    RoomMessage(
        title = "TV 소리 줄여",
        speechText = "TV 소리 좀 줄여."
    ),
    RoomMessage(
        title = "밥 먹자",
        speechText = "밥 먹자."
    ),
    RoomMessage(
        title = "거실로 와",
        speechText = "거실로 와."
    )
)