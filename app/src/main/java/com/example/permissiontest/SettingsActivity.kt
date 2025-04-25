package com.example.permissiontest

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    /**
     * Inflate layout using PreferenceFragmentCompat
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment(this))
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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