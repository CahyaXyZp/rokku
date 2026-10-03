package yokai.presentation.manga.notes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TaskListToggleTest {

    @Test
    fun `an unchecked item gets checked`() {
        assertEquals("- [x] read", toggleTaskItem("- [ ] read", 0))
    }

    @Test
    fun `a checked item gets unchecked`() {
        assertEquals("- [ ] read", toggleTaskItem("- [x] read", 0))
        assertEquals("- [ ] read", toggleTaskItem("- [X] read", 0))
    }

    @Test
    fun `only the requested item changes`() {
        val markdown = "- [ ] one\n- [ ] two\n- [x] three"

        assertEquals("- [ ] one\n- [x] two\n- [x] three", toggleTaskItem(markdown, 1))
        assertEquals("- [ ] one\n- [ ] two\n- [ ] three", toggleTaskItem(markdown, 2))
    }

    @Test
    fun `text around the items is kept as it is`() {
        val markdown = "# Plan\n\nsome text\n\n- [ ] one\n\nend\n"

        assertEquals("# Plan\n\nsome text\n\n- [x] one\n\nend\n", toggleTaskItem(markdown, 0))
    }

    @Test
    fun `nested and numbered items are counted`() {
        val markdown = "- [ ] parent\n  - [ ] child\n1. [ ] numbered"

        assertEquals("- [ ] parent\n  - [x] child\n1. [ ] numbered", toggleTaskItem(markdown, 1))
        assertEquals("- [ ] parent\n  - [ ] child\n1. [x] numbered", toggleTaskItem(markdown, 2))
    }

    @Test
    fun `items in a block quote are counted`() {
        assertEquals("> - [x] quoted", toggleTaskItem("> - [ ] quoted", 0))
    }

    @Test
    fun `items inside fenced code are not counted`() {
        val markdown = "```\n- [ ] code\n```\n- [ ] real"

        assertEquals("```\n- [ ] code\n```\n- [x] real", toggleTaskItem(markdown, 0))
        assertNull(toggleTaskItem(markdown, 1))
    }

    @Test
    fun `a chapter tag in the item survives`() {
        val markdown = "- [ ] read [Chapter 12](chapter://12)"

        assertEquals("- [x] read [Chapter 12](chapter://12)", toggleTaskItem(markdown, 0))
    }

    @Test
    fun `a missing item gives nothing`() {
        assertNull(toggleTaskItem("no tasks here", 0))
        assertNull(toggleTaskItem("- [ ] one", 1))
        assertNull(toggleTaskItem("- [ ] one", -1))
    }

    @Test
    fun `plain brackets are not task items`() {
        assertNull(toggleTaskItem("- [link] text\n[ ] alone", 0))
    }
}
