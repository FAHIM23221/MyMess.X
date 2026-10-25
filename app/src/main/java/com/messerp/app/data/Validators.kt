package com.messerp.app.data

object Validators {
    private val PHONE = Regex("^01[3-9][0-9]{8}$")

    /** Keeps digits only (Bengali digits are converted) and handles a +880 prefix. */
    fun normalizePhone(raw: String): String {
        var d = raw.mapNotNull { it.digitToIntOrNull() }.joinToString("")
        if (d.startsWith("8801") && d.length == 13) d = d.substring(2)
        return d
    }

    fun isValidPhone(phone: String): Boolean = PHONE.matches(phone)
    fun isValidPassword(password: String): Boolean = password.length >= 8
    fun isValidName(name: String): Boolean = name.trim().length in 2..60
    fun normalizeInvite(code: String): String = code.trim().uppercase()
}
