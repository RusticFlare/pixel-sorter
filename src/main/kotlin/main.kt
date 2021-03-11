import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.restrictTo
import com.sksamuel.scrimage.ImmutableImage
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
import java.util.Comparator.comparing
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis


class PixelSort : CliktCommand() {

    private val inputPath by argument().file(mustExist = true, canBeDir = false)

    private val outputPath by option("-o", "--output-path")
        .file(mustExist = false, canBeDir = false)

    private val angle by option("-a", "--angle")
        .double()
        .restrictTo(range = 0.0..360.0)
        .defaultLazy { 0.0 }

    override fun run() = runBlocking {
        val input = ImmutableImage.loader().fromFile(inputPath)
        val output = input.rot(angle)

        input.awt().createGraphics()

        val buildTimeMillis = measureTimeMillis {
            val colors = output.rows()
                .map { GlobalScope.async { it.sortPixels().map { it.awt() } } }
                .awaitAll()

            output.mapInPlace { colors[it.y][it.x] }
        }
        echo(message = "Build Image: ${buildTimeMillis}ms")

        val writeTimeMillis = measureTimeMillis {
            output.rotInv(angle)
                .resizeTo(input.width, input.height)
                .output(JpegWriter.NoCompression, outputPath ?: defaultOutputFile())
        }
        echo(message = "Write Image: ${writeTimeMillis}ms")
    }

    private fun Array<Pixel>.sortPixels() = fold(RowSorter(), RowSorter::insert).colors

    private fun defaultOutputFile(): File {
        val fileName = "${now().format(ISO_LOCAL_DATE_TIME)}.jpeg"
        echo(message = "Output file is '$fileName'")
        return inputPath.resolveSibling(fileName)
    }
}

fun main(args: Array<String>) = PixelSort().main(args)

class RowSorter {

    private val comparator = comparing<RGBColor, Float> { it.toHSL().lightness }

    val colors = mutableListOf<RGBColor>()

    private var fromIndex = 0

    fun insert(color: RGBColor) = also {
        if (color.shouldBeSorted) {
            val index = colors.binarySearch(color, comparator, fromIndex)
            colors.add(if (index < 0) (-(index + 1)) else index, color)
        } else {
            colors.add(color)
            fromIndex = colors.size
        }
    }

    private val RGBColor.shouldBeSorted: Boolean
        get() {
            val lightness = toHSL().lightness
            return 0.1 < lightness && lightness < 0.9
        }
}

fun RowSorter.insert(pixel: Pixel) = insert(pixel.toColor())

fun Int.squared() = toDouble().pow(2)

fun ImmutableImage.rot(degrees: Double): ImmutableImage {
    val size = sqrt(width.squared() + height.squared())
    val transform = AffineTransform().apply {
        rotate(Math.toRadians(degrees), width / 2.0, height / 2.0)
    }
    val target = BufferedImage(size.toInt(), size.toInt(), type)
    val graphics = target.createGraphics()
    graphics.translate((size - width) / 2, (size - height) / 2)
    graphics.drawImage(awt(), transform, null)
    return ImmutableImage.wrapAwt(target, metadata)
}

fun ImmutableImage.rotInv(degrees: Double): ImmutableImage {
    val transform = AffineTransform().apply {
        rotate(Math.toRadians(-degrees), width / 2.0, height / 2.0)
    }
    val target = BufferedImage(width, height, type)
    val graphics = target.createGraphics()
    graphics.drawImage(awt(), transform, null)
    return ImmutableImage.wrapAwt(target, metadata)
}