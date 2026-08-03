package com.joetr.basil.feature.scan

public data class ScanCaptureResult(
    val ocrText: String,
    val imageBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScanCaptureResult) return false
        return ocrText == other.ocrText && imageBytes.contentEquals(other.imageBytes)
    }

    override fun hashCode(): Int {
        var result = ocrText.hashCode()
        result = 31 * result + imageBytes.contentHashCode()
        return result
    }
}
