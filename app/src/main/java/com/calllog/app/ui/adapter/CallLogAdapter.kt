package com.calllog.app.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.calllog.app.R
import com.calllog.app.data.model.CallLog
import com.calllog.app.data.model.CallType
import com.calllog.app.databinding.ItemCallLogBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CallLogAdapter(
    private val onItemClick: (CallLog) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class ListItem {
        data class Header(val label: String) : ListItem()
        data class CallItem(val callLog: CallLog) : ListItem()
    }

    private var items: List<ListItem> = emptyList()
    private var searchQuery: String = ""

    fun setSearchQuery(query: String) {
        searchQuery = query
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_CALL   = 1

        // Initials साठी background colors — name hash वरून pick करतो
        private val AVATAR_COLORS = listOf(
            0xFF1565C0.toInt(), // blue
            0xFF2E7D32.toInt(), // green
            0xFF6A1B9A.toInt(), // purple
            0xFF00796B.toInt(), // teal
            0xFFE65100.toInt(), // deep orange
            0xFF4527A0.toInt(), // deep purple
            0xFF00838F.toInt(), // cyan
            0xFF558B2F.toInt()  // light green
        )
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun submitList(calls: List<CallLog>) {
        val newItems = buildGroupedList(calls)
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val old = items[o]; val new = newItems[n]
                return when {
                    old is ListItem.Header   && new is ListItem.Header   -> old.label == new.label
                    old is ListItem.CallItem && new is ListItem.CallItem -> old.callLog.id == new.callLog.id
                    else -> false
                }
            }
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    fun getCallAt(position: Int): CallLog? =
        (items.getOrNull(position) as? ListItem.CallItem)?.callLog

    fun isHeader(position: Int): Boolean =
        items.getOrNull(position) is ListItem.Header

    // ── RecyclerView overrides ───────────────────────────────────────────────

    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = when (items[position]) {
        is ListItem.Header   -> TYPE_HEADER
        is ListItem.CallItem -> TYPE_CALL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_date_header, parent, false) as TextView
            )
            else -> CallViewHolder(ItemCallLogBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.Header   -> (holder as HeaderViewHolder).bind(item.label)
            is ListItem.CallItem -> (holder as CallViewHolder).bind(item.callLog, searchQuery, onItemClick)
        }
    }

    // ── ViewHolders ──────────────────────────────────────────────────────────

    class HeaderViewHolder(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
        fun bind(label: String) { tv.text = label }
    }

    class CallViewHolder(private val binding: ItemCallLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(callLog: CallLog, query: String, onClick: (CallLog) -> Unit) {
            val ctx = binding.root.context

            // ── Name / Number with search highlight ──────────────────────────
            val hasName = callLog.name.isNotBlank() && callLog.name != "Unknown"
            val displayName = if (hasName) callLog.name else callLog.phoneNumber
            binding.tvName.text   = highlight(displayName, query)
            binding.tvNumber.text = highlight(callLog.phoneNumber, query)

            // ── Avatar: Initials vs Icon ─────────────────────────────────────
            if (hasName) {                // Contact name आहे → initials दाखव
                binding.tvInitials.visibility = View.VISIBLE
                binding.ivCallType.visibility = View.GONE

                val initials = getInitials(callLog.name)
                binding.tvInitials.text = initials

                // Name hash वरून consistent color pick करतो
                val avatarColor = AVATAR_COLORS[Math.abs(callLog.name.hashCode()) % AVATAR_COLORS.size]
                binding.tvInitials.background = ResourcesCompat.getDrawable(
                    ctx.resources, R.drawable.bg_circle, ctx.theme
                )?.also { it.setTint(avatarColor) }

            } else {
                // Unknown number → call type icon दाखव
                binding.tvInitials.visibility = View.GONE
                binding.ivCallType.visibility = View.VISIBLE

                val (iconRes, colorRes) = callTypeIconAndColor(callLog.callType)
                val iconColor = ContextCompat.getColor(ctx, colorRes)
                binding.ivCallType.setImageResource(iconRes)
                binding.ivCallType.setColorFilter(iconColor)
                val bgColor = Color.argb(0x1A,
                    Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor))
                binding.ivCallType.background = ResourcesCompat.getDrawable(
                    ctx.resources, R.drawable.bg_circle, ctx.theme
                )?.also { it.setTint(bgColor) }
            }

            // ── Call Type Label (colored) ────────────────────────────────────
            val (_, colorRes) = callTypeIconAndColor(callLog.callType)
            val typeColor = ContextCompat.getColor(ctx, colorRes)
            binding.tvCallTypeLabel.text      = callTypeLabel(callLog.callType)
            binding.tvCallTypeLabel.setTextColor(typeColor)

            // ── Time & Duration ──────────────────────────────────────────────
            binding.tvDate.text     = formatTime(callLog.callDate)
            binding.tvDuration.text = formatDuration(callLog.duration)

            // ── SIM Badge ────────────────────────────────────────────────────
            binding.tvSim.text = if (callLog.simSlot == 0) "SIM 1" else "SIM 2"
            binding.tvSim.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(ctx,
                    if (callLog.simSlot == 0) R.color.color_sim1 else R.color.color_sim2)
            )

            // ── Deleted badge ────────────────────────────────────────────────
            binding.ivDeletedBadge.visibility =
                if (callLog.isDeletedFromPhone) View.VISIBLE else View.GONE
            binding.cardCallItem.alpha = if (callLog.isDeletedFromPhone) 0.65f else 1.0f

            binding.cardCallItem.setOnClickListener { onClick(callLog) }
        }

        // ── Helpers ──────────────────────────────────────────────────────────

        /** "Rahul Sharma" → "RS", "Mom" → "M", "+91 9876" → icon (handled above) */
        private fun getInitials(name: String): String {
            val parts = name.trim().split(" ").filter { it.isNotBlank() }
            return when {
                parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
                parts.size == 1 -> parts[0].take(2).uppercase()
                else            -> "?"
            }
        }

        private fun callTypeIconAndColor(type: CallType): Pair<Int, Int> = when (type) {
            CallType.INCOMING -> R.drawable.ic_call_incoming to R.color.color_incoming
            CallType.OUTGOING -> R.drawable.ic_call_outgoing to R.color.color_outgoing
            CallType.MISSED   -> R.drawable.ic_call_missed   to R.color.color_missed
            CallType.REJECTED -> R.drawable.ic_call_rejected to R.color.color_unknown
            CallType.UNKNOWN  -> R.drawable.ic_call_unknown  to R.color.color_unknown
        }

        private fun callTypeLabel(type: CallType) = when (type) {
            CallType.INCOMING -> "Incoming"
            CallType.OUTGOING -> "Outgoing"
            CallType.MISSED   -> "Missed"
            CallType.REJECTED -> "Rejected"
            CallType.UNKNOWN  -> "Unknown"
        }

        private fun formatTime(timestamp: Long): String =
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))

        private fun formatDuration(seconds: Long): String {
            if (seconds == 0L) return "Not connected"
            val h = TimeUnit.SECONDS.toHours(seconds)
            val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
            val s = seconds % 60
            return "${h}h ${m}m ${s}s"
        }

        /** Search query match झालेला text yellow highlight + bold करतो */
        private fun highlight(text: String, query: String): CharSequence {
            if (query.isBlank()) return text
            val spannable = SpannableString(text)
            val lower = text.lowercase()
            val lowerQuery = query.lowercase()
            var start = lower.indexOf(lowerQuery)
            while (start >= 0) {
                val end = start + query.length
                spannable.setSpan(BackgroundColorSpan(0xFFFFEB3B.toInt()),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(Color.BLACK),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                start = lower.indexOf(lowerQuery, end)
            }
            return spannable
        }
    }

    // ── Date grouping ────────────────────────────────────────────────────────

    private fun buildGroupedList(calls: List<CallLog>): List<ListItem> {
        if (calls.isEmpty()) return emptyList()
        val result = mutableListOf<ListItem>()
        var lastLabel = ""
        calls.forEach { call ->
            val label = getDateLabel(call.callDate)
            if (label != lastLabel) { result.add(ListItem.Header(label)); lastLabel = label }
            result.add(ListItem.CallItem(call))
        }
        return result
    }

    private fun getDateLabel(timestamp: Long): String {
        val callCal       = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today         = Calendar.getInstance()
        val yesterday     = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val thisWeekStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        return when {
            isSameDay(callCal, today)     -> "Today"
            isSameDay(callCal, yesterday) -> "Yesterday"
            callCal.after(thisWeekStart)  -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
            else                          -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
