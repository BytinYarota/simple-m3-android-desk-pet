package com.example.permissiontest

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    // Service binding
    private lateinit var mainService: MainService
    private var isBound = false

    /**
     * Connection to MainService
     */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MainService.MainServiceBinder
            mainService = binder.getService()
            isBound = true
            Log.d("SettingsActivity", "ServiceConnected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    /**
     * Inflate layout using PreferenceFragmentCompat
     */
    override fun onCreate(savedInstanceState: Bundle?) {

        // Set up service
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment(this))
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Bind to service
        Intent(this, MainService::class.java).apply {
            bindService(this, connection, BIND_AUTO_CREATE)
        }
    }

    /**
     * Clean up on destroy
     */
    override fun onDestroy() {
        super.onDestroy()

        // Unbind from service if bound
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    /**
     * Set up preference behavior
     */
    class SettingsFragment(private val activity: AppCompatActivity) : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

            // Shared Preference Storage
            val preferenceEditor = activity.getSharedPreferences("user", MODE_PRIVATE).edit()

            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // quit button
            findPreference<Preference>("quit")?.setOnPreferenceClickListener {
                popConfirmDialog()
                true
            }

            findPreference<Preference>("centralize")?.setOnPreferenceClickListener {
                val screenSize = Point()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activity.display.getRealSize(screenSize)
                } else {
                    (activity.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(screenSize)
                }
                screenSize.apply{
                    (activity as SettingsActivity).mainService.moveCenterTo(x/2, y/2)
                }
                true
            }

        }

        /**
         * Pop up dialog to confirm quit
         */
        private fun popConfirmDialog(){
            AlertDialog.Builder(activity)
                .setTitle("退出")
                .setMessage("确定退出程序吗？")
                .setPositiveButton("确定") { dialog, _ ->
                    val intent = Intent(activity,MainService::class.java)
                    activity.stopService(intent)
                    activity.finishAndRemoveTask()
                    dialog.dismiss()
                }
                .setNegativeButton("取消") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
        }
    }
}