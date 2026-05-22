package com.calllog.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.calllog.app.databinding.ActivityLoginBinding
import com.calllog.app.ui.viewmodel.LoginViewModel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLoginButton()
        observeLoginState()
    }

    private fun setupLoginButton() {
        binding.btnLogin.setOnClickListener {
            hideKeyboard()
            val email      = binding.etEmail.text?.toString() ?: ""
            val password   = binding.etPassword.text?.toString() ?: ""
            val uniqueCode = binding.etUniqueCode.text?.toString() ?: ""
            viewModel.login(email, password, uniqueCode)
        }
    }

    private fun observeLoginState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is LoginViewModel.LoginState.Idle -> {
                        showLoading(false)
                        binding.tvError.visibility = View.GONE
                    }
                    is LoginViewModel.LoginState.Loading -> {
                        showLoading(true)
                        binding.tvError.visibility = View.GONE
                    }
                    is LoginViewModel.LoginState.Success -> {
                        showLoading(false)
                        val storage = com.calllog.app.data.local.SecureStorage(this@LoginActivity)
                        // SIM आधीच registered असेल तर Dashboard, नाहीतर SIM Setup
                        if (storage.isSim1Registered() || storage.isSim2Registered()) {
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        } else {
                            startActivity(Intent(this@LoginActivity, SimSetupActivity::class.java))
                        }
                        finish()
                    }
                    is LoginViewModel.LoginState.Error -> {
                        showLoading(false)
                        binding.tvError.text = state.message
                        binding.tvError.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.etEmail.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
        binding.etUniqueCode.isEnabled = !loading
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}
