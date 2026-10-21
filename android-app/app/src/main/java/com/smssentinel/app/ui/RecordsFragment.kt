package com.smssentinel.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smssentinel.app.IncomingCallActivity
import com.smssentinel.app.R
import com.smssentinel.app.data.SettingsStore
import com.smssentinel.app.data.Task
import com.smssentinel.app.data.TaskStatus
import com.smssentinel.app.data.TaskStore
import com.smssentinel.app.databinding.FragmentRecordsBinding
import com.smssentinel.app.reminder.ReminderScheduler
import com.smssentinel.app.util.TimeUtils

class RecordsFragment : Fragment() {
    private var _binding: FragmentRecordsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: RecordAdapter
    private var filter = Filter.ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = RecordAdapter(onClick = ::openTask, onAction = ::handleAction)
        binding.recordsList.layoutManager = LinearLayoutManager(requireContext())
        binding.recordsList.adapter = adapter
        binding.filterGroup.check(R.id.filterAll)
        TaskStore.revision.observe(viewLifecycleOwner) { render() }

        binding.filterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            filter = when (checkedId) {
                R.id.filterPending -> Filter.PENDING
                R.id.filterConfirmed -> Filter.CONFIRMED
                else -> Filter.ALL
            }
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val all = TaskStore.all(requireContext())
        val filtered = when (filter) {
            Filter.ALL -> all
            Filter.PENDING -> all.filter { it.status == TaskStatus.PENDING }
            Filter.CONFIRMED -> all.filter { it.status.isConfirmed }
        }
        adapter.submit(filtered)
        binding.recordsEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.recordsList.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openTask(task: Task) {
        if (task.isPending) {
            startActivity(
                Intent(requireContext(), IncomingCallActivity::class.java)
                    .putExtra(IncomingCallActivity.EXTRA_TASK_ID, task.id)
            )
            return
        }

        val status = when (task.status) {
            TaskStatus.CONFIRMED_READ -> "已确认 · 接听（已阅读）"
            TaskStatus.CONFIRMED_UNREAD -> "已确认 · 挂断（未阅读）"
            TaskStatus.PENDING -> "待确认"
        }
        val confirmed = task.confirmedAt?.let { "\n确认时间：${TimeUtils.dateTime(it)}" } ?: ""
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("短信详情")
            .setMessage("${task.body}\n\n状态：$status\n来源：${task.sender}\n响铃次数：${task.ringCount}$confirmed")
            .setNegativeButton("关闭", null)

        if (task.status == TaskStatus.CONFIRMED_UNREAD) {
            builder.setNeutralButton("恢复为待确认") { _, _ -> restore(task) }
        }
        builder.show()
    }

    private fun handleAction(task: Task) {
        when (task.status) {
            TaskStatus.PENDING -> openTask(task)
            TaskStatus.CONFIRMED_UNREAD -> restore(task)
            else -> Unit
        }
    }

    private fun restore(task: Task) {
        val settings = SettingsStore(requireContext())
        val restored = TaskStore.restore(requireContext(), task.id, settings.intervalMillis) ?: return
        ReminderScheduler.schedule(requireContext(), restored)
        Toast.makeText(requireContext(), "已恢复为待确认，将按间隔再次提醒", Toast.LENGTH_SHORT).show()
        render()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class Filter { ALL, PENDING, CONFIRMED }
}
