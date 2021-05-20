import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.sksamuel.scrimage.nio.ImageWriter
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter

sealed class Filetype(
    val writer: ImageWriter,
    val extension: String,
) : OptionGroup() {

    val name: String
        get() = this::class.simpleName!!.lowercase()

    val choice: Pair<String, Filetype>
        get() = name to this

    object Jpg : Filetype(writer = JpegWriter.NoCompression, extension = "jpg")

    object Png : Filetype(writer = PngWriter.NoCompression, extension = "png")
}
