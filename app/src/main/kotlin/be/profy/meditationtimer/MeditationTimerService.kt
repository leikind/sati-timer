/*
 * Sati Timer - A mindful meditation timing app
 * Copyright (c) 2025 Yuri Leikind
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package be.profy.meditationtimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TimerServiceState(
        val timerState: TimerState = TimerState.STOPPED,
        val remainingTime: Int = 1200,
        val preparationTime: Int = 10,
        val totalTime: Int = 1200
)

class MeditationTimerService : Service() {

  companion object {
    const val NOTIFICATION_CHANNEL_ID = "meditation_timer_channel"
    const val NOTIFICATION_ID = 1

    const val ACTION_START_TIMER = "START_TIMER"
    const val ACTION_STOP_TIMER = "STOP_TIMER"
    const val ACTION_PAUSE_TIMER = "PAUSE_TIMER"
    const val ACTION_RESUME_TIMER = "RESUME_TIMER"

    const val EXTRA_DURATION = "duration"

    // Intent actions for broadcasts
    const val ACTION_TIMER_UPDATE = "com.example.meditationtimer.TIMER_UPDATE"
    const val EXTRA_TIMER_STATE = "timer_state"
    const val EXTRA_REMAINING_TIME = "remaining_time"
    const val EXTRA_PREPARATION_TIME = "preparation_time"
    const val EXTRA_TOTAL_TIME = "total_time"
  }

  private val binder = LocalBinder()
  private var mediaPlayer: MediaPlayer? = null
  private var wakeLock: PowerManager.WakeLock? = null
  private var audioManager: AudioManager? = null
  private var audioFocusRequest: AudioFocusRequest? = null

  private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
  private var timerJob: Job? = null

  // Current timer state using StateFlow
  private val _state = MutableStateFlow(TimerServiceState())
  val state: StateFlow<TimerServiceState> = _state.asStateFlow()

  // Convenience properties
  private var currentState: TimerState
    get() = _state.value.timerState
    set(value) {
      _state.value = _state.value.copy(timerState = value)
    }

  private var remainingTime: Int
    get() = _state.value.remainingTime
    set(value) {
      _state.value = _state.value.copy(remainingTime = value)
    }

  private var preparationTime: Int
    get() = _state.value.preparationTime
    set(value) {
      _state.value = _state.value.copy(preparationTime = value)
    }

  private var totalTime: Int
    get() = _state.value.totalTime
    set(value) {
      _state.value = _state.value.copy(totalTime = value)
    }

  inner class LocalBinder : Binder() {
    fun getService(): MeditationTimerService = this@MeditationTimerService
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    initializeMediaPlayer()
    acquireWakeLock()
    setupAudioManager()
  }

  override fun onBind(intent: Intent): IBinder = binder

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    android.util.Log.d(
            "MeditationTimer",
            "Service onStartCommand called with action: ${intent?.action}"
    )
    when (intent?.action) {
      ACTION_START_TIMER -> {
        val duration = intent.getIntExtra(EXTRA_DURATION, 1200)
        android.util.Log.d("MeditationTimer", "Starting timer for $duration seconds")
        startTimer(duration)
      }
      ACTION_STOP_TIMER -> {
        android.util.Log.d("MeditationTimer", "Stopping timer")
        stopTimer()
      }
      ACTION_PAUSE_TIMER -> pauseTimer()
      ACTION_RESUME_TIMER -> resumeTimer()
    }
    return START_NOT_STICKY
  }

  private fun createNotificationChannel() {
    val channel =
            NotificationChannel(
                            NOTIFICATION_CHANNEL_ID,
                            getString(R.string.notification_channel_name),
                            NotificationManager.IMPORTANCE_LOW
                    )
                    .apply {
                      description = getString(R.string.notification_channel_description)
                      setSound(null, null)
                    }

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
  }

  private fun initializeMediaPlayer() {
    try {
      mediaPlayer = MediaPlayer.create(this, R.raw.gong)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun acquireWakeLock() {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock =
            powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MeditationTimer:TimerWakeLock"
            )
  }

  private fun setupAudioManager() {
    audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest =
              AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                      .setAudioAttributes(
                              AudioAttributes.Builder()
                                      .setUsage(AudioAttributes.USAGE_MEDIA)
                                      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                      .build()
                      )
                      .setAcceptsDelayedFocusGain(true)
                      .setOnAudioFocusChangeListener { focusChange ->
                        android.util.Log.d("MeditationTimer", "Audio focus changed: $focusChange")
                        // We don't need to handle focus changes - we just want to take focus
                        // to pause other apps during meditation
                      }
                      .build()
    }
  }

  private fun requestAudioFocus(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest?.let { request ->
        val result = audioManager?.requestAudioFocus(request)
        result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
      }
              ?: false
    } else {
      @Suppress("DEPRECATION")
      val result =
              audioManager?.requestAudioFocus(
                      { focusChange ->
                        android.util.Log.d("MeditationTimer", "Audio focus changed: $focusChange")
                      },
                      AudioManager.STREAM_MUSIC,
                      AudioManager.AUDIOFOCUS_GAIN
              )
      result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
  }

  private fun releaseAudioFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest?.let { request -> audioManager?.abandonAudioFocusRequest(request) }
    } else {
      @Suppress("DEPRECATION")
      audioManager?.abandonAudioFocus { focusChange ->
        android.util.Log.d("MeditationTimer", "Audio focus abandoned: $focusChange")
      }
    }
  }

  fun startTimer(durationSeconds: Int) {
    android.util.Log.d("MeditationTimer", "startTimer called with duration: $durationSeconds")
    _state.value =
            TimerServiceState(
                    timerState = TimerState.PREPARING,
                    totalTime = durationSeconds,
                    remainingTime = durationSeconds,
                    preparationTime = 10
            )

    wakeLock?.acquire(((durationSeconds + 15) * 1000).toLong())

    // Request audio focus to pause other apps
    val audioFocusGranted = requestAudioFocus()
    android.util.Log.d("MeditationTimer", "Audio focus granted: $audioFocusGranted")

    startForeground(NOTIFICATION_ID, createNotification())
    android.util.Log.d("MeditationTimer", "Foreground service started")
    startTimerCountdown()
  }

  fun stopTimer() {
    timerJob?.cancel()
    _state.value =
            _state.value.copy(
                    timerState = TimerState.STOPPED,
                    remainingTime = _state.value.totalTime,
                    preparationTime = 10
            )

    wakeLock?.release()
    releaseAudioFocus()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  fun pauseTimer() {
    if (currentState == TimerState.RUNNING) {
      timerJob?.cancel()
      _state.value = _state.value.copy(timerState = TimerState.PAUSED)
      updateNotification()
    }
  }

  fun resumeTimer() {
    if (currentState == TimerState.PAUSED) {
      _state.value = _state.value.copy(timerState = TimerState.RUNNING)
      startTimerCountdown()
      updateNotification()
    }
  }

  private fun startTimerCountdown() {
    android.util.Log.d("MeditationTimer", "startTimerCountdown called")
    timerJob =
            serviceScope.launch {
              while (true) {
                when (_state.value.timerState) {
                  TimerState.PREPARING -> {
                    if (_state.value.preparationTime > 0) {
                      delay(1000L)
                      _state.value =
                              _state.value.copy(preparationTime = _state.value.preparationTime - 1)
                      updateNotification()
                    } else {
                      // Preparation finished - play gong and start main timer
                      playGong()
                      _state.value = _state.value.copy(timerState = TimerState.RUNNING)
                      updateNotification()
                    }
                  }
                  TimerState.RUNNING -> {
                    if (_state.value.remainingTime > 0) {
                      delay(1000L)
                      _state.value =
                              _state.value.copy(remainingTime = _state.value.remainingTime - 1)
                      updateNotification()
                    } else {
                      // Timer finished - play gong and stop
                      playGong()
                      delay(2000L) // Give time for gong to play
                      stopTimer()
                      break
                    }
                  }
                  else -> break // STOPPED or PAUSED
                }
              }
            }
  }

  private fun playGong() {
    try {
      mediaPlayer?.let { player ->
        if (player.isPlaying) {
          player.stop()
          player.prepare()
        }
        player.start()
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun createNotification(): Notification {

    val notificationIntent = Intent(this, MainActivity::class.java)
    val pendingIntent =
            PendingIntent.getActivity(
                    this,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

    val stopIntent =
            Intent(this, MeditationTimerService::class.java).apply { action = ACTION_STOP_TIMER }
    val stopPendingIntent =
            PendingIntent.getService(
                    this,
                    0,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

    val state = _state.value
    val contentText =
            when (state.timerState) {
              TimerState.PREPARING ->
                      getString(R.string.notification_preparing, state.preparationTime)
              TimerState.RUNNING ->
                      getString(R.string.notification_meditating, formatTime(state.remainingTime))
              TimerState.PAUSED ->
                      getString(R.string.notification_paused, formatTime(state.remainingTime))
              TimerState.STOPPED -> getString(R.string.notification_complete)
            }

    return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.meditation_timer))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(
                    android.R.drawable.ic_media_pause,
                    getString(R.string.stop),
                    stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
  }

  private fun updateNotification() {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(NOTIFICATION_ID, createNotification())
  }

  private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
  }

  override fun onDestroy() {
    super.onDestroy()
    timerJob?.cancel()
    mediaPlayer?.release()
    wakeLock?.release()
    releaseAudioFocus()
  }
}
