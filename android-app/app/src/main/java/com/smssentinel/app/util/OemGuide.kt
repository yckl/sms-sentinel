package com.smssentinel.app.util

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object OemGuide {
    fun show(context: Context) {
        val message = """
            不同厂商会限制后台短信广播和自启动，请把下面几项都打开：

            小米 / Redmi（HyperOS）
            · 设置 → 应用设置 → 应用管理 → 重要短信哨兵 → 自启动：开启
            · 省电策略：无限制
            · 通知管理 → 锁屏通知 / 悬浮通知：开启
            · 最近任务里下拉该应用并锁定

            OPPO / 一加 / realme
            · 设置 → 电池 → 应用耗电管理 → 允许后台运行、允许自启动
            · 通知 → 锁屏通知：开启

            vivo / iQOO
            · 设置 → 电池 → 后台耗电管理 → 允许后台高耗电
            · 应用与权限 → 权限管理 → 自启动：开启

            荣耀 / 华为
            · 设置 → 应用 → 应用启动管理 → 重要短信哨兵 → 手动管理
            · 允许自启动、关联启动、后台活动

            原生 Android / Pixel / 三星
            · 设置 → 应用 → 重要短信哨兵 → 电池 → 不受限制
            · 关闭“暂停未使用应用”

            提示：App 内的“后台监听服务”显示“运行中”只代表前台服务已启动，厂商仍可能强制冻结；请务必完成上面的系统设置。
        """.trimIndent()

        MaterialAlertDialogBuilder(context)
            .setTitle("厂商保活设置")
            .setMessage(message)
            .setPositiveButton("打开自启动设置") { _, _ -> Permissions.openAutostartSettings(context) }
            .setNeutralButton("应用详情") { _, _ -> Permissions.openAppDetails(context) }
            .setNegativeButton("关闭", null)
            .show()
    }
}
