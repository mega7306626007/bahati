package com.pesaflow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.notifications.NotificationHelper
import com.pesaflow.app.data.notifications.ReminderScheduler
import com.pesaflow.app.data.parsers.CsvImporter
import com.pesaflow.app.ui.income.IncomeScreen
import com.pesaflow.app.ui.savings.SavingsScreen
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintSettingsNeutral
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.ui.bills.BillsScreen
import com.pesaflow.app.ui.budgets.BudgetsScreen
import com.pesaflow.app.ui.dashboard.DashboardScreen
import com.pesaflow.app.ui.dashboard.PesaBuddyAssistant
import com.pesaflow.app.ui.dashboard.QuickAddDialog
import com.pesaflow.app.ui.debt.DebtTrackingScreen
import com.pesaflow.app.ui.insights.InsightsScreen
import com.pesaflow.app.ui.onboarding.OnboardingScreen
import com.pesaflow.app.ui.reports.NetWorthScreen
import com.pesaflow.app.ui.reports.ReportsScreen
import com.pesaflow.app.ui.search.SearchScreen
import androidx.compose.material3.FloatingActionButton
import com.pesaflow.app.ui.semester.SemesterScreen
import com.pesaflow.app.ui.transactions.TransactionsScreen
import com.pesaflow.app.ui.settings.SettingsScreen
import com.pesaflow.app.ui.stock.BelongingsScreen
import com.pesaflow.app.ui.stock.KitchenScreen
import com.pesaflow.app.ui.theme.PesaFlowTheme
import com.pesaflow.app.ui.university.MealPlannerScreen
import com.pesaflow.app.ui.university.UniversityScreen
import com.pesaflow.app.viewmodels.FinanceViewModel


class MainActivity : ComponentActivity() {


    private val viewModel: FinanceViewModel by viewModels()


    private var onboarded by mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crash reporter first: any later startup failure lands on a
        // screenshot-able screen instead of a bare "keeps stopping".
        CrashReportActivity.installPreviousHandler(this)

        // Edge-to-edge: cinematic canvases draw behind status + nav bars;
        // content layers take inset padding themselves.
        WindowCompat.setDecorFitsSystemWindows(window, false)


        // Process intents from external clipboard sources or system shares
        handleIncomingSharedText(intent)


        // NOTE: no permission requests here on purpose. SMS is requested on the
        // onboarding SMS step (with rationale), notifications on first report
        // preview tap. Cold-start prompts get auto-denied and hurt Play review.


        val prefs = getPreferences(MODE_PRIVATE)
        onboarded = prefs.getBoolean("onboarding_done", false)
        viewModel.setUserName(prefs.getString("user_name", "") ?: "")


        // Re-arm one-time reminder chains (breakfast/lunch/night/Sunday): if Android killed
        // them, every app open restores the next firing. Idempotent by unique name.
        val nprefs = getSharedPreferences("pesaflow_prefs", MODE_PRIVATE)
        // Backfill for existing users: lunch_scan was introduced after they
        // onboarded, so the key is absent (not off) — default it ON once.
        if (!nprefs.contains("lunch_scan")) {
            nprefs.edit().putBoolean("lunch_scan", true).apply()
        }
        if (nprefs.getBoolean("breakfast_reminder", false)) ReminderScheduler.scheduleBreakfast(this)
        if (nprefs.getBoolean("lunch_reminder", false)) ReminderScheduler.scheduleLunch(this)
        if (nprefs.getBoolean("night_report", false)) ReminderScheduler.scheduleNightReport(this)
        if (nprefs.getBoolean("sunday_report", false)) ReminderScheduler.scheduleSundayReport(this)
        if (nprefs.getBoolean("lunch_scan", false)) ReminderScheduler.scheduleLunchScan(this)
        // Core chains have no off-switch: re-arm every cold start so the app
        // works for years, not one day. Daily/weekly stay behind user toggles.
        ReminderScheduler.scheduleDailyDigest(this)
        ReminderScheduler.scheduleMonthlyReport(this)
        ReminderScheduler.scheduleBudgetCrossingAlert(this)


        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            PesaFlowTheme(mode = themeMode) {
                // Event-driven notifications: fire the moment money moves,
                // not only on the clock. One stable id per transaction so a
                // re-notification replaces instead of stacking.
                LaunchedEffect(Unit) {
                    viewModel.transactionEvents.collect { tx ->
                        NotificationHelper.show(
                            this@MainActivity,
                            900000 + (tx.id.hashCode() and 0xFFFF),
                            "Transaction logged",
                            "${tx.merchant} · KSh ${tx.amount.toInt()} · ${tx.category}"
                        )
                        // Keep the home-screen widget fresh.
                        com.pesaflow.app.widget.PesaWidgetProvider.updateAll(this@MainActivity)
                    }
                }
                // Goal reached — the emotional peak of saving.
                LaunchedEffect(Unit) {
                    viewModel.goalEvents.collect { event ->
                        NotificationHelper.show(
                            this@MainActivity,
                            910000,
                            "Goal reached!",
                            "You saved ${event.goal.currentAmount.toInt()} of ${event.goal.targetAmount.toInt()} — ${event.goal.title} is fully funded."
                        )
                    }
                }
                // Low balance — fires once per crossing, not every emission.
                LaunchedEffect(Unit) {
                    viewModel.lowBalanceEvents.collect { bal ->
                        NotificationHelper.show(
                            this@MainActivity,
                            920000,
                            "Balance running low",
                            "KSh ${bal.toInt()} left in the ledger. Essentials only until the next upkeep."
                        )
                    }
                }
                if (onboarded) {
                    PesaFlowAppNav(viewModel = viewModel)
                } else {
                    OnboardingScreen(
                        viewModel = viewModel,
                        onDone = {
                            getPreferences(MODE_PRIVATE).edit()
                                .putBoolean("onboarding_done", true)
                                .putString("user_name", viewModel.userName.value)
                                .apply()
                            onboarded = true
                        }
                    )
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingSharedText(intent)
    }


    private fun handleIncomingSharedText(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                // Everything shared lands in Pending for Confirm / Ignore —
                // nothing unverified ever touches the permanent ledger.
                val parsedMpesa = MpesaParser.parseMessage(sharedText)
                if (parsedMpesa != null) {
                    viewModel.queueSharedTransaction(parsedMpesa)
                } else {
                    viewModel.queueSharedText(sharedText)
                }
            }
        }
    }
}


// Bottom navigation across the feature screens. State-based (no nav library)
// so no new dependency is required.
@Composable
private fun PesaFlowAppNav(viewModel: FinanceViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var moreSection by remember { mutableStateOf<String?>(null) }
    var showSpeedDial by remember { mutableStateOf(false) }
    var quickAddType by remember { mutableStateOf<TransactionType?>(null) }
    var importResult by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val rows = CsvImporter.parseCsvData(text, 0, 1, 2, 3)
                viewModel.importTransactions(rows)
                importResult = "Imported ${rows.size} transactions. ✅"
            } catch (e: Exception) {
                importResult = "Import failed: ${e.message}"
            }
        }
    }

    BackHandler(enabled = selectedTab == 4 && moreSection != null) {
        moreSection = null
    }

    Scaffold(
        // + lives on Home + Transactions only — planners/budgets/insights
        // edit their own domain, they don't need a global add button.
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showSpeedDial = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Menu, contentDescription = "Transactions") },
                    label = { Text("Transactions") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = "Budgets") },
                    label = { Text("Budgets") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.Info, contentDescription = "Insights") },
                    label = { Text("Insights") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4; moreSection = null },
                    icon = { Icon(Icons.Filled.MoreVert, contentDescription = "More") },
                    label = { Text("More") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onQuickAdd = { quickAddType = it },
                    onNavigate = { route ->
                        when (route) {
                            "search" -> { selectedTab = 4; moreSection = "search" }
                            "insights" -> { selectedTab = 3 }
                            "transactions" -> { selectedTab = 1 }
                            "budgets" -> { selectedTab = 2 }
                            "savings" -> { selectedTab = 4; moreSection = "savings" }
                            "bills" -> { selectedTab = 4; moreSection = "bills" }
                            "meals" -> { selectedTab = 4; moreSection = "meals" }
                            "reports" -> { selectedTab = 4; moreSection = "reports" }
                            "networth" -> { selectedTab = 4; moreSection = "networth" }
                            "pesa" -> { selectedTab = 4; moreSection = "pesa" }
                            "add-expense" -> { quickAddType = TransactionType.EXPENSE }
                        }
                    }
                )
                1 -> TransactionsScreen(
                    viewModel = viewModel,
                    onQuickAdd = { quickAddType = it }
                )
                2 -> BudgetsScreen(viewModel = viewModel)
                3 -> InsightsScreen(viewModel = viewModel)
                else -> when (moreSection) {
                    "settings" -> SettingsScreen(viewModel = viewModel)
                    "debt" -> DebtTrackingScreen(viewModel = viewModel)
                    "search" -> SearchScreen(viewModel = viewModel)
                    "university" -> UniversityScreen(viewModel = viewModel)
                    "semester" -> SemesterScreen(viewModel = viewModel, onNavigate = { route ->
                        when (route) {
                            "bills" -> { moreSection = "bills" }
                            "debt" -> { moreSection = "debt" }
                            "meals" -> { moreSection = "meals" }
                            "university" -> { moreSection = "university" }
                        }
                    })
                    "meals" -> MealPlannerScreen(viewModel = viewModel)
                    "bills" -> BillsScreen(viewModel = viewModel)
                    "income" -> IncomeScreen(viewModel = viewModel)
                    "pesa" -> PesaBuddyAssistant(viewModel = viewModel)
                    "networth" -> NetWorthScreen(viewModel = viewModel, onLogIncome = { quickAddType = TransactionType.INCOME })
                    "savings" -> SavingsScreen(viewModel = viewModel)
                    "reports" -> ReportsScreen(viewModel = viewModel)
                    "insights" -> InsightsScreen(viewModel = viewModel)
                    "things" -> BelongingsScreen(viewModel = viewModel)
                    "kitchen" -> KitchenScreen(viewModel = viewModel)
                    else -> MoreScreen(onSelect = { moreSection = it })
                }
            }

            quickAddType?.let { type ->
                QuickAddDialog(viewModel = viewModel, defaultType = type, onDismiss = { quickAddType = null })
            }

            if (showSpeedDial) {
                AlertDialog(
                    onDismissRequest = { showSpeedDial = false },
                    title = { Text("Add") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showSpeedDial = false; quickAddType = TransactionType.EXPENSE }, modifier = Modifier.fillMaxWidth()) {
                                Text("− Spend", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(onClick = { showSpeedDial = false; quickAddType = TransactionType.INCOME }, modifier = Modifier.fillMaxWidth()) {
                                Text("+ Income", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(onClick = { showSpeedDial = false; selectedTab = 3 }, modifier = Modifier.fillMaxWidth()) {
                                Text("Parse text (NLP)", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(onClick = { showSpeedDial = false; csvPicker.launch("text/*") }, modifier = Modifier.fillMaxWidth()) {
                                Text("Import CSV", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { showSpeedDial = false }) { Text("Close") } }
                )
            }

            importResult?.let { msg ->
                AlertDialog(
                    onDismissRequest = { importResult = null },
                    title = { Text("CSV Import") },
                    text = { Text(msg) },
                    confirmButton = { TextButton(onClick = { importResult = null }) { Text("OK") } }
                )
            }
        }
    }
}


@Composable
private fun MoreScreen(onSelect: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintSettingsNeutral, bgRes = R.drawable.bg_settings_neutral)
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("More Features", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Everything else, one tap away", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoreRow(icon = Icons.Filled.Info, title = "Insights", subtitle = "Charts, trends and advice") { onSelect("insights") }
        MoreRow(icon = Icons.Filled.DateRange, title = "Semester", subtitle = "Semester plan, runway and fees") { onSelect("semester") }
        MoreRow(icon = Icons.Filled.Menu, title = "Reports", subtitle = "Daily to annual summaries") { onSelect("reports") }
        MoreRow(icon = Icons.Filled.Settings, title = "Settings", subtitle = "Language, notifications, data") { onSelect("settings") }
        MoreRow(icon = Icons.Filled.Person, title = "Net Worth", subtitle = "Cash, savings, investments, debts") { onSelect("networth") }
        MoreRow(icon = Icons.Filled.Savings, title = "Savings", subtitle = "Goals that grow with your ledger") { onSelect("savings") }
        MoreRow(icon = Icons.Filled.Home, title = "Bills", subtitle = "Upcoming, recurring, repeats") { onSelect("bills") }
        MoreRow(icon = Icons.Filled.AccountBalance, title = "Income", subtitle = "HELB, parents, hustle — where money comes from") { onSelect("income") }
        MoreRow(icon = Icons.Filled.AccountBox, title = "Debt Tracking", subtitle = "Money owed and borrowed") { onSelect("debt") }
        MoreRow(icon = Icons.Filled.Search, title = "Search", subtitle = "Find any transaction") { onSelect("search") }
        MoreRow(icon = Icons.Filled.Star, title = "University", subtitle = "Semester planner and allowance") { onSelect("university") }
        MoreRow(icon = Icons.Filled.Favorite, title = "Meal Planner", subtitle = "Food menus under your budget") { onSelect("meals") }
        MoreRow(icon = Icons.Filled.Face, title = "PesaBuddy", subtitle = "Ask about your money") { onSelect("pesa") }
        MoreRow(icon = Icons.Filled.ShoppingCart, title = "My Things", subtitle = "Have it, need it, save for it") { onSelect("things") }
        MoreRow(icon = Icons.Filled.DateRange, title = "Kitchen Stock", subtitle = "Unga levels, refills, restock cost") { onSelect("kitchen") }
    }
    }
}


@Composable
private fun MoreRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingContent = { Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
