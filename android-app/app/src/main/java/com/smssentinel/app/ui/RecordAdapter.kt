package com.smssentinel.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.smssentinel.app.R
import com.smssentinel.app.data.Task
import com.smssentinel.app.data.TaskStatus
import com.smssentinel.app.databinding.ItemDayHeaderBinding
import com.smssentinel.app.databinding.ItemRecordBinding
import com.smssentinel.app.util.TimeUtils

class RecordAdapter(
    private val onClick: (Task) -> Unit,
    private val onAction: (Task) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val label: String) : Row()
        data class Record(val task: Task) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(newItems: List<Task>) {
        val grouped = mutableListOf<Row>()
        var lastDay: String? = null
        for (task in newItems) {
            val label = TimeUtils.dayLabel(task.receivedAt)
            if (label != lastDay) {
                grouped.add(Row.Header(label))
                lastDay = label
            }
            grouped.add(Row.Record(task))
        }
        rows = grouped
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Header) TYPE_HEADER else TYPE_RECORD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemDayHeaderBinding.inflate(inflater, parent, false))
        } else {
            RecordHolder(ItemRecordBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).binding.tvDay.text = row.label
            is Row.Record -> bindRecord((holder as RecordHolder).binding, row.task)
        }
    }

    private fun bindRecord(binding: ItemRecordBinding, task: Task) {
        val context = binding.root.context
        binding.tvTime.text = TimeUtils.clock(task.receivedAt)
        binding.tvBody.text = task.body

        when (task.status) {
            TaskStatus.PENDING -> {
                binding.tvBadge.text = "待确认"
                binding.tvBadge.setBackgroundResource(R.drawable.bg_status_coral)
                binding.tvBadge.setTextColor(ContextCompat.getColor(context, R.color.coral_deep))
            }
            TaskStatus.CONFIRMED_READ -> {
                binding.tvBadge.text = "已确认 · 接听"
                binding.tvBadge.setBackgroundResource(R.drawable.bg_status_green)
                binding.tvBadge.setTextColor(ContextCompat.getColor(context, R.color.green_deep))
            }
            TaskStatus.CONFIRMED_UNREAD -> {
                binding.tvBadge.text = "已确认 · 挂断"
                binding.tvBadge.setBackgroundResource(R.drawable.bg_status_grey)
                binding.tvBadge.setTextColor(ContextCompat.getColor(context, R.color.muted))
            }
        }

        val reads = if (task.status == TaskStatus.CONFIRMED_UNREAD) " · 未阅读" else ""
        binding.tvMetaLeft.text = "响铃 ${task.ringCount.coerceAtLeast(1)} 次$reads"
        binding.tvMetaRight.text = when {
            task.status == TaskStatus.PENDING -> "下次 ${TimeUtils.nextRing(task.nextRingAt)}"
            task.confirmedAt != null -> "确认 ${TimeUtils.clock(task.confirmedAt!!)}"
            else -> ""
        }

        when (task.status) {
            TaskStatus.PENDING -> {
                binding.tvAction.visibility = View.VISIBLE
                binding.tvAction.text = "立即提醒"
                binding.tvAction.setOnClickListener { onAction(task) }
            }
            TaskStatus.CONFIRMED_UNREAD -> {
                binding.tvAction.visibility = View.VISIBLE
                binding.tvAction.text = "恢复为待确认"
                binding.tvAction.setOnClickListener { onAction(task) }
            }
            else -> {
                binding.tvAction.visibility = View.GONE
                binding.tvAction.setOnClickListener(null)
            }
        }

        binding.root.setOnClickListener { onClick(task) }
    }

    class HeaderHolder(val binding: ItemDayHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class RecordHolder(val binding: ItemRecordBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_RECORD = 1
    }
}
