package com.joetr.basil.network

import com.joetr.basil.domain.model.ExtractedRecipe
import com.joetr.basil.platform.BasilConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

public class FunctionsApi(
    private val httpClient: HttpClient,
) {
    public suspend fun extractRecipe(idToken: String, url: String): ExtractedRecipe {
        val result = call(idToken, "extractRecipe", buildJsonObject { put("url", url) })
        return remoteRecipeJson.decodeFromJsonElement(ExtractedRecipe.serializer(), result)
    }

    public suspend fun requestEmailOtp(idToken: String, email: String) {
        call(idToken, "requestEmailOtp", buildJsonObject { put("email", email) })
    }

    public suspend fun verifyEmailOtp(
        idToken: String,
        email: String,
        code: String,
    ): VerifyEmailOtpResult {
        val result = call(
            idToken,
            "verifyEmailOtp",
            buildJsonObject {
                put("email", email)
                put("code", code)
            },
        )
        return remoteRecipeJson.decodeFromJsonElement(VerifyEmailOtpResult.serializer(), result)
    }

    private suspend fun call(idToken: String, name: String, data: JsonObject): JsonElement {
        val region = BasilConfig.FIREBASE_FUNCTIONS_REGION
        val projectId = BasilConfig.FIREBASE_PROJECT_ID
        val response: CallableEnvelope = httpClient.post(
            "https://$region-$projectId.cloudfunctions.net/$name",
        ) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $idToken")
            setBody(CallableRequest(data = data))
        }.body()
        return response.result
    }
}

@Serializable
private data class CallableRequest(val data: JsonObject)

@Serializable
private data class CallableEnvelope(val result: JsonElement)

@Serializable
public data class VerifyEmailOtpResult(
    val customToken: String,
    val alreadyExists: Boolean = false,
    val userId: String? = null,
)
