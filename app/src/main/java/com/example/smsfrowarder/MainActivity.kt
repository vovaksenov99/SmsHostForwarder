package com.example.smsfrowarder

import android.Manifest
import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.smsfrowarder.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    companion object {

        const val TELEGRAM_TOKEN_ARG = "TELEGRAM_TOKEN_ARG"
        const val CHAT_IDS_ARG = "CHAT_IDS_ARG"
        const val CREDS_PREF_NAME = "CREDS_PREF_NAME"

    }

    private lateinit var binding: ActivityMainBinding
    private val credsRepository by lazy {
        CredsRepository(
            getSharedPreferences(CREDS_PREF_NAME, Context.MODE_PRIVATE)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            chatsIds.setText(credsRepository.chatIds.orEmpty())
            token.setText(credsRepository.telegramToken.orEmpty())
            saveButton.setOnClickListener {
                credsRepository.chatIds = chatsIds.text.toString()
                credsRepository.telegramToken = token.text.toString()
                SmsService.startService(this@MainActivity)
            }
            stopButton.setOnClickListener {
                SmsService.stopService(this@MainActivity)
            }
        }
        requestSmsPermissions()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            requestForegroundServicePermission()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPostNotificationPermission()
        }
    }


    private fun requestSmsPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.WRITE_CALL_LOG,
                    Manifest.permission.READ_SMS,
                ),
                1
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun requestForegroundServicePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.FOREGROUND_SERVICE),
                1
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestPostNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "This permissions required", Toast.LENGTH_LONG).show()
        }
    }
}


