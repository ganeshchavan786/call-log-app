package com.calllog.app.ui.fragment

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
import kotlinx.coroutines.Dispatchers
import com.calllog.app.data.database.CallLogDatabase
import com.calllog.app.service.CallLogService
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
        setupSystemSettings()
        setupSyncHistory()
        setupSignOut()
        setupAppVersion()
    }

    override fun onResume() {
        super.onResume()
        // Settings screen वर परत आल्यावर sync summary refresh करतो
        refreshSyncSummary()
        refreshBatteryOptimizationStatus()
        refreshOverlayStatus()
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
                    // Service stop करतो
                    try {
                        CallLogService.stop(requireContext())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Room Database clear करतो
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            CallLogDatabase.getDatabase(requireContext().applicationContext).clearAllTables()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

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

    // ── System Settings ──────────────────────────────────────────────────────
    private fun setupSystemSettings() {
        refreshBatteryOptimizationStatus()
        refreshOverlayStatus()

        binding.cardBatteryOptimization.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        binding.cardAutostartSettings.setOnClickListener {
            openAutostartSettings()
        }

        binding.cardOverlayPermission.setOnClickListener {
            requestOverlayPermission()
        }
    }

    private fun refreshOverlayStatus() {
        if (_binding == null) return
        val context = requireContext()
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        if (hasOverlay) {
            binding.tvOverlayStatus.text = "🟢 Active (Call popup will show)"
            binding.tvOverlayStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            binding.tvOverlayStatus.text = "🔴 Disabled (Tap to enable)"
            binding.tvOverlayStatus.setTextColor(Color.parseColor("#F44336"))
        }
    }

    private fun requestOverlayPermission() {
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "Overlay permission is already granted! ✅", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(context, "Failed to open settings: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Overlay permission is not required on this Android version.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshBatteryOptimizationStatus() {
        if (_binding == null) return
        val context = requireContext()
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }

        if (isIgnoring) {
            binding.tvBatteryStatus.text = "🟢 Active (Unrestricted)"
            binding.tvBatteryStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            binding.tvBatteryStatus.text = "🔴 Restricted (Tap to disable optimization)"
            binding.tvBatteryStatus.setTextColor(Color.parseColor("#F44336"))
        }
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            val context = requireContext()
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }

            if (isIgnoring) {
                Toast.makeText(context, "Battery Optimization is already disabled! ✅", Toast.LENGTH_SHORT).show()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        } catch (e: Exception) {
            try {
                // Fallback to list settings
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(requireContext(), "Failed to open settings: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAutostartSettings() {
        val context = requireContext()
        val oemIntents = listOf(
            // Xiaomi
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // Oppo
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Intent().setClassName("com.oppo.safe", "com.oppo.safe.PermissionTopActivity"),
            // Vivo
            Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            // Realme
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            // Letv
            Intent().setClassName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutostartActivity"),
            // Huawei
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            // OnePlus
            Intent().setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.smartlaunch.SmartLaunchAppListActivity")
        )

        var launched = false
        for (intent in oemIntents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                launched = true
                Toast.makeText(context, "Redirecting to Autostart Settings... 🚀", Toast.LENGTH_SHORT).show()
                break
            } catch (e: Exception) {
                // Try next
            }
        }

        if (!launched) {
            // General Application Details fallback
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Toast.makeText(context, "General settings opened. Please enable autostart if available. ℹ️", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open system settings.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}


