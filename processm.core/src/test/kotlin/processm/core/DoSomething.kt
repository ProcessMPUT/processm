package processm.core

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import java.time.Duration
import kotlin.test.Test

class DoSomething {

    @Test
    fun test() {
        val service =
            ChromeDriverService.Builder().usingDriverExecutable(File("/home/processm/pseudochrome.sh")).build()
        val driver = ChromeDriver(service, ChromeOptions().apply {
            addArguments("--window-size=1920,1080")
            addArguments("--headless=new")
        })
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500))
        driver.get("http://www.cs.put.poznan.pl/")
        driver.close()
    }
}