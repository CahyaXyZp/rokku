package yokai.presentation.manga.notes

private val TASK_ITEM = Regex("""^((?:\s*>)*\s*(?:[-*+]|\d{1,9}[.)])\s+)\[([ xX])](\s)""")
private val CODE_FENCE = Regex("""^\s{0,3}(`{3,}|~{3,})""")

/**
 * Flips the checkbox of the [index]th task list item ("- [ ]" or "- [x]") of [markdown], counting
 * them in document order, which is the order they are rendered in. Task items inside fenced code
 * blocks aren't rendered as checkboxes, so they aren't counted.
 *
 * @return the updated markdown, or null when there is no such task item.
 */
internal fun toggleTaskItem(markdown: String, index: Int): String? {
    val lines = markdown.split("\n")
    var fenceChar: Char? = null
    var count = 0
    lines.forEachIndexed { lineIndex, line ->
        val fence = CODE_FENCE.find(line)
        if (fence != null) {
            val char = fence.groupValues[1].first()
            fenceChar = when {
                fenceChar == null -> char
                fenceChar == char -> null
                else -> fenceChar
            }
            return@forEachIndexed
        }
        if (fenceChar != null) return@forEachIndexed

        val match = TASK_ITEM.find(line) ?: return@forEachIndexed
        if (count == index) {
            val box = match.groups[2]!!
            val flipped = if (box.value == " ") "x" else " "
            return lines.toMutableList()
                .also { it[lineIndex] = line.replaceRange(box.range, flipped) }
                .joinToString("\n")
        }
        count++
    }
    return null
}
