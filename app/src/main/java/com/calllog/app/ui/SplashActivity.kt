package com.calllog.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.calllog.app.R
import com.calllog.app.data.local.SecureStorage

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val storage = SecureStorage(this)

        Handler(Looper.getMainLooper()).postDelayed({
            if (storage.isLoggedIn()) {
                // Token आहे → Dashboard
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // Token नाही → Login
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 2000)
    }
}
