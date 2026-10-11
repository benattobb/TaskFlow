package com.example.taskflow.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LinkedTaskStoreTest {
    private lateinit var context: Context

    @Before fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "linked_task_test_${UUID.randomUUID()}"
        context = object : ContextWrapper(base) {
            override fun getSharedPreferences(unused: String, mode: Int): SharedPreferences = base.getSharedPreferences(name, mode)
        }
    }

    @Test fun historyIsIsolatedByGoogleAccount() {
        LinkedTaskStore.save(context, "first@example.test", LinkedTask("id", "First account"))
        LinkedTaskStore.save(context, "second@example.test", LinkedTask("id", "Second account"))
        assertEquals("First account", LinkedTaskStore.load(context, "first@example.test").single().title)
        assertEquals("Second account", LinkedTaskStore.load(context, "second@example.test").single().title)
        assertTrue(LinkedTaskStore.load(context, "unknown@example.test").isEmpty())
    }

    @Test fun retryUpdatesOneRecordAndRetainsTheEventLink() {
        val task = LinkedTask("task", "Send proposal", calendarPending = true)
        LinkedTaskStore.save(context, "account", task)
        val linked = task.copy(calendarPending = false, calendarEventId = "event", calendarUrl = "https://calendar.google.com/event")
        LinkedTaskStore.save(context, "account", linked)
        assertEquals(listOf(linked), LinkedTaskStore.load(context, "account"))
    }

    @Test fun completionKeepsEventAndRemovingEventKeepsCompletion() {
        val completed = LinkedTask("task", "Supplements", calendarEventId = "series", completed = true)
        LinkedTaskStore.save(context, "account", completed)
        assertEquals("series", LinkedTaskStore.load(context, "account").single().calendarEventId)
        LinkedTaskStore.save(context, "account", completed.copy(calendarEventId = null))
        val saved = LinkedTaskStore.load(context, "account").single()
        assertTrue(saved.completed)
        assertNull(saved.calendarEventId)
    }

    @Test fun calendarPreferenceIsSharedAcrossCaptureSurfaces() {
        assertTrue(LinkedTaskStore.createsCalendarEvents(context))
        LinkedTaskStore.setCreatesCalendarEvents(context, false)
        assertFalse(LinkedTaskStore.createsCalendarEvents(context))
    }
}
