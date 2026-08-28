package com.joe.mepe.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 与桌面端 System.Text.Json 完全兼容的序列化器：
 * - DateTime → "yyyy-MM-ddTHH:mm:ss"（无时区后缀；容错解析小数秒/offset）
 * - TimeSpan → "c" 格式（"d.hh:mm:ss" / "hh:mm:ss"）
 */
object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    private val write = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val fallback = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) =
        encoder.encodeString(value.format(write))

    override fun deserialize(decoder: Decoder): LocalDateTime {
        val s = decoder.decodeString()
        return try {
            LocalDateTime.parse(s, fallback)
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(s.substringBefore('.').substringBefore('Z').substringBefore('+'), write)
            } catch (_: Exception) {
                LocalDateTime.MIN
            }
        }
    }
}

object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TimeSpan", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Duration) {
        val totalSec = value.seconds
        val days = totalSec / 86400
        val rem = totalSec % 86400
        val h = rem / 3600; val m = (rem % 3600) / 60; val sec = rem % 60
        val core = "%02d:%02d:%02d".format(h, m, sec)
        encoder.encodeString(if (days > 0) "$days.$core" else core)
    }

    override fun deserialize(decoder: Decoder): Duration {
        val s = decoder.decodeString().trim()
        return try {
            val (dPart, tPart) = if (s.contains('.')) {
                val p = s.split('.', limit = 2); p[0].toLong() to p[1]
            } else 0L to s
            val bits = tPart.split(':')
            val h = bits[0].toLong(); val m = bits.getOrNull(1)?.toLong() ?: 0; val sec = bits.getOrNull(2)?.toLong() ?: 0
            Duration.ofSeconds(dPart * 86400 + h * 3600 + m * 60 + sec)
        } catch (_: Exception) {
            Duration.ZERO
        }
    }
}
