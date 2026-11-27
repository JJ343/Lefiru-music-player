package com.lefiru.music.ui

/**
 * Selects every track in [visibleIds], or clears the whole selection when they
 * are already selected. Clearing removes every id, so "deseleccionar todas"
 * also drops tracks hidden by the current search.
 */
internal fun toggleSelectAll(
    selected: Set<Long>,
    visibleIds: Set<Long>,
): Set<Long> = if (visibleIds.isNotEmpty() && selected.containsAll(visibleIds)) {
    emptySet()
} else {
    selected + visibleIds
}
