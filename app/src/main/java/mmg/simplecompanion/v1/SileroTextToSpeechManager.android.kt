package mmg.simplecompanion.v1

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

import java.io.File

class SileroTTSProvider : TTSProvider {

    // Create a Ktor client instance
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun uploadFile(url: String, file: File, fileName: String, fieldName: String = "file") {
        try {
            val response: HttpResponse = httpClient.submitFormWithBinaryData(
                url = url,
                formData = formData {
                    append(fieldName, file.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, "application/octet-stream") // Or the actual file type
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    })
                    // You can add other form fields here if needed
                    // append("description", "This is a test file upload")
                }
            )

            if (response.status.isSuccess()) {
                println("File uploaded successfully! Response: ${response.body<String>()}")
            } else {
                println("File upload failed with status: ${response.status}. Response: ${response.body<String>()}")
            }
        } catch (e: Exception) {
            println("Error during file upload: ${e.message}")
        } finally {
            httpClient.close() // Close the client when done
        }
    }


    override fun initialize(onInitialized: () -> Unit) {

    }

    override fun speak(
        text: String,
        onWordBoundary: (Int, Int) -> Unit,
        onStart: () -> Unit,
        onComplete: () -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun pause() {
        TODO("Not yet implemented")
    }

    override fun resume() {
        TODO("Not yet implemented")
    }

    override fun isPlaying(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isPaused(): Boolean {
        TODO("Not yet implemented")
    }

    override fun release() {
        TODO("Not yet implemented")
    }

}