package deckers.thibault.aves.utils

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import android.util.Half
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.nio.ByteOrder


object BitmapConversion {

    private const val MAX_2_BITS_FLOAT = 0x3.toFloat()
    const val MAX_8_BITS_FLOAT = 0xff.toFloat()
    private const val MAX_10_BITS_FLOAT = 0x3ff.toFloat()

    // bytes per pixel with different bitmap config
    private const val BPP_ALPHA_8 = 1
    private const val BPP_RGB_565 = 2
    private const val BPP_ARGB_8888 = 4
    private const val BPP_RGBA_1010102 = 4
    private const val BPP_RGBA_F16 = 8
    private const val BPP_DART_RGBA_FLOAT32 = 16

    const val CONFIG_ANDROID_ALPHA_8 = 0
    const val CONFIG_ANDROID_RGB_565 = 1
    const val CONFIG_ANDROID_ARGB_8888 = 2
    const val CONFIG_ANDROID_RGBA_F16 = 3
    const val CONFIG_ANDROID_RGBA_1010102 = 4
    const val CONFIG_DART_RGBA_FLOAT32 = 5

    fun toCustomConfig(config: Bitmap.Config?): Int {
        return when (config) {
            Bitmap.Config.ALPHA_8 -> CONFIG_ANDROID_ALPHA_8
            Bitmap.Config.RGB_565 -> CONFIG_ANDROID_RGB_565
            Bitmap.Config.ARGB_8888 -> CONFIG_ANDROID_ARGB_8888
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.RGBA_F16) {
                    CONFIG_ANDROID_RGBA_F16
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && config == Bitmap.Config.RGBA_1010102) {
                    CONFIG_ANDROID_RGBA_1010102
                } else {
                    // default
                    CONFIG_ANDROID_ARGB_8888
                }
            }
        }
    }

    fun getBytePerPixel(config: Int): Int {
        return when (config) {
            CONFIG_ANDROID_ALPHA_8 -> BPP_ALPHA_8
            CONFIG_ANDROID_RGB_565 -> BPP_RGB_565
            CONFIG_ANDROID_ARGB_8888 -> BPP_ARGB_8888
            CONFIG_ANDROID_RGBA_F16 -> BPP_RGBA_F16
            CONFIG_ANDROID_RGBA_1010102 -> BPP_RGBA_1010102
            CONFIG_DART_RGBA_FLOAT32 -> BPP_DART_RGBA_FLOAT32
            else -> BPP_ARGB_8888
        }
    }

    // convert bytes, without reallocation:
    // - from original color space to the connector destination.
    @RequiresApi(Build.VERSION_CODES.O)
    fun fromArgb8888ToArgb8888(
        bytes: ByteArray,
        connector: ColorSpace.Connector,
        start: Int = 0,
        end: Int = bytes.size
    ): ByteArray {
        // unpacking from ARGB_8888 and packing to ARGB_8888
        // stored as [3,2,1,0] -> [AAAAAAAA BBBBBBBB GGGGGGGG RRRRRRRR]
        for (i in start..<end step BPP_ARGB_8888) {
            // mask with `0xff` to yield values in [0, 255], instead of [-128, 127]
            val iB = bytes[i + 2].toInt() and 0xff
            val iG = bytes[i + 1].toInt() and 0xff
            val iR = bytes[i].toInt() and 0xff

            val floats = connector.transform(iR / MAX_8_BITS_FLOAT, iG / MAX_8_BITS_FLOAT, iB / MAX_8_BITS_FLOAT)
            val dstR = (floats[0] * 255.0f + 0.5f).toInt()
            val dstG = (floats[1] * 255.0f + 0.5f).toInt()
            val dstB = (floats[2] * 255.0f + 0.5f).toInt()

            // keep alpha as it is, in `bytes[i + 3]`
            bytes[i + 2] = dstB.toByte()
            bytes[i + 1] = dstG.toByte()
            bytes[i] = dstR.toByte()
        }

        return bytes
    }

    // convert bytes, without reallocation:
    // - from config ARGB_8888 to RGBA_1010102,
    // - from original color space to the connector destination.
    @RequiresApi(Build.VERSION_CODES.O)
    fun fromArgb8888ToRgba1010102(
        bytes: ByteArray,
        connector: ColorSpace.Connector,
        start: Int = 0,
        end: Int = bytes.size,
    ): ByteArray {
        // unpacking from ARGB_8888 and packing to RGBA_1010102
        // stored as [3,2,1,0] -> [AAAAAAAA BBBBBBBB GGGGGGGG RRRRRRRR]
        for (i in start..<end step BPP_ARGB_8888) {
            // mask with `0xff` to yield values in [0, 255], instead of [-128, 127]
            val iA = bytes[i + 3].toInt() and 0xff
            val iB = bytes[i + 2].toInt() and 0xff
            val iG = bytes[i + 1].toInt() and 0xff
            val iR = bytes[i].toInt() and 0xff

            val floats = connector.transform(iR / MAX_8_BITS_FLOAT, iG / MAX_8_BITS_FLOAT, iB / MAX_8_BITS_FLOAT)
            val dstR = (floats[0] * MAX_10_BITS_FLOAT + 0.5f).toInt()
            val dstG = (floats[1] * MAX_10_BITS_FLOAT + 0.5f).toInt()
            val dstB = (floats[2] * MAX_10_BITS_FLOAT + 0.5f).toInt()
            val iA2 = (iA / MAX_8_BITS_FLOAT * MAX_2_BITS_FLOAT + 0.5f).toInt()

            // packing to RGBA_1010102
            // stored as [3,2,1,0] -> [AABBBBBB BBBBGGGG GGGGGGRR RRRRRRRR]
            bytes[i + 3] = (((iA2 and 0x3) shl 6) or ((dstB and 0x3f0) shr 4)).toByte()
            bytes[i + 2] = (((dstB and 0x00f) shl 4) or ((dstG and 0x3c0) shr 6)).toByte()
            bytes[i + 1] = (((dstG and 0x03f) shl 2) or ((dstR and 0x300) shr 8)).toByte()
            bytes[i] = (dstR and 0x0ff).toByte()
        }

        return bytes
    }

    /**
     * Converts 8-bit Android bitmap bytes to Dart RGBA float32 while applying
     * the supplied color-space connector before an optional gain-map transform.
     *
     * Float transport is important when the connector destination is extended
     * sRGB because wide-gamut colors can require components outside [0, 1].
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun fromArgb8888ToDartRgbaFloat32(
        bytes: ByteArray,
        connector: ColorSpace.Connector,
        start: Int = 0,
        end: Int = bytes.size,
        gainmapPixelTransformer: PixelTransformer?
    ): ByteArray {
        val pixelCount = (end - start) / BPP_ARGB_8888
        val dstByteBuffer = ByteBuffer.allocate(pixelCount * BPP_DART_RGBA_FLOAT32 + BitmapUtils.RAW_BYTES_TRAILER_LENGTH)
        // match byte order expected on the Dart side
        dstByteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (i in start..<end step BPP_ARGB_8888) {
            // stored as [3,2,1,0] -> [AAAAAAAA BBBBBBBB GGGGGGGG RRRRRRRR]
            val iA = bytes[i + 3].toInt() and 0xff
            val iB = bytes[i + 2].toInt() and 0xff
            val iG = bytes[i + 1].toInt() and 0xff
            val iR = bytes[i].toInt() and 0xff

            var floats = connector.transform(iR / MAX_8_BITS_FLOAT, iG / MAX_8_BITS_FLOAT, iB / MAX_8_BITS_FLOAT)
            if (gainmapPixelTransformer != null) {
                val pixelIndex = (i - start) / BPP_ARGB_8888
                floats = gainmapPixelTransformer(pixelIndex, floats)
            }

            dstByteBuffer.putFloat(floats[0])
            dstByteBuffer.putFloat(floats[1])
            dstByteBuffer.putFloat(floats[2])
            dstByteBuffer.putFloat(iA / MAX_8_BITS_FLOAT)
        }

        return dstByteBuffer.array()
    }

    /**
     * Converts Android RGBA_F16 directly to Dart RGBA float32, preserving the
     * source precision/range instead of quantizing through ARGB_8888.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun fromRgbaf16ToDartRgbaFloat32(
        bytes: ByteArray,
        connector: ColorSpace.Connector,
        start: Int = 0,
        end: Int = bytes.size,
    ): ByteArray {
        val pixelCount = (end - start) / BPP_RGBA_F16
        val dstByteBuffer = ByteBuffer.allocate(pixelCount * BPP_DART_RGBA_FLOAT32 + BitmapUtils.RAW_BYTES_TRAILER_LENGTH)
        dstByteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (i in start..<end step BPP_RGBA_F16) {
            val hA = Half((((bytes[i + 7].toInt() and 0xff) shl 8) or (bytes[i + 6].toInt() and 0xff)).toShort())
            val hB = Half((((bytes[i + 5].toInt() and 0xff) shl 8) or (bytes[i + 4].toInt() and 0xff)).toShort())
            val hG = Half((((bytes[i + 3].toInt() and 0xff) shl 8) or (bytes[i + 2].toInt() and 0xff)).toShort())
            val hR = Half((((bytes[i + 1].toInt() and 0xff) shl 8) or (bytes[i].toInt() and 0xff)).toShort())

            val floats = connector.transform(hR.toFloat(), hG.toFloat(), hB.toFloat())
            dstByteBuffer.putFloat(floats[0])
            dstByteBuffer.putFloat(floats[1])
            dstByteBuffer.putFloat(floats[2])
            dstByteBuffer.putFloat(hA.toFloat())
        }

        return dstByteBuffer.array()
    }

    /**
     * Converts Android RGBA_1010102 directly to Dart RGBA float32, retaining
     * 10-bit RGB precision instead of reducing the channels to 8 bits first.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun fromRgba1010102ToDartRgbaFloat32(
        bytes: ByteArray,
        connector: ColorSpace.Connector,
        start: Int = 0,
        end: Int = bytes.size,
    ): ByteArray {
        val pixelCount = (end - start) / BPP_RGBA_1010102
        val dstByteBuffer = ByteBuffer.allocate(pixelCount * BPP_DART_RGBA_FLOAT32 + BitmapUtils.RAW_BYTES_TRAILER_LENGTH)
        dstByteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (i in start..<end step BPP_RGBA_1010102) {
            val i3 = bytes[i + 3].toInt() and 0xff
            val i2 = bytes[i + 2].toInt() and 0xff
            val i1 = bytes[i + 1].toInt() and 0xff
            val i0 = bytes[i].toInt() and 0xff

            val iA = (i3 and 0xc0) shr 6
            val iB = ((i3 and 0x3f) shl 4) or ((i2 and 0xf0) shr 4)
            val iG = ((i2 and 0x0f) shl 6) or ((i1 and 0xfc) shr 2)
            val iR = ((i1 and 0x03) shl 8) or i0

            val floats = connector.transform(iR / MAX_10_BITS_FLOAT, iG / MAX_10_BITS_FLOAT, iB / MAX_10_BITS_FLOAT)
            dstByteBuffer.putFloat(floats[0])
            dstByteBuffer.putFloat(floats[1])
            dstByteBuffer.putFloat(floats[2])
            dstByteBuffer.putFloat(iA / MAX_2_BITS_FLOAT)
        }

        return dstByteBuffer.array()
    }

    // Legacy lossy conversion retained for compatibility/fallback callers.
    // Prefer `fromRgbaf16ToDartRgbaFloat32` in fidelity-sensitive paths.
    @RequiresApi(Build.VERSION_CODES.O)
    fun fromRgbaf16ToArgb8888(
        bytes: ByteArray,
        connector: ColorSpace.Connector,
        start: Int = 0,
        end: Int = bytes.size
    ): ByteArray {
        val indexDivider = BPP_RGBA_F16 / BPP_ARGB_8888
        for (i in start..<end step BPP_RGBA_F16) {
            val i7 = bytes[i + 7].toInt()
            val i6 = bytes[i + 6].toInt()
            val i5 = bytes[i + 5].toInt()
            val i4 = bytes[i + 4].toInt()
            val i3 = bytes[i + 3].toInt()
            val i2 = bytes[i + 2].toInt()
            val i1 = bytes[i + 1].toInt()
            val i0 = bytes[i].toInt()

            val hA = Half((((i7 and 0xff) shl 8) or (i6 and 0xff)).toShort())
            val hB = Half((((i5 and 0xff) shl 8) or (i4 and 0xff)).toShort())
            val hG = Half((((i3 and 0xff) shl 8) or (i2 and 0xff)).toShort())
            val hR = Half((((i1 and 0xff) shl 8) or (i0 and 0xff)).toShort())

            val floats = connector.transform(hR.toFloat(), hG.toFloat(), hB.toFloat())
            val dstR = (floats[0] * MAX_8_BITS_FLOAT + 0.5f).toInt()
            val dstG = (floats[1] * MAX_8_BITS_FLOAT + 0.5f).toInt()
            val dstB = (floats[2] * MAX_8_BITS_FLOAT + 0.5f).toInt()
            val alpha = (hA.toFloat() * MAX_8_BITS_FLOAT + 0.5f).toInt()

            val dstI = i / indexDivider
            bytes[dstI + 3] = alpha.toByte()
            bytes[dstI + 2] = dstB.toByte()
            bytes[dstI + 1] = dstG.toByte()
            bytes[dstI] = dstR.toByte()
        }

        val newConfigByteCount = end / indexDivider
        return bytes.sliceArray(0..<newConfigByteCount + BitmapUtils.RAW_BYTES_TRAILER_LENGTH)
    }

    // Legacy lossy conversion retained for compatibility/fallback callers.
    // Prefer `fromRgba1010102ToDartRgbaFloat32` in fidelity-sensitive paths.
    @RequiresApi(Build.VERSION_CODES.O)
    fun fromRgba1010102ToArgb8888(
        bytes: ByteArray,
        connector: ColorSpace.Connector,
        start: Int = 0,
        end: Int = bytes.size
    ): ByteArray {
        val alphaFactor = MAX_8_BITS_FLOAT / MAX_2_BITS_FLOAT

        for (i in start..<end step BPP_RGBA_1010102) {
            val i3 = bytes[i + 3].toInt()
            val i2 = bytes[i + 2].toInt()
            val i1 = bytes[i + 1].toInt()
            val i0 = bytes[i].toInt()

            val iA = ((i3 and 0xc0) shr 6)
            val iB = ((i3 and 0x3f) shl 4) or ((i2 and 0xf0) shr 4)
            val iG = ((i2 and 0x0f) shl 6) or ((i1 and 0xfc) shr 2)
            val iR = ((i1 and 0x03) shl 8) or ((i0 and 0xff) shr 0)

            val floats = connector.transform(iR / MAX_10_BITS_FLOAT, iG / MAX_10_BITS_FLOAT, iB / MAX_10_BITS_FLOAT)
            val dstR = (floats[0] * MAX_8_BITS_FLOAT + 0.5f).toInt()
            val dstG = (floats[1] * MAX_8_BITS_FLOAT + 0.5f).toInt()
            val dstB = (floats[2] * MAX_8_BITS_FLOAT + 0.5f).toInt()
            val alpha = (iA * alphaFactor + 0.5f).toInt()

            bytes[i + 3] = alpha.toByte()
            bytes[i + 2] = dstB.toByte()
            bytes[i + 1] = dstG.toByte()
            bytes[i] = dstR.toByte()
        }

        return bytes
    }
}
