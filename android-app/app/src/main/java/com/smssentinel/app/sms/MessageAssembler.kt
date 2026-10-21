package com.smssentinel.app.sms

import android.content.Intent
import android.provider.Telephony
import java.util.LinkedHashMap

data class IncomingSms(
    val sender: String,
    val body: String,
    val timestampMillis: Long,
)

object MessageAssembler {
    fun parse(intent: Intent): List<IncomingSms> {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return emptyList()
        val groups = LinkedHashMap<String, MutableList<android.telephony.SmsMessage>>()
        for (message in messages) {
            val sender = message.originatingAddress ?: continue
            val key = "$sender|${message.timestampMillis}"
            groups.getOrPut(key) { mutableListOf() }.add(message)
        }
        return groups.values.map { segments ->
            val first = segments.first()
            IncomingSms(
                sender = first.originatingAddress.orEmpty(),
                body = segments.joinToString(separator = "") { it.messageBody.orEmpty() },
                timestampMillis = first.timestampMillis,
            )
        }
    }
}
