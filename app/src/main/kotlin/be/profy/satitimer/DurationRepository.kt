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

package be.profy.satitimer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.durationDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "duration_settings")

class DurationRepository(private val context: Context) {

  companion object {
    private val RECENT_DURATIONS_KEY = stringPreferencesKey("recent_durations")
    private const val DEFAULT_DURATIONS = "60,1200,1800" // 1 min, 20 min, 30 min in seconds
    private const val MAX_RECENT_COUNT = 3
  }

  /**
   * Get the recent durations as a Flow of List<Int> (durations in seconds) Most recently used
   * duration is first in the list
   */
  val recentDurations: Flow<List<Int>> =
          context.durationDataStore.data.map { preferences ->
            val durationsString = preferences[RECENT_DURATIONS_KEY] ?: DEFAULT_DURATIONS
            parseDurations(durationsString)
          }

  /**
   * Add a duration to the MRU queue
   * - New duration becomes the first (leftmost) button
   * - If duration already exists, it moves to front
   * - Only keep the 3 most recent durations
   */
  suspend fun addRecentDuration(durationSeconds: Int) {
    context.durationDataStore.edit { preferences ->
      val currentDurationsString = preferences[RECENT_DURATIONS_KEY] ?: DEFAULT_DURATIONS
      val currentDurations = parseDurations(currentDurationsString)

      // Create new MRU list: new duration first, then others (excluding duplicates)
      val updatedDurations = buildList {
        add(durationSeconds) // New duration goes first
        addAll(
                currentDurations
                        .filter { it != durationSeconds } // Remove duplicates
                        .take(MAX_RECENT_COUNT - 1) // Keep only 2 more (total = 3)
        )
      }

      preferences[RECENT_DURATIONS_KEY] = updatedDurations.joinToString(",")
    }
  }

  /** Reset to default durations (1 min, 20 min, 30 min) */
  suspend fun resetToDefaults() {
    context.durationDataStore.edit { preferences ->
      preferences[RECENT_DURATIONS_KEY] = DEFAULT_DURATIONS
    }
  }

  /** Parse comma-separated duration string into list of integers */
  private fun parseDurations(durationsString: String): List<Int> {
    return try {
      durationsString.split(",").map { it.trim().toInt() }.filter {
        it > 0
      } // Only positive durations
    } catch (e: Exception) {
      // If parsing fails, return defaults
      listOf(60, 1200, 1800)
    }
  }
}
