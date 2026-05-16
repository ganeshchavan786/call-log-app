package com.calllog.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.calllog.app.data.model.SimInfo
import com.calllog.app.databinding.FragmentSimDetailsBinding
import com.calllog.app.ui.viewmodel.SimViewModel
import kotlinx.coroutines.launch

class SimDetailsFragment : Fragment() {

    private var _binding: FragmentSimDetailsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SimViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSimDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRefresh.setOnClickListener { viewModel.refreshSimDetails() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.simList.collect { sims -> showSimCards(sims) }
        }
    }

    private fun showSimCards(sims: List<SimInfo>) {
        // SIM 1
        sims.getOrNull(0)?.let { sim ->
            binding.cardSim1.visibility = View.VISIBLE
            binding.tvSim1Operator.text  = sim.operatorName.ifEmpty { "SIM 1" }
            binding.tvSim1Number.text    = sim.phoneNumber.ifEmpty { "Number not available" }
            binding.tvSim1Network.text   = sim.networkType
            binding.tvSim1Country.text   = sim.countryIso.uppercase()
            binding.tvSim1Imei.text      = sim.imei.ifEmpty { "Not available" }
        } ?: run { binding.cardSim1.visibility = View.GONE }

        // SIM 2
        sims.getOrNull(1)?.let { sim ->
            binding.cardSim2.visibility = View.VISIBLE
            binding.tvSim2Operator.text  = sim.operatorName.ifEmpty { "SIM 2" }
            binding.tvSim2Number.text    = sim.phoneNumber.ifEmpty { "Number not available" }
            binding.tvSim2Network.text   = sim.networkType
            binding.tvSim2Country.text   = sim.countryIso.uppercase()
            binding.tvSim2Imei.text      = sim.imei.ifEmpty { "Not available" }
        } ?: run { binding.cardSim2.visibility = View.GONE }

        binding.tvNoSim.visibility = if (sims.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
