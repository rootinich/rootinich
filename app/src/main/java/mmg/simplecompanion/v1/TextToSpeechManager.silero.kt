package mmg.simplecompanion.v1

import android.media.MediaPlayer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class SileroTTSProvider : TTSProvider {
    companion object {
        val client get() = HttpClient(Android) { }
    }
    suspend fun getSound(text: String) {
        val file = File.createTempFile("files", "index")
        lateinit var response: HttpResponse
        try {
            response = client.get("http://212.8.227.42:5010/getwav?text_to_speech=$text&speaker=xenia&sample_rate=48000&put_accent=1&put_yo=1")
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val responseBody: ByteArray = response.body()
        file.writeBytes(responseBody)
        println("A file saved to ${file.path}")
        println(response.status)
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setDataSource(file.path) // Replace with actual path
            mediaPlayer.prepare() // Prepare the media for playback
            mediaPlayer.start() // Start playback
        } catch (e: IOException) {
            e.printStackTrace()
        }
        client.close()
    }

    override fun initialize(onInitialized: () -> Unit) {
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun speak(
        text: String,
        onWordBoundary: (Int, Int) -> Unit,
        onStart: () -> Unit,
        onComplete: () -> Unit
    ) {
        GlobalScope.launch {
            getSound("<speak>$text</speak>")
        }

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