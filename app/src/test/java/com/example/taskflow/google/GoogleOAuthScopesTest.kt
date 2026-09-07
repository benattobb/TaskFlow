package com.example.taskflow.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GoogleOAuthScopesTest {
    @Test fun sensitiveConsentAndTokenPermissionsMatch() {
        assertEquals(
            GoogleOAuthScopes.permissions,
            GoogleOAuthScopes.tokenRequest.removePrefix("oauth2:").split(" ")
        )
    }

    @Test fun calendarAccessIsLimitedToOwnedCalendars() {
        assertEquals(listOf(
            "https://www.googleapis.com/auth/tasks",
            "https://www.googleapis.com/auth/calendar.events.owned"
        ), GoogleOAuthScopes.permissions)
        assertFalse(GoogleOAuthScopes.permissions.contains("https://www.googleapis.com/auth/calendar.events"))
    }
}
