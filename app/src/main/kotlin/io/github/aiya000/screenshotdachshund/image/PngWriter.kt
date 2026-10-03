package io.github.aiya000.screenshotdachshund.image

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Writes a [PixelRows] as a PNG, one row at a time.
 *
 * `Bitmap.compress` would need the whole image as one bitmap first, and a joined capture
 * of a long page is taller than it is worth allocating. This streams the rows straight
 * into the deflater instead, so the result costs the memory of one row.
 *
 * The output is 8-bit RGB (colour type 2), no interlace, filter 0 on every scanline;
 * the alpha of the screenshots is always opaque, so nothing is lost.
 */
object PngWriter {

    private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    /** How much deflated data one IDAT chunk holds. */
    private const val IDAT_SIZE = 64 * 1024

    fun write(image: PixelRows, out: OutputStream) {
        out.write(SIGNATURE)
        writeChunk(out, "IHDR", ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).apply {
                writeInt(image.width)
                writeInt(image.height)
                writeByte(8) // bit depth
                writeByte(2) // colour type: RGB
                writeByte(0) // compression
                writeByte(0) // filter
                writeByte(0) // interlace
            }
        }.toByteArray())

        val idat = IdatStream(out)
        // A joined capture is tens of thousands of rows; the default level takes many
        // seconds on a phone for a file only somewhat smaller
        val deflater = Deflater(Deflater.BEST_SPEED)
        DeflaterOutputStream(idat, deflater, 32 * 1024).use { deflated ->
            val row = IntArray(image.width)
            val scanline = ByteArray(1 + image.width * 3)
            for (y in 0 until image.height) {
                image.copyRow(y, row)
                scanline[0] = 0 // filter type: none
                var i = 1
                for (x in 0 until image.width) {
                    val pixel = row[x]
                    scanline[i++] = (pixel shr 16).toByte()
                    scanline[i++] = (pixel shr 8).toByte()
                    scanline[i++] = pixel.toByte()
                }
                deflated.write(scanline)
            }
        }
        deflater.end()
        idat.flushChunk()

        writeChunk(out, "IEND", ByteArray(0))
    }

    private fun writeChunk(out: OutputStream, type: String, data: ByteArray, length: Int = data.size) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        DataOutputStream(out).writeInt(length)
        out.write(typeBytes)
        out.write(data, 0, length)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data, 0, length)
        }
        DataOutputStream(out).writeInt(crc.value.toInt())
    }

    /** Collects deflated bytes and writes them out as IDAT chunks of [IDAT_SIZE]. */
    private class IdatStream(private val out: OutputStream) : OutputStream() {
        private val buffer = ByteArray(IDAT_SIZE)
        private var filled = 0

        override fun write(b: Int) {
            buffer[filled++] = b.toByte()
            if (filled == buffer.size) flushChunk()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var offset = off
            var remaining = len
            while (remaining > 0) {
                val n = minOf(remaining, buffer.size - filled)
                System.arraycopy(b, offset, buffer, filled, n)
                filled += n
                offset += n
                remaining -= n
                if (filled == buffer.size) flushChunk()
            }
        }

        fun flushChunk() {
            if (filled == 0) return
            writeChunk(out, "IDAT", buffer, filled)
            filled = 0
        }
    }
}
