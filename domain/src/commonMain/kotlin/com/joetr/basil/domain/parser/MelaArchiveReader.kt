package com.joetr.basil.domain.parser

/**
 * Reads Mela [.melarecipes](https://mela.recipes/fileformat/index.html) archives.
 * Mela exports use ZIP with stored (uncompressed) entries, which keeps this reader portable.
 */
internal object MelaArchiveReader {
    fun readRecipeJsonEntries(bytes: ByteArray): List<ByteArray> {
        val entries = readCentralDirectory(bytes)
            .filter { it.name.endsWith(".melarecipe", ignoreCase = true) }
            .sortedBy { it.localHeaderOffset }
        return entries.mapIndexedNotNull { index, entry ->
            val nextLocalOffset = entries.getOrNull(index + 1)?.localHeaderOffset
            readStoredEntry(bytes, entry, nextLocalOffset)
        }
    }

    private data class CentralEntry(
        val name: String,
        val compressionMethod: Int,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long,
    )

    private fun readCentralDirectory(bytes: ByteArray): List<CentralEntry> {
        val eocdOffset = findEndOfCentralDirectory(bytes)
        if (eocdOffset < 0) return emptyList()

        val totalEntries = readUInt16Le(bytes, eocdOffset + 10)
        val centralDirOffset = readUInt32Le(bytes, eocdOffset + 16).toInt()
        if (centralDirOffset < 0 || centralDirOffset >= bytes.size) return emptyList()

        val entries = mutableListOf<CentralEntry>()
        var offset = centralDirOffset
        repeat(totalEntries) {
            if (offset + 46 > bytes.size) return entries
            if (readUInt32Le(bytes, offset) != 0x02014b50L) return entries

            val compressionMethod = readUInt16Le(bytes, offset + 10)
            val compressedSize = readUInt32Le(bytes, offset + 20)
            val uncompressedSize = readUInt32Le(bytes, offset + 24)
            val nameLength = readUInt16Le(bytes, offset + 28)
            val extraLength = readUInt16Le(bytes, offset + 30)
            val commentLength = readUInt16Le(bytes, offset + 32)
            val localHeaderOffset = readUInt32Le(bytes, offset + 42)
            val nameStart = offset + 46
            val nameEnd = nameStart + nameLength
            if (nameEnd > bytes.size) return entries

            val name = bytes.decodeToString(nameStart, nameEnd)
            entries += CentralEntry(
                name = name,
                compressionMethod = compressionMethod,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                localHeaderOffset = localHeaderOffset,
            )
            offset = nameEnd + extraLength + commentLength
        }
        return entries
    }

    private fun readStoredEntry(
        bytes: ByteArray,
        entry: CentralEntry,
        nextLocalOffset: Long?,
    ): ByteArray? {
        if (entry.compressionMethod != 0) return null
        val localOffset = entry.localHeaderOffset.toInt()
        if (localOffset + 30 > bytes.size) return null
        if (readUInt32Le(bytes, localOffset) != 0x04034b50L) return null

        val nameLength = readUInt16Le(bytes, localOffset + 26)
        val extraLength = readUInt16Le(bytes, localOffset + 28)
        val dataStart = localOffset + 30 + nameLength + extraLength
        val size = storedEntrySize(bytes, entry, dataStart, nextLocalOffset)
        if (size <= 0 || dataStart + size > bytes.size) return null
        return bytes.copyOfRange(dataStart, dataStart + size)
    }

    private fun storedEntrySize(
        bytes: ByteArray,
        entry: CentralEntry,
        dataStart: Int,
        nextLocalOffset: Long?,
    ): Int {
        if (entry.uncompressedSize != ZIP_SIZE_UNKNOWN &&
            entry.uncompressedSize <= Int.MAX_VALUE
        ) {
            return entry.uncompressedSize.toInt()
        }
        val upperBound = when {
            nextLocalOffset != null -> nextLocalOffset.toInt()
            else -> centralDirectoryOffset(bytes).takeIf { it > dataStart } ?: bytes.size
        }
        val fromDescriptor = sizeFromDataDescriptor(bytes, dataStart, upperBound)
        if (fromDescriptor != null) return fromDescriptor
        return upperBound - dataStart
    }

    private fun sizeFromDataDescriptor(
        bytes: ByteArray,
        dataStart: Int,
        upperBound: Int,
    ): Int? {
        val minScan = (upperBound - 24).coerceAtLeast(dataStart)
        for (index in upperBound - 16 downTo minScan) {
            if (readUInt32Le(bytes, index) != 0x08074b50L) continue
            val compressed = readUInt32Le(bytes, index + 8)
            val uncompressed = readUInt32Le(bytes, index + 12)
            val size = when {
                uncompressed != ZIP_SIZE_UNKNOWN && uncompressed > 0 -> uncompressed.toInt()
                compressed != ZIP_SIZE_UNKNOWN && compressed > 0 -> compressed.toInt()
                else -> continue
            }
            if (size <= 0 || size > Int.MAX_VALUE || dataStart + size > upperBound) continue
            return size
        }
        return null
    }

    private fun centralDirectoryOffset(bytes: ByteArray): Int {
        val eocdOffset = findEndOfCentralDirectory(bytes)
        if (eocdOffset < 0) return -1
        return readUInt32Le(bytes, eocdOffset + 16).toInt()
    }

    private fun findNextLocalHeader(bytes: ByteArray, from: Int): Int {
        var index = from + 1
        while (index + 4 <= bytes.size) {
            if (readUInt32Le(bytes, index) == 0x04034b50L) return index
            index++
        }
        return bytes.size
    }

    private const val ZIP_SIZE_UNKNOWN = 0xFFFFFFFFL

    private fun findEndOfCentralDirectory(bytes: ByteArray): Int {
        val maxComment = 0xFFFF
        val minOffset = (bytes.size - 22 - maxComment).coerceAtLeast(0)
        for (offset in bytes.size - 22 downTo minOffset) {
            if (readUInt32Le(bytes, offset) == 0x06054b50L) return offset
        }
        return -1
    }

    private fun readUInt16Le(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readUInt32Le(bytes: ByteArray, offset: Int): Long {
        if (offset + 4 > bytes.size) return 0L
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }
}
