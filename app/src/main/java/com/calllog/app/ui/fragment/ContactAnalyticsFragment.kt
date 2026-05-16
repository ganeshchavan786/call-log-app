package com.calllog.app.ui.fragment

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.calllog.app.R
import com.calllog.app.databinding.FragmentContactAnalyticsBinding
import com.calllog.app.ui.viewmodel.ContactAnalyticsViewModel
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import kotlinx.coroutines.launch
import timber.log.Timber

class ContactAnalyticsFragment : Fragment() {

    private var _binding: FragmentContactAnalyticsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ContactAnalyticsViewModel by viewModels()

    companion object {
        const val ARG_PHONE_NUMBER = "phone_number"
        const val ARG_CONTACT_NAME = "contact_name"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bundle मधून arguments मिळवतो — Safe Args नाही
        val number = arguments?.getString(ARG_PHONE_NUMBER) ?: ""
        val name   = arguments?.getString(ARG_CONTACT_NAME) ?: "Unknown"

        Timber.d("ContactAnalytics opened for: ${number.take(4)}****")

        viewModel.init(number)

        // ── Header ───────────────────────────────────────────────────────────
        val hasName = name.isNotBlank() && name != "Unknown"
        binding.tvContactName.text   = if (hasName) name else number
        binding.tvContactNumber.text = number

        // Avatar initials
        val initials = if (hasName) {
            val parts = name.trim().split(" ").filter { it.isNotBlank() }
            if (parts.size >= 2) "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            else parts[0].take(2).uppercase()
        } else "#"

        binding.tvAvatar.text = initials
        val avatarColors = listOf(
            0xFF1565C0.toInt(), 0xFF2E7D32.toInt(), 0xFF6A1B9A.toInt(),
            0xFF00796B.toInt(), 0xFFE65100.toInt(), 0xFF4527A0.toInt()
        )
        val avatarColor = avatarColors[Math.abs(name.hashCode()) % avatarColors.size]
        binding.tvAvatar.background = ResourcesCompat.getDrawable(
            resources, R.drawable.bg_circle, requireContext().theme
        )?.also { it.setTint(avatarColor) }

        // ── Buttons ──────────────────────────────────────────────────────────
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnCall.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        }

        // ── Date Range Chips ─────────────────────────────────────────────────
        binding.chipAllTime.setOnClickListener   { viewModel.setDateRange(ContactAnalyticsViewModel.DateRange.ALL_TIME) }
        binding.chipToday.setOnClickListener     { viewModel.setDateRange(ContactAnalyticsViewModel.DateRange.TODAY) }
        binding.chipThisWeek.setOnClickListener  { viewModel.setDateRange(ContactAnalyticsViewModel.DateRange.THIS_WEEK) }
        binding.chipThisMonth.setOnClickListener { viewModel.setDateRange(ContactAnalyticsViewModel.DateRange.THIS_MONTH) }

        setupPieChart()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collect { stats -> bindStats(stats) }
        }
    }

    private fun setupPieChart() {
        binding.pieChart.apply {
            description.isEnabled   = false
            isDrawHoleEnabled       = true
            holeRadius              = 58f
            transparentCircleRadius = 62f
            setHoleColor(Color.TRANSPARENT)
            legend.isEnabled        = false
            setDrawEntryLabels(false)
            setTouchEnabled(false)
            animateY(800)
        }
    }

    private fun bindStats(stats: ContactAnalyticsViewModel.ContactStats) {
        binding.tvIncomingCount.text  = stats.incoming.toString()
        binding.tvOutgoingCount.text  = stats.outgoing.toString()
        binding.tvMissedCount.text    = stats.missed.toString()
        binding.tvRejectedCount.text  = stats.rejected.toString()
        binding.tvTotalCount.text     = stats.total.toString()

        binding.tvIncomingDuration.text = ContactAnalyticsViewModel.formatDuration(stats.incomingDuration)
        binding.tvOutgoingDuration.text = ContactAnalyticsViewModel.formatDuration(stats.outgoingDuration)
        binding.tvMissedDuration.text   = ContactAnalyticsViewModel.formatDuration(stats.missedDuration)
        binding.tvRejectedDuration.text = ContactAnalyticsViewModel.formatDuration(stats.rejectedDuration)
        binding.tvTotalDuration.text    = ContactAnalyticsViewModel.formatDuration(stats.totalDuration)

        updateChart(stats)
    }

    private fun updateChart(stats: ContactAnalyticsViewModel.ContactStats) {
        val entries = mutableListOf<PieEntry>()
        val colors  = mutableListOf<Int>()

        if (stats.incoming > 0) {
            entries.add(PieEntry(stats.incoming.toFloat(), "Incoming"))
            colors.add(ContextCompat.getColor(requireContext(), R.color.color_incoming))
        }
        if (stats.outgoing > 0) {
            entries.add(PieEntry(stats.outgoing.toFloat(), "Outgoing"))
            colors.add(ContextCompat.getColor(requireContext(), R.color.color_outgoing))
        }
        if (stats.missed > 0) {
            entries.add(PieEntry(stats.missed.toFloat(), "Missed"))
            colors.add(ContextCompat.getColor(requireContext(), R.color.color_missed))
        }
        if (stats.rejected > 0) {
            entries.add(PieEntry(stats.rejected.toFloat(), "Rejected"))
            colors.add(ContextCompat.getColor(requireContext(), R.color.color_unknown))
        }

        if (entries.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.centerText = "No Data"
            binding.pieChart.invalidate()
            return
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors    = colors
            sliceSpace     = 2f
            selectionShift = 4f
            setDrawValues(false)
        }

        binding.pieChart.apply {
            data       = PieData(dataSet)
            centerText = "${stats.total}\nCalls"
            setCenterTextSize(14f)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
