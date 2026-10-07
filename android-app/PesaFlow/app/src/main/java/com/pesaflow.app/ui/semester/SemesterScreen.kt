package com.pesaflow.app.ui.semester

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.pesaflow.app.data.models.*
import com.pesaflow.app.data.schedule.WeekPlan
import com.pesaflow.app.data.schedule.schoolDaysFromPlan
import com.pesaflow.app.data.schedule.monthlyFare
import com.pesaflow.app.data.schedule.parseClock
import com.pesaflow.app.data.schedule.formatClock
import com.pesaflow.app.data.schedule.commuteSummary
import com.pesaflow.app.data.schedule.CommutePlan
import com.pesaflow.app.data.schedule.TripRoute
import com.pesaflow.app.data.schedule.routeMonthly
import com.pesaflow.app.data.schedule.totalMonthly
import com.pesaflow.app.data.schedule.routeSummary
import com.pesaflow.app.data.schedule.encodeRoutes
import com.pesaflow.app.data.schedule.decodeRoutes
import com.pesaflow.app.data.schedule.normTime
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.SectionHeader
import com.pesaflow.app.ui.university.UniversityFinancialPlanner
import com.pesaflow.app.viewmodels.FinanceViewModel
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintSemesterGold
import com.pesaflow.app.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterScreen(viewModel: FinanceViewModel, onNavigate: (String) -> Unit = {}) {
    val profile by viewModel.universityProfile.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val mealItems by viewModel.mealItems.collectAsState()
    var roommates by remember { mutableStateOf(2) }
    var fare by remember { mutableStateOf("") }
    val context = LocalContext.current
    val semPrefs = remember { context.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE) }
    // School days: your saved pick → timetable → Mon–Fri. Toggle any day;
    // the timetable stays the source, this is the override.
    var commuteDaysSel by remember {
        mutableStateOf(
            semPrefs.getString("commute_days", null)?.split(",")?.map { it.trim() }
                .orEmpty().filter { it in WeekPlan.DAYS }.toSet()
                .ifEmpty { schoolDaysFromPlan(WeekPlan.load(context)) }
                .ifEmpty { setOf("Mon", "Tue", "Wed", "Thu", "Fri") }
        )
    }
    // Trip times: asked once, editable forever. Leave home ~go, back ~back.
    var goTime by remember { mutableStateOf(semPrefs.getString("commute_go", "07:30") ?: "07:30") }
    var backTime by remember { mutableStateOf(semPrefs.getString("commute_back", "17:00") ?: "17:00") }
    var timeErr by remember { mutableStateOf<String?>(null) }
    // Other destinations: church, town, shags. Each with own days, fare
    // and optional times (blank = flexible hours). Persisted, editable.
    var extraTrips by remember { mutableStateOf(decodeRoutes(semPrefs.getString("extra_trips", null))) }
    var showExForm by remember { mutableStateOf(false) }
    var exName by remember { mutableStateOf("") }
    var exFare by remember { mutableStateOf("") }
    var exDays by remember { mutableStateOf(setOf<String>()) }
    var exGo by remember { mutableStateOf("") }
    var exBack by remember { mutableStateOf("") }
    var exErr by remember { mutableStateOf<String?>(null) }
    fun saveExtras(next: List<TripRoute>) {
        extraTrips = next
        semPrefs.edit().putString("extra_trips", encodeRoutes(next)).apply()
    }
    // Autofill: onboarding already asked daily transport and saved a
    // Transport budget — never ask the same question twice. One-way fare
    // ≈ monthly / (2 × timetable days × 4.33).
    val transportBudget = budgets.filter {
        it.category.equals("Transport", ignoreCase = true)
    }.maxByOrNull { it.limitAmount }
    LaunchedEffect(transportBudget?.limitAmount) {
        if (fare.isBlank() && (transportBudget?.limitAmount ?: 0.0) > 0) {
            val days = commuteDaysSel.size.coerceAtLeast(1)
            val oneWay = transportBudget!!.limitAmount / (2 * days * 4.33)
            if (oneWay > 0) fare = oneWay.toInt().toString()
        }
    }
    var showChamaDialog by remember { mutableStateOf(false) }
    val chamas by viewModel.chamaGroups.collectAsState()
    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val doc = buildSemesterPdf(transactions, budgets, profile)
                    try {
                        doc.writeTo(out)
                    } finally {
                        doc.close()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "PDF failed — check storage and retry.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    val end = profile?.semesterEndTimestamp?.takeIf { it > 0 } ?: (now + 120 * day)
    val daysLeft = ((end - now) / day).coerceAtLeast(0)
    val openBills = bills.filter { it.status != "PAID" }
    val openDebts = debts.filter { it.status != "PAID" }
    val rentBills = openBills.filter {
        it.category.equals("Rent", ignoreCase = true) ||
            it.name.contains("rent", ignoreCase = true) ||
            it.name.contains("hostel", ignoreCase = true)
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintSemesterGold, bgRes = R.drawable.bg_semester_gold)
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Semester 🎓", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AtmosphereBand(
                workspace = AtmoWorkspace.OASIS,
                title = "Semester oasis",
                subtitle = "Funding · runway · fees countdown"
            )
            // Countdown hero
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Semester Countdown", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "$daysLeft days left",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        profile?.universityName?.takeIf { it.isNotBlank() }?.let { "$it · Semester ${profile?.currentSemester ?: 1}" } ?: "Set your university under More → University",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { pdfSaver.launch("pesaplanner-semester.pdf") }) { Text("Semester statement (PDF)") }
                }
            }

            // Fees countdown (HELB vs fees owed)
            run {
                val p = profile
                if ((p?.feesAmount ?: 0.0) > 0) {
                    val daysToFees = ((p!!.feesDueDate - now) / day).coerceAtLeast(0)
                    val cover = if (p.helbExpected > 0) (p.helbExpected / p.feesAmount * 100).toInt() else 0
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Fees Countdown 🎓", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "KSh ${p.feesAmount.toInt()} due in $daysToFees days",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (daysToFees <= 14) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (p.fundingSource == "SELF") "Self-sponsored — pocket + logged income carry the fees. 💪"
                                else if (p.helbExpected > 0) "HELB expected KSh ${p.helbExpected.toInt()} covers $cover% of it."
                                else "Set HELB expectation in onboarding or University profile.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (p.fundingSource != "SELF" && p.helbExpected > 0) {
                                val afterFees = p.helbExpected - p.feesAmount
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (afterFees >= 0) "After fees: KSh ${afterFees.toInt()} of HELB stays for upkeep. 🍲"
                                    else "Fees exceed HELB by KSh ${(-afterFees).toInt()} — plan the gap early, don't borrow it. 🙂",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (afterFees >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }


            // Live planner (reused, same math everywhere)
            UniversityFinancialPlanner(profile = profile, transactions = transactions)

            // Rent / hostel split
            if (rentBills.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Rent Split 🏠", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        rentBills.forEach { bill ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bill.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        "KSh ${bill.amount.toInt()} ÷ $roommates = KSh ${(bill.amount / roommates).toInt()} each",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { roommates = (roommates - 1).coerceAtLeast(1) }) { Text("−") }
                                    Text("$roommates", fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { roommates = (roommates + 1).coerceAtMost(8) }) { Text("+") }
                                }
                            }
                        }
                    }
                }
            }

            // Matatu preset: timetable days × trips × fare → Transport budget.
            // Days read the timetable, times are asked once and editable.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Matatu Preset 🚌", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if ((transportBudget?.limitAmount ?: 0.0) > 0) {
                        Text(
                            "Autofilled from your daily transport (≈ KSh ${(transportBudget!!.limitAmount / 30).toInt()}/day) — edit if the route changed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    val ttDays = remember { schoolDaysFromPlan(WeekPlan.load(context)) }
                    Text(
                        if (ttDays.isNotEmpty()) "School days from your timetable — tap to edit."
                        else "No timetable yet — using Mon–Fri. Set it in Menu Planner → timetable import.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WeekPlan.DAYS.forEach { d ->
                            FilterChip(
                                selected = d in commuteDaysSel,
                                onClick = {
                                    commuteDaysSel = if (d in commuteDaysSel) commuteDaysSel - d else commuteDaysSel + d
                                    semPrefs.edit().putString("commute_days", commuteDaysSel.sortedBy { WeekPlan.DAYS.indexOf(it) }.joinToString(",")).apply()
                                },
                                label = { Text(d, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fare,
                            onValueChange = { fare = it },
                            label = { Text("Fare one-way") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = goTime,
                            onValueChange = { v ->
                                goTime = v
                                parseClock(v)?.let { (h, m) ->
                                    goTime = formatClock(h, m)
                                    semPrefs.edit().putString("commute_go", goTime).apply()
                                    timeErr = null
                                } ?: run { timeErr = "Use times like 07:30." }
                            },
                            label = { Text("Leave") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = backTime,
                            onValueChange = { v ->
                                backTime = v
                                parseClock(v)?.let { (h, m) ->
                                    backTime = formatClock(h, m)
                                    semPrefs.edit().putString("commute_back", backTime).apply()
                                    timeErr = null
                                } ?: run { timeErr = "Use times like 17:00." }
                            },
                            label = { Text("Back") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    timeErr?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val monthlyTransport = monthlyFare(fare.toDoubleOrNull() ?: 0.0, commuteDaysSel.size) + totalMonthly(extraTrips)
                    Text(
                        commuteSummary(
                            CommutePlan(
                                days = commuteDaysSel,
                                fareOneWay = fare.toDoubleOrNull() ?: 0.0,
                                goTime = goTime,
                                backTime = backTime
                            )
                        ) + if (extraTrips.isNotEmpty()) " + ${extraTrips.size} other trip(s)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Other destinations, each deletable; times optional.
                    extraTrips.forEach { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                routeSummary(r),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { saveExtras(extraTrips - r) }) {
                                Text("×", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (showExForm) {
                        OutlinedTextField(
                            value = exName,
                            onValueChange = { exName = it },
                            label = { Text("Where to? (e.g. Church)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = exFare,
                                onValueChange = { exFare = it },
                                label = { Text("Fare one-way") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = exGo,
                                onValueChange = { exGo = it },
                                label = { Text("Leave (opt.)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = exBack,
                                onValueChange = { exBack = it },
                                label = { Text("Back (opt.)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WeekPlan.DAYS.forEach { d ->
                                FilterChip(
                                    selected = d in exDays,
                                    onClick = { exDays = if (d in exDays) exDays - d else exDays + d },
                                    label = { Text(d, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                        exErr?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val f = exFare.toDoubleOrNull()
                                val g = normTime(exGo.trim())
                                val b = normTime(exBack.trim())
                                when {
                                    exName.isBlank() -> exErr = "Name the trip."
                                    f == null || f <= 0 -> exErr = "Fare must be above 0."
                                    exDays.isEmpty() -> exErr = "Pick at least one day."
                                    g == null || b == null -> exErr = "Times like 08:00, or leave blank for flexible."
                                    else -> {
                                        saveExtras(extraTrips + TripRoute(exName.trim(), exDays, f, g, b))
                                        exName = ""; exFare = ""; exDays = emptySet(); exGo = ""; exBack = ""
                                        exErr = null; showExForm = false
                                    }
                                }
                            }) { Text("Add trip") }
                            TextButton(onClick = { showExForm = false; exErr = null }) { Text("Cancel") }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        TextButton(onClick = { showExForm = true }) { Text("+ Sometimes go elsewhere? Add a trip") }
                    }
                    val currentTransport = transportBudget?.limitAmount ?: 0.0
                    if (currentTransport > 0) {
                        Text(
                            "Budget now: KSh ${currentTransport.toInt()}/month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "≈ KSh ${monthlyTransport.toInt()}/month",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        var presetAck by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                viewModel.upsertBudget("Transport", monthlyTransport, com.pesaflow.app.data.models.BudgetType.MONTHLY)
                                presetAck = true
                            },
                            enabled = monthlyTransport > 0
                        ) { Text(if (presetAck) "Saved ✓" else "Set Budget") }
                    }
                    Text(
                        "Replaces your Transport budget (never duplicates) — Budgets, safe-to-spend and insights update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


            // Chama tracker: rotating savings, who eats next
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Chama 🤝", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showChamaDialog = true }) { Text("+ New") }
                    }
                    if (chamas.isEmpty()) {
                        Text("No chama yet. Track merry-go-rounds: members, contribution, who's next.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        chamas.forEach { g ->
                            val members = g.members.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val nextIdx = if (members.isNotEmpty()) g.paidCycles % members.size else 0
                            val nextDate = g.startTimestamp + (g.paidCycles + 1L) * g.cycleDays * day
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(g.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (members.isEmpty()) "Add members to start rotation"
                                        else "Next: ${members[nextIdx]} · KSh ${g.contribution.toInt()} · ${java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(nextDate))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.advanceChama(g) }) { Text("Paid ✓") }
                                TextButton(onClick = { viewModel.deleteChamaGroup(g.id) }) { Text("×", color = Color.Red) }
                            }
                        }
                    }
                }
            }


            if (showChamaDialog) {
                var cname by remember { mutableStateOf("") }
                var camt by remember { mutableStateOf("") }
                var cmembers by remember { mutableStateOf("") }
                var ccycle by remember { mutableStateOf("30") }
                AlertDialog(
                    onDismissRequest = { showChamaDialog = false },
                    title = { Text("New Chama") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = cname, onValueChange = { cname = it }, label = { Text("Group name") })
                            OutlinedTextField(value = camt, onValueChange = { camt = it }, label = { Text("Contribution (KSh)") })
                            OutlinedTextField(value = cmembers, onValueChange = { cmembers = it }, label = { Text("Members, comma order") })
                            OutlinedTextField(value = ccycle, onValueChange = { ccycle = it }, label = { Text("Cycle days") })
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val amt = camt.toDoubleOrNull()
                            val cyc = ccycle.toIntOrNull()
                            if (cname.isNotBlank() && amt != null && amt > 0 && cyc != null && cyc > 0 && cmembers.isNotBlank()) {
                                viewModel.addChamaGroup(cname.trim(), amt, cmembers.trim(), cyc)
                                showChamaDialog = false
                            }
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { showChamaDialog = false }) { Text("Cancel") } }
                )
            }


            // Hub links: everything semester-lives here
            SectionHeader(title = "Semester Hub")
            HubRow(title = "Bills", subtitle = "${openBills.size} open · KSh ${openBills.sumOf { it.amount }.toInt()}", onClick = { onNavigate("bills") })
            HubRow(title = "Debts", subtitle = "${openDebts.size} open · KSh ${openDebts.sumOf { it.amount }.toInt()}", onClick = { onNavigate("debt") })
            HubRow(title = "Meal Planner", subtitle = "${mealItems.size} foods saved", onClick = { onNavigate("meals") })
            HubRow(title = "University Profile", subtitle = "Allowance, campus, semester", onClick = { onNavigate("university") })
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


    }
private fun buildSemesterPdf(
    transactions: List<Transaction>,
    budgets: List<Budget>,
    profile: UniversityProfile?
): android.graphics.pdf.PdfDocument {
    val doc = android.graphics.pdf.PdfDocument()
    val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val c = page.canvas
    val title = android.graphics.Paint().apply { textSize = 22f; isFakeBoldText = true }
    val body = android.graphics.Paint().apply { textSize = 12f }
    var y = 60f
    fun line(s: String, big: Boolean = false) {
        c.drawText(s.take(80), 40f, y, if (big) title else body)
        y += if (big) 30f else 20f
    }
    line("PesaPlanner — Semester Statement", true)
    line(profile?.universityName?.takeIf { it.isNotBlank() } ?: "University")
    val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val spent = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    line("Income: KSh ${income.toInt()}")
    line("Expenses: KSh ${spent.toInt()}")
    line("Balance: KSh ${(income - spent).toInt()}")
    line("Top categories:")
    transactions.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }.take(5)
        .forEach { line("  ${it.key}: KSh ${it.value.toInt()}") }
    line("Budgets:")
    budgets.forEach { line("  ${it.category} (${it.type.name}): KSh ${it.limitAmount.toInt()}") }
    doc.finishPage(page)
    return doc
}


@Composable
private fun HubRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
