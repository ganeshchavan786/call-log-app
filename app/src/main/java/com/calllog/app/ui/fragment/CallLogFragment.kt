package com.calllog.app.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.fragment.findNavController
import com.calllog.app.R
import com.calllog.app.data.model.CallType
import com.calllog.app.databinding.FragmentCallLogBinding
import com.calllog.app.ui.adapter.CallLogAdapter
import com.calllog.app.ui.viewmodel.CallLogViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

class CallLogFragment : Fragment() {

    private var _binding: FragmentCallLogBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CallLogViewModel by viewModels()
    private lateinit var adapter: CallLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupFilterChips()
        setupSortButton()
        observeCallLogs()
        observeChipCounts()
    }

    // ── RecyclerView + Swipe ─────────────────────────────────────────────────
    private fun setupRecyclerView() {
        adapter = CallLogAdapter { callLog ->
            // Bundle वापरून navigate करतो — Safe Args नाही
            val bundle = Bundle().apply {
                putString(ContactAnalyticsFragment.ARG_PHONE_NUMBER, callLog.phoneNumber)
                putString(ContactAnalyticsFragment.ARG_CONTACT_NAME, callLog.name)
            }
            findNavController().navigate(
                R.id.action_callLog_to_contactAnalytics,
                bundle
            )
        }
        binding.rvCallLogs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CallLogFragment.adapter
        }
        ItemTouchHelper(
            SwipeActionCallback(
                adapter      = this.adapter,
                context      = requireContext(),
                onCallBack   = { number -> doCallBack(number) },
                onCopyNumber = { number -> doCopyNumber(number) },
                onHaptic     = { doHaptic() }
            )
        ).attachToRecyclerView(binding.rvCallLogs)
    }


    // ── Sort ─────────────────────────────────────────────────────────────────
    private fun setupSortButton() {
        binding.btnSort.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(), view)
            popup.menu.add(0, 0, 0, "Newest First")
            popup.menu.add(0, 1, 1, "Oldest First")
            popup.menu.add(0, 2, 2, "Longest Duration")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> viewModel.onSortSelected(CallLogViewModel.SortOrder.NEWEST)
                    1 -> viewModel.onSortSelected(CallLogViewModel.SortOrder.OLDEST)
                    2 -> viewModel.onSortSelected(CallLogViewModel.SortOrder.LONGEST)
                }
                true
            }
            popup.show()
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────
    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged { text ->
            val query = text?.toString() ?: ""
            viewModel.onSearchQueryChanged(query)
            adapter.setSearchQuery(query)   // highlight साठी
        }
    }

    // ── Filter Chips ─────────────────────────────────────────────────────────
    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener      { viewModel.onFilterSelected(null) }
        binding.chipIncoming.setOnClickListener { viewModel.onFilterSelected(CallType.INCOMING) }
        binding.chipOutgoing.setOnClickListener { viewModel.onFilterSelected(CallType.OUTGOING) }
        binding.chipMissed.setOnClickListener   { viewModel.onFilterSelected(CallType.MISSED) }
        binding.chipRejected.setOnClickListener { viewModel.onFilterSelected(CallType.REJECTED) }
    }

    // ── Chip Counts ──────────────────────────────────────────────────────────
    private fun observeChipCounts() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.incomingCallCount.collect { count ->
                    binding.chipIncoming.text = "Incoming ($count)"
                }
            }
            launch {
                viewModel.outgoingCallCount.collect { count ->
                    binding.chipOutgoing.text = "Outgoing ($count)"
                }
            }
            launch {
                viewModel.missedCallCount.collect { count ->
                    binding.chipMissed.text = "Missed ($count)"
                }
            }
            launch {
                viewModel.rejectedCallCount.collect { count ->
                    binding.chipRejected.text = "Rejected ($count)"
                }
            }
        }
    }

    // ── Observe Calls ────────────────────────────────────────────────────────
    private fun observeCallLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.callLogs.collect { calls ->
                adapter.submitList(calls)
                binding.tvTotalCount.text = "${calls.size} calls"
                val isEmpty = calls.isEmpty()
                binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.rvCallLogs.visibility       = if (isEmpty) View.GONE   else View.VISIBLE
            }
        }
    }

    // ── Actions ──────────────────────────────────────────────────────────────
    private fun doCallBack(number: String) {
        Timber.d("Call back: ${number.take(4)}****")
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    private fun doCopyNumber(number: String) {
        val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("Phone Number", number))
        Toast.makeText(requireContext(), "Number copied!", Toast.LENGTH_SHORT).show()
    }

    private fun doHaptic() {
        try {
            val v = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(40)
            }
        } catch (e: Exception) { Timber.w("Haptic failed: ${e.message}") }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ── SwipeActionCallback ───────────────────────────────────────────────────────
class SwipeActionCallback(
    private val adapter:      CallLogAdapter,
    private val context:      Context,
    private val onCallBack:   (String) -> Unit,
    private val onCopyNumber: (String) -> Unit,
    private val onHaptic:     () -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder) = false

    override fun getSwipeDirs(recyclerView: RecyclerView,
                               viewHolder: RecyclerView.ViewHolder): Int {
        val pos = viewHolder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION || adapter.isHeader(pos)) return 0
        return super.getSwipeDirs(recyclerView, viewHolder)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val pos = viewHolder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return
        val callLog = adapter.getCallAt(pos)
        adapter.notifyItemChanged(pos)
        onHaptic()
        if (callLog != null) {
            when (direction) {
                ItemTouchHelper.LEFT  -> onCallBack(callLog.phoneNumber)
                ItemTouchHelper.RIGHT -> onCopyNumber(callLog.phoneNumber)
            }
        }
    }

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView,
                              viewHolder: RecyclerView.ViewHolder,
                              dX: Float, dY: Float, actionState: Int,
                              isCurrentlyActive: Boolean) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }
        val item = viewHolder.itemView
        val paint = Paint().apply { isAntiAlias = true }
        val iconSize = 72; val margin = 48

        if (dX < 0) {
            paint.color = ContextCompat.getColor(context, R.color.color_incoming)
            c.drawRoundRect(RectF(item.right + dX, item.top.toFloat(),
                item.right.toFloat(), item.bottom.toFloat()), 28f, 28f, paint)
            ContextCompat.getDrawable(context, R.drawable.ic_phone)?.apply {
                setTint(Color.WHITE)
                val top = item.top + (item.height - iconSize) / 2
                setBounds(item.right - margin - iconSize, top, item.right - margin, top + iconSize)
                draw(c)
            }
            paint.color = Color.WHITE; paint.textSize = 32f; paint.textAlign = Paint.Align.RIGHT
            c.drawText("Call Back", (item.right - margin - iconSize - 12).toFloat(),
                (item.top + item.height / 2 + 10).toFloat(), paint)
        } else if (dX > 0) {
            paint.color = ContextCompat.getColor(context, R.color.color_outgoing)
            c.drawRoundRect(RectF(item.left.toFloat(), item.top.toFloat(),
                item.left + dX, item.bottom.toFloat()), 28f, 28f, paint)
            ContextCompat.getDrawable(context, R.drawable.ic_copy)?.apply {
                setTint(Color.WHITE)
                val top = item.top + (item.height - iconSize) / 2
                setBounds(item.left + margin, top, item.left + margin + iconSize, top + iconSize)
                draw(c)
            }
            paint.color = Color.WHITE; paint.textSize = 32f; paint.textAlign = Paint.Align.LEFT
            c.drawText("Copy", (item.left + margin + iconSize + 12).toFloat(),
                (item.top + item.height / 2 + 10).toFloat(), paint)
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
