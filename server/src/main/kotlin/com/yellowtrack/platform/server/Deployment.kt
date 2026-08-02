package com.yellowtrack.platform.server

/**
 * The things a deployment has to be told, and what happens when it is not.
 *
 * Every one of these has a default that is right on a laptop and wrong on a server, which
 * is the dangerous shape for configuration to have: it works until it is in front of
 * somebody. So they are read once, at boot, and reported.
 */
data class Deployment(
    /**
     * Origins the browser build is served from.
     *
     * Empty means no cross-origin requests are allowed at all, which is correct for the
     * native clients — they are not a browser and send no Origin. The wasm build is served
     * from somewhere, and without its origin here every request it makes is refused by the
     * browser before it reaches this server.
     */
    val allowedOrigins: List<String>,
) {
    companion object {
        fun fromEnvironment(): Deployment =
            Deployment(
                allowedOrigins =
                    System
                        .getenv("ALLOWED_ORIGINS")
                        .orEmpty()
                        .split(',')
                        .map(String::trim)
                        .filter(String::isNotEmpty),
            )
    }
}
