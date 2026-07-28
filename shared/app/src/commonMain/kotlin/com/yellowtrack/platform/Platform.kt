package com.yellowtrack.platform

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
