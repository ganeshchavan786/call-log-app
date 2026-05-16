package com.calllog.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.calllog.app.data.model.SimInfo
import com.calllog.app.databinding.FragmentSettingsBinding
import com.calllog.app.ui.viewmodel.SimViewModel
import kotlinx.coroutines.launch

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
    }

    // ── Dark Mode ─────────────────────────────────────────────────────────────
    private fun setupDarkMode() {
        val isNight = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        binding.switchDarkMode.isChecked = isNight

        binding.switchDarkMode.setOnCheckedChangeListener { _, checked ->
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES
                else         AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    // ── SIM Section ───────────────────────────────────────────────────────────
    private fun setupSimSection() {
        // आधी hidden ठेवतो
        binding.layoutSimDetailsContent.visibility = View.GONE

        // Click → expand/collapse
        binding.cardSimDetailsToggle.setOnClickListener {
            val isVisible = binding.layoutSimDetailsContent.visibility == View.VISIBLE
            if (isVisible) {
                binding.layoutSimDetailsContent.visibility = View.GONE
                binding.ivSimArrow.rotation = -90f
            } else {
                binding.layoutSimDetailsContent.visibility = View.VISIBLE
                binding.ivSimArrow.rotation = 90f
                // Expand झाल्यावर refresh करतो
                simViewModel.refreshSimDetails()
            }
        }

        binding.btnRefreshSim.setOnClickListener {
            simViewModel.refreshSimDetails()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            simViewModel.simList.collect { sims -> bindSimCards(sims) }
        }
    }

    private fun bindSimCards(sims: List<SimInfo>) {
        // SIM 1
        val sim1 = sims.getOrNull(0)
        if (sim1 != null) {
            binding.cardSim1.visibility      = View.VISIBLE
            binding.tvSim1Operator.text      = sim1.operatorName.ifEmpty { "Unknown Operator" }
            binding.tvSim1Number.text        = sim1.phoneNumber.ifEmpty { "Number not available" }
            binding.tvSim1Network.text       = sim1.networkType.ifEmpty { "—" }
            binding.tvSim1Country.text       = sim1.countryIso.uppercase().ifEmpty { "—" }
            binding.tvSim1Imei.text          = sim1.imei.ifEmpty { "Not available" }
        } else {
            binding.cardSim1.visibility = View.GONE
        }

        // SIM 2
        val sim2 = sims.getOrNull(1)
        if (sim2 != null) {
            binding.cardSim2.visibility      = View.VISIBLE
            binding.tvSim2Operator.text      = sim2.operatorName.ifEmpty { "Unknown Operator" }
            binding.tvSim2Number.text        = sim2.phoneNumber.ifEmpty { "Number not available" }
            binding.tvSim2Network.text       = sim2.networkType.ifEmpty { "—" }
            binding.tvSim2Country.text       = sim2.countryIso.uppercase().ifEmpty { "—" }
            binding.tvSim2Imei.text          = sim2.imei.ifEmpty { "Not available" }
        } else {
            binding.cardSim2.visibility = View.GONE
        }

        // No SIM state
        binding.layoutNoSim.visibility = if (sims.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
