package com.kazahana.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val did: String,
    val handle: String,
    val accessJwt: String,
    val refreshJwt: String,
    val email: String? = null,
)

@Serializable
data class CreateSessionRequest(
    val identifier: String,
    val password: String,
)

@Serializable
data class DIDDocument(
    val id: String,
    val service: List<DIDService> = emptyList(),
)

@Serializable
data class DIDService(
    val id: String,
    val type: String,
    val serviceEndpoint: String,
)

@Serializable
data class ResolveHandleResponse(
    val did: String,
)

@Serializable
data class RefreshSessionResponse(
    val did: String,
    val handle: String,
    val accessJwt: String,
    val refreshJwt: String,
)

@Serializable
data class ATProtoError(
    val error: String? = null,
    val message: String? = null,
)
