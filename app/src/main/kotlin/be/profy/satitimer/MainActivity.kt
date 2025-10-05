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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import be.profy.satitimer.ui.theme.SatiTimerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  private var timerService: SatiTimerService? = null
  private var isBound = false

  private val connection =
          object : ServiceConnection {

            override fun onServiceConnected(className: ComponentName, service: IBinder) {
              val binder = service as SatiTimerService.LocalBinder
              timerService = binder.getService()
              isBound = true
            }

            override fun onServiceDisconnected(arg0: ComponentName) {
              timerService = null
              isBound = false
            }
          }

  companion object {
    private const val NOTIFICATION_PERMISSION_REQUEST = 100
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    android.util.Log.d("SatiTimer", "MainActivity onCreate() called")
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

    android.util.Log.d("SatiTimer", "MainActivity onCreate() completed")

    setContent {
      SatiTimerTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          SatiTimerScreen(
                  modifier = Modifier.padding(innerPadding),
                  getTimerService = { timerService }
          )
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    android.util.Log.d("SatiTimer", "MainActivity onStart() called")
    Intent(this, SatiTimerService::class.java).also { intent ->
      bindService(intent, connection, Context.BIND_AUTO_CREATE)
      android.util.Log.d("SatiTimer", "Service bind requested in onStart()")
    }
  }

  override fun onStop() {
    super.onStop()
    android.util.Log.d("SatiTimer", "MainActivity onStop() called")
    // Don't unbind service when going to background if timer is running
    // This prevents the service from being destroyed and losing the notification
    val shouldUnbind =
            timerService?.state?.value?.let { state -> state.timerState == TimerState.STOPPED }
                    ?: true

    if (isBound && shouldUnbind) {
      unbindService(connection)
      isBound = false
      android.util.Log.d("SatiTimer", "Service unbound in onStop() - timer stopped")
    } else if (isBound) {
      android.util.Log.d("SatiTimer", "Service kept bound in onStop() - timer running")
    }
    android.util.Log.d("SatiTimer", "MainActivity onStop() completed")
  }

  override fun onResume() {
    super.onResume()
    android.util.Log.d("SatiTimer", "MainActivity onResume() called")
    // Re-enter immersive mode if timer is running
    timerService?.state?.value?.let { state ->
      if (state.timerState == TimerState.PREPARING || state.timerState == TimerState.RUNNING) {
        enterImmersiveMode()
      }
    }
  }

  override fun onPause() {
    super.onPause()
    android.util.Log.d("SatiTimer", "MainActivity onPause() called")
    // Exit immersive mode when app goes to background
    exitImmersiveMode()
  }

  override fun onDestroy() {
    android.util.Log.d("SatiTimer", "MainActivity onDestroy() called")
    // Always unbind when activity is destroyed
    if (isBound) {
      unbindService(connection)
      isBound = false
      android.util.Log.d("SatiTimer", "Service unbound in onDestroy()")
    }
    super.onDestroy()
  }

  fun enterImmersiveMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.setDecorFitsSystemWindows(false)
      window.insetsController?.let { controller ->
        controller.hide(
                android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
        )
        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    } else {
      @Suppress("DEPRECATION")
      window.decorView.systemUiVisibility =
              (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                      android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                      android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                      android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                      android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                      android.view.View.SYSTEM_UI_FLAG_FULLSCREEN)
    }
  }

  fun exitImmersiveMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.setDecorFitsSystemWindows(true)
      window.insetsController?.show(
              android.view.WindowInsets.Type.statusBars() or
                      android.view.WindowInsets.Type.navigationBars()
      )
    } else {
      @Suppress("DEPRECATION")
      window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
    }
  }
}

@Composable
fun SatiTimerScreen(modifier: Modifier = Modifier, getTimerService: () -> SatiTimerService?) {
  var timeInSeconds by remember { mutableIntStateOf(1200) } // 20 minutes default
  var serviceState by remember { mutableStateOf(TimerServiceState()) }
  var showDurationPicker by remember { mutableStateOf(false) }

  val context = LocalContext.current
  val durationRepository = remember { DurationRepository(context) }
  val recentDurations by
          durationRepository.recentDurations.collectAsState(initial = listOf(60, 1200, 1800))
  val coroutineScope = rememberCoroutineScope()

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
                      text = "Get ready\n${serviceState.preparationTime}",
                      style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                      fontWeight = FontWeight.Light,
                      color = Color(0xFFE3C170),
                      textAlign = TextAlign.Center
              )
            } else {
              // Show selected time when stopped, otherwise show remaining time
              val displayTime =
                      if (serviceState.timerState == TimerState.STOPPED) {
                        timeInSeconds
                      } else {
                        serviceState.remainingTime
                      }
              Text(
                      text = formatTime(displayTime),
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
            // Recent duration buttons (MRU queue)
            recentDurations.take(3).forEach { duration ->
              TimeButton(
                      text = "${duration / 60} min",
                      isSelected = timeInSeconds == duration,
                      onClick = {
                        timeInSeconds = duration
                        // Duration selection only - MRU update happens when Start is pressed
                      }
              )
            }

            // Add icon
            IconButton(onClick = { showDurationPicker = true }) {
              Icon(
                      imageVector = Icons.Default.AddCircleOutline,
                      contentDescription = "Add custom duration",
                      modifier = Modifier.size(32.dp),
                      tint = Color(0xFFE3C170)
              )
            }
          }
        }
      }

      // Duration Picker Dialog
      if (showDurationPicker) {
        DurationPickerDialog(
                currentDurations = recentDurations,
                onDurationSelected = { durationMinutes ->
                  val durationSeconds = durationMinutes * 60
                  timeInSeconds = durationSeconds
                  // For new custom durations, immediately add to MRU queue
                  coroutineScope.launch { durationRepository.addRecentDuration(durationSeconds) }
                  showDurationPicker = false
                },
                onDismiss = { showDurationPicker = false }
        )
      }

      Spacer(modifier = Modifier.height(32.dp))

      // Control Button - Circular design to match theme
      Surface(
              onClick = {
                val service = getTimerService()
                when (serviceState.timerState) {
                  TimerState.STOPPED -> {
                    // Add selected duration to MRU queue when actually starting meditation
                    coroutineScope.launch { durationRepository.addRecentDuration(timeInSeconds) }

                    // Start the service if not running, or call startTimer if already running
                    if (service == null) {
                      val intent =
                              Intent(context, SatiTimerService::class.java).apply {
                                action = SatiTimerService.ACTION_START_TIMER
                                putExtra(SatiTimerService.EXTRA_DURATION, timeInSeconds)
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
                  modifier = Modifier.size(32.dp),
                  tint = Color(0xFF1A120B)
          )
        }
      }

      Spacer(modifier = Modifier.height(48.dp))
    }
  }
}

@Composable
fun TimeButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
  Button(
          onClick = onClick,
          modifier = Modifier.width(88.dp),
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
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
    )
  }
}

@Composable
fun DurationPickerDialog(
        currentDurations: List<Int>,
        onDurationSelected: (Int) -> Unit,
        onDismiss: () -> Unit
) {
  var selectedDuration by remember { mutableIntStateOf(20) }

  AlertDialog(
          onDismissRequest = onDismiss,
          containerColor = Color(0xFF8B5E2D),
          title = {
            Text(
                    text = "Select Duration",
                    color = Color(0xFFE3C170),
                    style = MaterialTheme.typography.headlineSmall
            )
          },
          text = {
            Column {
              Text(
                      text = "Choose meditation duration:",
                      color = Color(0xFFE3C170),
                      style = MaterialTheme.typography.bodyMedium
              )

              Spacer(modifier = Modifier.height(16.dp))

              // Segmented duration picker - exclude current durations
              val allDurations = (10..90 step 5).toList()
              val currentDurationMinutes = currentDurations.map { it / 60 }
              val availableDurations = allDurations.filter { it !in currentDurationMinutes }

              LazyRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      modifier = Modifier.fillMaxWidth()
              ) {
                items(availableDurations) { duration ->
                  Surface(
                          onClick = { selectedDuration = duration },
                          modifier = Modifier.width(56.dp).height(40.dp),
                          shape = RoundedCornerShape(8.dp),
                          color =
                                  if (selectedDuration == duration) {
                                    Color(0xFFE7C469)
                                  } else {
                                    Color(0xFF6B4423)
                                  }
                  ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                      Text(
                              text = "$duration",
                              color =
                                      if (selectedDuration == duration) {
                                        Color(0xFF1A120B)
                                      } else {
                                        Color(0xFFE3C170)
                                      },
                              style = MaterialTheme.typography.bodySmall,
                              fontWeight =
                                      if (selectedDuration == duration) FontWeight.Bold
                                      else FontWeight.Normal
                      )
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              Text(
                      text = "Selected: $selectedDuration minutes",
                      color = Color(0xFFE3C170),
                      style = MaterialTheme.typography.bodySmall
              )
            }
          },
          confirmButton = {
            TextButton(onClick = { onDurationSelected(selectedDuration) }) {
              Text(text = "Add", color = Color(0xFFE7C469), fontWeight = FontWeight.Bold)
            }
          },
          dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel", color = Color(0xFFE3C170)) }
          }
  )
}

fun formatTime(seconds: Int): String {
  val minutes = seconds / 60
  val remainingSeconds = seconds % 60
  return "%02d:%02d".format(minutes, remainingSeconds)
}

@Preview(showBackground = true)
@Composable
fun SatiTimerPreview() {
  SatiTimerTheme { SatiTimerScreen(getTimerService = { null }) }
}
