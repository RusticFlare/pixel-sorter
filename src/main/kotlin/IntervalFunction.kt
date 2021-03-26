import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
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

        private val averageWidth by option(
            "-w",
            "--average-width",
            help = "The average pixel width of the random sections"
        ).int()
            .restrictTo(1..Int.MAX_VALUE)
            .default(400)

        override fun shouldBeSorted(pixel: Pixel) = sort.also {
            if (random.nextInt(until = averageWidth) == 0) {
                sort = !sort
            }
        }
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
