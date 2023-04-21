package com.example.smsfrowarder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BATTERY_LOW
import android.content.IntentFilter
import android.os.Build
import android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION
import android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.smsfrowarder.MainActivity.Companion.CHAT_IDS_ARG
import com.example.smsfrowarder.MainActivity.Companion.TELEGRAM_TOKEN_ARG
import kotlin.random.Random

class SmsService : Service() {

    companion object {

        private const val CHANNEL_ID = "defaultChannel"

        fun startService(context: Context) {
            val credsRepository =
                CredsRepository(
                    context.getSharedPreferences(
                        MainActivity.CREDS_PREF_NAME,
                        Context.MODE_PRIVATE
                    )
                )
            val telegramToken = credsRepository.telegramToken
            val chatsIds = credsRepository.chatIds
            if (telegramToken.isNullOrEmpty() || chatsIds.isNullOrEmpty()) {
                Toast.makeText(context, "Requires a token and at least one chat id", Toast.LENGTH_LONG)
                    .show()
                return
            }

            val smsServiceIntent = Intent(context, SmsService::class.java).apply {
                putExtra(TELEGRAM_TOKEN_ARG, telegramToken)
                putExtra(CHAT_IDS_ARG, chatsIds)
            }
            stopService(context)
            context.startService(smsServiceIntent)
        }

        fun stopService(context: Context){
            context.stopService(Intent(context, SmsService::class.java))
        }
    }

    private var smsReceiver: SMSReceiver? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val telegramToken = intent?.getStringExtra(TELEGRAM_TOKEN_ARG)
        val chatIds = intent?.getStringExtra(CHAT_IDS_ARG)?.split(",")?.map { it.trim() }

        if (chatIds.isNullOrEmpty() || telegramToken.isNullOrEmpty()) return START_NOT_STICKY

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            service.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Default channel",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
        } else {
            NotificationCompat.Builder(this)
        }

        val notification = builder
            .setContentTitle("SMS forwarder is running")
            .setContentText("Don't stop this notification")
            .build()

        startForeground(Random.nextInt(), notification)
        startBroadcast(telegramToken, chatIds)
        return START_STICKY
    }

    private fun startBroadcast(telegramToken: String, chatIds: List<String>) {
        smsReceiver?.let { unregisterReceiver(it) }
        smsReceiver = SMSReceiver(telegramToken, chatIds)
        registerReceiver(smsReceiver, IntentFilter().apply {
            addAction(SMS_RECEIVED_ACTION)
            addAction(ACTION_PHONE_STATE_CHANGED)
            addAction(ACTION_BATTERY_LOW)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        smsReceiver?.let { unregisterReceiver(it) }
        stopForeground(true)
    }
}