package com.example.smsfrowarder

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BATTERY_LOW
import android.os.BatteryManager
import android.os.Build
import android.provider.CallLog
import android.provider.CallLog.Calls.MISSED_TYPE
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.TelephonyManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import kotlinx.coroutines.*
import java.lang.Exception

class SMSReceiver(
    private val telegramToken: String,
    private val telegramChatIds: List<String>
) : BroadcastReceiver() {

    companion object {

        private const val MAX_ATTEMPT_COUNT = 10
    }

    private var previousCallState = -1
    private var legacyPhoneNumber: String? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    private val okhttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(
                    chain: Array<X509Certificate?>?,
                    authType: String?
                ) {
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(
                    chain: Array<X509Certificate?>?,
                    authType: String?
                ) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate?> {
                    return arrayOfNulls<X509Certificate>(0)
                }
            }
        )

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier({ hostname: String?, session: SSLSession? -> true })
            .build()
    }


    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val telephonyManager =
                    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    telephonyManager.registerTelephonyCallback(
                        context.mainExecutor,
                        object : TelephonyCallback(), TelephonyCallback.CallStateListener {

                            override fun onCallStateChanged(state: Int) {
                                if (TelephonyManager.CALL_STATE_IDLE == state && previousCallState == TelephonyManager.CALL_STATE_RINGING)
                                    readCallLogs(context)
                                previousCallState = state
                            }
                        })
                } else {
                    telephonyManager.listen(object : PhoneStateListener() {
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            if (TelephonyManager.CALL_STATE_IDLE == state && previousCallState == TelephonyManager.CALL_STATE_RINGING)
                                sendMessages("Missed call from `$legacyPhoneNumber`")
                            legacyPhoneNumber = phoneNumber ?: legacyPhoneNumber
                            previousCallState = state
                        }
                    }, PhoneStateListener.LISTEN_CALL_STATE)
                }
            }
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                for (message in smsMessages) {
                    processSMS(message)
                }
            }
            ACTION_BATTERY_LOW -> {
                val batteryManager =
                    context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val batteryLevel =
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                sendMessages("Low battery level! $batteryLevel% charge left")
            }
        }
    }

    private fun processSMS(smsMessage: SmsMessage) {
        val phoneNumber = smsMessage.displayOriginatingAddress
        val messageText = smsMessage.displayMessageBody.highlightVerificationCodeInSms()
        val date = Date(smsMessage.timestampMillis).toString()

        sendMessages(
            """
            From *$phoneNumber* at $date

            $messageText
        """.trimIndent()
        )
    }

    @SuppressLint("Range")
    private fun readCallLogs(context: Context) {
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, null, null, null,
            CallLog.Calls.DEFAULT_SORT_ORDER
        )

        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER))
                    val type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE))
                    val date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE))
                    if (type == MISSED_TYPE) {
                        sendMessages("Missed call from `$number`. ${Date(date)}")
                        break
                    }
                } while (cursor.moveToNext())
                cursor.close()
            } else {
                sendMessages("Missed call from unknown")
            }
        } catch (e: Exception) {
            // На нет и суда нет
        }
    }

    private fun String.highlightVerificationCodeInSms(): String {
        var rez = this
        "\\d{4,}|[A-Z\\d]{4,}".toRegex().findAll(rez)
            .forEach {
                rez = rez.replace(it.value, "`${it.value}`")
            }
        return rez
    }

    private fun sendMessages(messageText: String) {
        telegramChatIds.forEach { chatId ->
            sendMessage(messageText, chatId)
        }
    }

    private fun sendMessage(
        messageText: String,
        chatId: String,
        repeatCount: Int = MAX_ATTEMPT_COUNT
    ) {
        if (repeatCount == 0) return
        scope.launch(CoroutineExceptionHandler { _, t ->
            sendMessage(messageText, chatId, repeatCount - 1)
        } + SupervisorJob()) {
            delay(calculateDelayMs(repeatCount))
            sentMessageToTelegram(messageText, chatId)
        }
    }

    private fun calculateDelayMs(repeatCount: Int) = (MAX_ATTEMPT_COUNT - repeatCount) * 1000L

    private fun sentMessageToTelegram(messageText: String, chatId: String) {
        val body = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            "{\"text\":\"$messageText\",\"parse_mode\":\"Markdown\",\"disable_web_page_preview\":false,\"disable_notification\":false,\"reply_to_message_id\":null,\"chat_id\":\"$chatId\"}"
        )
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$telegramToken/sendMessage")
            .post(body)
            .addHeader("accept", "application/json")
            .addHeader("content-type", "application/json")
            .build()

        okhttpClient.newCall(request).execute()
    }
}

