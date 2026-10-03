package io.github.aiya000.screenshotdachshund.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Inflater

class PngWriterTest {

    /** The PNG pulled apart again: the header fields and the RGB of every pixel, row by row. */
    private class Decoded(val width: Int, val height: Int, val rows: List<List<Int>>)

    /**
     * There is no image library on this test classpath, so this reads the PNG the way a
     * decoder would: the signature, the chunks with their CRCs, the inflated scanlines
     * with their filter byte.
     */
    private fun decode(bytes: ByteArray): Decoded {
        val buffer = ByteBuffer.wrap(bytes)
        val signature = ByteArray(8).also { buffer.get(it) }
        assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), signature)

        var width = 0
        var height = 0
        val idat = ByteArrayOutputStream()
        var ended = false
        while (!ended) {
            val length = buffer.int
            val type = ByteArray(4).also { buffer.get(it) }
            val data = ByteArray(length).also { buffer.get(it) }
            val crc = buffer.int
            val expected = CRC32().apply { update(type); update(data) }.value.toInt()
            assertEquals("crc of ${String(type)}", expected, crc)
            when (String(type, Charsets.US_ASCII)) {
                "IHDR" -> {
                    val header = ByteBuffer.wrap(data)
                    width = header.int
                    height = header.int
                    assertEquals(8, header.get().toInt()) // bit depth
                    assertEquals(2, header.get().toInt()) // RGB
                }
                "IDAT" -> idat.write(data)
                "IEND" -> ended = true
            }
        }
        assertEquals(bytes.size, buffer.position())

        val inflater = Inflater().apply { setInput(idat.toByteArray()) }
        val raw = ByteArray(height * (1 + width * 3))
        var filled = 0
        while (filled < raw.size) {
            val n = inflater.inflate(raw, filled, raw.size - filled)
            if (n == 0 && (inflater.finished() || inflater.needsInput())) break
            filled += n
        }
        assertEquals(raw.size, filled)

        val rows = (0 until height).map { y ->
            val start = y * (1 + width * 3)
            assertEquals("filter of row $y", 0, raw[start].toInt())
            (0 until width).map { x ->
                val i = start + 1 + x * 3
                ((raw[i].toInt() and 0xFF) shl 16) or
                    ((raw[i + 1].toInt() and 0xFF) shl 8) or
                    (raw[i + 2].toInt() and 0xFF)
            }
        }
        return Decoded(width, height, rows)
    }

    private fun roundTrip(image: PixelRows): Decoded {
        val bytes = ByteArrayOutputStream().also { PngWriter.write(image, it) }.toByteArray()
        return decode(bytes)
    }

    @Test
    fun `every pixel survives the round trip`() {
        val pixels = IntArray(3 * 2) { i -> (i * 0x010203 + 0x7F3311) or 0xFF000000.toInt() }
        val image = ArrayPixelRows(3, 2, pixels)

        val decoded = roundTrip(image)

        assertEquals(3, decoded.width)
        assertEquals(2, decoded.height)
        assertEquals(
            (0 until 2).map { y -> (0 until 3).map { x -> pixels[y * 3 + x] and 0xFFFFFF } },
            decoded.rows,
        )
    }

    @Test
    fun `a tall image is written whole, across several IDAT chunks`() {
        val width = 7
        val height = 50_000
        val image = object : PixelRows {
            override val width = width
            override val height = height
            override fun copyRow(y: Int, out: IntArray) {
                // Noisy enough not to compress away, so that more than one IDAT is needed.
                for (x in 0 until width) out[x] = (y * 2654435761L.toInt() + x * 40503) or 0xFF000000.toInt()
            }
            override fun rowHash(y: Int): Long = y.toLong()
        }

        val decoded = roundTrip(image)

        assertEquals(height, decoded.rows.size)
        val row = IntArray(width)
        image.copyRow(49_999, row)
        assertEquals(row[6] and 0xFFFFFF, decoded.rows[49_999][6])
        image.copyRow(0, row)
        assertEquals(row[0] and 0xFFFFFF, decoded.rows[0][0])
    }
}
