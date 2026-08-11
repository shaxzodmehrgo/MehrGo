package uz.teamwork.mehrgodriver.common.services.instroction

import android.app.Service
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.IBinder
import timber.log.Timber

class VoiceService : Service() {
    private val soundFileName = ArrayList<String>()
    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(p0: Intent?): IBinder? = null


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mediaPlayer?.release()
        mediaPlayer = null
        count = 0
        intent?.getStringArrayListExtra("voice")?.let {
            Timber.d("Sounds " + it.joinToString())
            soundFileName.clear()
            soundFileName.addAll(it)
            runPlayer()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        count = 0
    }

    private var count = 0
    private fun runPlayer() {
        try {
            mediaPlayer = MediaPlayer().apply {
                isLooping = false
                val descriptor: AssetFileDescriptor = assets.openFd(soundFileName[count])
                setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                descriptor.close()
                prepare()
                val playbackParams = PlaybackParams()
                playbackParams.speed = 1.25f
                playbackParams.pitch = 1f
                playbackParams.audioFallbackMode = PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT
                setPlaybackParams(playbackParams)

                setOnCompletionListener {
                    if (count == soundFileName.lastIndex) {
                        count = 0
                        stop()
                        soundFileName.clear()
                        stopSelf()
                    } else {
                        count++
                        runPlayer()
                    }
                }
                start()
            }
        } catch (e: Exception) {
            Timber.d("Error " + e.localizedMessage)
        }
    }
}
