package ru.ytkab0bp.slicebeam.manufacturing

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Local, opt-in store for measured fit calibration. A missing/corrupt record is always `null`;
 * callers must never interpret it as a zero or factory calibration.
 */
object CncFitCalibrationStore {
    private const val KEY_PREFIX = "cnc_fit_calibration_v1_"

    fun get(
        printerId: String,
        nozzleDiameterMm: Double,
        materialId: String,
        readString: (String) -> String?,
    ): CncFitCalibration? {
        if (!validIdentity(printerId, nozzleDiameterMm, materialId)) return null
        val encoded = readString(key(printerId, nozzleDiameterMm, materialId)) ?: return null
        val fields = encoded.split('|')
        if (fields.size != 5 || fields[0] != "1") return null
        return try {
            val recordPrinter = decodeText(fields[1]) ?: return null
            val recordNozzle = fields[2].toDouble()
            val recordMaterial = decodeText(fields[3]) ?: return null
            val inner = fields[4].substringBefore(',').toDouble()
            val outer = fields[4].substringAfter(',', "").toDouble()
            CncFitCalibration(recordPrinter, recordNozzle, recordMaterial, inner, outer)
                .takeIf { sameIdentity(it, printerId, nozzleDiameterMm, materialId) }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun put(calibration: CncFitCalibration, writeString: (String, String) -> Unit) {
        val payload = listOf(
            "1",
            encodeText(calibration.printerId.trim()),
            calibration.nozzleDiameterMm.toString(),
            encodeText(calibration.materialId.trim()),
            "${calibration.innerCompensationMm},${calibration.outerCompensationMm}",
        ).joinToString("|")
        writeString(key(calibration.printerId, calibration.nozzleDiameterMm, calibration.materialId), payload)
    }

    fun remove(
        printerId: String,
        nozzleDiameterMm: Double,
        materialId: String,
        remove: (String) -> Unit,
    ) {
        if (validIdentity(printerId, nozzleDiameterMm, materialId)) {
            remove(key(printerId, nozzleDiameterMm, materialId))
        }
    }

    private fun validIdentity(printerId: String, nozzle: Double, materialId: String): Boolean =
        printerId.isNotBlank() && materialId.isNotBlank() && nozzle.isFinite() && nozzle in 0.1..2.0

    private fun sameIdentity(
        calibration: CncFitCalibration,
        printerId: String,
        nozzleDiameterMm: Double,
        materialId: String,
    ): Boolean = calibration.printerId.trim().equals(printerId.trim(), ignoreCase = true) &&
        calibration.materialId.trim().equals(materialId.trim(), ignoreCase = true) &&
        kotlin.math.abs(calibration.nozzleDiameterMm - nozzleDiameterMm) < 0.0001

    private fun key(printerId: String, nozzle: Double, materialId: String): String {
        val identity = listOf(
            printerId.trim().lowercase(Locale.ROOT),
            // Use microns for stable keys despite harmless binary float representation noise.
            (nozzle * 1_000_000.0).toLong().toString(),
            materialId.trim().lowercase(Locale.ROOT),
        ).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(StandardCharsets.UTF_8))
        return KEY_PREFIX + digest.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    }

    private fun encodeText(value: String): String = value.toByteArray(StandardCharsets.UTF_8)
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

    private fun decodeText(value: String): String? {
        if (value.length % 2 != 0 || value.any { it.digitToIntOrNull(16) == null }) return null
        val bytes = ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        return String(bytes, StandardCharsets.UTF_8)
    }
}
