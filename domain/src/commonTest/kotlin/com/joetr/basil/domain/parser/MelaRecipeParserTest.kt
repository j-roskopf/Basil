package com.joetr.basil.domain.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MelaRecipeParserTest {
    @Test
    fun parseRecipeJson_mapsFields() {
        val json = """
            {
              "title": "Easy Instant Pot Butter Chicken",
              "text": "A fast family dinner.",
              "yield": "6",
              "prepTime": "5min",
              "cookTime": "10min",
              "link": "https://example.com/butter-chicken",
              "categories": ["Dinner", "Indian"],
              "ingredients": "1 cup tomatoes\n2 tbsp butter",
              "instructions": "Cook for 10 minutes.\nStir in cream.",
              "notes": "Serve with rice.",
              "favorite": true
            }
        """.trimIndent()

        val item = MelaRecipeParser.parseRecipeJson(json.encodeToByteArray())
        assertNotNull(item)
        assertEquals("Easy Instant Pot Butter Chicken", item.extracted.title)
        assertEquals("A fast family dinner.", item.extracted.description)
        assertEquals(6, item.extracted.servings)
        assertEquals(5, item.extracted.prepMinutes)
        assertEquals(10, item.extracted.cookMinutes)
        assertEquals("https://example.com/butter-chicken", item.extracted.sourceUrl)
        assertEquals(listOf("Dinner", "Indian"), item.extracted.tags)
        assertEquals(listOf("1 cup tomatoes", "2 tbsp butter"), item.extracted.ingredients)
        assertEquals(listOf("Cook for 10 minutes.", "Stir in cream."), item.extracted.steps.map { it.text })
        assertEquals("Serve with rice.", item.notes)
        assertTrue(item.isFavourite)
    }

    @Test
    fun parseMelaDuration_handlesHumanReadableTimes() {
        assertEquals(5, MelaRecipeParser.parseMelaDuration("5min"))
        assertEquals(15, MelaRecipeParser.parseMelaDuration("15 minutes"))
        assertEquals(90, MelaRecipeParser.parseMelaDuration("1h30m"))
        assertEquals(20, MelaRecipeParser.parseMelaDuration("PT20M"))
    }

    @Test
    fun parseRecipeJson_decodesBase64Image() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        )
        val base64 = kotlin.io.encoding.Base64.encode(png)
        val json = """
            {
              "title": "Photo recipe",
              "ingredients": "salt",
              "instructions": "mix",
              "images": ["$base64"]
            }
        """.trimIndent()

        val item = MelaRecipeParser.parseRecipeJson(json.encodeToByteArray())
        assertNotNull(item)
        assertNotNull(item.imageBytes)
        assertTrue(item.imageBytes!!.contentEquals(png))
    }

    @Test
    fun parseRecipeJson_decodesDataUriBase64Image() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val base64 = kotlin.io.encoding.Base64.encode(jpeg)
        val json = """
            {
              "title": "Photo recipe",
              "ingredients": "salt",
              "instructions": "mix",
              "images": ["data:image/jpeg;base64,$base64"]
            }
        """.trimIndent()

        val item = MelaRecipeParser.parseRecipeJson(json.encodeToByteArray())
        assertNotNull(item)
        assertTrue(item.imageBytes!!.contentEquals(jpeg))
    }

    @Test
    fun parseArchive_readsDataDescriptorZipEntries() {
        val json = """{"title":"Test Recipe","ingredients":"salt","instructions":"mix"}"""
        val archive = buildMelaStyleArchive("recipe.melarecipe", json.encodeToByteArray())
        val items = MelaRecipeParser.parseArchive(archive)
        assertEquals(1, items.size)
        assertEquals("Test Recipe", items.first().extracted.title)
    }

    private fun buildMelaStyleArchive(name: String, content: ByteArray): ByteArray {
        val nameBytes = name.encodeToByteArray()
        val flagDataDescriptor = 0x0808
        val localHeader = ByteArray(30 + nameBytes.size)
        writeUInt32Le(localHeader, 0, 0x04034b50)
        writeUInt16Le(localHeader, 4, 20) // version needed
        writeUInt16Le(localHeader, 6, flagDataDescriptor)
        writeUInt16Le(localHeader, 8, 0) // stored
        writeUInt16Le(localHeader, 10, 0)
        writeUInt16Le(localHeader, 12, 0)
        writeUInt32Le(localHeader, 14, 0xFFFFFFFFL) // crc unknown
        writeUInt32Le(localHeader, 18, 0xFFFFFFFFL) // compressed size unknown
        writeUInt32Le(localHeader, 22, 0xFFFFFFFFL) // uncompressed size unknown
        writeUInt16Le(localHeader, 26, nameBytes.size)
        writeUInt16Le(localHeader, 28, 0)
        nameBytes.copyInto(localHeader, 30)

        val localOffset = 0
        val dataOffset = localHeader.size
        val dataDescriptor = ByteArray(16)
        writeUInt32Le(dataDescriptor, 0, 0x08074b50L)
        writeUInt32Le(dataDescriptor, 8, content.size.toLong())
        writeUInt32Le(dataDescriptor, 12, content.size.toLong())

        val centralOffset = dataOffset + content.size + dataDescriptor.size
        val centralHeader = ByteArray(46 + nameBytes.size)
        writeUInt32Le(centralHeader, 0, 0x02014b50)
        writeUInt16Le(centralHeader, 4, 20)
        writeUInt16Le(centralHeader, 6, 20)
        writeUInt16Le(centralHeader, 8, flagDataDescriptor)
        writeUInt16Le(centralHeader, 10, 0)
        writeUInt16Le(centralHeader, 12, 0)
        writeUInt16Le(centralHeader, 14, 0)
        writeUInt32Le(centralHeader, 16, 0)
        writeUInt32Le(centralHeader, 20, 0xFFFFFFFFL)
        writeUInt32Le(centralHeader, 24, 0xFFFFFFFFL)
        writeUInt16Le(centralHeader, 28, nameBytes.size)
        writeUInt16Le(centralHeader, 30, 0)
        writeUInt16Le(centralHeader, 32, 0)
        writeUInt16Le(centralHeader, 34, 0)
        writeUInt16Le(centralHeader, 36, 0)
        writeUInt32Le(centralHeader, 38, 0)
        writeUInt32Le(centralHeader, 42, localOffset.toLong())
        nameBytes.copyInto(centralHeader, 46)

        val eocd = ByteArray(22)
        writeUInt32Le(eocd, 0, 0x06054b50L)
        writeUInt16Le(eocd, 4, 0)
        writeUInt16Le(eocd, 6, 0)
        writeUInt16Le(eocd, 8, 1)
        writeUInt16Le(eocd, 10, 1)
        writeUInt32Le(eocd, 12, centralHeader.size.toLong())
        writeUInt32Le(eocd, 16, centralOffset.toLong())
        writeUInt16Le(eocd, 20, 0)

        return localHeader + content + dataDescriptor + centralHeader + eocd
    }

    private fun writeUInt16Le(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun writeUInt32Le(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
