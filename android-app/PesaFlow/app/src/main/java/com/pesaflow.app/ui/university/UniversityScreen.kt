package com.pesaflow.app.ui.university

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.UniversityProfile
import com.pesaflow.app.viewmodels.FinanceViewModel
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.R
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.SkinAccentLine
import com.pesaflow.app.ui.theme.SkinCampus
import com.pesaflow.app.ui.theme.SkinCard
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversityScreen(viewModel: FinanceViewModel) {
    val universityProfile by viewModel.universityProfile.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var showAllowanceDialog by remember { mutableStateOf(false) }
    var showSavingsDialog by remember { mutableStateOf(false) }


    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = SkinCampus.tint, bgRes = R.drawable.bg_university_campus)
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("University Profile", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                val up = universityProfile
                val heroDaysLeft = run {
                    val e = up?.semesterEndTimestamp ?: 0L
                    if (e > System.currentTimeMillis()) ((e - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt() else 0
                }
                SkinCard(skin = SkinCampus) {
                    Text("Campus command", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    SkinAccentLine(SkinCampus.accent)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        (up?.universityName?.takeIf { it.isNotBlank() } ?: "Your campus") + " - Semester " + ((up?.currentSemester ?: 1).toString()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (heroDaysLeft > 0) heroDaysLeft.toString() + " days left - KSh " + (up?.startingFunding ?: 0.0).toInt() + " starting funds" else "Set semester dates in Edit Profile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // University Info Header
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "University Finance Tracker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showEditDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Edit Profile")
                    }
                }

                // Profile details (local val enables smart cast)
                val profile = universityProfile
                if (profile != null) {
                    UniversityProfileCard(
                        universityName = profile.universityName,
                        campus = profile.campus,
                        currentSemester = profile.currentSemester,
                        academicYear = profile.academicYear,
                        startingFunding = profile.startingFunding
                    )
                } else {
                    UniversityProfileCard(
                        universityName = "--",
                        campus = "--",
                        currentSemester = 1,
                        academicYear = "--",
                        startingFunding = 0.0
                    )
                }


                // Semester Financial Planner (real math from live transactions)
                UniversityFinancialPlanner(
                    profile = profile,
                    transactions = transactions
                )


                // Action Buttons
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Button(
                        onClick = { showAllowanceDialog = true },
                        modifier = Modifier.width(140.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Set Allowance")
                    }
                    Button(
                        onClick = { showSavingsDialog = true },
                        modifier = Modifier.width(140.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Add Savings Goal")
                    }
                }
            }
    }

    if (showEditDialog) {
        val current = universityProfile
        var name by remember { mutableStateOf(current?.universityName ?: "") }
        var campus by remember { mutableStateOf(current?.campus ?: "") }
        var semester by remember { mutableStateOf((current?.currentSemester ?: 1).toString()) }
        var year by remember { mutableStateOf(current?.academicYear ?: "") }
        var fees by remember { mutableStateOf((current?.feesAmount ?: 0.0).takeIf { it > 0 }?.toInt()?.toString() ?: "") }
        var helb by remember { mutableStateOf((current?.helbExpected ?: 0.0).takeIf { it > 0 }?.toInt()?.toString() ?: "") }
        var fundSource by remember { mutableStateOf(current?.fundingSource ?: "HELB") }
        var weeksLeft by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit University Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("University") })
                    OutlinedTextField(value = campus, onValueChange = { campus = it }, label = { Text("Campus") })
                    OutlinedTextField(value = semester, onValueChange = { semester = it }, label = { Text("Semester") })
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Academic Year") })
                    OutlinedTextField(value = fees, onValueChange = { fees = it }, label = { Text("Fees owed (KSh, optional)") })
                    OutlinedTextField(value = helb, onValueChange = { helb = it }, label = { Text("HELB expected (KSh, optional)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("HELB", "SELF", "BOTH").forEach { s ->
                            FilterChip(selected = fundSource == s, onClick = { fundSource = s }, label = { Text(s) })
                        }
                    }
                    OutlinedTextField(value = weeksLeft, onValueChange = { weeksLeft = it }, label = { Text("Weeks left in semester (sets countdown)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val sem = semester.toIntOrNull() ?: 1
                    val weeks = weeksLeft.toIntOrNull()?.takeIf { it > 0 }
                    viewModel.saveUniversityProfile(
                        (current ?: UniversityProfile()).copy(
                            universityName = name.trim(),
                            campus = campus.trim(),
                            currentSemester = sem,
                            academicYear = year.trim(),
                            feesAmount = fees.toDoubleOrNull() ?: 0.0,
                            helbExpected = helb.toDoubleOrNull() ?: 0.0,
                            fundingSource = fundSource,
                            semesterEndTimestamp = weeks?.let { System.currentTimeMillis() + it * 7 * 24L * 60 * 60 * 1000 }
                                ?: (current?.semesterEndTimestamp ?: 0L)
                        )
                    )
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAllowanceDialog) {
        var allowance by remember { mutableStateOf((universityProfile?.startingFunding ?: 0.0).toString()) }

        AlertDialog(
            onDismissRequest = { showAllowanceDialog = false },
            title = { Text("Semester Starting Funds") },
            text = {
                OutlinedTextField(value = allowance, onValueChange = { allowance = it }, label = { Text("Amount (KSh)") })
            },
            confirmButton = {
                Button(onClick = {
                    val amt = allowance.toDoubleOrNull()
                    if (amt != null && amt >= 0) {
                        viewModel.saveUniversityProfile(
                            (universityProfile ?: UniversityProfile()).copy(startingFunding = amt)
                        )
                        showAllowanceDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAllowanceDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSavingsDialog) {
        var title by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var days by remember { mutableStateOf("90") }

        AlertDialog(
            onDismissRequest = { showSavingsDialog = false },
            title = { Text("New Savings Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Goal name") })
                    OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target (KSh)") })
                    OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Days from now") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val targetVal = target.toDoubleOrNull()
                    val daysVal = days.toIntOrNull()
                    if (targetVal != null && targetVal > 0 && daysVal != null && title.isNotBlank()) {
                        viewModel.addSavingsGoal(title.trim(), targetVal, daysVal)
                        showSavingsDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSavingsDialog = false }) { Text("Cancel") } }
        )
    }
    }
}


@Composable
fun UniversityProfileCard(
    universityName: String,
    campus: String,
    currentSemester: Int,
    academicYear: String,
    startingFunding: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("University Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            UniversityInfoRow(label = "University", value = universityName)
            UniversityInfoRow(label = "Campus", value = campus)
            UniversityInfoRow(label = "Semester", value = "$currentSemester")
            UniversityInfoRow(label = "Academic Year", value = academicYear)
            UniversityInfoRow(label = "Starting Funding", value = "KSh ${startingFunding.toInt()}")
        }
    }
}


@Composable
fun UniversityInfoRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.width(120.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "More info",
            tint = Color.Gray
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}


@Composable
fun UniversityFinancialPlanner(
    profile: UniversityProfile?,
    transactions: List<Transaction>
) {
    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    val funding = profile?.startingFunding ?: 0.0
    // Real semester bounds: profile dates win, else the academic calendar —
    // never "earliest transaction ever" (that summed all-time) and never an
    // uncapped `>= start` (that kept counting past the semester's end).
    val (start, end) = com.pesaflow.app.data.academic.semesterBounds(
        profile?.semesterStartTimestamp ?: 0L,
        profile?.semesterEndTimestamp ?: 0L,
        now
    )
    val cap = minOf(end, now + 1)

    val income = transactions
        .filter { it.type == TransactionType.INCOME && it.dateTimestamp >= start && it.dateTimestamp < cap }
        .sumOf { it.amount }
    val spent = transactions
        .filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= start && it.dateTimestamp < cap }
        .sumOf { it.amount }
    val remaining = funding + income - spent
    val daysElapsed = ((now - start) / day).coerceAtLeast(1)
    val daysLeft = ((end - now) / day).coerceAtLeast(0)
    // Ceiling weeks: 8 days left is 2 weeks of allowance, not 1.
    val weeksLeft = com.pesaflow.app.data.academic.wholeWeeksCeil(daysLeft).toInt()
    val pace = spent / daysElapsed
    val weeklyAllowance = if (weeksLeft > 0) remaining / weeksLeft else remaining
    val projected = (remaining - pace * daysLeft).toInt()


    if (funding <= 0 && income <= 0 && spent <= 0) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Semester Financial Planner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Nothing to forecast yet.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Set your allowance below and log a few transactions — this card becomes your live forecast: what's left, daily pace, weekly allowance, projected end.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    val ended = now >= end
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Semester Financial Planner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "KSh ${remaining.toInt()}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Text(
                "remaining of KSh ${funding.toInt()} starting funds",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            UniversityInfoRow(label = "Semester income", value = "+ KSh ${income.toInt()}", color = MaterialTheme.colorScheme.primary)
            UniversityInfoRow(label = "Spent so far", value = "− KSh ${spent.toInt()}", color = MaterialTheme.colorScheme.error)
            UniversityInfoRow(label = "Daily pace", value = "KSh ${pace.toInt()}/day")
            UniversityInfoRow(label = "Time left", value = if (weeksLeft > 0) "$weeksLeft weeks ($daysLeft days)" else "$daysLeft days")
            UniversityInfoRow(
                label = "Weekly allowance",
                value = "KSh ${weeklyAllowance.toInt()}",
                color = if (weeklyAllowance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            UniversityInfoRow(
                label = "Projected end",
                value = "KSh $projected",
                color = if (projected < 2000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            val healthTotal = (funding + income).coerceAtLeast(1.0)
            val healthFrac = (remaining / healthTotal).toFloat().coerceIn(0f, 1f)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Semester health " + (healthFrac * 100).toInt() + "%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { healthFrac },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = when {
                    remaining < 0 || projected < 2000 -> MaterialTheme.colorScheme.error
                    healthFrac < 0.3f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            val feesAmt = profile?.feesAmount ?: 0.0
            val feesDue = profile?.feesDueDate ?: 0L
            if (feesAmt > 0) {
                val feeDays = ((feesDue - now) / day).toInt()
                UniversityInfoRow(
                    label = "Fees owed",
                    value = "KSh " + feesAmt.toInt() + if (feesDue <= 0L) "" else if (feeDays < 0) " - overdue " + (-feeDays) + "d" else " - due in " + feeDays + "d",
                    color = if (feeDays < 0 && feesDue > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                val helbExp = profile?.helbExpected ?: 0.0
                if (helbExp > 0) {
                    val afterFees = helbExp - feesAmt
                    UniversityInfoRow(
                        label = "HELB after fees",
                        value = "KSh " + afterFees.toInt() + if (afterFees >= 0) " for upkeep" else " short — gap plan",
                        color = if (afterFees >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                when {
                    ended -> "Semester window ended with KSh ${remaining.toInt()} left. Start a new semester from Edit Profile. 🏁"
                    remaining < 0 -> "⚠️ You're past zero — log income or cut spending immediately."
                    projected < 2000 -> "⚠️ At this pace you'll end near KSh $projected. Slow spending or add income."
                    else -> "On track 👌 — projected KSh $projected at semester end if pace holds."
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (remaining < 0 || projected < 2000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            if (!ended && (remaining < 0 || projected < 2000)) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Broke-week essentials 🛟", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                listOf(
                    "Ugali + sukuma stretches furthest per shilling",
                    "Cook in bulk twice a week, reheat",
                    "Carry water — skip the soda",
                    "Walk trips under 2 km",
                    "Borrow notes, don't buy bundles for PDFs"
                ).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}
