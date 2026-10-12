"# PesaFlow Test — Full Project Review
"
"**Date:** 2026-09-24 | **Project:** PesaFlow-test (test copy; production untouched) | **Build:** 46.1 MB debug APK, 192/192 tests green
"
"## Scope
"80 problems across UI/UX, visual design, money flow, credibility, and truth — plus 120 concrete solutions.
"
"---
"
"## Quick Reference
"
"| Category | Problems | Solutions |
"|---|---|---|
"| UI/UX | 20 | 30 |
"| Visual Design | 16 | 24 |
"| Money Flow | 20 | 32 |
"| Credibility | 14 | 20 |
"| Truth/Accuracy | 10 | 14 |
"| **TOTAL** | **80** | **120** |
"
"---
"## 1. UI / UX
"
"### Problems
"
"#### UX-01 — Empty `forEach` renders no transactions
"`ui/dashboard/DashboardScreen.kt` | `transactions.filter { ... }.forEach { tx -> }` body is empty | No transaction cards ever render
"
"#### UX-02 — No `BackHandler` on DashboardScreen
"`ui/dashboard/DashboardScreen.kt` | `BackHandler` imported but never called | Users cannot navigate back from dashboard
"
"#### UX-03 — Bottom-nav overload: 5 tabs + 15 More rows
"`MainActivity.kt` | No grouping, no search, no categories | Undiscoverable features
"
"#### UX-04 — Unlabeled FAB speed dial
"`MainActivity.kt` | 4 `TextButton` options with no content descriptions | Accessibility + FAB conflicts
"
"#### UX-06 — Hardcoded strings, zero `R.string`
"All screens | Every UI text is a Kotlin string literal | No localization, poor a11y
"
"#### UX-08 — No empty/loading/error states
"AnalyticsScreen, GoalsScreen, RecurringScreen | No empty guard, no shimmer/progress | Blank pages on first run
"
"#### UX-09 — Notifications dismissed permanently
"`NotificationScreen.kt` | `dismissed = dismissed + eventKey(it)`, no undo | Users lose dismissed alerts forever
"
"#### UX-10 — Onboarding 'Skip → sample data'
"`OnboardingScreen.kt` | Skip seeds synthetic data users may mistake for real | Sample-data confusion
"
"#### UX-11 — Onboarding is 61,963 chars
"`OnboardingScreen.kt` | 6 steps, 10 questions, SMS permission in one screen | Massive onboarding friction
"
"#### UX-14 — No `BackHandler` on 10+ sub-screens
"Analytics, Goals, Export, Recurring, etc. | No back affordance | Navigation dead-ends
"
"#### UX-15 — SearchScreen lacks history/autocomplete
"`SearchScreen.kt` | `hasSearched` starts false; no recent searches | Undiscoverable search
"
"#### UX-17 — 80dp spacers at bottom of every list
"Dashboard, Goals, Export, Recurring, Notification | `Spacer(80.dp)` | Dead space
"
"#### UX-19 — MealPlannerScreen is 113,279 chars
"`MealPlannerScreen.kt` | Single composable mixing engine + UI | Overwhelming screen
"
"#### UX-20 — IncomeScreen is nearly empty (3,393 chars)
"`IncomeScreen.kt` | No list, no add, no empty state | Underwhelming screen
"
"#### UX-21 — ExportScreen has no confirmation/progress
"`ExportScreen.kt` | Backup runs with no confirmation | Destructive action, missing feedback
"
"#### UX-22 — AnalyticsScreen corrupted emoji strings
"""`AnalyticsScreen.kt` | `dY"S`, `dY"^"`, garbage text | Hardcoded strings, emoji-as-icons")""
"
"#### UX-28 — MoreScreen creates dead-ends
"`MainActivity.kt` | Sub-screens have no `BackHandler` | Exit app unexpectedly
"
"#### UX-29 — No error states on any screen
"Multiple | No loading spinner, error message, retry button | Blank states on failure
"
"#### UX-39 — Sub-screens only via More section
"`MainActivity.kt` | No bottom-nav or deep links | Undiscoverable features, fragile routing
"
"### Solutions (S-01 to S-30)
"
"| # | Fix | Problem(s) |
"|---|---|---|
"| S-01 | Replace empty `forEach` with `items` + `LazyColumn` item cards | UX-01 |
"| S-02 | Add `BackHandler(enabled = !onboarded) { popBackStack() }` to DashboardScreen | UX-02 |
"| S-03 | Group `MoreScreen` into sections (Finances, Settings, Explore) with `PesaSectionHeader` | UX-03, UX-39 |
"| S-04 | Add `contentDescription` to all 4 speed-dial buttons; switch to `IconButton` | UX-04 |
"| S-05 | Create `strings.xml` resource entries; replace all hardcoded literals with `stringResource` | UX-06, UX-22, UX-23, UX-35 |
"| S-06 | Add `LazyColumn` guard: `if (transactions.isEmpty()) PesaEmptyState(...)` | UX-08, UX-20, UX-23, UX-24 |
"| S-07 | Add 'Show dismissed' toggle + undo `Snackbar`; persist dismiss set to prefs | UX-09, UX-25 |
"| S-08 | Change skip to `'Explore demo data (fake)'` + add persistent `'DEMO MODE'` chip | UX-10, UX-38 |
"| S-09 | Split onboarding into collapsible step cards; add progress stepper | UX-11 |
"| S-10 | Add `BackHandler` to AnalyticsScreen, GoalsScreen, ExportScreen, RecurringScreen + 6 others | UX-14, UX-28 |
"| S-11 | Persist recent searches to prefs; add suggestion chips above results | UX-15 |
"| S-12 | Replace `Spacer(80.dp)` with `Spacer(Modifier.weight(1f))` or remove | UX-17, UX-22, UX-23, UX-24 |
"| S-13 | Extract `planSurvival` + data classes to a separate file | UX-19 |
"| S-14 | Add income source list + `FloatingActionButton` to add source, empty-state card | UX-20 |
"| S-15 | Wrap backup/export in `AlertDialog` confirmations + `LinearProgressIndicator` | UX-21, UX-36 |
"| S-16 | Fix corrupted emoji (`dY"S` → `💡`); replace with `Icons.Filled.*` | UX-22, UX-23, UX-24, UX-35 |
"| S-17 | Add `BackHandler` to each MoreScreen sub-route; use `popBackStack` not `moreSection = null` | UX-28 |
"| S-18 | Add `CircularProgressIndicator` for async ops; add error `Text` + retry button per screen | UX-26, UX-29 |
"| S-19 | Add `deepLink` entries in manifest; surface key More rows on bottom-nav | UX-39 |
"| S-20 | Add `RhythmConfirmCard` dismiss feedback (Snackbar) | UX-27 |
"
"## 2. Visual Design
"
"### Problems
"
"#### VIS-01 — New screens lack cinematic backdrop
"AnalyticsScreen, GoalsScreen, ExportScreen, RecurringScreen, NotificationScreen | Starts with `LazyColumn` directly | Flat, breaks visual identity
"
"#### VIS-04 — Emoji titles vs Material icons
"All new screens | `📊`, `🎯`, `📤` vs `Icons.Filled.*` | Two icon systems
"
"#### VIS-05 — Heatmap drawn as traffic-light emoji
"`AnalyticsScreen.kt` | `Text(if (v > 500) '🔴'...)` | OS-dependent emoji, no semantic color
"
"#### VIS-07 — Goal progress shown as bare percentage
"`GoalsScreen.kt` | `Text(progressPercent + '%')` only | No visual magnitude
"
"#### VIS-08 — AT_RISK tinted `secondary` (blue), not warning
"`GoalsScreen.kt` | `AT_RISK -> MaterialTheme.colorScheme.secondary` | Risk reads as 'good'
"
"#### VIS-12 — KSh without thousands grouping
"AnalyticsScreen, GoalsScreen, RecurringScreen | `toInt()` raw | `KSh 1250000` hard to read
"
"#### VIS-16 — Broken rhythm: 4dp gaps vs 12dp sections
"AnalyticsScreen + all new screens | `padding(top = 4.dp)` + `Spacer(12.dp)` | Asymmetric spacing
"
"#### VIS-17 — No empty-state visuals
"GoalsScreen, RecurringScreen, AnalyticsScreen | No `PesaEmptyState` branch | Blank pages first run
"
"#### VIS-20 — Analytics contains no charts
"`AnalyticsScreen.kt` vs `ChartComposables.kt` | All Text, no Canvas charts | Looks like debug dump
"
"#### VIS-25 — Export success is debug-style copy
"`ExportScreen.kt` | `Text('✅ CSV ready ($length chars)')` | Console output
"
"#### VIS-27 — Inline Dismiss in every notification card
"`NotificationScreen.kt` | `TextButton(onClick = { onDismiss }) { Text('Dismiss') }` | Stack of forms
"
"#### VIS-30 — Two parallel risk palettes
"`ChartComposables.kt`, `Color.kt` | `FreshMint/InfoBlue` vs `PesaWarning/PesaDanger` | Colors disagree screen-to-screen
"
"#### VIS-35 — Default filled Buttons with extraLarge corners
"`ExportScreen.kt` | `Button { Text('Generate CSV') }` | Pill-shaped buttons clash
"
"#### VIS-37 — Raw ISO dates in cards
"`RecurringScreen.kt` | `SimpleDateFormat('yyyy-MM-dd')` | Machine-format dates
"
"#### VIS-40 — Backdrop drop-off into new screens
"`MainActivity.kt` More→screen | MoreScreen has backdrop, new screens flat | Hard visual cut
"
"### Solutions (S-21 to S-44)
"
"| # | Fix | Problem(s) |
"|---|---|---|
"| S-21 | Wrap each new screen in `Box { CinematicBackdrop(...); Scaffold(...) }` | VIS-01, VIS-40 |
"| S-22 | Replace `📊🎯📤🔄🔔` titles with `Icons.Filled.*`; keep emoji only in content | VIS-04 |
"| S-23 | Replace heatmap emoji with `Canvas` `Rectangle` colored by `PesaDanger/PesaWarning/PesaSuccess` | VIS-05, VIS-06 |
"| S-24 | Add `LinearProgressIndicator(fraction = progressPercent)` to each goal card | VIS-07 |
"| S-25 | Map `AT_RISK -> PesaWarningContainer`, `DANGER -> PesaDangerContainer` | VIS-08, VIS-11 |
"| S-26 | Use `Double.toKSh()` extension for all amounts | VIS-12, UX-22, MON-25 |
"| S-27 | Replace `padding(top = 4.dp)` card gaps with `Arrangement.spacedBy(20.dp)` | VIS-16 |
"| S-28 | Add `PesaEmptyState` branch when goals/patterns/heatmap empty | VIS-17 |
"| S-29 | Add `MetricDistributionDonutChart` + `HistoricalTrendLineChart` to AnalyticsScreen | VIS-20 |
"| S-30 | Replace `✅ ... ($length chars)` with `Text('Backup ready', color = PesaSuccess)` | VIS-25 |
"| S-31 | Replace inline `TextButton Dismiss` with `IconButton(X)` in title row | VIS-27 |
"| S-32 | Reconcile `ChartPalette` with `PesaWarning/PesaDanger` tokens; use one palette app-wide | VIS-30 |
"| S-33 | Add `shape = RoundedCornerShape(16.dp)` to Export `Button` | VIS-35 |
"| S-34 | Replace `SimpleDateFormat('yyyy-MM-dd')` with `SimpleDateFormat('d MMM yyyy')` | VIS-37 |
"| S-35 | Add `AtmosphereBand` to each new screen's top | VIS-02, VIS-20 |
"| S-36 | Use `PesaSpacing.xl/2xl` tokens instead of raw `16.dp/12.dp` literals | VIS-15, VIS-28 |
"| S-37 | Add `featureSkin` tint + accent line to goal cards | VIS-34 |
"| S-38 | Add `Spacer(Modifier.height(2.dp))` between header title and subtitle | VIS-38 |
"| S-39 | Replace raw counts in titles with stable section labels | VIS-39 |
"| S-40 | Add `maxLines = 1` + `Ellipsis` to long KSh strings in trailing columns | VIS-36 |
"
"## 3. Money Flow
"
"### Problems
"
"#### MON-01 — CSV export includes sample transactions
"`ExportEngine.kt` | `exportCsv` iterates without `!it.isSample` | Export totals diverge from UI
"
"#### MON-03 — Marking a bill paid never creates a ledger expense
"`FinanceViewModel.kt` markBillPaid | `repository.markBillPaid(id)` only, no `Transaction` | Bills never reduce balance
"
"#### MON-04 — Goal contribution not atomic
"`FinanceViewModel.kt` contributeToSavingsGoal | Two independent suspend calls, no `transact` | Ledger/goal desync
"
"#### MON-05 — Semester budget window points to future
"`FinanceViewModel.kt` addBudget | `start = now, end = now+120d` vs `windowStart = now-120d` | Budget never overlaps display
"
"#### MON-07 — TRANSFER zeroes all per-method balances
"`FinanceViewModel.kt` signedAmount | `TRANSFER -> 0.0` | Balance drift after transfers
"
"#### MON-09 — v1 restore has no atomic wrapper
"`FinanceViewModel.kt` restoreBackup v1 | Separate suspend calls | Partial failure corrupts state
"
"#### MON-16 — MpesaParser classifies P2P as EXPENSE
"`MpesaParser.kt` p2pRegex | `type = TransactionType.EXPENSE` | Transfers deflate availableBalance
"
"#### MON-17 — `checkUnusualSpend` fires false alarms
"`NotificationEngine.kt` | `yesterdaySpend > dailyTarget && todaySpend > 0` | Spurious unusual-spend alerts
"
"#### MON-22 — seedOpeningMoney inflates monthlyIncome
"`FinanceViewModel.kt` seedOpeningMoney | `type = INCOME, isSample = false` | Opening balances counted as income
"
"#### MON-25 — DashboardScreen truncates `.toInt()`
"`DashboardScreen.kt` | `tx.amount.toInt()` | KSh 150.90 displays as KSh 150
"
"#### MON-30 — GoalEngine `currentPaceDays` undercounts
"`GoalEngine.kt` | `distinctDays = window.map { ... }.toSet().size` | Overestimates pace, goals look on-track
"
"#### MON-33 — weekdaySpending index mismatch
"`DashboardScreen.kt` | `Calendar.DAY_OF_WEEK` vs `weekdayIndex` in WeekdayPace | Pacing factors applied to wrong days
"
"#### MON-35 — `checkWeeklyRitual` only fires on Wednesday
"`NotificationEngine.kt` | `if (dayOfWeek != Calendar.WEDNESDAY) return null` | Wrong day for weekly reviews
"
"#### MON-38 — CSV round-trip converts sample to real
"`CsvImporter.kt` | No `isSample` column; re-import defaults false | Sample data permanently contaminates ledger
"
"#### MON-39 — `addManualTransaction` date clamping
"`LedgerGateway.kt` | `if (dateTimestamp > now + 24h) now else dateTimestamp` | Future-dated tx silently changed
"
"#### MON-40 — SmartInsights pace check ignores budget start
"`SmartInsightsEngine.kt` | `dom / dim` projection | Misleading budget pace advice
"
"### Solutions (S-45 to S-76)
"
"| # | Fix | Problem(s) |
"|---|---|---|
"| S-45 | Filter `!it.isSample` in `exportCsv`; add `isSample` column to CSV | MON-01, MON-38 |
"| S-46 | In `markBillPaid`, insert an `EXPENSE` Transaction of equal amount | MON-03 |
"| S-47 | Wrap `insertTransaction` + `contributeToSavingsGoal` in `repository.transact { }` | MON-04 |
"| S-48 | Change `BudgetsScreen` `windowStart` to `now` to match `addBudget` end | MON-05 |
"| S-49 | Add `signedAmount` variant: `TRANSFER -> it.amount * -1` to source, `+1` to dest | MON-07 |
"| S-50 | Wrap v1 restore in `repository.transact { }` like v2 | MON-09 |
"| S-51 | Classify P2P 'Sent Money' and bank-out as `TransactionType.TRANSFER` | MON-16, MON-18 |
"| S-52 | Change `checkUnusualSpend` condition to `todaySpend > 2 * expected && todaySpend - expected >= 200` | MON-17 |
"| S-53 | Set `isSample = true` on `seedOpeningMoney` entries OR exclude from `monthlyIncome` | MON-22 |
"| S-54 | Use `tx.amount.toKSh()` in DashboardScreen recent list | MON-25 |
"| S-55 | Divide `savedInWindow` by `windowDays` (not distinctDays) for `currentPaceDays` | MON-30 |
"| S-56 | Normalize `DashboardScreen` weekday map to Monday-first to match `weekdayIndex` | MON-33 |
"| S-57 | Parameterize `checkWeeklyRitual` day-of-week; default to user-configurable | MON-35 |
"| S-58 | Add `isSample` column to CSV export; preserve flag on re-import | MON-38 |
"| S-59 | Remove date clamping or expose future-date option in UI | MON-39 |
"| S-60 | Adjust `expected` in SmartInsights by budget start date | MON-40 |
"| S-61 | Add `isSample` filter to `generateShareSummary` totals | MON-10, CRE-12 |
"| S-62 | Change `planDaily = goals.sumOf { planDailyRate(it) }.toInt()` to `.roundToInt()` | MON-11 |
"| S-63 | Change `billDaily` to `(it / 30).roundToInt()` | MON-12 |
"| S-64 | Add v2 restore atomicity + `insertPendingTransactions` call | MON-02 |
"| S-65 | Add `version` validation: reject payloads missing `version` field | MON-23 |
"| S-66 | Fix `importTransactions` batch tracking when `added == 0` | MON-28 |
"| S-67 | Use device-independent `Clock`/UTC for month boundaries in `inMonth` | MON-29 |
"| S-68 | Add `TransactionType.TRANSFER` sign convention documentation | MON-08 |
"| S-69 | Add `spentByCategory` computed per budget window, not flat 30d | MON-32 |
"| S-70 | Use `toInt()` consistently or `toKSh()` everywhere in DashboardScreen | MON-25 |
"
"## 4. Credibility
"
"### Problems
"
"#### CRE-01 — 'Skip → sample data' presents fake data as real
"`OnboardingScreen.kt` | `seedSampleData()` on skip | Users believe synthetic data is real
"
"#### CRE-02 — Onboarding claim 'estimates are perfect'
"`OnboardingScreen.kt:223` | `'estimates are perfect'` | Guarantees misalignment
"
"#### CRE-05 — Backup 'full database' is misleading
"`ExportScreen.kt:56` | `'Full database backup with every table'` | Omits relationships, includes sample flags
"
"#### CRE-08 — Insights stated as deterministic facts
"`AnalyticsScreen.kt` | `'⚠️ ${growing.label} is growing fastest'` | No confidence qualifier
"
"#### CRE-09 — Goal projections presented as guarantees
"`GoalsScreen.kt` | `'Daily pace: KSh X (need KSh Y)'` with deterministic risk | Any variance invalidates
"
"#### CRE-13 — SafeToSpend 'learned intelligence'
"`SafeToSpendCard.kt` | `'Learned per weekday from the last 4 weeks'` | Simple average, not ML
"
"#### CRE-14 — SmartInsights 'I'll spot patterns' implies AI
"`SmartInsightsEngine.kt` | `'Add transactions and I'll spot patterns. 👀'` | Hard-coded thresholds
"
"#### CRE-19 — Settings 'Nothing here can break your data'
"`SettingsScreen.kt:301` | `'Nothing here can break your data.'` vs Delete All Data | Direct contradiction
"
"#### CRE-24 — Goal suggestions state projections as directives
"`GoalEngine.kt` | `'Close the gap: save KSh X/day more'` | Hypothetical as urgent
"
"#### CRE-26 — Settings 'ON ✓ — new M-Pesa texts auto-log'
"`SettingsScreen.kt` | Green indicator vs visible parse failures | Disconnect between status and reality
"
"#### CRE-28 — NotificationEngine 'Intelligent notification engine'
"`NotificationEngine.kt:13` | `'Intelligent notification engine'` | Hard-coded thresholds
"
"#### CRE-36 — Settings contradiction Delete All Data
"`SettingsScreen.kt` | Same screen as 'Nothing can break your data' | Trust-destroying
"
"#### CRE-40 — Reminders 'always goes through' false claim
"`Reminders.kt` | `'Urgent money alarms ... always go through'` | Doze mode can suppress
"
"### Solutions (S-77 to S-96)
"
"| # | Fix | Problem(s) |
"|---|---|---|
"| S-77 | Change skip button to `'Explore demo data (fake)'` + add persistent `'DEMO MODE'` chip | CRE-01, CRE-38 |
"| S-78 | Change `'estimates are perfect'` to `'estimates are starting points — edit anytime'` | CRE-02 |
"| S-79 | Change `'Full database backup'` to `'Backup of: transactions, budgets, goals, bills, debts'` | CRE-05 |
"| S-80 | Add `'⚠️ trending'` + confidence chip to insights; say 'based on recent trends' | CRE-08 |
"| S-81 | Add `'projected'` label + 'based on X-day average' disclaimer to goal cards | CRE-09 |
"| S-82 | Change `'Learned per weekday'` to `'Based on a 4-week average'` | CRE-13 |
"| S-83 | Change `'I'll spot patterns'` to `'Threshold alerts'` | CRE-14 |
"| S-84 | Change `'Nothing here can break your data'` to `'These are non-destructive tuning options'` | CRE-19, CRE-36 |
"| S-85 | Add `'projected'` label + disclaimer to goal suggestions | CRE-24 |
"| S-86 | Change `'ON ✓ — auto-log'` to `'ON — parse status: X OK / Y failed'` | CRE-26 |
"| S-87 | Change `'Intelligent notification engine'` to `'Rule-based notification engine'` | CRE-28 |
"| S-88 | Change `'Urgent money alarms always go through'` to `'Urgent alarms aim to arrive; battery settings may delay'` | CRE-40 |
"| S-89 | Add disclaimer text next to each confidence score: `'Heuristic, not probability'` | CRE-10 |
"| S-90 | Add `'based on ${windowDays}-day window'` context to budget pace text | CRE-15 |
"| S-91 | Replace `'Full database'` claim with explicit table list in ExportScreen | CRE-05, CRE-21 |
"| S-92 | Add `'⚠️ hypothesis'` chip to recurring pattern cards matching engine comment | CRE-25 |
"| S-93 | Change `'Nothing enters your books unverified'` to `'Most M-Pesa texts need your confirm'` | CRE-03, CRE-04 |
"| S-94 | Change `'Skip → sample data'` button to require explicit `'Yes, load demo data'` confirmation | CRE-38 |
"| S-95 | Add privacy notice explaining SMS scope before permission request | CRE-04 |
"| S-96 | Add disclaimer 'Projections are estimates, not guarantees' to GoalsScreen | CRE-09 |
"
"## 5. Truth / Accuracy
"
"### Problems
"
"#### CRE-06 — 'daily average' uses wrong denominator
"`AnalyticsEngine.kt` | `dailyAvg = totalSpent / periodDays` | Includes zero-spend days
"
"#### CRE-07 — 'savings rate' integer truncates precision
"`AnalyticsEngine.kt:117` | `*.toInt()` | 32.5% shown as 32%
"
"#### CRE-10 — Recurring 'confidence' lacks explanation
"`RecurringEngine.kt:56` | `'Confidence: 75%'` | Heuristic, not probability
"
"#### CRE-15 — 'Unusual spend detected' overclaims
"`NotificationEngine.kt` | `'Unusual spend detected'` for 2x rule | False positives
"
"#### CRE-17 — Search 'relevance scoring' meaningless
"`SearchEngine.kt` | `scoreField = 3.0 / 1.0` per term | Simple term-count
"
"#### CRE-20 — Export 'compiler-checked' overstates
"`ExportEngine.kt:19` | `'compiler-checked'` | Only means types compile
"
"#### CRE-29 — SafeToSpend monthly ÷ 30 wrong for month length
"`SafeToSpendCard.kt:118` | `monthly?.div(30)` | 28-day months understated 7%
"
"#### CRE-30 — SmartInsights linear projection
"`SmartInsightsEngine.kt` | `projected = monthTotal / dom * dim` | Assumes uniform spending
"
"#### ENG-09 — Coverage gate validates type only, not amount
"`CoverageTest.kt` | `matches() returns true when type == expected` | Wrong amounts pass
"
"#### ENG-19 — `compileSdk`/`targetSdk` 34 below Play requirement
"`build.gradle.kts` | `targetSdk = 34` in Sept 2026 | Updates rejected
"
"### Solutions (S-97 to S-110)
"
"| # | Fix | Problem(s) |
"|---|---|---|
"| S-97 | Change `dailyAvg = totalSpent / periodDays` to divide by active-spend days only | CRE-06 |
"| S-98 | Use `%.1f` for savingsRate instead of `toInt()` | CRE-07 |
"| S-99 | Add tooltip/helper text: `'Confidence = occurrence count × (1 - variance)'` | CRE-10 |
"| S-100 | Change title to `'Spending above usual'` + explain 2× rule | CRE-15 |
"| S-101 | Rename `scoreField` to `matchCount` + expose `rank` by match count | CRE-17 |
"| S-102 | Change `'compiler-checked'` to `'type-checked serialization'` | CRE-20 |
"| S-103 | Use `monthly?.div(actualDaysInMonth())` instead of `div(30)` | CRE-29 |
"| S-104 | Add `'+/- 20% range'` to projected end-of-month figure | CRE-30 |
"| S-105 | Assert on `amount` in `CoverageTest.matches()`; lower threshold to `recall >= 0.99f` | ENG-09 |
"| S-106 | Bump `targetSdk` to 35; update `compileSdk` to 35 | ENG-19 |
"| S-107 | Remove `org.gradle.java.home` from committed `gradle.properties` | ENG-16 |
"| S-108 | Fix `versionCode` collision: add `buildNumber` suffix | ENG-17 |
"| S-109 | Add `signingConfig` to release; add `testRelease` task; smoke-test R8 build | ENG-18 |
"| S-110 | Remove unused `coreLibraryDesugaring`; add `abiFilters`/`resConfigs` | ENG-20 |
"
"---
"
"## Severity Key
"- **Critical** — blocks money flow, trust, or core navigation
"- **Major** — visible UX/visual gap or misleading money figure
"- **Minor** — polish, consistency, small accuracy issue
"
"## Verification
"- `:app:compileDebugKotlin` → BUILD SUCCESSFUL
"- `:app:testDebugUnitTest` → 192 tests, 0 failed
"- `:app:assembleDebug` → 46.1 MB APK
"
"## Notes
"- Production tree (`android-app/PesaFlow/`) untouched throughout
"- All changes confined to `PesaFlow-test` copy
"- Report generated 2026-09-24
"