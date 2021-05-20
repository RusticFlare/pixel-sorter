import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.defaultByName
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.color.Colors
import com.sksamuel.scrimage.color.RGBColor
import com.sksamuel.scrimage.pixels.Pixel
import kotlinx.coroutines.runBlocking
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Math.toRadians
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt
import kotlin.time.measureTime

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")

private const val KO_FI = "ko-fi.com/jamesbaker"


object PixelSorter : CliktCommand(
    help = "For detailed help see github.com/RusticFlare/pixel-sorter",
    epilog = "If you find this useful please consider buying me a coffee @ $KO_FI",
    printHelpOnEmptyArgs = true,
) {

    init {
        context { helpFormatter = CliktHelpFormatter(showDefaultValues = true) }
    }

    private val inputPath by argument().file(mustExist = true, canBeDir = false)

    private val outputFilename by option("-o", "--output-filename", help = "(default: DATE-TIME)")
        .defaultLazy { now().format(dateFormat) }

    private val maskPath by option(
        "-m",
        "--mask-path",
        help = "Path to a mask with the same dimensions as INPUTPATH - only the white portions will be sorted",
    ).file(mustExist = true, canBeDir = false)

    internal val angle by option("-a", "--angle", help = "Between 0 and 360")
        .double()
        .restrictTo(range = 0.0..360.0)

    internal val averageWidth by option(
        "-w",
        "--average-width",
        help = "The average pixel width of the random sections"
    ).int()
        .restrictTo(1..Int.MAX_VALUE)
        .default(400)

    private val intervalFunction by option(
        "-i",
        "--interval-function",
        help = "(default: ${IntervalFunction.Lightness.name})",
    ).groupChoice(
        IntervalFunction.Lightness.choice,
        IntervalFunction.Random.choice,
        IntervalFunction.RandomFile.choice,
        IntervalFunction.None.choice,
    ).defaultByName(name = IntervalFunction.Lightness.name)

    private val sortingFunction by option(
        "-s",
        "--sorting-function",
        help = "(default: ${SortingFunction.HSL.Lightness.name})",
    ).groupChoice(
        SortingFunction.HSL.Hue.choice,
        SortingFunction.HSL.Saturation.choice,
        SortingFunction.HSL.Lightness.choice,
        SortingFunction.RGB.Intensity.choice,
    ).defaultByName(name = SortingFunction.HSL.Lightness.name)

    private val outputFiletype by option(
        "-e",
        "--output-filetype",
        help = "(default: ${Filetype.Jpg.name})",
    ).groupChoice(
        Filetype.Jpg.choice,
        Filetype.Png.choice,
    ).defaultByName(name = Filetype.Jpg.name)

    internal val pattern by option(
        "-p",
        "--pattern",
        help = "(default: ${Pattern.Lines.name})",
    ).groupChoice(
        Pattern.Lines.choice,
        Pattern.Circles.choice,
    ).defaultByName(name = Pattern.Lines.name)

    private val reverse by option(
        "-r",
        "--reverse",
        help = "Reverse the sort"
    ).flag()

    internal val finalIntervalFunction by lazy { mask(intervalFunction) }

    internal val finalSortingFunction by lazy {
        if (reverse) SortingFunction.Reverse(sortingFunction) else sortingFunction
    }

    override fun run() = runBlocking {
        val input = inputPath.immutableImage()
        val output = pattern.sortTimed(input)

        echo(message = "Saving image")
        val saveDuration = measureTime { output.save() }
        echo(message = "Saved in $saveDuration")
        echo(message = "Buy me a coffee @ $KO_FI")
    }

    private fun ImmutableImage.save(): File? {
        val filename = "$outputFilename.${outputFiletype.extension}"
        echo(message = "Filename: $filename")
        return output(outputFiletype.writer, inputPath.resolveSibling(relative = filename))
    }

    private fun mask(intervalFunction: IntervalFunction) = maskPath?.immutableImage()
        ?.let { if (pattern is Pattern.Lines) it.rotateAntiClockwise(angle ?: 0.0) else it }
        ?.let { IntervalFunction.Mask(intervalFunction, it) }
        ?: intervalFunction
}

fun main(args: Array<String>) = PixelSorter.main(args)

class PixelSequenceSorter(
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

fun ImmutableImage.rotateAntiClockwise(degrees: Double): ImmutableImage {
    val size = sqrt(width.squared() + height.squared())
    val transform = AffineTransform().apply {
        translate((size - width) / 2, (size - height) / 2)
        rotate(toRadians(-degrees), width / 2.0, height / 2.0)
    }
    val target = ImmutableImage.filled(size.toInt(), size.toInt(), Colors.Transparent.awt()).awt()
    target.createGraphics().drawImage(awt(), transform, null)
    return ImmutableImage.wrapAwt(target, metadata)
}

fun ImmutableImage.rotateClockwise(degrees: Double): ImmutableImage {
    val transform = AffineTransform().apply {
        rotate(toRadians(degrees), width / 2.0, height / 2.0)
    }
    val target = BufferedImage(width, height, type)
    target.createGraphics().drawImage(awt(), transform, null)
    return ImmutableImage.wrapAwt(target, metadata)
}

internal fun File.immutableImage() = ImmutableImage.loader().fromFile(this)
