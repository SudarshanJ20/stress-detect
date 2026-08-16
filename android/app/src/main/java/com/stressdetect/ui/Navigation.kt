package com.stressdetect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.stressdetect.data.AnalysisResult

/**
 * Where the app can be.
 *
 * The flow stopped being linear when Home arrived, so there is a real back stack now — but
 * five destinations still do not justify a navigation library and its dependency surface.
 */
sealed interface Screen {
    /** One-time data explanation, before the first check-in only. */
    data object FirstRun : Screen
    data object Permissions : Screen
    data object Home : Screen
    data class CheckIn(val itemIndex: Int) : Screen
    data object Analysing : Screen
    data class Result(val result: AnalysisResult) : Screen
    data object History : Screen
    data object About : Screen
}

/**
 * A tiny explicit back stack.
 *
 * [replaceAll] exists for the transitions that must not be reversible: once a check-in has
 * been submitted, backing into the half-finished questionnaire would show stale answers and
 * invite a second submission, so Result replaces the whole stack rather than sitting on top
 * of it.
 */
class BackStack(initial: Screen) {
    private val entries: SnapshotStateList<Screen> = mutableListOf(initial).toMutableStateList()

    val current: Screen get() = entries.last()
    val canGoBack: Boolean get() = entries.size > 1

    fun push(screen: Screen) {
        entries.add(screen)
    }

    /** Replaces the top of the stack — used to step between questionnaire items. */
    fun replaceTop(screen: Screen) {
        entries[entries.lastIndex] = screen
    }

    fun replaceAll(screen: Screen) {
        entries.clear()
        entries.add(screen)
    }

    fun pop(): Boolean {
        if (!canGoBack) return false
        entries.removeAt(entries.lastIndex)
        return true
    }
}

@Composable
fun rememberBackStack(initial: Screen): BackStack = remember { BackStack(initial) }
