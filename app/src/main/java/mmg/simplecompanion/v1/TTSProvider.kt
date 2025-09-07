package mmg.simplecompanion.v1

interface TTSProvider {
    fun initialize(onInitialized: () -> Unit)
    fun speak(
        text: String,
        onWordBoundary: (Int, Int) -> Unit,
        onStart: () -> Unit,
        onComplete: () -> Unit
    )

    fun stop()
    fun pause()
    fun resume()
    fun isPlaying(): Boolean
    fun isPaused(): Boolean
    fun release()
}