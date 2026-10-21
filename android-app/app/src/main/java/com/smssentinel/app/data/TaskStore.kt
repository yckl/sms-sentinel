package com.smssentinel.app.data

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.smssentinel.app.sms.NumberMatcher
import org.json.JSONArray

object TaskStore {
    private const val PREFS = "sentinel_tasks"
    private const val KEY = "tasks"

    val revision = MutableLiveData(0L)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(context: Context): List<Task> = load(context).sortedByDescending { it.receivedAt }

    @Synchronized
    fun pending(context: Context): List<Task> =
        load(context).filter { it.isPending }.sortedBy { it.receivedAt }

    @Synchronized
    fun get(context: Context, id: String): Task? = load(context).firstOrNull { it.id == id }

    @Synchronized
    fun add(
        context: Context,
        sender: String,
        body: String,
        receivedAt: Long,
        nextRingAt: Long,
        ringCount: Int = 1,
        simulated: Boolean = false,
    ): Task {
        val task = Task(
            sender = sender,
            body = body,
            receivedAt = receivedAt,
            ringCount = ringCount,
            nextRingAt = nextRingAt,
            simulated = simulated,
        )
        val list = load(context).toMutableList()
        list.add(task)
        save(context, list, sync = true)
        return task
    }

    @Synchronized
    fun upsert(context: Context, task: Task) {
        val list = load(context).toMutableList()
        val index = list.indexOfFirst { it.id == task.id }
        if (index >= 0) list[index] = task else list.add(task)
        save(context, list, sync = true)
    }

    @Synchronized
    fun confirm(context: Context, id: String, read: Boolean): Task? {
        val list = load(context).toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return null
        val task = list[index]
        task.status = if (read) TaskStatus.CONFIRMED_READ else TaskStatus.CONFIRMED_UNREAD
        task.confirmedAt = System.currentTimeMillis()
        task.nextRingAt = 0L
        list[index] = task
        save(context, list, sync = true)
        return task
    }

    @Synchronized
    fun restore(context: Context, id: String, delayMillis: Long): Task? {
        val list = load(context).toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return null
        val task = list[index]
        task.status = TaskStatus.PENDING
        task.confirmedAt = null
        task.nextRingAt = System.currentTimeMillis() + delayMillis
        list[index] = task
        save(context, list, sync = true)
        return task
    }

    @Synchronized
    fun bumpRing(context: Context, id: String, nextRingAt: Long): Task? {
        val list = load(context).toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return null
        val task = list[index]
        if (!task.isPending) return null
        task.ringCount += 1
        task.nextRingAt = nextRingAt
        list[index] = task
        save(context, list, sync = true)
        return task
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        val list = load(context).toMutableList()
        list.removeAll { it.id == id }
        save(context, list, sync = true)
    }

    @Synchronized
    fun clearConfirmed(context: Context) {
        val list = load(context).filter { it.isPending }.toMutableList()
        save(context, list, sync = true)
    }

    @Synchronized
    fun cleanup(context: Context, retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - retentionDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        val list = load(context).filter { task ->
            when {
                task.simulated -> false
                !task.isConfirmed -> true
                else -> (task.confirmedAt ?: now) >= cutoff
            }
        }.toMutableList()
        save(context, list, sync = false)
    }

    @Synchronized
    fun isDuplicate(context: Context, sender: String, receivedAt: Long, body: String): Boolean {
        val senderDigits = NumberMatcher.digits(sender)
        return load(context).any { task ->
            NumberMatcher.digits(task.sender) == senderDigits &&
                task.body == body &&
                kotlin.math.abs(task.receivedAt - receivedAt) < 10_000L
        }
    }

    private fun load(context: Context): MutableList<Task> {
        val raw = prefs(context).getString(KEY, "[]") ?: "[]"
        val list = mutableListOf<Task>()
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                list.add(Task.fromJson(array.getJSONObject(i)))
            }
        }
        return list
    }

    private fun save(context: Context, list: List<Task>, sync: Boolean) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        val editor = prefs(context).edit().putString(KEY, array.toString())
        if (sync) editor.commit() else editor.apply()
        revision.postValue((revision.value ?: 0L) + 1)
    }
}
