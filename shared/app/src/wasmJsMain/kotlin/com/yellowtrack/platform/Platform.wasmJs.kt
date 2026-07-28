package com.yellowtrack.platform

class WasmJsPlatform : Platform {
    override val name: String = "WebAssembly (JS Target)"
}

actual fun getPlatform(): Platform = WasmJsPlatform()
