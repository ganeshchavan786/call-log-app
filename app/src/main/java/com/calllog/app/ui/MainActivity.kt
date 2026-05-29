package com.calllog.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.calllog.app.R
import com.calllog.app.data.local.SecureStorage
import com.calllog.app.databinding.ActivityMainBinding
import com.calllog.app.service.CallLogService
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // सगळे required permissions
    private val requiredPermissions = mutableListOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "Permissions Granted! ✅", Toast.LENGTH_SHORT).show()
            val storage = SecureStorage(this)
            if (storage.isLoggedIn()) {
                CallLogService.start(this)
            }
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        checkAndRequestPermissions()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            showPermissionRationaleDialog(missing.toTypedArray())
        } else {
            val storage = SecureStorage(this)
            if (storage.isLoggedIn()) {
                CallLogService.start(this)
            }
        }
    }

    private fun showPermissionRationaleDialog(permissions: Array<String>) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_permission_rationale, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<View>(R.id.btn_rationale_grant).setOnClickListener {
            dialog.dismiss()
            permissionLauncher.launch(permissions)
        }

        view.findViewById<View>(R.id.btn_rationale_later).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Denied")
            .setMessage("The app cannot display call logs without permissions. Please enable them in Settings.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
