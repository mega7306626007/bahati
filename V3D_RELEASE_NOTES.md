# PesaPlanner V3D — Optimization Clone

Branch: `v3d-optimized`
Base: `master`
Purpose: performance + conversation-engine optimization without rewriting the ledger.

## Implemented

- Added a cached `FinancialSnapshot` that computes common balance, income, expense, window and category aggregates once per ledger emission.
- Updated `FinanceViewModel` so the existing balance/income/expense/savings/Ziidi StateFlows reuse that snapshot.
- Updated Buddy to consume the cached snapshot instead of recomputing common ledger totals on every message.
- Added `BuddyMemoryState` with last/previous topic, entities, time window, turn count, last input and last response.
- Added deterministic follow-up resolution for topic switches, time-window changes and pronoun/reference follow-ups.
- Added deterministic Buddy humor variants for greetings, thanks and errors plus bounded occasional quips.
- Removed recursive Buddy follow-up rewriting and the duplicated user-message append path.
- Added Buddy conversation and humor regression tests.
- Added financial snapshot regression test.
- Batched pending M-Pesa/source-code duplicate checks to avoid two database lookups per pending row.
- Memoized Dashboard rhythm proposal calculation.
- Budgets now reuse the canonical snapshot for daily/weekly/monthly spend and monthly carryover.
- Changed daily digest and budget-crossing unique work from REPLACE to KEEP so pending work is not needlessly recreated on app launch.
- Removed destructive Room migration fallback so a missing migration cannot silently wipe the ledger.

## Verification

- The new Buddy conversation/humor layer was independently compiled with Kotlin/JVM 1.9 and its core checks passed.
- The connected GitHub repository snapshot does not contain the Gradle wrapper/build configuration, so a full Android `assemble`/unit-test run against the repository itself could not be honestly performed here.
- The source tree was re-read after edits and checked for stale references from the Buddy migration, destructive migration fallback, recursive follow-up call and old scheduler policies.

## Deliberately not changed

The large notification, parser, reports and settings files were not blindly split into dozens of files. Their remaining optimization work is lower-risk once a complete buildable Gradle project is present, because that allows compile/test/performance validation after each structural change.
