package com.example.myapplication.presentation.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
class Navigator @Inject constructor() {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(NavKey.Chat)

    fun goTo(key: NavKey) {
        backStack.add(key)
    }

    fun goBack() {
        backStack.removeLastOrNull()
    }
}
