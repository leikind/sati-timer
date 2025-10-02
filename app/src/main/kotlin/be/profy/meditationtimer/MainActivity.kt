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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import be.profy.meditationtimer.ui.theme.MeditationTimerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

  companion object {
    private const val NOTIFICATION_PERMISSION_REQUEST = 100
  }

  private var timerService: MeditationTimerService? = null
  private var isBound = false

  private val connection =
          object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
              val binder = service as MeditationTimerService.LocalBinder
              timerService = binder.getService()
              isBound = true
            }

            override fun onServiceDisconnected(arg0: ComponentName) {
              timerService = null
              isBound = false
            }
          }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Request notification permission for Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                      PackageManager.PERMISSION_GRANTED
      ) {
        ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
        )
      }
    }

    setContent {
      MeditationTimerTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          MeditationTimerScreen(
                  modifier = Modifier.padding(innerPadding),
                  getTimerService = { timerService }
          )
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    Intent(this, MeditationTimerService::class.java).also { intent ->
      bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
  }

  override fun onStop() {
    super.onStop()
    if (isBound) {
      unbindService(connection)
      isBound = false
    }
  }

  override fun onPause() {
    super.onPause()
    // Exit immersive mode when app goes to background
    exitImmersiveMode()
  }

  override fun onResume() {
    super.onResume()
    // Re-enter immersive mode if timer is running
    timerService?.state?.value?.let { state ->
      if (state.timerState == TimerState.PREPARING || state.timerState == TimerState.RUNNING) {
        enterImmersiveMode()
      }
    }
  }

  fun enterImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.insetsController?.let { controller ->
      controller.hide(WindowInsetsCompat.Type.statusBars())
      controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }

  fun exitImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, true)
    window.insetsController?.show(WindowInsetsCompat.Type.statusBars())
  }
}

enum class TimerState {
  STOPPED,
  PREPARING,
  RUNNING,
  PAUSED
}

@Composable
fun MeditationTimerScreen(
        modifier: Modifier = Modifier,
        getTimerService: () -> MeditationTimerService?
) {
  var timeInSeconds by remember { mutableIntStateOf(1200) } // 20 minutes default
  var serviceState by remember { mutableStateOf(TimerServiceState()) }

  val context = LocalContext.current

  // Observe service state
  LaunchedEffect(Unit) {
    while (true) {
      val service = getTimerService()
      if (service != null) {
        // Collect state from service
        service.state.collect { state ->
          serviceState = state
          // Handle immersive mode based on timer state
          val activity = context as? MainActivity
          activity?.let {
            when (state.timerState) {
              TimerState.PREPARING, TimerState.RUNNING -> it.enterImmersiveMode()
              TimerState.STOPPED -> it.exitImmersiveMode()
              else -> {
                /* Keep current mode for PAUSED */
              }
            }
          }
        }
        break // Exit loop once we have service and start collecting
      } else {
        delay(100L) // Wait for service to be bound
      }
    }
  }

  Surface(modifier = modifier.fillMaxSize(), color = Color(0xFF1A120B)) {
    Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
      Spacer(modifier = Modifier.height(48.dp))

      // Timer Display Card with Progress
      Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        // Progress visualization (only during meditation)
        if (serviceState.timerState == TimerState.RUNNING) {
          val progress =
                  if (serviceState.totalTime > 0) {
                    1f - (serviceState.remainingTime.toFloat() / serviceState.totalTime.toFloat())
                  } else 0f
          Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2

            // Background circle
            drawCircle(
                    color = Color(0xFF8B5E2D).copy(alpha = 0.3f),
                    radius = radius,
                    style = Stroke(width = strokeWidth)
            )

            // Progress arc
            if (progress > 0f) {
              drawArc(
                      color = Color(0xFFF2A93B),
                      startAngle = -90f, // Start from top
                      sweepAngle = progress * 360f,
                      useCenter = false,
                      style = Stroke(width = strokeWidth)
              )
            }
          }
        }

        // Inner card
        Card(
                modifier = Modifier.size(240.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = Color(0xFF8B5E2D)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (serviceState.timerState == TimerState.PREPARING) {
              Text(
                      text = context.getString(R.string.get_ready, serviceState.preparationTime),
                      style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                      fontWeight = FontWeight.Light,
                      color = Color(0xFFE3C170)
              )
            } else {
              Text(
                      text = formatTime(serviceState.remainingTime),
                      style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                      fontWeight = FontWeight.Light,
                      color = Color(0xFFE3C170)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(48.dp))

      // Time Selection Buttons - reserve space always
      Box(modifier = Modifier.height(80.dp), contentAlignment = Alignment.Center) {
        if (serviceState.timerState == TimerState.STOPPED) {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeButton(
                    text = context.getString(R.string.one_minute),
                    isSelected = timeInSeconds == 60,
                    onClick = { timeInSeconds = 60 }
            )
            TimeButton(
                    text = context.getString(R.string.twenty_minutes),
                    isSelected = timeInSeconds == 1200,
                    onClick = { timeInSeconds = 1200 }
            )
            TimeButton(
                    text = context.getString(R.string.thirty_minutes),
                    isSelected = timeInSeconds == 1800,
                    onClick = { timeInSeconds = 1800 }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(32.dp))

      // Control Button - Circular design to match theme
      Surface(
              onClick = {
                val service = getTimerService()
                when (serviceState.timerState) {
                  TimerState.STOPPED -> {
                    // Start the service if not running, or call startTimer if already running
                    if (service == null) {
                      val intent =
                              Intent(context, MeditationTimerService::class.java).apply {
                                action = MeditationTimerService.ACTION_START_TIMER
                                putExtra(MeditationTimerService.EXTRA_DURATION, timeInSeconds)
                              }
                      context.startForegroundService(intent)
                    } else {
                      service.startTimer(timeInSeconds)
                    }
                  }
                  TimerState.PREPARING, TimerState.RUNNING -> {
                    service?.stopTimer()
                  }
                  TimerState.PAUSED -> {
                    service?.resumeTimer()
                  }
                }
              },
              modifier = Modifier.size(80.dp),
              shape = CircleShape,
              color = Color(0xFFE7C469),
              shadowElevation = 8.dp
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Icon(
                  imageVector =
                          if (serviceState.timerState == TimerState.RUNNING ||
                                          serviceState.timerState == TimerState.PREPARING
                          ) {
                            Icons.Default.Stop
                          } else {
                            Icons.Default.PlayArrow
                          },
                  contentDescription =
                          if (serviceState.timerState == TimerState.RUNNING ||
                                          serviceState.timerState == TimerState.PREPARING
                          )
                                  context.getString(R.string.stop)
                          else context.getString(R.string.start),
                  modifier = Modifier.size(40.dp),
                  tint = Color(0xFF1A120B)
          )
        }
      }
    }
  }
}

@Composable
fun TimeButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
  Button(
          onClick = onClick,
          modifier = Modifier.width(72.dp),
          colors =
                  if (isSelected) {
                    ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE7C469),
                            contentColor = Color(0xFF1A120B)
                    )
                  } else {
                    ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5E2D),
                            contentColor = Color(0xFFE3C170)
                    )
                  }
  ) {
    Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
    )
  }
}

fun formatTime(seconds: Int): String {
  val minutes = seconds / 60
  val remainingSeconds = seconds % 60
  return "%02d:%02d".format(minutes, remainingSeconds)
}

@Preview(showBackground = true)
@Composable
fun MeditationTimerPreview() {
  MeditationTimerTheme { Surface { Text("Preview not available - requires service") } }
}
