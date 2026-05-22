package com.calllog.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.calllog.app.R
import com.calllog.app.data.model.CallType
import com.calllog.app.databinding.FragmentOverallAnalyticsBinding
import com.calllog.app.databinding.LayoutAnalyticsBottomSheetBinding
import com.calllog.app.ui.adapter.CallLogAdapter
import com.calllog.app.ui.viewmodel.OverallAnalyticsViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class OverallAnalyticsFragment : Fragment() {

    private var _binding: FragmentOverallAnalyticsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OverallAnalyticsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOverallAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        setupDateChips()
        setupCardClicks()
        observeStats()
    }

    private fun setupCardClicks() {
        binding.cardTotal.setOnClickListener    { showCallsBottomSheet(null, "All Calls", R.drawable.ic_phone, R.color.purple_600) }
        binding.cardIncoming.setOnClickListener { showCallsBottomSheet(CallType.INCOMING, "Incoming Calls", R.drawable.ic_call_incoming, R.color.color_incoming) }
        binding.cardOutgoing.setOnClickListener { showCallsBottomSheet(CallType.OUTGOING, "Outgoing Calls", R.drawable.ic_call_outgoing, R.color.color_outgoing) }
        binding.cardMissed.setOnClickListener   { showCallsBottomSheet(CallType.MISSED,   "Missed Calls",   R.drawable.ic_call_missed,   R.color.color_missed) }
        binding.cardRejected.setOnClickListener { showCallsBottomSheet(CallType.REJECTED,  "Rejected Calls",  R.drawable.ic_call_rejected,  R.color.color_unknown) }
    }

    private fun setupDateChips() {
        binding.chipToday.setOnClickListener {
            viewModel.setDateRange(OverallAnalyticsViewModel.DateRange.TODAY)
        }
        binding.chipYesterday.setOnClickListener {
            viewModel.setDateRange(OverallAnalyticsViewModel.DateRange.YESTERDAY)
        }
        binding.chipThisWeek.setOnClickListener {
            viewModel.setDateRange(OverallAnalyticsViewModel.DateRange.THIS_WEEK)
        }
        binding.chipThisMonth.setOnClickListener {
            viewModel.setDateRange(OverallAnalyticsViewModel.DateRange.THIS_MONTH)
        }
        binding.chipAllTime.setOnClickListener {
            viewModel.setDateRange(OverallAnalyticsViewModel.DateRange.ALL_TIME)
        }
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collect { stats ->
                if (stats.isLoading) {
                    // Loading state — dashes दाखवतो
                    binding.tvTotalCalls.text    = "..."
                    binding.tvTotalDuration.text = ""
                    binding.tvIncomingCount.text  = "..."
                    binding.tvOutgoingCount.text  = "..."
                    binding.tvMissedCount.text    = "..."
                    binding.tvRejectedCount.text  = "..."
                    binding.tvUniqueCount.text    = "..."
                    return@collect
                }

                binding.tvTotalCalls.text    = stats.total.toString()
                binding.tvTotalDuration.text = OverallAnalyticsViewModel.formatDuration(stats.totalDuration)

                binding.tvIncomingCount.text    = stats.incoming.toString()
                binding.tvIncomingDuration.text = OverallAnalyticsViewModel.formatDuration(stats.incomingDuration)

                binding.tvOutgoingCount.text    = stats.outgoing.toString()
                binding.tvOutgoingDuration.text = OverallAnalyticsViewModel.formatDuration(stats.outgoingDuration)

                binding.tvMissedCount.text   = stats.missed.toString()
                binding.tvRejectedCount.text = stats.rejected.toString()
                binding.tvUniqueCount.text   = stats.uniqueNumbers.toString()
            }
        }
    }

    private fun showCallsBottomSheet(
        callType: CallType?,
        title: String,
        iconRes: Int,
        colorRes: Int
    ) {
        // ViewModel ला filter सेट करतो
        viewModel.setCallTypeFilter(callType)

        // Bottom Sheet Dialog बनवतो
        val dialog = BottomSheetDialog(requireContext(), R.style.ThemeOverlay_App_BottomSheetDialog)
        val sheetBinding = LayoutAnalyticsBottomSheetBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Title + Icon
        sheetBinding.tvSheetTitle.text = title
        sheetBinding.ivSheetIcon.setImageResource(iconRes)
        sheetBinding.ivSheetIcon.setColorFilter(ContextCompat.getColor(requireContext(), colorRes))
        sheetBinding.ivSheetIcon.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), colorRes) and 0x22FFFFFF or (0x22 shl 24)
        )

        // Adapter setup
        val sheetAdapter = CallLogAdapter { callLog ->
            dialog.dismiss()
            val bundle = Bundle().apply {
                putString(ContactAnalyticsFragment.ARG_PHONE_NUMBER, callLog.phoneNumber)
                putString(ContactAnalyticsFragment.ARG_CONTACT_NAME, callLog.name)
            }
            findNavController().navigate(R.id.action_overallAnalytics_to_contactAnalytics, bundle)
        }
        sheetBinding.rvSheetCallLogs.layoutManager = LinearLayoutManager(requireContext())
        sheetBinding.rvSheetCallLogs.adapter = sheetAdapter

        // Close button
        sheetBinding.btnCloseSheet.setOnClickListener { dialog.dismiss() }

        // Observe filtered calls
        val job = lifecycleScope.launch {
            viewModel.callsList.collect { calls ->
                sheetAdapter.submitList(calls)
                val isEmpty = calls.isEmpty()
                sheetBinding.layoutSheetEmpty.visibility  = if (isEmpty) View.VISIBLE else View.GONE
                sheetBinding.rvSheetCallLogs.visibility   = if (isEmpty) View.GONE   else View.VISIBLE

                // Empty state text
                sheetBinding.tvSheetEmptyText.text = "No $title found"
            }
        }

        // Dialog dismiss केल्यावर coroutine cancel करतो
        dialog.setOnDismissListener { job.cancel() }

        // Bottom sheet expand करतो (peek height वाढवतो)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.7).toInt()
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
