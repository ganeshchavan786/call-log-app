package com.calllog.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.calllog.app.data.database.CallLogDatabase
import com.calllog.app.data.local.SecureStorage
import com.calllog.app.data.model.CallLog
import com.calllog.app.data.model.CallType
import com.calllog.app.databinding.FragmentEmployeeSummaryBinding
import com.calllog.app.ui.viewmodel.OverallAnalyticsViewModel
import com.calllog.app.util.SimDetailsManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class EmployeeSummaryFragment : Fragment() {

    private var _binding: FragmentEmployeeSummaryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmployeeSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        val rangeStr = arguments?.getString("date_range") ?: "TODAY"
        val range = try {
            OverallAnalyticsViewModel.DateRange.valueOf(rangeStr)
        } catch (e: Exception) {
            OverallAnalyticsViewModel.DateRange.TODAY
        }

        val (from, to) = getDateRangeMillis(range)
        val dateBadge = getBadgeLabel(range, from, to)
        binding.tvNavDateBadge.text = dateBadge

        loadEmployeeDetails()
        loadStatistics(from, to)
    }

    private fun loadEmployeeDetails() {
        val context = requireContext()
        val storage = SecureStorage(context)
        val name = storage.getUserName() ?: "Employee"
        binding.tvEmployeeName.text = name

        // Avatar Initials
        val initials = if (name.isNotBlank() && name != "Unknown") {
            val parts = name.trim().split(" ").filter { it.isNotBlank() }
            if (parts.size >= 2) {
                "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            } else {
                parts[0].take(2).uppercase()
            }
        } else {
            "EE"
        }
        binding.tvAvatar.text = initials

        // Phone Number
        val sims = SimDetailsManager.getSimDetails(context)
        val phoneNumber = if (sims.isNotEmpty()) {
            val num = sims.first().phoneNumber
            if (num.isNullOrEmpty()) "Number not available" else num
        } else {
            "Number not available"
        }
        binding.tvEmployeePhone.text = phoneNumber

        // Last Sync Time
        val lastSync = storage.getLastSyncTime()
        if (lastSync > 0) {
            val sdfSync = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            binding.tvLastSyncTime.text = sdfSync.format(Date(lastSync))
        } else {
            binding.tvLastSyncTime.text = "Never synced"
        }
    }

    private fun loadStatistics(from: Long, to: Long) {
        val dao = CallLogDatabase.getDatabase(requireContext()).callLogDao()
        viewLifecycleOwner.lifecycleScope.launch {
            dao.getCallsByDateRange(from, to).collect { calls ->
                bindStats(calls)
            }
        }
    }

    private fun bindStats(list: List<CallLog>) {
        val totalCalls = list.size
        val totalDuration = list.sumOf { it.duration }

        val connectedCallsList = list.filter { it.duration > 0 }
        val connectedCalls = connectedCallsList.size
        val connCallsDuration = connectedCallsList.sumOf { it.duration }
        val connAvgDuration = if (connectedCalls > 0) connCallsDuration / connectedCalls else 0L

        // Working Hours: max call date - min call date
        val workingHours = if (list.size >= 2) {
            val dates = list.map { it.callDate }
            val maxDate = dates.maxOrNull() ?: 0L
            val minDate = dates.minOrNull() ?: 0L
            (maxDate - minDate) / 1000 // duration in seconds
        } else {
            0L
        }

        val uniqueClients = list.map { it.phoneNumber }.distinct().size
        val uniqueConnCalls = connectedCallsList.map { it.phoneNumber }.distinct().size

        // Incoming stats
        val incomingCalls = list.filter { it.callType == CallType.INCOMING }
        val incomingTotal = incomingCalls.size
        val incomingDuration = incomingCalls.sumOf { it.duration }
        val incomingConnectedList = incomingCalls.filter { it.duration > 0 }
        val incomingConnected = incomingConnectedList.size
        val incomingConnDuration = incomingConnectedList.sumOf { it.duration }
        val incomingConnAvgDuration = if (incomingConnected > 0) incomingConnDuration / incomingConnected else 0L

        // Outgoing stats
        val outgoingCalls = list.filter { it.callType == CallType.OUTGOING }
        val outgoingTotal = outgoingCalls.size
        val outgoingDuration = outgoingCalls.sumOf { it.duration }
        val outgoingConnectedList = outgoingCalls.filter { it.duration > 0 }
        val outgoingConnected = outgoingConnectedList.size
        val outgoingConnDuration = outgoingConnectedList.sumOf { it.duration }
        val outgoingConnAvgDuration = if (outgoingConnected > 0) outgoingConnDuration / outgoingConnected else 0L

        // Missed & Rejected
        val missedCount = list.count { it.callType == CallType.MISSED }
        val rejectedCount = list.count { it.callType == CallType.REJECTED }

        // Never attended & Not pickup by client
        val callsByNum = list.groupBy { it.phoneNumber }
        var neverAttendedCount = 0
        var notPickupByClientCount = 0

        for ((_, calls) in callsByNum) {
            val hasConn = calls.any { it.duration > 0 }
            if (!hasConn) {
                val hasMissedOrRejected = calls.any { it.callType == CallType.MISSED || it.callType == CallType.REJECTED }
                val hasOutgoing = calls.any { it.callType == CallType.OUTGOING }
                if (hasMissedOrRejected) {
                    neverAttendedCount++
                } else if (hasOutgoing) {
                    notPickupByClientCount++
                }
            }
        }

        // BIND TO VIEWS
        binding.tvHeroTotalCalls.text = totalCalls.toString()
        binding.tvHeroTotalDuration.text = formatDurationReport(totalDuration)
        binding.tvHeroConnectedCalls.text = connectedCalls.toString()
        binding.tvHeroConnDuration.text = formatDurationReport(connCallsDuration)
        binding.tvHeroConnAvgDuration.text = formatDurationReport(connAvgDuration)
        binding.tvHeroWorkingHours.text = formatDurationReport(workingHours)
        binding.tvHeroUniqueClients.text = uniqueClients.toString()
        binding.tvHeroUniqueConnCalls.text = uniqueConnCalls.toString()

        // Incoming Calls Card
        binding.tvIncTotal.text = incomingTotal.toString()
        binding.tvIncDuration.text = formatDurationReport(incomingDuration)
        binding.tvIncConnected.text = incomingConnected.toString()
        binding.tvIncConnDuration.text = formatDurationReport(incomingConnDuration)
        binding.tvIncConnAvgDuration.text = formatDurationReport(incomingConnAvgDuration)

        // Outgoing Calls Card
        binding.tvOutTotal.text = outgoingTotal.toString()
        binding.tvOutDuration.text = formatDurationReport(outgoingDuration)
        binding.tvOutConnected.text = outgoingConnected.toString()
        binding.tvOutConnDuration.text = formatDurationReport(outgoingConnDuration)
        binding.tvOutConnAvgDuration.text = formatDurationReport(outgoingConnAvgDuration)

        // Missed & Rejected
        binding.tvMissed.text = missedCount.toString()
        binding.tvRejected.text = rejectedCount.toString()
        binding.tvNeverAttended.text = neverAttendedCount.toString()
        binding.tvNotPickupByClient.text = notPickupByClientCount.toString()
    }

    private fun formatDurationReport(seconds: Long): String {
        val h = TimeUnit.SECONDS.toHours(seconds)
        val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val s = seconds % 60
        return "${h}h ${m}m ${s}s"
    }

    private fun getDateRangeMillis(range: OverallAnalyticsViewModel.DateRange): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return when (range) {
            OverallAnalyticsViewModel.DateRange.TODAY -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Pair(start, now)
            }
            OverallAnalyticsViewModel.DateRange.YESTERDAY -> {
                val start = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                Pair(start, end)
            }
            OverallAnalyticsViewModel.DateRange.THIS_WEEK -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Pair(start, now)
            }
            OverallAnalyticsViewModel.DateRange.THIS_MONTH -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Pair(start, now)
            }
            OverallAnalyticsViewModel.DateRange.ALL_TIME -> Pair(0L, now)
        }
    }

    private fun getBadgeLabel(range: OverallAnalyticsViewModel.DateRange, from: Long, to: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return when (range) {
            OverallAnalyticsViewModel.DateRange.TODAY -> sdf.format(Date(to))
            OverallAnalyticsViewModel.DateRange.YESTERDAY -> sdf.format(Date(from))
            OverallAnalyticsViewModel.DateRange.THIS_WEEK -> {
                val sdfShort = SimpleDateFormat("dd MMM", Locale.getDefault())
                "${sdfShort.format(Date(from))} - ${sdf.format(Date(to))}"
            }
            OverallAnalyticsViewModel.DateRange.THIS_MONTH -> {
                val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                sdfMonth.format(Date(from))
            }
            OverallAnalyticsViewModel.DateRange.ALL_TIME -> "All Time"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
