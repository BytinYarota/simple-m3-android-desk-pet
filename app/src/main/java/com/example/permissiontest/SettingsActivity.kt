package com.example.permissiontest

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    // Service binding
    private lateinit var myService: MainService
    private var isBound = false

    /**
     * Connection to MainService
     */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MainService.MainServiceBinder
            myService = binder.getService()
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
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // quit button
            findPreference<Preference>("quit")?.setOnPreferenceClickListener {
                popConfirmDialog()
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