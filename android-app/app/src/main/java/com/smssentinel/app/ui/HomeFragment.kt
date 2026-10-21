package com.smssentinel.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.smssentinel.app.IncomingCallActivity
import com.smssentinel.app.PermissionActivity
import com.smssentinel.app.R
import com.smssentinel.app.data.SettingsStore
import com.smssentinel.app.data.TaskStore
import com.smssentinel.app.databinding.FragmentHomeBinding
import com.smssentinel.app.util.Permissions
import com.smssentinel.app.util.TimeUtils

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        TaskStore.revision.observe(viewLifecycleOwner) { render() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val context = requireContext()
        val settings = SettingsStore(context)
        val hasSms = Permissions.hasSms(context)
        val listening = settings.enabled && hasSms

        binding.tvNumber.text = settings.targetNumber
        when {
            listening -> {
                binding.tvStatusPill.text = "● ${getString(R.string.status_listening)}"
                binding.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_green)
                binding.tvStatusPill.setTextColor(Color.parseColor("#C8F7D6"))
                binding.tvStatusHint.text = if (settings.filterEnabled) {
                    "唯一监听号码 · 含关键词才提醒"
                } else {
                    getString(R.string.only_number)
                }
            }
            !settings.enabled -> {
                binding.tvStatusPill.text = "● ${getString(R.string.status_paused)}"
                binding.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_neutral)
                binding.tvStatusPill.setTextColor(Color.parseColor("#E5E5EA"))
                binding.tvStatusHint.text = "监听已关闭 · 请在设置中重新开启"
            }
            else -> {
                binding.tvStatusPill.text = "● ${getString(R.string.status_unavailable)}"
                binding.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_coral)
                binding.tvStatusPill.setTextColor(Color.parseColor("#FFD1CE"))
                binding.tvStatusHint.text = "缺少短信接收权限 · 点击下方检查"
            }
        }

        val pending = TaskStore.pending(context)
        binding.tvPendingCount.text = "${pending.size} 条"

        val notifications = Permissions.hasNotifications(context)
        val fullScreen = Permissions.canUseFullScreen(context)
        val overlay = Permissions.canDrawOverlays(context)
        val wakeIssue = when {
            !notifications -> "通知权限未开启，锁屏不会提醒"
            !fullScreen -> "全屏来电未授权，锁屏可能不会唤醒"
            !overlay -> "悬浮来电未授权，其他应用内只显示通知"
            else -> null
        }
        binding.wakeWarning.visibility = if (wakeIssue != null) View.VISIBLE else View.GONE
        binding.tvWakeWarning.text = wakeIssue.orEmpty()
        binding.btnWakeFix.setOnClickListener {
            when {
                !notifications -> startActivity(Intent(context, PermissionActivity::class.java))
                !fullScreen -> Permissions.openFullScreenSettings(context)
                else -> Permissions.openOverlaySettings(context)
            }
        }

        val nextAt = if (settings.enabled) pending.minOfOrNull { it.nextRingAt } ?: 0L else 0L
        binding.tvNextRing.text = TimeUtils.nextRing(nextAt)

        val latest = pending.maxByOrNull { it.receivedAt }
        if (latest == null) {
            binding.taskCard.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.taskCard.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            binding.tvTaskLabel.text = "重要短信 · 待确认"
            binding.tvTaskBody.text = latest.body
            binding.tvTaskMetaLeft.text = "已响铃 ${latest.ringCount.coerceAtLeast(1)} 次"
            binding.tvTaskMetaRight.text = "下次 ${TimeUtils.nextRing(latest.nextRingAt)}"
            binding.taskCard.setOnClickListener {
                startActivity(
                    Intent(context, IncomingCallActivity::class.java)
                        .putExtra(IncomingCallActivity.EXTRA_TASK_ID, latest.id)
                )
            }
        }

        binding.btnSimulate.setOnClickListener { simulateCall() }
        binding.btnPermission.setOnClickListener {
            startActivity(Intent(context, PermissionActivity::class.java))
        }
    }

    private fun simulateCall() {
        val context = requireContext()
        val settings = SettingsStore(context)
        val task = TaskStore.add(
            context = context,
            sender = settings.targetNumber,
            body = settings.simulatedBody(),
            receivedAt = System.currentTimeMillis(),
            nextRingAt = 0L,
            ringCount = 1,
            simulated = true,
        )
        startActivity(
            Intent(context, IncomingCallActivity::class.java)
                .putExtra(IncomingCallActivity.EXTRA_TASK_ID, task.id)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
