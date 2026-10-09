package com.pesaflow.app.ui.buddy

// Safety gate: READ ≠ WRITE. Phase 1 only allows read-only queries and
// navigation without confirmation; anything mutating must arrive with an
// explicit confirmation step in its own phase. Pure, unit-tested.
sealed interface BuddyValidation {
    data object Allowed : BuddyValidation
    data class NeedsConfirmation(val prompt: String) : BuddyValidation
    data class Unsupported(val reason: String) : BuddyValidation
}

object BuddyValidator {
    fun validate(intent: BuddyIntent): BuddyValidation {
        val capability = BuddyCapabilities.forIntent(intent)
            ?: return BuddyValidation.Unsupported("That's outside what I can do yet — try 'what can you do?'")
        return when (capability.risk) {
            BuddyRiskClass.READ, BuddyRiskClass.APP_CONTROL ->
                if (capability.needsConfirmation) {
                    BuddyValidation.NeedsConfirmation(capability.title)
                } else {
                    BuddyValidation.Allowed
                }
            BuddyRiskClass.FINANCIAL_MUTATION, BuddyRiskClass.DESTRUCTIVE ->
                BuddyValidation.NeedsConfirmation(capability.title)
        }
    }
}
