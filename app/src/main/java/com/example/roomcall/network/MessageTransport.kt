package com.example.roomcall.network

import com.example.roomcall.model.RoomMessage

interface MessageTransport {

    fun start()

    fun stop()

    fun send(message: RoomMessage): Boolean

}