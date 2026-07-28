package com.yellowtrack.platform.core.model.client

data class Client(
    val id: ClientId,
    val firstName: String,
    val lastName: String,
) {
    val displayName: String
        get() = "$firstName $lastName".trim()
}
