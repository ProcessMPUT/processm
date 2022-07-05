package processm.enhancement.simulation

import processm.core.log.Helpers

object Logs {
    val basic = Helpers.logFromString(
        """a b c d
            a b c d
            a b c d
            a c b d
            a c b d
            a e d""".trimIndent()
    )

    /**
     * The log from Table 8.1 in the Process Mining: Data Science in Action book.
     */
    val table81 = Helpers.logFromString(buildString {
        repeat(455) { appendLine("a c d e h") }
        repeat(191) { appendLine("a b d e g") }
        repeat(177) { appendLine("a d c e h") }
        repeat(144) { appendLine("a b d e h") }
        repeat(111) { appendLine("a c d e g") }
        repeat(82) { appendLine("a d c e g") }
        repeat(56) { appendLine("a d b e h") }
        repeat(47) { appendLine("a c d e f d b e h") }
        repeat(38) { appendLine("a d b e g") }
        repeat(33) { appendLine("a c d e f b d e h") }
        repeat(14) { appendLine("a c d e f b d e g") }
        repeat(11) { appendLine("a c d e f d b e g") }
        repeat(9) { appendLine("a d c e f c d e h") }
        repeat(8) { appendLine("a d c e f d b e h") }
        repeat(5) { appendLine("a d c e f b d e g") }
        repeat(3) { appendLine("a c d e f b d e f d b e g") }
        repeat(2) { appendLine("a d c e f d b e g") }
        repeat(2) { appendLine("a d c e f b d e f b d e g") }
        repeat(1) { appendLine("a d c e f d b e f b d e h") }
        repeat(1) { appendLine("a d b e f b d e f d b e g") }
        repeat(1) { appendLine("a d c e f d b e f c d e f d b e g") }
    })

    /**
     * The log from the beginning of Section 7.2.2 in the Process Mining: Data Science in Action book.
     * This log contains short loop of length 1.
     */
    val sec722 = Helpers.logFromString(buildString {
        repeat(5) { appendLine("a e") }
        repeat(10) { appendLine("a b c e") }
        repeat(10) { appendLine("a c b e") }
        repeat(1) { appendLine("a b e") }
        repeat(1) { appendLine("a c e") }
        repeat(10) { appendLine("a d e") }
        repeat(2) { appendLine("a d d e") }
        repeat(1) { appendLine("a d d d e") }
    })
}
