package pretty_errors

/**
 * Example usage:
 * ```
 * prettyPrint {
 *  lineNumber(12)
 *  text("Error at line 1:")
 *  line("time is window a but")
 *      .underline(8..14, hint = "This...")
 *      .underline(17..20, hint = "...and this...")
 *  text("...should be swapped?")
 * }
 * ```
 * Output:
 * ```
 * Error at line 1:
 * 12 | time is window a but
 *              ------   ---
 *              This...
 *                       ...and this...
 * ...should be swapped?
 * ```
 */
fun prettyPrint(color: Boolean = false, builder: PEBuilder.() -> Unit) {
    println(PEBuilder(color).apply(builder).build())
}

/**
 * @see prettyPrint
 */
fun prettyFmt(color: Boolean = false, builder: PEBuilder.() -> Unit): String {
    return PEBuilder(color).apply(builder).build()
}

/**
 * Convert string to TextObject, which can have effects like underlining etc. applied to it.
 * Example usage:
 * ```
 * "blubb".pe().underline(0..5, colorPrefix = ANSI_GREEN).fullColor(ANSI_YELLOW).toString(true)
 * ```
 */
fun String.pe(): TextObject {
    return TextObject(this, true)
}

infix fun String.concat(other: TextObject): TextObject {
    return this.pe() concat other
}