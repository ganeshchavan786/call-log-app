package com.calllog.app.ui

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.calllog.app.data.api.ApiClient
import com.calllog.app.data.api.models.RegisterSimRequest
import com.calllog.app.data.local.SecureStorage
import com.calllog.app.databinding.ActivitySimSetupBinding
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch
import timber.log.Timber

class SimSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimSetupBinding
    private lateinit var storage: SecureStorage

    private var sim1Number: String? = null
    private var sim2Number: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = SecureStorage(this)

        // Step 1: Google Hint API ने नंबर मिळवण्याचा प्रयत्न
        requestPhoneNumberHint()

        // Step 2: जुना SIM detection (Hint API fail झाले तर Fallback)
        detectSIMs()
        setupContinueButton()
    }

    // ── Google Phone Number Hint API ──────────────────────────────────────────
    // हे 100% Free आहे — Google Play Services वापरतो
    // युझरला एक सुंदर popup दिसतो ज्यामध्ये त्याचे SIM नंबर दिसतात
    // एक क्लिक केला की नंबर auto-fill होतो
    // Fail झाले तरी ॲप नेहमीप्रमाणे चालतो (detectSIMs fallback)

    private val phoneNumberHintLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                val phoneNumber = Identity.getSignInClient(this)
                    .getPhoneNumberFromIntent(result.data!!)
                Timber.i("Google Hint API: got number $phoneNumber")

                // SIM 1 मध्ये number नसेल तर auto-fill करतो
                if (sim1Number == null && !phoneNumber.isNullOrBlank()) {
                    sim1Number = phoneNumber
                    binding.tvSim1Number.text = phoneNumber
                    binding.layoutSim1Manual.visibility = View.GONE
                    Timber.i("Auto-filled SIM 1 from Google Hint: $phoneNumber")
                }
            } catch (e: Exception) {
                Timber.w(e, "Google Hint API: failed to extract number")
            }
        } else {
            Timber.d("Google Hint API: user dismissed or no number")
        }
    }

    private fun requestPhoneNumberHint() {
        try {
            val request = GetPhoneNumberHintIntentRequest.builder().build()
            Identity.getSignInClient(this)
                .getPhoneNumberHintIntent(request)
                .addOnSuccessListener { pendingIntent ->
                    try {
                        phoneNumberHintLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        Timber.w(e, "Google Hint API: failed to launch")
                    }
                }
                .addOnFailureListener { e ->
                    Timber.w(e, "Google Hint API: not available — using fallback")
                }
        } catch (e: Exception) {
            Timber.w(e, "Google Hint API: initialization failed — using fallback")
        }
    }

    // ── SIM Detection ─────────────────────────────────────────────────────────
    private fun detectSIMs() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            binding.tvSim1Number.text = "Permission needed"
            showManualInputs()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subManager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE)
                        as SubscriptionManager
                val subs = subManager.activeSubscriptionInfoList

                if (!subs.isNullOrEmpty()) {
                    // Robust detection based on exact simSlotIndex
                    var sim1Found = false
                    var sim2Found = false

                    subs.forEach { sub ->
                        val slot = sub.simSlotIndex
                        if (slot == 0) {
                            sim1Found = true
                            sim1Number = sub.number?.takeIf { it.isNotBlank() }
                            binding.tvSim1Number.text = sim1Number ?: "Number not available"
                            if (sim1Number == null) binding.layoutSim1Manual.visibility = View.VISIBLE
                            Timber.d("SIM 1 detected: slot=${sub.simSlotIndex}, number=$sim1Number")
                        } else if (slot == 1) {
                            sim2Found = true
                            sim2Number = sub.number?.takeIf { it.isNotBlank() }
                            binding.cardSim2.visibility = View.VISIBLE
                            binding.tvSim2Number.text = sim2Number ?: "Number not available"
                            if (sim2Number == null) binding.layoutSim2Manual.visibility = View.VISIBLE
                            Timber.d("SIM 2 detected: slot=${sub.simSlotIndex}, number=$sim2Number")
                        }
                    }

                    if (!sim1Found) {
                        binding.tvSim1Number.text = "No SIM in slot 1"
                        binding.layoutSim1Manual.visibility = View.VISIBLE
                    }

                    if (!sim2Found) {
                        binding.cardSim2.visibility = View.GONE
                        Timber.d("SIM 2 not found or empty slot")
                    }
                } else {
                    binding.tvSim1Number.text = "No SIM detected"
                    showManualInputs()
                }

            } else {
                // Older devices
                val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                sim1Number = tm.line1Number?.takeIf { it.isNotBlank() }
                binding.tvSim1Number.text = sim1Number ?: "Number not available"
                if (sim1Number == null) binding.layoutSim1Manual.visibility = View.VISIBLE
            }

        } catch (e: Exception) {
            Timber.e(e, "SIM detection failed")
            binding.tvSim1Number.text = "Detection failed"
            showManualInputs()
        }
    }

    private fun showManualInputs() {
        binding.layoutSim1Manual.visibility = View.VISIBLE
    }

    // ── Continue Button ───────────────────────────────────────────────────────
    private fun setupContinueButton() {
        binding.btnContinue.setOnClickListener {
            registerSIMs()
        }
    }

    private fun registerSIMs() {
        val token = storage.getAuthHeader()
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)

        // Manual input check
        val manualSim1 = binding.etSim1Manual.text?.toString()?.trim()
        val manualSim2 = binding.etSim2Manual.text?.toString()?.trim()

        if (sim1Number == null && manualSim1.isNullOrBlank() && binding.cbSim1.isChecked) {
            binding.tvSimError.text = "Please enter SIM 1 phone number"
            binding.tvSimError.visibility = View.VISIBLE
            return
        }

        val finalSim1 = sim1Number ?: manualSim1
        val finalSim2 = sim2Number ?: manualSim2?.takeIf { it.isNotBlank() }

        showLoading(true)

        lifecycleScope.launch {
            try {
                // SIM 1 register
                if (binding.cbSim1.isChecked && !finalSim1.isNullOrBlank()) {
                    val response = ApiClient.service.registerSim(
                        token,
                        RegisterSimRequest(
                            simSlot    = "SIM_1",
                            phoneNumber = finalSim1,
                            deviceName  = Build.MODEL,
                            deviceId    = deviceId
                        )
                    )
                    if (response.isSuccessful) {
                        storage.saveSim1Registered(true)
                        Timber.i("SIM 1 registered: $finalSim1")
                    }
                }

                // SIM 2 register
                if (binding.cbSim2.isChecked && !finalSim2.isNullOrBlank()) {
                    val response = ApiClient.service.registerSim(
                        token,
                        RegisterSimRequest(
                            simSlot    = "SIM_2",
                            phoneNumber = finalSim2,
                            deviceName  = Build.MODEL,
                            deviceId    = deviceId
                        )
                    )
                    if (response.isSuccessful) {
                        storage.saveSim2Registered(true)
                        Timber.i("SIM 2 registered: $finalSim2")
                    }
                }

                showLoading(false)
                Toast.makeText(this@SimSetupActivity, "SIM registered successfully!", Toast.LENGTH_SHORT).show()

                // Dashboard वर navigate करतो
                startActivity(Intent(this@SimSetupActivity, MainActivity::class.java))
                finish()

            } catch (e: Exception) {
                showLoading(false)
                Timber.e(e, "SIM registration failed")
                binding.tvSimError.text = "Registration failed. Please try again."
                binding.tvSimError.visibility = View.VISIBLE
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressSim.visibility  = if (loading) View.VISIBLE else View.GONE
        binding.btnContinue.isEnabled   = !loading
        binding.tvSimError.visibility   = View.GONE
    }
}
