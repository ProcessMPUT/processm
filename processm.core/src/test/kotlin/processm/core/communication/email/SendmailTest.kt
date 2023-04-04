package processm.core.communication.email

import io.mockk.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SendmailTest {

    @Test
    fun `send email successfully`() {
        val dataStream = ByteArrayOutputStream()
        val pb = mockk<ProcessBuilder> {
            every { start() } returns mockk {
                every { outputStream } returns dataStream
                every { waitFor() } returns 0
            }
        }
        Sendmail().apply {
            sendmailExecutable = "/nonexisting/sendmail"
            defaultFrom = "ProcessM Software"
            envelopeSender = "envelope@example.com"
            processBuilder = { cmd ->
                assertEquals(
                    listOf(
                        "/nonexisting/sendmail",
                        "-F",
                        "ProcessM Software",
                        "-f",
                        "envelope@example.com",
                        "-i",
                        "test1@example.com",
                        "test2@example.com"
                    ),
                    cmd
                )
                pb
            }
        }.send(listOf("test1@example.com", "test2@example.com"), "This is a test".toByteArray())
        verify(exactly = 1) { pb.start() }
        assertEquals("This is a test", dataStream.toByteArray().decodeToString())
    }

    @Test
    fun `fail while sending email`() {
        val dataStream = ByteArrayOutputStream()
        val pb = mockk<ProcessBuilder> {
            every { start() } returns mockk {
                every { outputStream } returns dataStream
                every { waitFor() } returns 1
            }
        }
        assertFailsWith<RuntimeException> {
            Sendmail().apply {
                processBuilder = { pb }
            }.send(listOf("test1@example.com", "test2@example.com"), "This is a test".toByteArray())
        }
        verify(exactly = 1) { pb.start() }
    }

    @Test
    fun `broken pipe while sending email`() {
        val pb = mockk<ProcessBuilder> {
            every { start() } returns mockk {
                every { outputStream } returns mockk {
                    every { write(any<ByteArray>()) } throws IOException()
                    every { close() } just Runs
                }
                every { waitFor() } returns 0
            }
        }
        assertFailsWith<RuntimeException> {
            Sendmail().apply {
                processBuilder = { pb }
            }.send(listOf("test1@example.com", "test2@example.com"), "This is a test".toByteArray())
        }
        verify(exactly = 1) { pb.start() }
    }
}