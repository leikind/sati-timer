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

package be.profy.satitimer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeditationRepository(private val context: Context) {

  private val database = MeditationDatabase.getDatabase(context)
  private val sessionDao = database.meditationSessionDao()
  private val durationRepository = be.profy.satitimer.DurationRepository(context)
  private val sharedPrefs: SharedPreferences =
          context.getSharedPreferences("meditation_session", Context.MODE_PRIVATE)

  // Session tracking - stored in SharedPreferences to persist across instances
  companion object {
    private const val KEY_SESSION_START_TIME = "session_start_time"
  }

  // Total time flow for reactive UI updates
  private val _totalMinutesMeditated = MutableStateFlow(0)
  val totalMinutesMeditated: Flow<Int> = _totalMinutesMeditated.asStateFlow()

  /** Start a new meditation session - stores start time in SharedPreferences */
  fun startSession(durationSeconds: Int) {
    val startTime = System.currentTimeMillis()
    sharedPrefs.edit().putLong(KEY_SESSION_START_TIME, startTime).apply()
    android.util.Log.d(
            "MeditationRepository",
            "Session started: $durationSeconds seconds planned, startTime: $startTime"
    )
  }

  /**
   * Complete the current meditation session and save to database Called when the final gong sounds
   */
  suspend fun completeSession(actualDurationSeconds: Int) {
    val startTime = sharedPrefs.getLong(KEY_SESSION_START_TIME, 0L)
    if (startTime > 0) {
      val endTime = System.currentTimeMillis()

      val session =
              MeditationSession(
                      durationSeconds = actualDurationSeconds,
                      startTimestamp = startTime,
                      endTimestamp = endTime
              )

      sessionDao.insertSession(session)
      android.util.Log.d(
              "MeditationRepository",
              "Session completed and saved: $actualDurationSeconds seconds, startTime: $startTime"
      )
      sharedPrefs.edit().remove(KEY_SESSION_START_TIME).apply() // Clear the stored start time

      // Update total time flow on main thread
      val newTotal = getTotalMinutesMeditated()
      android.util.Log.d("MeditationRepository", "Updated total time: $newTotal minutes")
      CoroutineScope(Dispatchers.Main).launch {
        _totalMinutesMeditated.value = newTotal
        android.util.Log.d("MeditationRepository", "StateFlow updated on main thread: $newTotal")
      }
    }
  }

  /** Get total minutes meditated across all completed sessions */
  suspend fun getTotalMinutesMeditated(): Int {
    val totalSeconds = sessionDao.getTotalSecondsMediated() ?: 0
    return totalSeconds / 60
  }

  /** Initialize total minutes - call this on app start */
  suspend fun initializeTotalTime() {
    val initialTotal = getTotalMinutesMeditated()
    android.util.Log.d("MeditationRepository", "Initialized total time: $initialTotal minutes")
    CoroutineScope(Dispatchers.Main).launch {
      _totalMinutesMeditated.value = initialTotal
      android.util.Log.d(
              "MeditationRepository",
              "Initial StateFlow updated on main thread: $initialTotal"
      )
    }
  }

  /** Get all meditation sessions ordered by most recent first */
  fun getAllSessions(): Flow<List<MeditationSession>> {
    return sessionDao.getAllSessions()
  }

  /** Get recent meditation sessions */
  suspend fun getRecentSessions(limit: Int = 10): List<MeditationSession> {
    return sessionDao.getRecentSessions(limit)
  }

  /** Get total number of meditation sessions completed */
  suspend fun getSessionCount(): Int {
    return sessionDao.getSessionCount()
  }

  // Duration management (delegate to existing DurationRepository)
  val recentDurations: Flow<List<Int>> = durationRepository.recentDurations

  suspend fun addRecentDuration(durationSeconds: Int) {
    durationRepository.addRecentDuration(durationSeconds)
  }

  suspend fun resetDurationsToDefaults() {
    durationRepository.resetToDefaults()
  }

  /** Reset all meditation data - delete all sessions and reset total time */
  suspend fun resetAllMeditationData() {
    sessionDao.deleteAllSessions()
    android.util.Log.d("MeditationRepository", "All meditation sessions deleted")

    // Update total time flow to 0 on main thread
    CoroutineScope(Dispatchers.Main).launch {
      _totalMinutesMeditated.value = 0
      android.util.Log.d("MeditationRepository", "Total time reset to 0")
    }
  }
}
