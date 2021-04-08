import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.pixels.Pixel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.time.measureTimedValue

internal sealed class Pattern : OptionGroup() {

    val name: String
        get() = this::class.simpleName!!.toLowerCase()

    val choice: Pair<String, Pattern>
        get() = name to this

    fun Sequence<Pixel>.sortPixels() = fold(
        initial = PixelSequenceSorter(PixelSorter.finalIntervalFunction, PixelSorter.sortingFunction),
        operation = PixelSequenceSorter::insert
    ).colors

    abstract suspend fun sort(input: ImmutableImage): ImmutableImage

    internal object Lines : Pattern() {

        override suspend fun sort(input: ImmutableImage): ImmutableImage {
            val output = input.rotateAntiClockwise(degrees = PixelSorter.angle)

            val colors = output.columns()
                .map { GlobalScope.async { it.sortPixels().map { it.awt() } } }
                .awaitAll()

            output.mapInPlace { colors[it.x][it.y] }

            return output.rotateClockwise(degrees = PixelSorter.angle)
                .resizeTo(input.width - 2, input.height - 2)
        }
    }
}

internal suspend fun Pattern.sortTimed(input: ImmutableImage): ImmutableImage {
    TermUi.echo(message = "Sorting pixels")
    val timedValue = measureTimedValue { sort(input) }
    TermUi.echo(message = "Sorted pixels in ${timedValue.duration}")
    return timedValue.value
}