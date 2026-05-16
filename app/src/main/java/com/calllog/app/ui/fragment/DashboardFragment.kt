package com.calllog.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.calllog.app.databinding.FragmentDashboardBinding
import com.calllog.app.ui.adapter.CallLogAdapter
import com.calllog.app.ui.viewmodel.CallLogViewModel
import com.calllog.app.worker.CallLogSyncWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CallLogViewModel by viewModels()
    private lateinit var recentAdapter: CallLogAdapter

    // Local state — coroutines update these, then updateBreakdown() reads them
    private var totalCount = 0
    private var incomingCount = 0
    private var outgoingCount = 0
    private var missedCount = 0
    private var rejectedCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecentCallsList()
        observeDashboardData()
        setupSyncButton()
    }

    private fun setupRecentCallsList() {
        recentAdapter = CallLogAdapter { }
        binding.rvRecentCalls.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeDashboardData() {
        viewLifecycleOwner.lifecycleScope.launch {

            launch {
                viewModel.totalCallCount.collect { count ->
                    totalCount = count
                    binding.tvTotalCalls.text = count.toString()
                    updateBreakdown()
                }
            }

            launch {
                viewModel.missedCallCount.collect { count ->
                    missedCount = count
                    binding.tvMissedCalls.text = count.toString()
                    updateBreakdown()
                }
            }

            launch {
                viewModel.incomingCallCount.collect { count ->
                    incomingCount = count
                    binding.tvIncomingCalls.text = count.toString()
                    updateBreakdown()
                }
            }

            launch {
                viewModel.outgoingCallCount.collect { count ->
                    outgoingCount = count
                    binding.tvOutgoingCalls.text = count.toString()
                    updateBreakdown()
                }
            }

            launch {
                viewModel.rejectedCallCount.collect { count ->
                    rejectedCount = count
                    binding.tvRejectedCalls.text = count.toString()
                    updateBreakdown()
                }
            }

            launch {
                viewModel.recentCalls.collect { calls ->
                    recentAdapter.submitList(calls)
                }
            }
        }
    }

    /**
     * totalCount वापरून प्रत्येक call type चा % calculate करतो
     * आणि progress bars + labels update करतो.
     */
    private fun updateBreakdown() {
        if (totalCount == 0) {
            binding.tvIncomingPct.text = "0%"
            binding.tvOutgoingPct.text = "0%"
            binding.tvRejectedPct.text = "0%"
            binding.pbIncoming.progress = 0
            binding.pbOutgoing.progress = 0
            binding.pbMissed.progress = 0
            binding.pbRejected.progress = 0
            binding.tvBreakdownIncomingPct.text = "0%"
            binding.tvBreakdownOutgoingPct.text = "0%"
            binding.tvBreakdownMissedPct.text = "0%"
            binding.tvBreakdownRejectedPct.text = "0%"
            return
        }

        val inPct  = incomingCount  * 100 / totalCount
        val outPct = outgoingCount  * 100 / totalCount
        val misPct = missedCount    * 100 / totalCount
        val rejPct = rejectedCount  * 100 / totalCount

        // Cards
        binding.tvIncomingPct.text = "$inPct%"
        binding.tvOutgoingPct.text = "$outPct%"
        binding.tvRejectedPct.text = "$rejPct%"

        // Breakdown progress bars
        binding.pbIncoming.progress = inPct
        binding.pbOutgoing.progress = outPct
        binding.pbMissed.progress   = misPct
        binding.pbRejected.progress = rejPct

        // Breakdown labels
        binding.tvBreakdownIncomingPct.text = "$inPct%"
        binding.tvBreakdownOutgoingPct.text = "$outPct%"
        binding.tvBreakdownMissedPct.text   = "$misPct%"
        binding.tvBreakdownRejectedPct.text = "$rejPct%"
    }

    private fun setupSyncButton() {
        binding.btnSyncNow.setOnClickListener {
            val request = OneTimeWorkRequestBuilder<CallLogSyncWorker>()
                .setInitialDelay(1, TimeUnit.SECONDS)
                .addTag("manual_sync")
                .build()

            WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                "manual_call_log_sync",
                ExistingWorkPolicy.REPLACE,
                request
            )

            Toast.makeText(requireContext(), "Syncing call logs...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
