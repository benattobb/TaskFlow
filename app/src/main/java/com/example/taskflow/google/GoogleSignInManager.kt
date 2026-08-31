package com.example.taskflow.google

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

object GoogleSignInManager {
    private val scopes = listOf(
        Scope("https://www.googleapis.com/auth/tasks"),
        Scope("https://www.googleapis.com/auth/calendar.events")
    )

    fun client(context: Context) = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(scopes.first(), *scopes.drop(1).toTypedArray())
            .build()
    )

    fun isConnected(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, *scopes.toTypedArray())
    }
}
