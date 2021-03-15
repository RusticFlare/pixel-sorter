import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.defaultByName
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.restrictTo
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.color.Colors
import com.sksamuel.scrimage.color.RGBColor
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.pixels.Pixel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

class PixelSort : CliktCommand() {

    private val inputPath by argument().file(mustExist = true, canBeDir = false)

    private val outputPath by option("-o", "--output-path", help = "Default date-time.")
        .file(mustExist = false, canBeDir = false)

    private val maskPath by option("-m", "--mask-path")
        .file(mustExist = true, canBeDir = false)

    private val angle by option("-a", "--angle", help = "Between 0 and 360. Default 0.")
        .double()
        .restrictTo(range = 0.0..360.0)
        .default(value = 0.0)

    private val intervalFunction by option("-i", "--interval-function", help = "Default lightness.")
        .groupChoice(
            "lightness" to IntervalFunction.Lightness(),
            "random" to IntervalFunction.Random(),
            "none" to IntervalFunction.None,
        ).defaultByName(name = "lightness")

    private val sortingFunction by option("-s", "--sorting-function", help = "Default lightness.")
        .groupChoice(
            "hue" to SortingFunction.HSL.Hue,
            "saturation" to SortingFunction.HSL.Saturation,
            "lightness" to SortingFunction.HSL.Lightness,
            "intensity" to SortingFunction.RGB.Intensity,
        ).defaultByName(name = "lightness")

    private val finalIntervalFunction by lazy { mask(intervalFunction) }

    override fun run() = runBlocking {
        val input = inputPath.immutableImage()
        val output = input.rotateClockwise(degrees = angle)

        val buildTimeMillis = measureTimeMillis {
            val colors = output.rows()
                .map { GlobalScope.async { it.sortPixels().map { it.awt() } } }
                .awaitAll()

            output.mapInPlace { colors[it.y][it.x] }
        }
        echo(message = "Build Image: ${buildTimeMillis}ms")

        val writeTimeMillis = measureTimeMillis {
            output.rotateAntiClockwise(degrees = angle)
                .resizeTo(input.width, input.height)
                .save()
        }
        echo(message = "Write Image: ${writeTimeMillis}ms")
    }

    private fun Array<Pixel>.sortPixels() =
        fold(RowSorter(finalIntervalFunction, sortingFunction), RowSorter::insert).colors

    private fun defaultOutputFile(): File {
        val fileName = "${
            now().format(ISO_LOCAL_DATE_TIME)
                .replace(oldValue = ".", newValue = "")
                .replace(oldValue = ":", newValue = "")
        }.jpg"
        echo(message = "Output file is '$fileName'")
        return inputPath.resolveSibling(fileName)
    }

    private fun ImmutableImage.save() = output(JpegWriter.NoCompression, outputPath ?: defaultOutputFile())

    private fun mask(intervalFunction: IntervalFunction) = maskPath?.immutableImage()?.rotateClockwise(angle)
        ?.let { IntervalFunction.Mask(intervalFunction, it) }
        ?: intervalFunction
}

fun main(args: Array<String>) = PixelSort().main(args)

class RowSorter(
    private val intervalFunction: IntervalFunction,
    private val sortingFunction: SortingFunction,
) {

    val colors = mutableListOf<RGBColor>()

    private var indexSortedTo = 0

    fun insert(pixel: Pixel) = also {
        val color = pixel.toColor()
        if (color.alpha != 0 && intervalFunction.shouldBeSorted(pixel)) {
            val index = colors.binarySearch(color, sortingFunction.comparator, indexSortedTo)
            colors.add(if (index < 0) (-(index + 1)) else index, color)
        } else {
            colors.add(color)
            indexSortedTo = colors.size
        }
    }
}

fun Int.squared() = (this * this).toDouble()

fun ImmutableImage.rotateClockwise(degrees: Double): ImmutableImage {
    val size = sqrt(width.squared() + height.squared())
    val transform = AffineTransform().apply {
        translate((size - width) / 2, (size - height) / 2)
        rotate(Math.toRadians(degrees), width / 2.0, height / 2.0)
    }
    val target = ImmutableImage.filled(size.toInt(), size.toInt(), Colors.Transparent.awt()).awt()
    target.createGraphics().drawImage(awt(), transform, null)
    return ImmutableImage.wrapAwt(target, metadata)
}

fun ImmutableImage.rotateAntiClockwise(degrees: Double): ImmutableImage {
    val transform = AffineTransform().apply {
        rotate(Math.toRadians(-degrees), width / 2.0, height / 2.0)
    }
    val target = BufferedImage(width, height, type)
    val graphics = target.createGraphics()
    graphics.drawImage(awt(), transform, null)
    return ImmutableImage.wrapAwt(target, metadata)
}

private fun File.immutableImage() = ImmutableImage.loader().fromFile(this)
