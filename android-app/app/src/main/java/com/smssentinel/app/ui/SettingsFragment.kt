package com.smssentinel.app.ui

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smssentinel.app.R
import com.smssentinel.app.data.SettingsStore
import com.smssentinel.app.data.TaskStore
import com.smssentinel.app.databinding.FragmentSettingsBinding
import com.smssentinel.app.databinding.ItemSettingRowBinding
import com.smssentinel.app.notification.CallNotifier
import com.smssentinel.app.reminder.ReminderScheduler
import com.smssentinel.app.reminder.RingtoneController
import com.smssentinel.app.service.MonitoringService
import com.smssentinel.app.util.OemGuide
import com.smssentinel.app.util.Permissions

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settings: SettingsStore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = SettingsStore(requireContext())
        MonitoringService.runningLive.observe(viewLifecycleOwner) {
            if (_binding != null) render()
        }
    }

    override fun onResume() {
        super.onResume()
        settings = SettingsStore(requireContext())
        render()
    }

    private fun render() {
        binding.groupMonitor.removeAllViews()
        binding.groupFilter.removeAllViews()
        binding.groupCall.removeAllViews()
        binding.groupConfirm.removeAllViews()
        binding.groupData.removeAllViews()

        addSwitch(
            binding.groupMonitor, "启用短信监听", settings.enabled,
            R.drawable.ic_message, R.color.blue,
        ) { checked ->
            settings.enabled = checked
            if (checked) {
                resumeSchedules()
                MonitoringService.start(requireContext())
            } else {
                pauseSchedules()
                MonitoringService.stop(requireContext())
            }
            render()
        }
        addValue(
            binding.groupMonitor, "指定发送号码", settings.targetNumber,
            iconRes = R.drawable.ic_shield, iconColor = R.color.green,
        ) { editNumber() }
        addSwitch(
            binding.groupMonitor, "保持后台监听", settings.backgroundService,
            R.drawable.ic_shield, R.color.blue,
        ) { checked ->
            settings.backgroundService = checked
            if (checked) MonitoringService.start(requireContext()) else MonitoringService.stop(requireContext())
            render()
        }

        addSwitch(
            binding.groupFilter, "只提醒含关键词的短信", settings.filterEnabled,
            R.drawable.ic_alert, R.color.amber,
        ) { checked ->
            settings.filterEnabled = checked
            render()
        }
        addValue(
            binding.groupFilter, "提醒关键词", settings.filterKeywords,
            iconRes = R.drawable.ic_message, iconColor = R.color.blue,
        ) { editKeywords() }
        addValue(
            binding.groupMonitor,
            "后台监听服务",
            if (MonitoringService.running) "运行中" else "未运行",
            color = ContextCompat.getColor(
                requireContext(),
                if (MonitoringService.running) R.color.green_deep else R.color.amber,
            ),
            iconRes = R.drawable.ic_shield,
            iconColor = if (MonitoringService.running) R.color.green else R.color.amber,
        ) { MonitoringService.start(requireContext()); render() }

        addValue(
            binding.groupCall, "重复提醒间隔", "${settings.intervalMinutes} 分钟",
            iconRes = R.drawable.ic_clock, iconColor = R.color.blue,
        ) { chooseInterval() }
        addValue(
            binding.groupCall, "振铃时长", "${settings.ringSeconds} 秒",
            iconRes = R.drawable.ic_clock, iconColor = R.color.amber,
        ) { chooseRingDuration() }
        addSwitch(
            binding.groupCall, "提醒音效", settings.soundEnabled,
            R.drawable.ic_bell, R.color.amber,
        ) { settings.soundEnabled = it }
        addSwitch(
            binding.groupCall, "震动反馈", settings.vibrationEnabled,
            R.drawable.ic_alert, R.color.coral,
        ) { checked ->
            settings.vibrationEnabled = checked
            if (checked) {
                val ok = RingtoneController.test(requireContext())
                if (!ok) Toast.makeText(requireContext(), "设备没有可用的震动马达", Toast.LENGTH_SHORT).show()
            }
        }
        addValue(
            binding.groupCall,
            "测试震动",
            if (settings.vibrationEnabled) "点此测试" else "震动已关闭 · 点此测试",
            color = ContextCompat.getColor(
                requireContext(),
                if (settings.vibrationEnabled) R.color.muted else R.color.amber,
            ),
            iconRes = R.drawable.ic_alert,
            iconColor = R.color.coral,
        ) {
            if (!settings.vibrationEnabled) {
                settings.vibrationEnabled = true
                render()
            }
            val ok = RingtoneController.test(requireContext())
            if (ok) {
                Toast.makeText(
                    requireContext(),
                    "已开启震动并触发。若没有感觉，请检查系统“静音时振动 / 触感反馈 / 震动强度”设置。",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                Toast.makeText(requireContext(), "设备没有可用的震动马达", Toast.LENGTH_LONG).show()
            }
        }

        addValue(
            binding.groupConfirm,
            "挂断视为确认",
            "固定开启",
            color = ContextCompat.getColor(requireContext(), R.color.blue),
            iconRes = R.drawable.ic_call_decline,
            iconColor = R.color.coral,
            onClick = null,
        )
        addSwitch(
            binding.groupConfirm, "锁屏显示正文", settings.lockScreenBody,
            R.drawable.ic_lock, R.color.muted,
        ) { checked ->
            if (checked) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("开启锁屏显示正文？")
                    .setMessage("锁屏上可能直接显示 OA 短信内容，存在信息泄露风险。是否继续？")
                    .setPositiveButton("开启") { _, _ ->
                        settings.lockScreenBody = true
                        render()
                    }
                    .setNegativeButton("取消") { _, _ -> render() }
                    .show()
            } else {
                settings.lockScreenBody = false
            }
        }
        val fullScreen = Permissions.canUseFullScreen(requireContext())
        addValue(
            binding.groupConfirm,
            "优先请求全屏提醒",
            if (fullScreen) "已授权" else "视权限而定",
            color = ContextCompat.getColor(
                requireContext(),
                if (fullScreen) R.color.green_deep else R.color.amber,
            ),
            iconRes = R.drawable.ic_message,
            iconColor = R.color.blue,
        ) { Permissions.openFullScreenSettings(requireContext()) }

        addValue(
            binding.groupData, "已确认记录保留", "${settings.retentionDays} 天",
            iconRes = R.drawable.ic_clock, iconColor = R.color.muted,
        ) { chooseRetention() }
        addValue(
            binding.groupData, "厂商保活设置", "查看指引",
            iconRes = R.drawable.ic_shield, iconColor = R.color.green,
        ) { OemGuide.show(requireContext()) }
        addValue(
            binding.groupData, "清空已确认记录", "",
            iconRes = R.drawable.ic_delete, iconColor = R.color.coral,
        ) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("清空已确认记录？")
                .setMessage("只清除已确认记录，未确认的提醒会保留。")
                .setPositiveButton("清空") { _, _ ->
                    TaskStore.clearConfirmed(requireContext())
                    Toast.makeText(requireContext(), "已清空已确认记录", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        addValue(
            binding.groupData, "关于", "V1.4.1",
            iconRes = R.drawable.ic_settings, iconColor = R.color.muted,
        ) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("重要短信哨兵")
                .setMessage("只为一条来源短信发起来电式提醒。\n\n接听 = 确认并阅读\n挂断 = 确认但未阅读\n\n纯本地运行，不联网、不收集数据。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun addSwitch(
        group: ViewGroup,
        title: String,
        checked: Boolean,
        iconRes: Int? = null,
        iconColor: Int? = null,
        onChange: (Boolean) -> Unit,
    ) {
        val row = ItemSettingRowBinding.inflate(layoutInflater, group, true)
        row.rowTitle.text = title
        row.rowValue.visibility = View.GONE
        row.rowSwitch.visibility = View.VISIBLE
        row.rowSwitch.isChecked = checked
        bindIcon(row, iconRes, iconColor)
        row.rowSwitch.setOnCheckedChangeListener { _, value -> onChange(value) }
        row.root.setOnClickListener { row.rowSwitch.toggle() }
    }

    private fun addValue(
        group: ViewGroup,
        title: String,
        value: String,
        color: Int = ContextCompat.getColor(requireContext(), R.color.muted),
        iconRes: Int? = null,
        iconColor: Int? = null,
        onClick: (() -> Unit)?,
    ) {
        val row = ItemSettingRowBinding.inflate(layoutInflater, group, true)
        row.rowTitle.text = title
        row.rowSwitch.visibility = View.GONE
        row.rowValue.visibility = View.VISIBLE
        row.rowValue.text = value
        row.rowValue.setTextColor(color)
        bindIcon(row, iconRes, iconColor)
        if (onClick != null) {
            row.root.setOnClickListener { onClick() }
        } else {
            row.root.isClickable = false
        }
    }

    private fun bindIcon(
        row: ItemSettingRowBinding,
        iconRes: Int?,
        iconColor: Int?,
    ) {
        if (iconRes == null) {
            row.rowIconWrap.visibility = View.GONE
            return
        }
        row.rowIconWrap.visibility = View.VISIBLE
        row.rowIcon.setImageResource(iconRes)
        row.rowIcon.imageTintList =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        row.rowIconWrap.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), iconColor ?: R.color.blue)
        )
    }

    private fun editNumber() {
        val context = requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setText(settings.targetNumber)
            setSelection(text.length)
            setPadding(48, 32, 48, 16)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("指定发送号码")
            .setMessage("只监听这一个来源；只按号码匹配，不按关键词筛选。")
            .setView(input)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                val digits = value.filter { it.isDigit() }
                if (digits.length < 5) {
                    input.error = "请输入有效号码"
                    return@setOnClickListener
                }
                settings.targetNumber = value
                input.clearFocus()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(input.windowToken, 0)
                dialog.dismiss()
                render()
            }
        }
        dialog.show()
        input.requestFocus()
    }

    private fun editKeywords() {
        val context = requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(settings.filterKeywords)
            setSelection(text.length)
            setPadding(48, 32, 48, 16)
            hint = "会议,开会,参会,议程"
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("提醒关键词")
            .setMessage("短信正文包含任意一个关键词才会来电提醒。用逗号分隔；清空后提醒全部短信。")
            .setView(input)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                settings.filterKeywords = input.text.toString()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(input.windowToken, 0)
                dialog.dismiss()
                render()
            }
        }
        dialog.show()
        input.requestFocus()
    }

    private fun chooseInterval() {
        val values = intArrayOf(5, 10, 15, 30, 60)
        val labels = values.map { "$it 分钟" }.toTypedArray()
        val checked = values.indexOfFirst { it == settings.intervalMinutes }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("重复提醒间隔")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                settings.intervalMinutes = values[which]
                dialog.dismiss()
                resumeSchedules()
                render()
            }
            .show()
    }

    private fun chooseRingDuration() {
        val values = intArrayOf(30, 45, 60)
        val labels = values.map { "$it 秒" }.toTypedArray()
        val checked = values.indexOfFirst { it == settings.ringSeconds }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("振铃时长")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                settings.ringSeconds = values[which]
                dialog.dismiss()
                render()
            }
            .show()
    }

    private fun chooseRetention() {
        val values = intArrayOf(7, 30, 90)
        val labels = values.map { "$it 天" }.toTypedArray()
        val checked = values.indexOfFirst { it == settings.retentionDays }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("已确认记录保留")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                settings.retentionDays = values[which]
                dialog.dismiss()
                render()
            }
            .show()
    }

    private fun pauseSchedules() {
        val context = requireContext()
        TaskStore.pending(context).forEach { task ->
            ReminderScheduler.cancel(context, task.id)
            CallNotifier.cancel(context, task.id)
        }
    }

    private fun resumeSchedules() {
        val context = requireContext()
        val now = System.currentTimeMillis()
        TaskStore.pending(context).forEach { task ->
            if (task.nextRingAt <= now) {
                task.nextRingAt = now + settings.intervalMillis
                TaskStore.upsert(context, task)
            }
            ReminderScheduler.schedule(context, task)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
