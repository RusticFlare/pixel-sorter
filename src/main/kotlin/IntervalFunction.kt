import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.restrictTo
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.color.RGBColor
import com.sksamuel.scrimage.pixels.Pixel
import java.awt.Color
import kotlin.random.Random

sealed class IntervalFunction : OptionGroup() {

    val name: String
        get() = this::class.simpleName!!.toLowerCase()

    val choice: Pair<String, IntervalFunction>
        get() = name to this

    abstract fun shouldBeSorted(pixel: Pixel): Boolean

    object Lightness : IntervalFunction() {

        private val lowerThreshold by option("-l", "--lower-threshold", help = "Between 0 and 1")
            .double()
            .restrictTo(range = 0.0..1.0)
            .default(value = 0.25)

        private val upperThreshold by option("-u", "--upper-threshold", help = "Between 0 and 1")
            .double()
            .restrictTo(range = 0.0..1.0)
            .default(value = 0.8)

        override fun shouldBeSorted(pixel: Pixel) = pixel.toColor().toHSL().lightness in lowerThreshold..upperThreshold
    }

    object Random : IntervalFunction() {

        private val random = Random(seed = 0)

        private var sort = true

        override fun shouldBeSorted(pixel: Pixel) = sort.also {
            if (random.nextInt(until = PixelSorter.averageWidth) == 0) {
                sort = !sort
            }
        }
    }

    object RandomFile : IntervalFunction() {

        private val random = Random(seed = 0)

        private var sort = true

        private val randomFile by option(
            "-f",
            "--file",
            help = "A file where the brighter the pixel, the more likely it is to be sorted (use with 'randomfile')"
        ).file(mustExist = true, canBeDir = false).required()

        private val randomImage by lazy {
            randomFile.immutableImage()
                .let { if (PixelSorter.pattern is Pattern.Lines) it.rotateAntiClockwise(PixelSorter.angle) else it }
        }

        override fun shouldBeSorted(pixel: Pixel) = when (val grey = randomImage.getGrey(pixel.x, pixel.y)) {
            0 -> false
            255 -> true
            else -> sort.also {
                if (random.nextInt(until = PixelSorter.averageWidth) == 0) {
                    sort = random.nextInt(until = 256) < grey
                }
            }
        }

        private fun ImmutableImage.getGrey(x: Int, y: Int) = color(x, y).toGrayscale().gray
    }

    object None : IntervalFunction() {

        override fun shouldBeSorted(pixel: Pixel) = true
    }

    class Mask(private val intervalFunction: IntervalFunction, private val mask: ImmutableImage) : IntervalFunction() {

        override fun shouldBeSorted(pixel: Pixel) = mask.pixel(pixel.x, pixel.y).isWhite() &&
                intervalFunction.shouldBeSorted(pixel)

        private companion object {

            private val WHITE: RGBColor = RGBColor.fromAwt(Color.WHITE)

            fun Pixel.isWhite() = toColor() == WHITE
        }
    }
}
