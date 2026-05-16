package com.calllog.app.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.calllog.app.R
import com.calllog.app.data.model.CallLog
import com.calllog.app.data.model.CallType
import com.calllog.app.databinding.FragmentCallDetailsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CallDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentCallDetailsBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_CALL_ID  = "call_id"
        private const val ARG_NAME     = "name"
        private const val ARG_NUMBER   = "number"
        private const val ARG_TYPE     = "type"
        private const val ARG_DURATION = "duration"
        private const val ARG_DATE     = "date"
        private const val ARG_SIM      = "sim"
        private const val ARG_DELETED  = "deleted"

        fun newInstance(call: CallLog) = CallDetailsBottomSheet().apply {
            arguments = Bundle().apply {
                putLong(ARG_CALL_ID,  call.id)
                putString(ARG_NAME,   call.name)
                putString(ARG_NUMBER, call.phoneNumber)
                putString(ARG_TYPE,   call.callType.name)
                putLong(ARG_DURATION, call.duration)
                putLong(ARG_DATE,     call.callDate)
                putInt(ARG_SIM,       call.simSlot)
                putBoolean(ARG_DELETED, call.isDeletedFromPhone)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args     = requireArguments()
        val callType = CallType.valueOf(args.getString(ARG_TYPE, "UNKNOWN"))
        val number   = args.getString(ARG_NUMBER, "")
        val name     = args.getString(ARG_NAME, "Unknown")

        // ── Header ──────────────────────────────────────────────────────────
        binding.tvDetailName.text   = name
        binding.tvDetailNumber.text = number
        binding.tvDetailType.text   = callTypeLabel(callType)

        // Call type icon + colored circle
        val (iconRes, colorRes) = when (callType) {
            CallType.INCOMING -> R.drawable.ic_call_incoming to R.color.color_incoming
            CallType.OUTGOING -> R.drawable.ic_call_outgoing to R.color.color_outgoing
            CallType.MISSED   -> R.drawable.ic_call_missed   to R.color.color_missed
            CallType.REJECTED -> R.drawable.ic_call_rejected to R.color.color_unknown
            CallType.UNKNOWN  -> R.drawable.ic_call_unknown  to R.color.color_unknown
        }
        val iconColor = ContextCompat.getColor(requireContext(), colorRes)
        binding.ivDetailCallType.setImageResource(iconRes)
        binding.ivDetailCallType.setColorFilter(iconColor)
        binding.ivDetailCallType.background = ResourcesCompat.getDrawable(
            resources, R.drawable.bg_circle, requireContext().theme
        )?.also {
            it.setTint(Color.argb(0x22,
                Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor)))
        }

        // Call type label color
        binding.tvDetailType.setTextColor(iconColor)

        // SIM badge
        val simSlot = args.getInt(ARG_SIM)
        binding.tvDetailSim.text = "SIM ${simSlot + 1}"
        binding.tvDetailSim.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(),
                if (simSlot == 0) R.color.color_sim1 else R.color.color_sim2)
        )

        // ── Info cards ───────────────────────────────────────────────────────
        binding.tvDetailDuration.text = formatDuration(args.getLong(ARG_DURATION))
        binding.tvDetailDate.text     = formatDate(args.getLong(ARG_DATE))

        // ── Deleted badge ────────────────────────────────────────────────────
        binding.badgeDeleted.visibility =
            if (args.getBoolean(ARG_DELETED)) View.VISIBLE else View.GONE

        // ── Action Buttons ───────────────────────────────────────────────────

        // Call Back
        binding.btnCallBack.setOnClickListener {
            Timber.d("Call back: ${number.take(4)}****")
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            startActivity(intent)
            dismiss()
        }

        // Copy Number
        binding.btnCopyNumber.setOnClickListener {
            val clipboard = requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Phone Number", number))
            Toast.makeText(requireContext(), "Number copied!", Toast.LENGTH_SHORT).show()
            Timber.d("Number copied to clipboard")
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun callTypeLabel(type: CallType) = when (type) {
        CallType.INCOMING -> "Incoming Call"
        CallType.OUTGOING -> "Outgoing Call"
        CallType.MISSED   -> "Missed Call"
        CallType.REJECTED -> "Rejected Call"
        CallType.UNKNOWN  -> "Unknown"
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds == 0L) return "Not connected"
        val h = TimeUnit.SECONDS.toHours(seconds)
        val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val s = seconds % 60
        return "${h}h ${m}m ${s}s"
    }

    private fun formatDate(ts: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
