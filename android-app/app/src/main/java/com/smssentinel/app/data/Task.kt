package com.smssentinel.app.data

import org.json.JSONObject
import java.util.UUID

enum class TaskStatus {
    PENDING,
    CONFIRMED_READ,
    CONFIRMED_UNREAD;

    val isConfirmed: Boolean get() = this != PENDING
}

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val body: String,
    val receivedAt: Long,
    var status: TaskStatus = TaskStatus.PENDING,
    var confirmedAt: Long? = null,
    var ringCount: Int = 0,
    var nextRingAt: Long = 0L,
    val simulated: Boolean = false,
) {
    val isPending: Boolean get() = status == TaskStatus.PENDING
    val isConfirmed: Boolean get() = status.isConfirmed

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sender", sender)
        put("body", body)
        put("receivedAt", receivedAt)
        put("status", status.name)
        put("confirmedAt", confirmedAt ?: JSONObject.NULL)
        put("ringCount", ringCount)
        put("nextRingAt", nextRingAt)
        put("simulated", simulated)
    }

    companion object {
        fun fromJson(json: JSONObject): Task = Task(
            id = json.optString("id"),
            sender = json.optString("sender"),
            body = json.optString("body"),
            receivedAt = json.optLong("receivedAt"),
            status = runCatching { TaskStatus.valueOf(json.optString("status")) }
                .getOrDefault(TaskStatus.PENDING),
            confirmedAt = if (json.isNull("confirmedAt")) null else json.optLong("confirmedAt"),
            ringCount = json.optInt("ringCount"),
            nextRingAt = json.optLong("nextRingAt"),
            simulated = json.optBoolean("simulated"),
        )
    }
}
