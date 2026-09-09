package pl.blizinski.microsofttodostore.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class MicrosoftToDoContentMergerTest {

    private val merge = MicrosoftToDoContentMerger

    private val base = MicrosoftTask(
        title = "Base title",
        notes = "base notes",
        createdDate = 500L,
        dueDate = 1_000L,
        dueHasTime = false,
        priority = 1,
        labels = listOf("home"),
    )

    @Test
    fun disjointFieldEdits_bothSurvive() {
        val local = base.copy(title = "Local title")
        val remote = base.copy(notes = "remote notes")

        val merged = merge.merge(base, local, remote, preferLocal = true)

        assertEquals("Local title", merged.title)
        assertEquals("remote notes", merged.notes)
    }

    @Test
    fun sameFieldConflict_preferLocalDecides() {
        val local = base.copy(title = "Local")
        val remote = base.copy(title = "Remote")

        assertEquals("Local", merge.merge(base, local, remote, preferLocal = true).title)
        assertEquals("Remote", merge.merge(base, local, remote, preferLocal = false).title)
    }

    @Test
    fun nullBase_fillsUnsetLocalFieldsFromRemote_contestedUsesPreferLocal() {
        val local = MicrosoftTask(title = "Local title")               // notes/due never set locally
        val remote = MicrosoftTask(
            title = "Server title", notes = "server notes", dueDate = 4_000L, dueHasTime = true, priority = 2,
        )

        val mergedLocal = merge.merge(null, local, remote, preferLocal = true)
        assertEquals("Local title", mergedLocal.title, "contested title -> preferLocal")
        assertEquals("server notes", mergedLocal.notes, "unset notes filled from server")
        assertEquals(4_000L, mergedLocal.dueDate, "unset due filled from server")
        assertEquals(true, mergedLocal.dueHasTime)
        assertEquals(2, mergedLocal.priority, "server-owned field from remote")

        assertEquals("Server title", merge.merge(null, local, remote, preferLocal = false).title)
    }

    @Test
    fun dueDateAndHasTime_moveAsAUnit() {
        // Local gave the task a real due time; server left due untouched.
        val local = base.copy(dueDate = 2_000L, dueHasTime = true)
        val remote = base.copy(title = "Remote title")

        val merged = merge.merge(base, local, remote, preferLocal = false)

        assertEquals(2_000L, merged.dueDate)
        assertEquals(true, merged.dueHasTime, "dueHasTime follows dueDate to the same side")
        assertEquals("Remote title", merged.title)
    }

    @Test
    fun dueConflict_takesOneSidePairIntact() {
        val local = base.copy(dueDate = 2_000L, dueHasTime = true)
        val remote = base.copy(dueDate = 3_000L, dueHasTime = false)

        val mergedLocal = merge.merge(base, local, remote, preferLocal = true)
        assertEquals(2_000L, mergedLocal.dueDate)
        assertEquals(true, mergedLocal.dueHasTime)

        val mergedRemote = merge.merge(base, local, remote, preferLocal = false)
        assertEquals(3_000L, mergedRemote.dueDate)
        assertEquals(false, mergedRemote.dueHasTime)
    }

    @Test
    fun nonEditorFields_takenFromRemote() {
        // priority/labels aren't in the app editor; a local update never changes them, but the
        // server can (e.g. importance set in Outlook).
        val local = base.copy(title = "Local title")
        val remote = base.copy(priority = 2, labels = listOf("work", "urgent"))

        val merged = merge.merge(base, local, remote, preferLocal = true)

        assertEquals(2, merged.priority)
        assertEquals(listOf("work", "urgent"), merged.labels)
        assertEquals("Local title", merged.title)
    }
}
