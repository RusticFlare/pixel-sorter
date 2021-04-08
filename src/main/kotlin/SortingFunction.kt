import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.sksamuel.scrimage.color.HSLColor
import com.sksamuel.scrimage.color.RGBColor
import java.util.Comparator.comparing

sealed class SortingFunction(comparator: Comparator<RGBColor>) : OptionGroup() {

    val comparator: Comparator<RGBColor> = comparator.reversed()

    val name: String
        get() = this::class.simpleName!!.toLowerCase()

    val choice: Pair<String, SortingFunction>
        get() = name to this

    sealed class HSL(private val fieldToCompare: HSLColor.() -> Float) :
        SortingFunction(comparing { it.toHSL().fieldToCompare() }) {

        object Hue : HSL({ hue })

        object Saturation : HSL({ saturation })

        object Lightness : HSL({ lightness })
    }

    sealed class RGB(private val fieldToCompare: RGBColor.() -> Int) :
        SortingFunction(comparing { it.fieldToCompare() }) {

        object Intensity : RGB({ red + green + blue })
    }

    internal class Reverse(sortingFunction: SortingFunction) :
        SortingFunction(sortingFunction.comparator)
}
