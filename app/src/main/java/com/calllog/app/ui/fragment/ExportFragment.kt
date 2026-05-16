package com.calllog.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.calllog.app.databinding.FragmentExportBinding
import com.calllog.app.ui.viewmodel.CallLogViewModel
import com.calllog.app.util.ExportManager
import kotlinx.coroutines.launch
import timber.log.Timber

class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CallLogViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeStats()
        setupButtons()
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.totalCallCount.collect { count ->
                    binding.tvExportCount.text = "$count calls ready to export"
                    binding.tvStatTotal.text   = count.toString()
                }
            }
            launch {
                viewModel.missedCallCount.collect { count ->
                    binding.tvStatMissed.text = count.toString()
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnExportCsv.setOnClickListener  { exportCsv(share = false) }
        binding.btnShareCsv.setOnClickListener   { exportCsv(share = true) }
    }

    private fun exportCsv(share: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val calls = viewModel.callLogs.value
            if (calls.isEmpty()) {
                Toast.makeText(requireContext(), "No calls to export", Toast.LENGTH_SHORT).show()
                return@launch
            }

            binding.layoutProgress.visibility = View.VISIBLE
            binding.btnExportCsv.isEnabled    = false
            binding.btnShareCsv.isEnabled     = false

            Timber.d("Export started — ${calls.size} records, share=$share")

            val file = ExportManager.exportToCsv(requireContext(), calls)

            binding.layoutProgress.visibility = View.GONE
            binding.btnExportCsv.isEnabled    = true
            binding.btnShareCsv.isEnabled     = true

            if (file != null) {
                Toast.makeText(requireContext(), "Exported ${calls.size} calls!", Toast.LENGTH_SHORT).show()
                if (share) ExportManager.shareFile(requireContext(), file)
            } else {
                Toast.makeText(requireContext(), "Export failed. Try again.", Toast.LENGTH_SHORT).show()
                Timber.e("CSV export returned null")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
