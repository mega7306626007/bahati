package com.pesaflow.app.ui

/** Single source of truth for in-app routes. String switches elsewhere must
 *  reference these � a typo in a raw literal used to fail silently by
 *  dropping the user back on the More screen. */
object NavRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val INSIGHTS = "insights"
    const val TRANSACTIONS = "transactions"
    const val BUDGETS = "budgets"
    const val SAVINGS = "savings"
    const val BILLS = "bills"
    const val MEALS = "meals"
    const val REPORTS = "reports"
    const val NETWORTH = "networth"
    const val BUDDY = "pesa"
    const val SETTINGS = "settings"
    const val DEBT = "debt"
    const val UNIVERSITY = "university"
    const val SEMESTER = "semester"
    const val THINGS = "things"
    const val KITCHEN = "kitchen"
    const val INCOME = "income"
    const val REVIEW = "review"
    const val ANALYTICS = "analytics"
    const val NOTIFICATIONS = "notifications"
    const val EXPORT = "export"
    const val RECURRING = "recurring"
    const val GOALS = "goals"
    const val CONTACTS = "contacts"
    const val INFO = "info"
}

