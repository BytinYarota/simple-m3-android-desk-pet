package com.example.permissiontest

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher

/**
 * Checks permissions then starts main service thread and die
 */
class MainActivity : AppCompatActivity() {

    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideView()
        deinteractableActivity()
//        setContentView(R.layout.activity_main)

        initializeLauncher()

        if(!serviceRunning()){
            progress(ACTIVITY_STARTED)
        }else{
            val openSettingsIntent = Intent(this, SettingsActivity::class.java)
            openSettingsIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            startActivity(openSettingsIntent)
            finishAndRemoveTask()
        }
    }

    private val mainHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ACTIVITY_STARTED -> {
                    printLog("act start")
                    this.post{
                        checkOverlayPermission()
                    }
                }

                OVERLAY_PERMISSION_GAINED -> {
                    printLog("overlay gained")
                    checkNotificationPermission()
                }

                NOTIFICATION_PERMISSION_GAINED -> {
                    printLog("notification gained")
                    startMainService()
                }

                SERVICE_STARTED -> {
                    printLog("service start")
                    finishAndRemoveTask()
                }
            }
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            progress(OVERLAY_PERMISSION_GAINED)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT > 33) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                progress(NOTIFICATION_PERMISSION_GAINED)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }


    private fun startMainService() {
        val serviceIntent = Intent(this, MainService::class.java)
        serviceIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        progress(SERVICE_STARTED)
    }

    private fun initializeLauncher() {
        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            if (Settings.canDrawOverlays(this)) {
                popText("success")
                progress(OVERLAY_PERMISSION_GAINED)
            } else {
                failProgress(OVERLAY_FORBIDDEN)
            }
        }
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                progress(NOTIFICATION_PERMISSION_GAINED)
            } else {
                failProgress(NOTIFICATION_FORBIDDEN)
            }
        }
    }

    private fun requestOverlayPermission() {
        popText("requesting overlay")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }


    private fun deinteractableActivity() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }

    private fun failProgress(failure: Int) {
        when (failure) {
            OVERLAY_FORBIDDEN -> {
                popText("运行失败：请给予悬浮窗权限")
            }

            NOTIFICATION_FORBIDDEN -> {
                popText("运行失败：请给予通知权限")
            }
        }
        finishAndRemoveTask()
    }

    private fun progress(what: Int) {
        val msg = mainHandler.obtainMessage(what)
        mainHandler.sendMessage(msg)
    }

    private fun popText(text: CharSequence) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun printLog(text: String) {
        Log.d("PermissionTest", text)
    }

    private fun serviceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = manager.getRunningServices(Integer.MAX_VALUE)

        val targetServiceName = ComponentName(this, MainService::class.java).className
        val isRunning = runningServices.any { it.service.className == targetServiceName }

        return isRunning
    }

    private fun hideView() {
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            }
        }
    }

    private companion object {
        const val ACTIVITY_STARTED = 0
        const val OVERLAY_PERMISSION_GAINED = 1
        const val NOTIFICATION_PERMISSION_GAINED = 2
        const val SERVICE_STARTED = 3

        const val OVERLAY_FORBIDDEN = 4
        const val NOTIFICATION_FORBIDDEN = 5
    }
}