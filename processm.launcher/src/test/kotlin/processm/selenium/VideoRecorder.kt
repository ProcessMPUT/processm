package processm.selenium

import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import java.nio.file.FileSystems
import java.nio.file.Files

internal class VideoRecorder(private val obj: TakesScreenshot) {
    private var ctr = 0
    private val target = Files.createTempDirectory("processm")

    fun take() {
        val f = obj.getScreenshotAs(OutputType.FILE)
        Files.move(f.toPath(), FileSystems.getDefault().getPath(target.toString(), String.format("%04d.png", ctr)))
        ctr++
    }

    fun finish() {
        println()
        println()
        println()
        println("Screenshots were saved to $target")
        println("Go there and run: ffmpeg -framerate 2 -i %04d.png output.mp4")
        println()
        println()
        println()
    }
}