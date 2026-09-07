package com.example.taskflow.google

/** Keep the sensitive API permissions used by consent and token requests aligned. */
internal object GoogleOAuthScopes {
    val permissions = listOf(
        "https://www.googleapis.com/auth/tasks",
        "https://www.googleapis.com/auth/calendar.events.owned"
    )

    val tokenRequest: String = "oauth2:" + permissions.joinToString(" ")
}
