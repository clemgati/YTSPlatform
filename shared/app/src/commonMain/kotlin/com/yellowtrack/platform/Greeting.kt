package com.yellowtrack.platform

class Greeting {
    private val platform = getPlatform()

    fun greet(): String = sayHello(platform.name)
}
