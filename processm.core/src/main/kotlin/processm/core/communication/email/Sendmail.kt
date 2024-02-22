package processm.core.communication.email

import processm.helpers.getPropertyIgnoreCase
import processm.logging.loggedScope

/**
 * API for the classic Unix sendmail command. This should be the preferred way of sending emails, since in principle
 * it is much more robust than SMTP. It uses three optional properties:
 *
 * * `processm.email.sendmailExecutable` - The path to a sendmail-compatible executable. If not specified, `/usr/sbin/sendmail` is used
 * * `processm.email.defaultFrom` - The default for the sender full name, used only if there is no `From` header in the email
 * * `processm.email.envelopeSender` - The value for the envelope sender address
 */
class Sendmail {
    var sendmailExecutable: String = getPropertyIgnoreCase("processm.email.sendmailExecutable") ?: "/usr/sbin/sendmail"
    var defaultFrom: String? = getPropertyIgnoreCase("processm.email.defaultFrom")
    var envelopeSender: String? = getPropertyIgnoreCase("processm.email.envelopeSender")
    var processBuilder: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) }

    fun send(recipients: List<String>, body: ByteArray) = loggedScope { logger ->
        val cmd = mutableListOf(sendmailExecutable)
        defaultFrom?.let {
            cmd.add("-F")
            cmd.add(it)
        }
        envelopeSender?.let {
            cmd.add("-f")
            cmd.add(it)
        }
        // don´t treat a line with only a . character as the end of input
        cmd.add("-i")
        cmd.addAll(recipients)
        val process = processBuilder(cmd).start()
        try {
            process.outputStream.use { os ->
                os.write(body)
            }
        } catch (e: Throwable) {
            logger.warn("An exception was thrown while sending an email", e)
            process.destroy()
        } finally {
            // Wait indefinitely since I am not convinced timeout is needed or even reasonable
            val exitCode = process.waitFor()
            if (exitCode != 0)
                throw RuntimeException("Sending failed")
        }
    }

    fun send(email: Email) = send(email.recipients.split(EMAIL_ADDRESS_SEPARATOR), email.body)
}
