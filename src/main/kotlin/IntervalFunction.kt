import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.restrictTo
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.color.RGBColor
import com.sksamuel.scrimage.pixels.Pixel
import java.awt.Color
import kotlin.random.Random

sealed class IntervalFunction : OptionGroup() {

    abstract fun shouldBeSorted(pixel: Pixel): Boolean

    class Lightness : IntervalFunction() {

        private val lowerThreshold by option("-l", "--lower-threshold", help = "Between 0 and 1. Default 0.25.")
            .double()
            .restrictTo(range = 0.0..1.0)
            .default(value = 0.25)

        private val upperThreshold by option("-u", "--upper-threshold", help = "Between 0 and 1. Default 0.8.")
            .double()
            .restrictTo(range = 0.0..1.0)
            .default(value = 0.8)

        override fun shouldBeSorted(pixel: Pixel) = pixel.toColor().toHSL().lightness in lowerThreshold..upperThreshold
    }

    class Random : IntervalFunction() {

        private val random = Random(0)

        private var sort = true

        override fun shouldBeSorted(pixel: Pixel) = sort.also { if (random.nextInt(until = 600) == 1) sort = !sort }
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
