package com.smssentinel.app.sms

object NumberMatcher {
    fun digits(value: String?): String = value.orEmpty().filter { it.isDigit() }

    fun matches(sender: String?, target: String): Boolean {
        val senderDigits = digits(sender)
        val targetDigits = digits(target)
        if (senderDigits.isEmpty() || targetDigits.isEmpty()) return false
        if (senderDigits == targetDigits) return true
        return stripCountryCode(senderDigits) == stripCountryCode(targetDigits)
    }

    private fun stripCountryCode(number: String): String = when {
        number.startsWith("0086") -> number.drop(4)
        number.startsWith("86") && number.length > 11 -> number.drop(2)
        else -> number
    }
}
