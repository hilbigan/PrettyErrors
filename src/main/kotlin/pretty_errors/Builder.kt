package pretty_errors

import java.lang.IllegalArgumentException
import javax.xml.soap.Text
import kotlin.math.floor
import kotlin.math.log10

class PEBuilder(private var color: Boolean = false) {

    private val textObjects = mutableListOf<TextObject>()
    private var lineNo = 1
    private var lineNumberChanged = false
    private var indentAllLines = false

    /**
     * If set to true, every line will be indented to match the line start.
     * This is off by default, because in most cases you'd probably want the 'information text'
     * to be distinguishable from the actual cause of error:
     *
     *
     * ```
     * // indentAllLines = false
     * Here is your error:
     * 8   | mistaek
     *  ```
     *
     *  versus
     *
     * ```
     * // indentAllLines = true
     *     | Here is your error:
     * 8   | mistaek
     *  ```
     */
    fun indentAllLines(flag: Boolean){
        indentAllLines = flag
    }

    /**
     * Enable/Disable color code printing
     */
    fun color(useColor: Boolean){
        color = useColor
    }

    /**
     * Skip to a line number.
     *
     * @param line the line number
     * @param skipInfo wether a short text with the number of lines skipped should be shown (only if lines have been skipped)
     */
    fun lineNumber(line: Int, skipInfo: Boolean = true){
        if(line < 0){
            throw IllegalArgumentException("Line number should be positive!")
        }
        if(skipInfo && lineNumberChanged && line > lineNo + 1){
            textObjects.add(TextObject("... (${line - lineNo} lines not shown)", hasLineNumber = false))
        }

        lineNo = line
        lineNumberChanged = true
    }

    /**
     * Append informational text (No line number will be added)
     */
    fun text(string: String): TextObject {
        val obj = TextObject(string, hasLineNumber = false)
        textObjects.add(obj)
        return obj
    }

    fun text(textObject: TextObject): TextObject {
        textObject.hasLineNumber = false
        textObjects.add(textObject)
        return textObject
    }

    /**
     * Append multiple lines of text (line numbers will be automatically added)
     */
    fun lines(strings: List<String>){
        this.textObjects.addAll(strings.map { TextObject(it, hasLineNumber = true, lineNumber = lineNo++) })
    }

    /**
     * Append one line of text (line number will be automatically added).
     * This returns the generated TextObject, so you can chain it with another function call, e.g. underline:
     * ```
     * line("example").underline( ... )
     * ```
     *
     */
    fun line(string: String): TextObject {
        val obj = TextObject(string, hasLineNumber = true, lineNumber = lineNo++)
        textObjects.add(obj)
        return obj
    }

    fun line(textObject: TextObject): TextObject{
        textObjects.add(textObject)
        return textObject
    }

    /**
     * Generate a string
     */
    internal fun build(): String {
        val startingLine = if(textObjects.any { it.hasLineNumber }) {
            textObjects.first { it.hasLineNumber }.lineNumber
        } else {
            1
        }
        val lines = lineNo
        val lineSpacing = 1 + (startingLine + lines).strlen()
        var out = ""

        textObjects.forEach {

            if(!it.hasLineNumber){

                if(indentAllLines){
                    out += (" " * (lineSpacing) + "| ${it.getText(color)}")
                } else {
                    out += (it.getText(color))
                }
                out += "\n"

            } else {

                out += ("${it.lineNumber}" + " " * (lineSpacing - it.lineNumber.strlen()) + "| ${it.getText(color)}") + "\n"

                val indent = " " * (lineSpacing + 2)
                if(it.isUnderlined()){
                    out += (indent + it.getUnderlines(color)) + "\n"
                }

                if(it.hasHints()){
                    it.getHints(color).forEach {
                        out += indent + it + "\n"
                    }
                }

            }

        }

        return out
    }

}

/*
 * Helper (extension-) functions
 */

private fun Int.strlen(): Int = floor(log10(this.toDouble())).toInt() + 1
private operator fun String.times(i: Int): String = this.repeat(i)

/*
 * Helper classes
 * TODO extract common attributes to abstract class?
 */

private class Underline(
    var range: IntRange,
    val char: Char = '-',
    val hasArrowTip: Boolean = false,
    val arrowTip: Char = 0.toChar(),
    val colorPrefix: String = "",
    val resetColorSuffix: String = ANSI_RESET,
    val hint: String = ""
)

private class Colored(
    var range: IntRange,
    val colorPrefix: String,
    val resetColorSuffix: String
)

class TextObject(val rawString: String, var hasLineNumber: Boolean = false, val lineNumber: Int = 1) {

    fun isUnderlined(): Boolean = underlines.size > 0
    fun hasHints(): Boolean = underlines.any { it.hint.isNotEmpty() }

    private val colors = mutableListOf<Colored>()
    private val underlines = mutableListOf<Underline>()

    /**
     * Add a line below the text
     *
     * @param range determines start and end of the line. The end index is always exclusive. Step and other range attributes are ignored.
     * @param char the character that will be repeated to form the line
     * @param hasArrowTip whether the line should be rendered with a special first character (given by arrowTip)
     * @param arrowTip
     * @param colorPrefix will be prepended
     * @param resetColorSuffix will be appended
     * @param hint informational text that will be displayed below the line
     */
    fun underline(range: IntRange,
                  char: Char = '-',
                  hasArrowTip: Boolean = false,
                  arrowTip: Char = 0.toChar(),
                  colorPrefix: String = "",
                  resetColorSuffix: String = ANSI_RESET,
                  hint: String = ""): TextObject {
        if(underlines.any { it.range.contains(range.start + 1) || it.range.contains(range.last - 1) }){
            throw IllegalArgumentException("Overlapping underlines are not supported!")
        }
        underlines.add(Underline(range, char, hasArrowTip, arrowTip, colorPrefix, resetColorSuffix, hint))

        return this
    }

    /**
     * Add a line below the whole text
     *
     * @param char the character that will be repeated to form the line
     * @param hasArrowTip whether the line should be rendered with a special first character (given by arrowTip)
     * @param arrowTip
     * @param colorPrefix will be prepended
     * @param resetColorSuffix will be appended
     * @param hint informational text that will be displayed below the line
     */
    fun underline(char: Char = '-',
                  hasArrowTip: Boolean = false,
                  arrowTip: Char = 0.toChar(),
                  colorPrefix: String = "",
                  resetColorSuffix: String = ANSI_RESET,
                  hint: String = ""): TextObject {
        return underline(0..rawString.length, char, hasArrowTip, arrowTip, colorPrefix, resetColorSuffix, hint)
    }

    /**
     * Mark the characters given by range with a sharp red line.
     *
     * @param range determines start and end of the line. The end index is always exclusive. Step and other range attributes are ignored.
     * @param hint informational text that will be displayed below the line
     */
    fun error(range: IntRange, hint: String = ""): TextObject {
        return underline(range, char = '^', colorPrefix = ANSI_RED, hint = hint)
    }

    /**
     * Mark the whole string with a sharp red line.
     *
     * @param range determines start and end of the line. The end index is always exclusive. Step and other range attributes are ignored.
     * @param hint informational text that will be displayed below the line
     */
    fun error(hint: String = ""): TextObject {
        return error(0..rawString.length, hint)
    }

    /**
     * Mark the characters given by range with a yellow squiggly line.
     *
     * @param range determines start and end of the line. The end index is always exclusive. Step and other range attributes are ignored.
     * @param hint informational text that will be displayed below the line
     */
    fun warn(range: IntRange, hint: String = ""): TextObject {
        return underline(range, char = '~', hasArrowTip = true, arrowTip = '^', colorPrefix = ANSI_LIGHT_YELLOW, hint = hint)
    }

    /**
     * Mark the whole string with a yellow squiggly line.
     *
     * @param range determines start and end of the line. The end index is always exclusive. Step and other range attributes are ignored.
     * @param hint informational text that will be displayed below the line
     */
    fun warn(hint: String = ""): TextObject {
        return warn(0..rawString.length, hint)
    }

    /**
     * Change the color of characters inside the given range
     * @param range determines start and end of colored characters. The end index is always exclusive. Step and other range attributes are ignored.
     * @param colorPrefix will be prepended
     * @param resetColorSuffix will be appended
     */
    fun color(range: IntRange, colorPrefix: String, resetColorSuffix: String = ANSI_RESET): TextObject {
        colors.add(Colored(range, colorPrefix, resetColorSuffix))

        return this
    }

    /**
     * Colors the whole line in one single color
     */
    fun color(colorPrefix: String, resetColorSuffix: String = ANSI_RESET): TextObject {
        return color(0..rawString.length, colorPrefix, resetColorSuffix)
    }

    /**
     * Concatenates to TextObjects. Keeps the line number of the left-hand TextObject.
     * TODO Does not check for overlapping ranges?
     */
    infix fun concat(other: TextObject): TextObject {
        val new = TextObject(this.rawString + other.rawString, this.hasLineNumber, this.lineNumber)
        new.colors.addAll(this.colors)
        new.colors.addAll(other.colors.map {
            it.apply {
                range = (this@TextObject.rawString.length + range.start)..(this@TextObject.rawString.length + range.last)
            }
        })
        new.underlines.addAll(this.underlines)
        new.underlines.addAll(other.underlines.map {
            it.apply {
                range = (this@TextObject.rawString.length + range.start)..(this@TextObject.rawString.length + range.last)
            }
        })
        return new
    }

    infix fun concat(string: String): TextObject {
        return this concat string.pe()
    }

    override fun toString(): String {
        return toString(false)
    }

    /**
     * Generate a multiline string (the originaal text, but colored; the lines; and the hints)
     */
    fun toString(color: Boolean): String {
        var out: String = if(hasLineNumber) "$lineNumber | " else ""
        out += getText(color) + "\n"

        val indent = if(hasLineNumber) " " * (lineNumber.strlen() + 3) else ""
        if(isUnderlined()){
            out += (indent + getUnderlines(color)) + "\n"
        }

        if(hasHints()){
            getHints(color).forEach {
                out += indent + it + "\n"
            }
        }

        return out
    }

    internal fun getText(colored: Boolean = true): String {
        if(!colored || colors.isEmpty()){
            return rawString
        }

        var colorIndex = 0
        var nextColor: Colored = colors[colorIndex]
        return rawString.foldIndexed(""){ index, acc, char ->
            if(colorIndex >= colors.size){
                return@foldIndexed acc + char
            }

            return@foldIndexed if(index == nextColor.range.start) {
                acc + nextColor.colorPrefix + char
            } else if(index == nextColor.range.last) {
                var ret = acc + nextColor.resetColorSuffix
                if(colorIndex + 1 < colors.size){
                    colorIndex++
                    nextColor = colors[colorIndex]

                    if(index == nextColor.range.start) {
                        ret += nextColor.colorPrefix
                    }
                }
                ret + char
            } else {
                acc + char
            }
        }.let {
            if(colorIndex <= colors.size){
                it + colors[colorIndex].resetColorSuffix
            } else {
                it
            }
        }
    }

    internal fun getUnderlines(colored: Boolean = true): String {
        if(underlines.isEmpty()){
            return ""
        }

        underlines.sortBy { it.range.start }

        var ulineIndex = 0
        var nextUline: Underline = underlines[ulineIndex]
        var acc = ""
        val loopStart = if(colored) -1 else 0
        loop@ for(index in loopStart..(rawString.length)){
            when {
                index == nextUline.range.start - 1 && colored -> acc += nextUline.colorPrefix + if(ulineIndex > 0) " " else ""
                index == nextUline.range.start -> acc += (if(nextUline.hasArrowTip) { nextUline.arrowTip } else { nextUline.char })
                index > nextUline.range.start && index < nextUline.range.last - 1 -> acc += nextUline.char
                index == nextUline.range.last - 1 -> {
                    acc += nextUline.char

                    if(colored){
                        acc += nextUline.resetColorSuffix
                    }

                    if(ulineIndex + 1 < underlines.size){
                        ulineIndex++
                        nextUline = underlines[ulineIndex]

                        if(index == nextUline.range.start - 1 && colored) {
                            acc += nextUline.colorPrefix
                        }
                    } else {
                        break@loop
                    }
                }
                else -> acc += " "
            }
        }

        return acc
    }

    internal fun getHints(colored: Boolean): List<String> {
        return underlines.filter { it.hint.isNotEmpty() }.sortedBy { it.range.start }.map {
            var ret = (" " * it.range.start)
            if(colored){
                ret += it.colorPrefix
                ret += it.hint
                ret += it.resetColorSuffix
            } else {
                ret += it.hint
            }
            ret
        }
    }
}
