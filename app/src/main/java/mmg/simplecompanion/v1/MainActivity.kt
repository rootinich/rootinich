package mmg.simplecompanion.v1

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.ToggleButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.SpeechStreamService
import org.vosk.android.StorageService
import java.io.IOException

val APP_NAME = listOf("ВЕРТЕР","ВЕРТЕ")
const val PLUS = "%2B"
val NEWS = listOf("НОВОСТИ","НОВОСТь")
val SPEECH_APP = listOf(
    "Да, слушаю Вас внимательно, говорите...",
    "Можете уже говорить...", "Ну говорите уже что-нибудь...",
    "Если Вы готовы, то можете сказать")

class MainActivity: Activity(), RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var speechStreamService: SpeechStreamService? = null
    private var resultView: TextView? = null
    private val _isInitialized = MutableStateFlow(false)
    private var ttsManager = getTTSProvider()

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.main)
        setActivityProvider { this }
        ttsManager = getTTSProvider()
        ttsManager.initialize {
            _isInitialized.value = true
        }
        // Setup layout
        resultView = findViewById(R.id.result_text)
        setUiState(STATE_START)

        findViewById<View?>(R.id.recognize_mic).setOnClickListener { view: View? -> recognizeMicrophone() }
        (findViewById<View?>(R.id.pause) as ToggleButton).setOnCheckedChangeListener { view: CompoundButton?, isChecked: Boolean ->
            pause(
                isChecked
            )
        }

        LibVosk.setLogLevel(LogLevel.INFO)

        // Check if user has given permission to record audio, init the model after permission is granted
        val permissionCheck = ContextCompat.checkSelfPermission(
            getApplicationContext(),
            Manifest.permission.RECORD_AUDIO
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(Manifest.permission.RECORD_AUDIO),
                PERMISSIONS_REQUEST_RECORD_AUDIO
            )
        } else {
            initModel()
        }
    }

    private fun initModel() {
        StorageService.unpack(
            this, "model-ru-ru", "model",
            { model: Model ->
                this.model = model
                setUiState(STATE_READY)
            },
            { exception: IOException? -> setErrorState("Failed to unpack the model" + exception!!.message) })
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                initModel()
            } else {
                finish()
            }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()

        if (speechService != null) {
            speechService!!.stop()
            speechService!!.shutdown()
        }

        if (speechStreamService != null) {
            speechStreamService!!.stop()
        }
    }

    enum class TTSState {
        IDLE, PLAYING, PAUSED
    }
    private val _currentWordRange = MutableStateFlow(-1..-1)
    private val _ttsState = MutableStateFlow(TTSState.IDLE)
    private var isWork: Boolean = false
    override fun onResult(hypothesis: String?) {
            val afterTextKey = hypothesis?.substringAfter("\"text\" : \"")
            // Extract the part before the closing double quote
            var extractedValue = afterTextKey?.substringBefore("\"")
            if (extractedValue != null && extractedValue.isNotEmpty()) {
                resultView!!.append(extractedValue + "\n")
                if (!isWork && APP_NAME.any {extractedValue.contains(it, ignoreCase = true)}) {
                    val randomSpeech = SPEECH_APP.random()
                    speak(randomSpeech, 5000)
                    isWork = true
                } else if (isWork) {
                    if (NEWS.any {extractedValue.contains(it, ignoreCase = true)})
                        extractedValue = getNews()
                    speak(extractedValue, 4000)
                    isWork = false
                }
            }
        val state = speechService != null
        resultView!!.append("$hypothesis $isWork $state \n")
    }

    private fun speak(text: String, time: Long) {
        stopSpeechService()
        stateDone()
        speak(text)
        Thread.sleep(time)
        startSpeechService()
        stateMic()
    }

    private fun getNews(): String {
        return "Вы может$PLUS+е услыш$PLUS+ать самы$PLUS+е последни$PLUS+е и актуальны$PLUS+е новост$PLUS+и из первоисточник$PLUS+а..."
    }

    fun speak(text: String) {
        // Reset highlight immediately when starting
        _currentWordRange.update {
            -1..-1
        }

        ttsManager.speak(
            text = text,
            onWordBoundary = { wordStart, wordEnd ->
                _currentWordRange.update {
                    wordStart..wordEnd
                }
            },
            onStart = {
                _ttsState.update {
                    TTSState.PLAYING
                }
            },
            onComplete = {
                _ttsState.update {
                    TTSState.IDLE
                }
                _currentWordRange.update {
                    -1..-1
                }
            }
        )
    }

    override fun onFinalResult(hypothesis: String?) {
        resultView!!.append(hypothesis + "\n")
        //setUiState(STATE_DONE)
        if (speechStreamService != null) {
            speechStreamService = null
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        //resultView!!.append(hypothesis + "\n")
    }

    override fun onError(e: Exception) {
        setErrorState(e.message)
    }

    override fun onTimeout() {
        setUiState(STATE_DONE)
    }

    private fun setUiState(state: Int) {
        when (state) {
            STATE_START -> {
                resultView!!.setText(R.string.preparing)
                resultView!!.setMovementMethod(ScrollingMovementMethod())
                findViewById<View?>(R.id.recognize_mic).setEnabled(false)
                findViewById<View?>(R.id.pause).setEnabled((false))
            }

            STATE_READY -> {
                resultView!!.setText(R.string.ready)
                (findViewById<View?>(R.id.recognize_mic) as Button).setText(R.string.recognize_microphone)
                findViewById<View?>(R.id.recognize_mic).setEnabled(true)
                findViewById<View?>(R.id.pause).setEnabled((false))
            }

            STATE_DONE -> {
                stateDone()
            }

            STATE_MIC -> {
                stateMic()
            }

            else -> throw IllegalStateException("Unexpected value: " + state)
        }
    }

    private fun stateDone() {
        (findViewById<View?>(R.id.recognize_mic) as Button).setText(R.string.recognize_microphone)
        findViewById<View?>(R.id.recognize_mic).setEnabled(true)
        findViewById<View?>(R.id.pause).setEnabled((false))
        (findViewById<View?>(R.id.pause) as ToggleButton).setChecked(false)
    }

    private fun stateMic() {
        (findViewById<View?>(R.id.recognize_mic) as Button).setText(R.string.stop_microphone)
        resultView!!.setText(getString(R.string.say_something))
        findViewById<View?>(R.id.recognize_mic).setEnabled(true)
        findViewById<View?>(R.id.pause).setEnabled((true))
    }

    private fun setErrorState(message: String?) {
        resultView!!.setText(message)
        (findViewById<View?>(R.id.recognize_mic) as Button).setText(R.string.recognize_microphone)
        findViewById<View?>(R.id.recognize_mic).setEnabled(false)
    }

    private fun recognizeMicrophone() {
        if (speechService != null) {
            stopSpeechService()
        } else {
            startSpeechService()
        }
    }

    public fun stopSpeechService() {
        setUiState(STATE_DONE)
        speechService!!.stop()
        speechService = null
    }

    private fun startSpeechService() {
        setUiState(STATE_MIC)
        try {
            val rec = Recognizer(model, 48000.0f)
            speechService = SpeechService(rec, 48000.0f)
            speechService!!.startListening(this)
        } catch (e: IOException) {
            setErrorState(e.message)
        }
    }

    private fun pause(checked: Boolean) {
        onResult("Text")
    }

    companion object {
        private const val STATE_START = 0
        private const val STATE_READY = 1
        private const val STATE_DONE = 2
        private const val STATE_MIC = 3

        /* Used to handle permission request */
        private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
    }
}
