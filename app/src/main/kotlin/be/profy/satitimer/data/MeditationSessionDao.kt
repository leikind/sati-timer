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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeditationSessionDao {

  @Insert suspend fun insertSession(session: MeditationSession)

  @Query("SELECT * FROM meditation_sessions ORDER BY startTimestamp DESC")
  fun getAllSessions(): Flow<List<MeditationSession>>

  @Query("SELECT SUM(durationSeconds) FROM meditation_sessions")
  suspend fun getTotalSecondsMediated(): Int?

  @Query("SELECT * FROM meditation_sessions ORDER BY startTimestamp DESC LIMIT :limit")
  suspend fun getRecentSessions(limit: Int): List<MeditationSession>

  @Query("SELECT COUNT(*) FROM meditation_sessions") suspend fun getSessionCount(): Int

  @Query("DELETE FROM meditation_sessions") suspend fun deleteAllSessions()
}
