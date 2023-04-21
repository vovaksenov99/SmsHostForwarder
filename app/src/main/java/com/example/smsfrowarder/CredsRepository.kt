package com.example.smsfrowarder

import android.content.SharedPreferences
import com.example.smsfrowarder.MainActivity.Companion.CHAT_IDS_ARG
import com.example.smsfrowarder.MainActivity.Companion.TELEGRAM_TOKEN_ARG

class CredsRepository(private val sharedPreferences: SharedPreferences) {

    var telegramToken: String?
        get() = sharedPreferences.getString(TELEGRAM_TOKEN_ARG, null)
        set(value) {
            sharedPreferences.edit().putString(TELEGRAM_TOKEN_ARG, value).commit()
        }

    var chatIds: String?
        get() = sharedPreferences.getString(CHAT_IDS_ARG, null)
        set(value) {
            sharedPreferences.edit().putString(CHAT_IDS_ARG, value).commit()
        }

}