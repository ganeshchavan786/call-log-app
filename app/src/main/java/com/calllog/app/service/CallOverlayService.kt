package com.calllog.app.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.calllog.app.R
import com.calllog.app.data.local.SecureStorage
import kotlinx.coroutines.*
import timber.log.Timber

class CallOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val ACTION_SHOW = "com.calllog.app.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.calllog.app.action.HIDE_OVERLAY"

        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"
        const val EXTRA_SIM_SLOT = "extra_sim_slot"

        fun show(context: Context, phoneNumber: String, isIncoming: Boolean, simSlot: Int) {
            val intent = Intent(context, CallOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_IS_INCOMING, isIncoming)
                putExtra(EXTRA_SIM_SLOT, simSlot)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start CallOverlayService")
            }
        }

        fun hide(context: Context) {
            val intent = Intent(context, CallOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop CallOverlayService")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Timber.i("CallOverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_SHOW -> {
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, true)
                val simSlot = intent.getIntExtra(EXTRA_SIM_SLOT, 0)
                Timber.d("CallOverlayService ACTION_SHOW: number=$phoneNumber, isIncoming=$isIncoming, simSlot=$simSlot")
                showOverlay(phoneNumber, isIncoming, simSlot)
            }
            ACTION_HIDE -> {
                Timber.d("CallOverlayService ACTION_HIDE")
                removeOverlay()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlay(phoneNumber: String, isIncoming: Boolean, simSlot: Int) {
        if (overlayView == null) {
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = inflater.inflate(R.layout.layout_call_overlay, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                x = 0
                y = 120
            }

            try {
                windowManager?.addView(overlayView, params)
                Timber.d("Overlay view added to WindowManager")
            } catch (e: Exception) {
                Timber.e(e, "Failed to add overlay view to WindowManager")
                overlayView = null
                return
            }
        }

        // Bind Views
        val view = overlayView ?: return
        val tvName = view.findViewById<TextView>(R.id.tv_caller_name)
        val tvNumber = view.findViewById<TextView>(R.id.tv_caller_number)
        val tvSimStatus = view.findViewById<TextView>(R.id.tv_overlay_sim_status)
        val ivSimIcon = view.findViewById<ImageView>(R.id.iv_overlay_sim_icon)
        val ivCallIcon = view.findViewById<ImageView>(R.id.iv_call_type_icon)
        val btnClose = view.findViewById<View>(R.id.btn_close_overlay)
        val simBadge = view.findViewById<LinearLayout>(R.id.layout_sim_badge)
        val callTypeContainer = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_call_type_container)

        // Set Close click listener
        btnClose.setOnClickListener {
            removeOverlay()
            stopSelf()
        }

        // Set Call type icon & colors
        if (isIncoming) {
            ivCallIcon.setImageResource(R.drawable.ic_call_incoming)
            ivCallIcon.setColorFilter(Color.parseColor("#2E7D32"))
            callTypeContainer.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
        } else {
            ivCallIcon.setImageResource(R.drawable.ic_call_outgoing)
            ivCallIcon.setColorFilter(Color.parseColor("#1565C0"))
            callTypeContainer.setCardBackgroundColor(Color.parseColor("#E3F2FD"))
        }

        // Populate caller details
        tvNumber.text = phoneNumber.ifEmpty { "Private/Unknown Number" }
        tvName.text = "Loading Caller Details..."

        // Lookup Name asynchronously
        serviceScope.launch {
            val contactName = withContext(Dispatchers.IO) {
                getContactName(applicationContext, phoneNumber)
            }
            if (isActive && overlayView != null) {
                tvName.text = contactName ?: "Unknown Number"
            }
        }

        // Resolve SIM sync status
        val storage = SecureStorage(applicationContext)
        val isSimRegistered = if (simSlot == 0) storage.isSim1Registered() else storage.isSim2Registered()
        val simLabel = "SIM ${simSlot + 1}"

        if (isSimRegistered) {
            tvSimStatus.text = "$simLabel: Active (Syncing)"
            tvSimStatus.setTextColor(Color.parseColor("#4CAF50"))
            ivSimIcon.setColorFilter(Color.parseColor("#4CAF50"))
            simBadge.setBackgroundColor(Color.parseColor("#E8F5E9"))
        } else {
            tvSimStatus.text = "$simLabel: Inactive (Not Syncing)"
            tvSimStatus.setTextColor(Color.parseColor("#F44336"))
            ivSimIcon.setColorFilter(Color.parseColor("#F44336"))
            simBadge.setBackgroundColor(Color.parseColor("#FFEBEE"))
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                Timber.d("Overlay view removed successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error removing overlay view")
            }
            overlayView = null
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isEmpty()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        var name: String? = null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying contact name for number: ${maskNumber(phoneNumber)}")
        }
        return name
    }

    private fun maskNumber(number: String): String {
        if (number.length < 5) return "****"
        return "${number.take(2)}****${number.takeLast(4)}"
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        removeOverlay()
        Timber.i("CallOverlayService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
