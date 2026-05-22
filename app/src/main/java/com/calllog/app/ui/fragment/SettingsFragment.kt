package com.calllog.app.ui.fragment

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.calllog.app.data.api.ApiClient
import com.calllog.app.data.api.models.RegisterSimRequest
import com.calllog.app.data.local.SecureStorage
import com.calllog.app.data.model.SimInfo
import com.calllog.app.databinding.FragmentSettingsBinding
import com.calllog.app.ui.LoginActivity
import com.calllog.app.ui.viewmodel.SimViewModel
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val simViewModel: SimViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDarkMode()
        setupSimSection()
        setupSyncHistory()
        setupSignOut()
        setupAppVersion()
    }

    override fun onResume() {
        super.onResume()
        // Settings screen वर परत आल्यावर sync summary refresh करतो
        refreshSyncSummary()
    }

    // ── Dark Mode ─────────────────────────────────────────────────────────────
    private fun setupDarkMode() {
        val storage = SecureStorage(requireContext())
        val isNight = storage.isDarkMode()
        binding.switchDarkMode.isChecked = isNight
        binding.switchDarkMode.setOnCheckedChangeListener { _, checked ->
            storage.saveDarkMode(checked)
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES
                else         AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    // ── Sync History ──────────────────────────────────────────────────────────
    private fun setupSyncHistory() {
        refreshSyncSummary()

        binding.cardSyncHistoryToggle.setOnClickListener {
            val isVisible = binding.layoutSyncHistoryContent.visibility == View.VISIBLE
            if (isVisible) {
                binding.layoutSyncHistoryContent.visibility = View.GONE
                binding.ivSyncHistoryArrow.rotation = -90f
            } else {
                binding.layoutSyncHistoryContent.visibility = View.VISIBLE
                binding.ivSyncHistoryArrow.rotation = 90f
                loadSyncHistoryItems(SecureStorage(requireContext()))
            }
        }
    }

    private fun refreshSyncSummary() {
        if (_binding == null) return
        val storage = SecureStorage(requireContext())
        val history = storage.getSyncHistory()
        if (history.isNotEmpty()) {
            val last = parseSyncEntry(history.first())
            binding.tvLastSyncSummary.text = "Last: ${last.timeStr} — ${last.statusLabel}"
        } else {
            binding.tvLastSyncSummary.text = "No sync yet"
        }
    }

    private fun loadSyncHistoryItems(storage: SecureStorage) {
        val history = storage.getSyncHistory()
        val container = binding.layoutSyncHistoryItems
        container.removeAllViews()

        if (history.isEmpty()) {
            binding.cardSyncHistoryEmpty.visibility = View.VISIBLE
            return
        }

        binding.cardSyncHistoryEmpty.visibility = View.GONE

        history.forEach { entryStr ->
            val entry = parseSyncEntry(entryStr)
            val card = buildSyncHistoryCard(entry)
            container.addView(card)
        }
    }

    private data class SyncEntry(
        val timeStr: String,
        val typeLabel: String,   // 👆 Manual / 🤖 Auto
        val statusLabel: String,
        val statusColor: Int,
        val synced: Int,
        val skipped: Int,
        val failed: Int,
        val message: String
    )

    private fun parseSyncEntry(raw: String): SyncEntry {
        // Format: "timestamp|status|synced|skipped|failed|message|syncType"
        val parts     = raw.split("|")
        val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val status    = parts.getOrNull(1) ?: "unknown"
        val synced    = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val skipped   = parts.getOrNull(3)?.toIntOrNull() ?: 0
        val failed    = parts.getOrNull(4)?.toIntOrNull() ?: 0
        val message   = parts.getOrNull(5) ?: ""
        val syncType  = parts.getOrNull(6) ?: "auto"  // "manual" or "auto"

        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val timeStr = if (timestamp > 0) sdf.format(Date(timestamp)) else "Unknown time"

        // Auto/Manual label
        val typeLabel = if (syncType == "manual") "👆 Manual" else "🤖 Auto"

        val (statusLabel, statusColor) = when (status) {
            "success"     -> Pair("✅ Success",          Color.parseColor("#4CAF50"))
            "partial"     -> Pair("⚠️ Partial",          Color.parseColor("#FF9800"))
            "failed"      -> Pair("❌ Failed",            Color.parseColor("#F44336"))
            "up_to_date"  -> Pair("✅ Up to date",        Color.parseColor("#4CAF50"))
            "auth_error"  -> Pair("🔒 Auth Error",        Color.parseColor("#F44336"))
            "server_down" -> Pair("🔴 Server Down",       Color.parseColor("#FF5722"))
            else          -> Pair("— Unknown",            Color.GRAY)
        }

        return SyncEntry(timeStr, typeLabel, statusLabel, statusColor, synced, skipped, failed, message)
    }

    private fun buildSyncHistoryCard(entry: SyncEntry): MaterialCardView {
        val ctx = requireContext()
        val dp8  = (8  * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        val dp16 = (16 * resources.displayMetrics.density).toInt()

        // Outer card
        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp8 }
            radius = dp12.toFloat()
            cardElevation = 0f
            strokeWidth = 1
            strokeColor = entry.statusColor
        }

        // Inner layout
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp12, dp16, dp12)
        }

        // Row 1: time + status badge
        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp8 }
        }

        val tvTime = TextView(ctx).apply {
            text = "${entry.timeStr}  ${entry.typeLabel}"
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, android.R.color.darker_gray))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvStatus = TextView(ctx).apply {
            text = entry.statusLabel
            textSize = 12f
            setTextColor(entry.statusColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        row1.addView(tvTime)
        row1.addView(tvStatus)

        // Row 2: synced / skipped / failed counts
        val tvCounts = TextView(ctx).apply {
            text = buildString {
                if (entry.synced   > 0) append("📤 ${entry.synced} synced")
                if (entry.skipped  > 0) {
                    if (entry.synced > 0) append("  •  ")
                    append("⏭ ${entry.skipped} already existed")
                }
                if (entry.failed   > 0) {
                    if (entry.synced > 0 || entry.skipped > 0) append("  •  ")
                    append("❌ ${entry.failed} failed")
                }
                if (entry.synced == 0 && entry.skipped == 0 && entry.failed == 0) append(entry.message)
            }
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, android.R.color.darker_gray))
        }

        inner.addView(row1)
        inner.addView(tvCounts)
        card.addView(inner)
        return card
    }

    // ── App Version ───────────────────────────────────────────────────────────
    private fun setupAppVersion() {
        try {
            val pInfo = requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
            binding.tvAppVersion.text = "Version ${pInfo.versionName} (${pInfo.versionCode})"
        } catch (e: Exception) {
            binding.tvAppVersion.text = "Version 1.0"
        }
    }

    // ── Sign Out ──────────────────────────────────────────────────────────────
    private fun setupSignOut() {
        binding.cardSignOut.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    // Token + data clear करतो
                    SecureStorage(requireContext()).clearAll()
                    // Login screen वर navigate करतो
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ── SIM Section ───────────────────────────────────────────────────────────
    private fun setupSimSection() {
        binding.btnRefreshSim.setOnClickListener { simViewModel.refreshSimDetails() }
        viewLifecycleOwner.lifecycleScope.launch {
            simViewModel.simList.collect { sims -> bindSimCards(sims) }
        }
    }

    private fun bindSimCards(sims: List<SimInfo>) {
        val storage = SecureStorage(requireContext())

        val sim1 = sims.getOrNull(0)
        if (sim1 != null) {
            binding.cardSim1.visibility = View.VISIBLE
            binding.tvSim1Operator.text = sim1.operatorName.ifEmpty { "Unknown Operator" }
            binding.tvSim1Number.text   = sim1.phoneNumber.ifEmpty { "Number not available" }
            updateSim1RegStatus(storage, sim1)
        } else {
            binding.cardSim1.visibility = View.GONE
        }

        val sim2 = sims.getOrNull(1)
        if (sim2 != null) {
            binding.cardSim2.visibility = View.VISIBLE
            binding.tvSim2Operator.text = sim2.operatorName.ifEmpty { "Unknown Operator" }
            binding.tvSim2Number.text   = sim2.phoneNumber.ifEmpty { "Number not available" }
            updateSim2RegStatus(storage, sim2)
        } else {
            binding.cardSim2.visibility = View.GONE
        }

        binding.layoutNoSim.visibility = if (sims.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── SIM Registration Status & Button ─────────────────────────────────────

    private fun updateSim1RegStatus(storage: SecureStorage, sim: SimInfo) {
        val isReg = storage.isSim1Registered()
        if (isReg) {
            binding.tvSim1RegStatus.text      = "🟢 Registered"
            binding.tvSim1RegStatus.setTextColor(Color.parseColor("#4CAF50"))
            binding.btnRegisterSim1.visibility = View.GONE
        } else {
            binding.tvSim1RegStatus.text      = "🔴 Not Registered"
            binding.tvSim1RegStatus.setTextColor(Color.parseColor("#F44336"))
            binding.btnRegisterSim1.visibility = View.VISIBLE
            binding.btnRegisterSim1.setOnClickListener {
                registerSimToServer("SIM_1", sim.phoneNumber, storage) {
                    storage.saveSim1Registered(true)
                    updateSim1RegStatus(storage, sim)
                }
            }
        }
    }

    private fun updateSim2RegStatus(storage: SecureStorage, sim: SimInfo) {
        val isReg = storage.isSim2Registered()
        if (isReg) {
            binding.tvSim2RegStatus.text      = "🟢 Registered"
            binding.tvSim2RegStatus.setTextColor(Color.parseColor("#4CAF50"))
            binding.btnRegisterSim2.visibility = View.GONE
        } else {
            binding.tvSim2RegStatus.text      = "🔴 Not Registered"
            binding.tvSim2RegStatus.setTextColor(Color.parseColor("#F44336"))
            binding.btnRegisterSim2.visibility = View.VISIBLE
            binding.btnRegisterSim2.setOnClickListener {
                registerSimToServer("SIM_2", sim.phoneNumber, storage) {
                    storage.saveSim2Registered(true)
                    updateSim2RegStatus(storage, sim)
                }
            }
        }
    }

    private fun registerSimToServer(
        simSlot: String,
        phoneNumber: String,
        storage: SecureStorage,
        onSuccess: () -> Unit
    ) {
        val token    = storage.getAuthHeader()
        val deviceId = Settings.Secure.getString(
            requireContext().contentResolver, Settings.Secure.ANDROID_ID
        )
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.service.registerSim(
                    token,
                    RegisterSimRequest(
                        simSlot     = simSlot,
                        phoneNumber = phoneNumber,
                        deviceName  = Build.MODEL,
                        deviceId    = deviceId
                    )
                )
                if (response.isSuccessful) {
                    onSuccess()
                    Toast.makeText(
                        requireContext(),
                        "✅ $simSlot registered successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "❌ Registration failed (${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "❌ Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


