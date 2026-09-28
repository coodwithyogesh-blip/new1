package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.local.AppDatabase
import com.example.data.local.TaskRepository
import com.example.engine.DeviceActionExecutor
import com.example.engine.GeminiBrainService
import com.example.engine.UniversalTaskManager
import com.example.engine.VoiceManager
import com.example.service.ArushiAssistantService
import com.example.ui.ArushiScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var taskManager: UniversalTaskManager
    private lateinit var taskRepository: TaskRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestRequiredPermissions()

        val app = application as ArushiApplication
        val database = app.database
        taskRepository = TaskRepository(database.taskDao())

        voiceManager = VoiceManager(this, lifecycleScope)
        val actionExecutor = DeviceActionExecutor(this)
        val brainService = GeminiBrainService(this)
        taskManager = UniversalTaskManager(
            context = this,
            taskRepository = taskRepository,
            brainService = brainService,
            actionExecutor = actionExecutor,
            voiceManager = voiceManager,
            scope = lifecycleScope
        )

        val prefs = getSharedPreferences("arushi_prefs", Context.MODE_PRIVATE)

        setContent {
            MyApplicationTheme {
                var isBackgroundActive by remember {
                    mutableStateOf(prefs.getBoolean("bg_service_enabled", false))
                }
                var customApiKey by remember {
                    mutableStateOf(prefs.getString("custom_gemini_key", "") ?: "")
                }

                ArushiScreen(
                    taskManager = taskManager,
                    voiceManager = voiceManager,
                    allTasksFlow = taskRepository.allTasks,
                    isBackgroundServiceActive = isBackgroundActive,
                    onToggleBackgroundService = { enabled ->
                        isBackgroundActive = enabled
                        prefs.edit().putBoolean("bg_service_enabled", enabled).apply()
                        if (enabled) {
                            ArushiAssistantService.start(this)
                        } else {
                            ArushiAssistantService.stop(this)
                        }
                    },
                    savedApiKey = customApiKey,
                    onSaveApiKey = { key ->
                        customApiKey = key
                        prefs.edit().putString("custom_gemini_key", key).apply()
                    }
                )
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::voiceManager.isInitialized) {
            voiceManager.release()
        }
    }
}
