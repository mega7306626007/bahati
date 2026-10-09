package com.pesaflow.app.ui.buddy

import com.pesaflow.app.ui.NavRoutes

// Canonical navigation targets. Aliases resolve to NavRoutes constants only —
// never raw literals — so a typo can't silently drop the user on More.
// Navigation fires ONLY on explicit movement verbs ("open", "go to",
// "take me", "where is"...), so "show my bills" answers while
// "where are my bills" navigates. Pure, unit-tested.
object BuddyNavigation {

    private val NAV_VERBS = listOf(
        "open", "go to", "take me", "navigate", "switch to",
        "where is", "where are", "where do i", "where can i",
        "show me where", "back to", "go home", "take me to"
    )

    private val ALIASES: List<Pair<List<String>, String>> = listOf(
        listOf("home", "dashboard") to NavRoutes.HOME,
        listOf("transactions", "ledger", "spending history") to NavRoutes.TRANSACTIONS,
        listOf("budgets", "budget") to NavRoutes.BUDGETS,
        listOf("bills", "bill") to NavRoutes.BILLS,
        listOf("debts", "debt") to NavRoutes.DEBT,
        listOf("savings") to NavRoutes.SAVINGS,
        listOf("goal planner", "goals", "goal") to NavRoutes.GOALS,
        listOf("income") to NavRoutes.INCOME,
        listOf("semester") to NavRoutes.SEMESTER,
        listOf("university", "campus") to NavRoutes.UNIVERSITY,
        listOf("meal planner", "meals") to NavRoutes.MEALS,
        listOf("kitchen stock", "kitchen") to NavRoutes.KITCHEN,
        listOf("my things", "belongings", "things") to NavRoutes.THINGS,
        listOf("reports") to NavRoutes.REPORTS,
        listOf("analytics") to NavRoutes.ANALYTICS,
        listOf("insights") to NavRoutes.INSIGHTS,
        listOf("net worth") to NavRoutes.NETWORTH,
        listOf("search") to NavRoutes.SEARCH,
        listOf("settings") to NavRoutes.SETTINGS,
        listOf("notifications", "reminders", "alerts") to NavRoutes.NOTIFICATIONS,
        listOf("export", "backup") to NavRoutes.EXPORT,
        listOf("recurring") to NavRoutes.RECURRING,
        listOf("contact book", "contacts") to NavRoutes.CONTACTS,
        listOf("pending transactions", "pending", "review") to NavRoutes.REVIEW
    )

    private fun mentions(normalized: String, key: String): Boolean =
        Regex("(?<![a-z0-9])${Regex.escape(key)}(?![a-z0-9])").containsMatchIn(normalized)

    fun screenFor(normalized: String): String? {
        if (NAV_VERBS.none { v ->
                if (v.contains(" ")) normalized.contains(v)
                else com.pesaflow.app.ui.buddy.BuddyTextNorm.hasWord(normalized, v)
            }
        ) return null
        return ALIASES.firstOrNull { (keys, _) -> keys.any { mentions(normalized, it) } }?.second
    }
}
