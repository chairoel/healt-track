package com.mascill.healthtrack.presentation.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Pushes [key] onto this back stack.
 */
fun MutableList<NavKey>.navigateTo(key: NavKey) {
    add(key)
}

/**
 * Pops the top key and keeps the start destination.
 */
fun MutableList<NavKey>.navigateBack() {
    if (size > 1) removeLastOrNull()
}

/**
 * Switches to a bottom-navigation tab and drops nested keys.
 */
fun MutableList<NavKey>.navigateToTopLevel(key: NavKey) {
    if (lastOrNull() == key) return
    add(key)
    while (firstOrNull() != key) {
        removeAt(0)
    }
}
