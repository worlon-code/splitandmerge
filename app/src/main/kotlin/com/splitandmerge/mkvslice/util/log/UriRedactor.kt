package com.splitandmerge.mkvslice.util.log

import java.security.MessageDigest
import java.util.regex.Pattern

object UriRedactor {
    private val regex = Pattern.compile("\\b(content|file|tree|document)://([^\\s\"'`<>]+)")

    fun redact(message: String): String {
        val matcher = regex.matcher(message)
        if (!matcher.find()) {
            return message
        }
        val sb = StringBuffer()
        do {
            val scheme = matcher.group(1) ?: ""
            val fullUri = matcher.group(0) ?: ""
            val hash = sha256Prefix(fullUri)
            matcher.appendReplacement(sb, "$scheme://$hash/")
        } while (matcher.find())
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun sha256Prefix(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }.take(8)
    }
}
