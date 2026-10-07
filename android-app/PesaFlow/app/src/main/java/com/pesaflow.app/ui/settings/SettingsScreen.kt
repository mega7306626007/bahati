package com.pesaflow.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.app.Activity
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.pesaflow.app.data.notifications.NotificationHelper
import com.pesaflow.app.data.notifications.ReminderScheduler
import com.pesaflow.app.data.parsers.MpesaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.AppTheme
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.parsers.CsvImporter
import com.pesaflow.app.viewmodels.FinanceViewModel
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.glass
import com.pesaflow.app.R
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintSettingsNeutral
import com.pesaflow.app.ui.theme.SkinSectionHeader


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val hiddenSections by viewModel.hiddenSections.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val mealItems by viewModel.mealItems.collectAsState()
    val chamas by viewModel.chamaGroups.collectAsState()
    val universityProfile by viewModel.universityProfile.collectAsState()

    val prefs = remember { context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE) }
    var lastRun by remember { mutableStateOf(prefs.getLong("last_run", 0)) }
    var notifTransactions by remember { mutableStateOf(prefs.getBoolean("daily_summary", false)) }
    var notifWeekly by remember { mutableStateOf(prefs.getBoolean("weekly_recap", false)) }
    var sundayReport by remember { mutableStateOf(prefs.getBoolean("sunday_report", false)) }
    var nightReport by remember { mutableStateOf(prefs.getBoolean("night_report", true)) }
    var smsGranted by remember { mutableStateOf(hasSmsPermission(context)) }
    var smsAsked by remember { mutableStateOf(false) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsGranted = granted
        smsAsked = true
    }
    val inboxScope = rememberCoroutineScope()
    var inboxResult by remember { mutableStateOf<String?>(null) }
    var scanRange by remember { mutableStateOf("All") }
    fun scanInbox(range: String = scanRange) {
        inboxScope.launch(Dispatchers.IO) {
            try {
                // Time scope: Today / Week / Month start at local midnight,
                // month start, or 0 (everything).
                val now = System.currentTimeMillis()
                val day = 24L * 60 * 60 * 1000
                val dayStart = java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val rangeStart = when (range) {
                    "Today" -> dayStart
                    "Week" -> dayStart - 6 * day
                    "Month" -> java.util.Calendar.getInstance().apply {
                        timeInMillis = now
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    else -> 0L
                }
                var found = 0
                var queued = 0
                var dupes = 0
                var unreadable = 0
                val samples = mutableListOf<String>()
                val badSenders = mutableMapOf<String, Int>()
                val selection = if (rangeStart > 0) {
                    "(address LIKE ? OR address LIKE ? OR body LIKE ?) AND date >= ?"
                } else {
                    "address LIKE ? OR address LIKE ? OR body LIKE ?"
                }
                val args = if (rangeStart > 0) {
                    arrayOf("%MPESA%", "%Safaricom%", "%M-PESA%", rangeStart.toString())
                } else {
                    arrayOf("%MPESA%", "%Safaricom%", "%M-PESA%")
                }
                context.contentResolver.query(
                    android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf("_id", "address", "body", "date"),
                    selection,
                    args,
                    "date DESC LIMIT 750"
                )?.use { c ->
                    val bodyIdx = c.getColumnIndexOrThrow("body")
                    val addrIdx = c.getColumnIndexOrThrow("address")
                    while (c.moveToNext()) {
                        found++
                        val body = c.getString(bodyIdx) ?: ""
                        val sender = c.getString(addrIdx) ?: "?"
                        // Balance harvest runs on every text, parseable or not.
                        com.pesaflow.app.data.parsers.parseBalance(body)?.let {
                            com.pesaflow.app.data.parsers.saveMpesaBalance(context, it)
                        }
                        val pending = MpesaParser.parseMessage(body)
                        if (pending == null) {
                            unreadable++
                            badSenders[sender] = (badSenders[sender] ?: 0) + 1
                            // Keep the first 3 failures (sender + opening words) so the
                            // exact format can be taught to the parser next update.
                            if (samples.size < 3) {
                                val head = body.replace("\n", " ").trim().take(90)
                                samples.add("$sender: $head")
                            }
                        } else if (viewModel.tryQueuePending(pending)) {
                            queued++
                        } else {
                            dupes++
                        }
                    }
                }
                val scopeLabel = if (range == "All") "" else " ($range)"
                com.pesaflow.app.data.parsers.saveScanStats(context, found, (found - unreadable).coerceAtLeast(0), unreadable)
                inboxResult = if (found == 0) {
                    "No M-Pesa/Safaricom texts found$scopeLabel — nothing to parse."
                } else {
                    buildString {
                        append("Scanned $found text(s)$scopeLabel: $queued new pending — approve them on Home. ✅")
                        append(" Coverage ${if (found > 0) ((found - unreadable) * 100 / found).coerceIn(0, 100) else 100}% read.")
                        if (dupes > 0) append(" $dupes already in your ledger (skipped, no doubles).")
                        if (unreadable > 0) {
                            append(" $unreadable I can't read yet")
                            val topSenders = badSenders.entries.sortedByDescending { it.value }.take(2)
                            if (topSenders.isNotEmpty()) {
                                append(" [" + topSenders.joinToString(", ") { "${it.key}×${it.value}" } + "]")
                            }
                            append(".")
                            samples.forEach { append("\n• $it…") }
                        }
                    }
                }
            } catch (e: SecurityException) {
                inboxResult = "SMS permission needed — tap Enable first."
            } catch (e: Exception) {
                inboxResult = "Scan failed: ${e.message}"
            }
        }
    }
    var autoApprove by remember { mutableStateOf(prefs.getBoolean("auto_approve_mpesa", false)) }
    var autoFaces by remember { mutableStateOf(prefs.getBoolean("auto_confirm_faces", false)) }
    var notifGranted by remember { mutableStateOf(hasNotifPermission(context)) }
    var notifAsked by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifGranted = granted
        notifAsked = true
    }
    fun askNotif() {
        notifGranted = hasNotifPermission(context)
        if (notifGranted) return
        if (Build.VERSION.SDK_INT < 33) {
            notifGranted = true
            return
        }
        val activity = context as? Activity
        if (notifAsked && activity != null && !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            context.startActivity(i)
        } else {
            notifAsked = true
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<List<Transaction>?>(null) }
    var showDupeConfirm by remember { mutableStateOf(false) }
    var dupeResult by remember { mutableStateOf<String?>(null) }

    var backupMsg by remember { mutableStateOf<String?>(null) }
    fun buildBackupJson(): String {
        val root = org.json.JSONObject()
        root.put("version", 1)
        val ta = org.json.JSONArray()
        transactions.forEach { tx ->
            ta.put(org.json.JSONObject()
                .put("id", tx.id).put("amount", tx.amount).put("type", tx.type.name)
                .put("category", tx.category).put("dateTimestamp", tx.dateTimestamp)
                .put("merchant", tx.merchant).put("description", tx.description)
                .put("paymentMethod", tx.paymentMethod.name).put("source", tx.source.name)
                .put("sourceTransactionId", tx.sourceTransactionId ?: ""))
        }
        root.put("transactions", ta)
        val ba = org.json.JSONArray()
        budgets.forEach { b ->
            ba.put(org.json.JSONObject()
                .put("id", b.id).put("category", b.category).put("limitAmount", b.limitAmount)
                .put("type", b.type.name).put("startTimestamp", b.startTimestamp)
                .put("endTimestamp", b.endTimestamp).put("sharedWith", b.sharedWith))
        }
        root.put("budgets", ba)
        val ga = org.json.JSONArray()
        savingsGoals.forEach { g ->
            ga.put(org.json.JSONObject()
                .put("id", g.id).put("title", g.title).put("targetAmount", g.targetAmount)
                .put("currentAmount", g.currentAmount).put("targetTimestamp", g.targetTimestamp))
        }
        root.put("goals", ga)
        val pa = org.json.JSONArray()
        universityProfile?.let { p ->
            pa.put(org.json.JSONObject()
                .put("universityName", p.universityName).put("campus", p.campus)
                .put("currentSemester", p.currentSemester).put("academicYear", p.academicYear)
                .put("semesterStartTimestamp", p.semesterStartTimestamp).put("semesterEndTimestamp", p.semesterEndTimestamp)
                .put("startingFunding", p.startingFunding).put("helbExpected", p.helbExpected).put("fundingSource", p.fundingSource)
                .put("feesAmount", p.feesAmount).put("feesDueDate", p.feesDueDate))
        }
        root.put("profile", pa)
        val la = org.json.JSONArray()
        bills.forEach { b ->
            la.put(org.json.JSONObject()
                .put("id", b.id).put("name", b.name).put("amount", b.amount)
                .put("dueDate", b.dueDate).put("category", b.category)
                .put("frequency", b.frequency).put("status", b.status))
        }
        root.put("bills", la)
        val da = org.json.JSONArray()
        debts.forEach { d ->
            da.put(org.json.JSONObject()
                .put("id", d.id).put("person", d.person).put("amount", d.amount)
                .put("dateBorrowed", d.dateBorrowed).put("dueDate", d.dueDate)
                .put("description", d.description).put("status", d.status))
        }
        root.put("debts", da)
        val ma = org.json.JSONArray()
        mealItems.forEach { m ->
            ma.put(org.json.JSONObject()
                .put("id", m.id).put("name", m.name).put("mealType", m.mealType)
                .put("price", m.price).put("component", m.component).put("source", m.source))
        }
        root.put("meals", ma)
        val ca = org.json.JSONArray()
        chamas.forEach { g ->
            ca.put(org.json.JSONObject()
                .put("id", g.id).put("name", g.name).put("contribution", g.contribution)
                .put("members", g.members).put("cycleDays", g.cycleDays)
                .put("startTimestamp", g.startTimestamp).put("paidCycles", g.paidCycles))
        }
        root.put("chamas", ca)
        return root.toString()
    }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(buildBackupJson().toByteArray()) }
                backupMsg = "Backup saved. ✅"
            } catch (e: Exception) {
                backupMsg = "Backup failed: ${e.message}"
            }
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                viewModel.restoreBackup(text) { ok -> backupMsg = if (ok) "Restore complete. ✅" else "Not a PesaPlanner backup." }
            } catch (e: Exception) {
                backupMsg = "Restore failed: ${e.message}"
            }
        }
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importPreview = try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                CsvImporter.parseCsvData(text, 0, 1, 2, 3)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintSettingsNeutral, bgRes = R.drawable.bg_settings_neutral)
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // How it all fits together (plain words first, toggles after)
            item {
                AtmosphereBand(
                    workspace = AtmoWorkspace.STAGE,
                    title = "Control deck",
                    subtitle = "Detection · alerts · data"
                )
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(shape = RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("How PesaFlow works 🔄", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("1️⃣ We spot your M-Pesa texts the moment they arrive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("2️⃣ You confirm each one — right in the notification, or on Home → Pending.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("3️⃣ Budgets, insights and reports update themselves. That's the whole app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Everything below tunes that flow. Nothing here can break your data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SkinSectionHeader(title = "APPEARANCE", subtitle = "Language, theme, home sections")
            }


            // Language Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Language", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            LanguageOptionChip(
                                language = AppLanguage.ENGLISH,
                                isSelected = currentLanguage == AppLanguage.ENGLISH,
                                onSelect = { viewModel.setLanguage(AppLanguage.ENGLISH) }
                            ) {
                                Text("English", color = if (currentLanguage == AppLanguage.ENGLISH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            LanguageOptionChip(
                                language = AppLanguage.KISWAHILI,
                                isSelected = currentLanguage == AppLanguage.KISWAHILI,
                                onSelect = { viewModel.setLanguage(AppLanguage.KISWAHILI) }
                            ) {
                                Text("Kiswahili", color = if (currentLanguage == AppLanguage.KISWAHILI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            LanguageOptionChip(
                                language = AppLanguage.SHENG,
                                isSelected = currentLanguage == AppLanguage.SHENG,
                                onSelect = { viewModel.setLanguage(AppLanguage.SHENG) }
                            ) {
                                Text("Sheng", color = if (currentLanguage == AppLanguage.SHENG) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            LanguageOptionChip(
                                language = AppLanguage.MIXED,
                                isSelected = currentLanguage == AppLanguage.MIXED,
                                onSelect = { viewModel.setLanguage(AppLanguage.MIXED) }
                            ) {
                                Text("Mixed", color = if (currentLanguage == AppLanguage.MIXED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }


            // Theme Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = themeMode == AppTheme.SYSTEM,
                                onClick = { viewModel.setThemeMode(AppTheme.SYSTEM) },
                                label = { Text("📱 System") }
                            )
                            FilterChip(
                                selected = themeMode == AppTheme.LIGHT,
                                onClick = { viewModel.setThemeMode(AppTheme.LIGHT) },
                                label = { Text("☀️ Light") }
                            )
                            FilterChip(
                                selected = themeMode == AppTheme.DARK,
                                onClick = { viewModel.setThemeMode(AppTheme.DARK) },
                                label = { Text("🌙 Dark") }
                            )
                            FilterChip(
                                selected = themeMode == AppTheme.AMOLED,
                                onClick = { viewModel.setThemeMode(AppTheme.AMOLED) },
                                label = { Text("⬛ AMOLED") }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Dark saves battery, Light wins in sunlight. Instant switch. ✨", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemePreviewSwatch(
                                name = "Light",
                                bg = Color(0xFFFFEFD5),
                                selected = themeMode == AppTheme.LIGHT,
                                onClick = { viewModel.setThemeMode(AppTheme.LIGHT) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemePreviewSwatch(
                                name = "Dark",
                                bg = Color(0xFF121212),
                                selected = themeMode == AppTheme.DARK,
                                onClick = { viewModel.setThemeMode(AppTheme.DARK) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemePreviewSwatch(
                                name = "AMOLED",
                                bg = Color.Black,
                                selected = themeMode == AppTheme.AMOLED,
                                onClick = { viewModel.setThemeMode(AppTheme.AMOLED) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }


            // Home sections visibility
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Home Sections", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf("safe" to "Safe-to-spend", "pending" to "Pending approvals", "recent" to "Recent ledgers").forEach { (key, label) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Switch(checked = key !in hiddenSections, onCheckedChange = { viewModel.toggleSection(key) })
                            }
                        }
                    }
                }
            }


            item {
                SkinSectionHeader(title = "NOTIFICATIONS AND DETECTION", subtitle = "Alerts, M-Pesa scan, listeners")
            }


            // Notification Settings
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Notifications", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("System permission", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (notifGranted) "ON ✓ — reminders can appear."
                                    else "Off — reminders are scheduled but silent.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (notifGranted) {
                                Text("ON ✓", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            } else {
                                TextButton(onClick = { askNotif() }) { Text(if (notifAsked) "Open Settings" else "Enable") }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Hear one now", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                NotificationHelper.show(context, 99, "Test ✓", "PesaFlow notifications work on this phone.")
                            }) { Text("Send test") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Preview real reports", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { ReminderScheduler.previewNightReport(context) }) { Text("Night now") }
                            TextButton(onClick = { ReminderScheduler.previewSundayReport(context) }) { Text("Sunday now") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Last background check", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (lastRun == 0L) "never yet"
                                    else java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastRun)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { lastRun = prefs.getLong("last_run", 0) }) { Text("Refresh") }
                            }
                        }
                        Text(
                            "If scheduled pings never arrive, exempt PesaFlow from battery optimization in system settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Battery exemption", style = MaterialTheme.typography.bodySmall)
                                val powerManager = remember {
                                    context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                                }
                                var batteryClean by remember {
                                    mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
                                }
                                Text(
                                    if (batteryClean) "ON ✓ — Android won't pause alerts."
                                    else "Off — alerts may pause when you leave the app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                try {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = android.net.Uri.parse("package:" + context.packageName)
                                        }
                                    )
                                } catch (e: Exception) {
                                    try {
                                        context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = android.net.Uri.parse("package:" + context.packageName)
                                            }
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                            }) { Text("Keep alive") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Daily spending summary", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(checked = notifTransactions, onCheckedChange = {
                                notifTransactions = it
                                prefs.edit().putBoolean("daily_summary", it).apply()
                                if (it) {
                                    ReminderScheduler.scheduleDaily(context)
                                    NotificationHelper.show(context, 10, "Reminders on ✓", "You'll get a daily spending summary here.")
                                } else ReminderScheduler.cancelDaily(context)
                            })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Weekly summaries", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(checked = notifWeekly, onCheckedChange = {
                                notifWeekly = it
                                prefs.edit().putBoolean("weekly_recap", it).apply()
                                if (it) {
                                    ReminderScheduler.scheduleWeekly(context)
                                    NotificationHelper.show(context, 11, "Weekly recap on ✓", "You'll get a 7-day spending recap every week.")
                                } else ReminderScheduler.cancelWeekly(context)
                            })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sunday 8pm report", style = MaterialTheme.typography.bodySmall)
                                Text("Week summary, every Sunday evening", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = sundayReport, onCheckedChange = {
                                sundayReport = it
                                prefs.edit().putBoolean("sunday_report", it).apply()
                                if (it) {
                                    ReminderScheduler.scheduleSundayReport(context)
                                    NotificationHelper.show(context, 12, "Sunday report on ✓", "See you Sunday 8pm. 🌙")
                                } else ReminderScheduler.cancelSundayReport(context)
                            })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Night report 9:30pm", style = MaterialTheme.typography.bodySmall)
                                Text("Today's total, biggest item, pending SMS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = nightReport, onCheckedChange = {
                                nightReport = it
                                prefs.edit().putBoolean("night_report", it).apply()
                                if (it) {
                                    ReminderScheduler.scheduleNightReport(context)
                                    NotificationHelper.show(context, 13, "Night report on ✓", "See you tonight 9:30pm. 🌙")
                                } else ReminderScheduler.cancelNightReport(context)
                            })
                        }
                    }
                }
            }


            // M-Pesa Detection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("M-Pesa Detection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "We read M-Pesa texts so you don't type them. Approve each in the notification or on Home → Pending. No permission? Paste texts in the 🧪 box below instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Today", "Week", "Month", "All").forEach { r ->
                                FilterChip(selected = scanRange == r, onClick = { scanRange = r }, label = { Text(r) })
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Scan SMS inbox now", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scanInbox(scanRange) }) { Text("Scan $scanRange") }
                        }
                        inboxResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("SMS-based M-Pesa parsing", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (smsGranted) "ON ✓ — new M-Pesa texts auto-log."
                                    else "Needs SMS access, or share texts to PesaFlow instead.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (smsGranted) {
                                Text("ON ✓", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            } else {
                                TextButton(onClick = {
                                    smsGranted = hasSmsPermission(context)
                                    if (smsGranted) return@TextButton
                                    val activity = context as? Activity
                                    if (smsAsked && activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)) {
                                        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                        context.startActivity(i)
                                    } else {
                                        smsAsked = true
                                        smsLauncher.launch(Manifest.permission.READ_SMS)
                                    }
                                }) { Text(if (smsAsked) "Open Settings" else "Enable") }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-log M-Pesa 🤖", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (autoApprove) "ON — new texts skip Pending, straight to ledger."
                                    else "Off — every text waits in Pending for your tap.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = autoApprove, onCheckedChange = {
                                autoApprove = it
                                prefs.edit().putBoolean("auto_approve_mpesa", it).apply()
                            })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-confirm familiar faces 🤝", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (autoFaces) "ON — remembered people with a usual category skip Pending."
                                    else "Off — even remembered faces wait for your tap.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = autoFaces, onCheckedChange = {
                                autoFaces = it
                                prefs.edit().putBoolean("auto_confirm_faces", it).apply()
                            })
                        }
                    }
                }
            }


            // SMS parse tester: paste any M-Pesa text, see instantly what the app reads
            item {
                var testSms by remember { mutableStateOf("") }
                var testParsed by remember { mutableStateOf<com.pesaflow.app.data.models.PendingTransaction?>(null) }
                var testFailed by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Test SMS Parsing 🧪", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Paste one M-Pesa text below — see instantly what the app reads from it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = testSms,
                            onValueChange = { testSms = it; testParsed = null; testFailed = false },
                            label = { Text("Paste M-Pesa SMS here") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val p = MpesaParser.parseMessage(testSms)
                                    testParsed = p
                                    testFailed = p == null
                                },
                                enabled = testSms.isNotBlank(),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Parse this message") }
                            if (testParsed != null) {
                                OutlinedButton(
                                    onClick = {
                                        testParsed?.let { viewModel.queueSharedTransaction(it) }
                                        testSms = ""
                                        testParsed = null
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("Send to Pending") }
                            }
                        }
                        testParsed?.let { p ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "✅ KSh ${p.amount.toInt()} · ${p.type} · ${p.category} · ${p.merchant} (${(p.confidenceScore * 100).toInt()}% sure). Approve it on Home → Pending.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (testFailed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "❌ Couldn't read this one. Copy the exact text to the developer so this format gets added in the next update.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }


            // Notification Detection (real wiring: system Notification Access)
            item {
                var notifAccess by remember { mutableStateOf(hasNotifAccess(context)) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Notification Detection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (notifAccess) "ON ✓ — transaction notifications auto-log (or wait in Pending)."
                            else "For phones where SMS access is denied: reads transaction notifications instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Notification-based tracking", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            if (notifAccess) {
                                Text("ON ✓", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            } else {
                                TextButton(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }) { Text("Enable") }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { notifAccess = hasNotifAccess(context) }) { Text("Refresh status") }
                        }
                    }
                }
            }


            item {
                SkinSectionHeader(title = "TRANSACTIONS AND DATA", subtitle = "Import, export, backup, delete")
            }


            // Data Management
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Data Management (${transactions.size} transactions)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { shareCsvExport(context, transactions) },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Export CSV", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { csvPicker.launch("text/*") },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Import CSV", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { backupSaver.launch("pesaplanner-backup.json") },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Backup JSON", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { backupPicker.launch(arrayOf("application/json")) },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Restore JSON", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        backupMsg?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "CSV columns: date (yyyy-MM-dd), description, amount, category. Minus amounts come in as spending.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Duplicate cleaner: rescans double-log the same SMS —
                        // same code twice, or same person + amount + minute.
                        // You start it, you confirm it, newest copy survives.
                        val dupeGroups = remember(transactions) {
                            com.pesaflow.app.data.parsers.findDuplicateGroups(transactions)
                        }
                        val dupeCount = remember(dupeGroups) {
                            com.pesaflow.app.data.parsers.duplicateIdsToRemove(dupeGroups).size
                        }
                        Text(
                            if (dupeCount > 0) "$dupeCount duplicate row(s) in ${dupeGroups.size} group(s) — safe to clean."
                            else "No duplicates found — ledger is clean. ✨",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        dupeResult?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { showDupeConfirm = true },
                            enabled = dupeCount > 0,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Clean Duplicates ($dupeCount)", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showDeleteConfirm = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Delete All Data", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }


            item {
                SkinSectionHeader(title = "UNIVERSITY", subtitle = "School, campus, allowance")
            }


            // University Profile
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("University Profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Set your university, campus and semester allowance under More → University.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }


            item {
                SkinSectionHeader(title = "MODEL DIAGNOSTICS", subtitle = "On-device training, held-out metrics")
            }
            item {
                val feedback by viewModel.modelFeedback.collectAsState()
                val trainingOptIn by viewModel.trainingOptIn.collectAsState()
                var diag by remember { mutableStateOf("Evaluating on-device…") }
                var diagWorst by remember { mutableStateOf("") }
                var exportMsg by remember { mutableStateOf("") }
                val exportScope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.withContext(Dispatchers.Default) {
                        try {
                            val typeData = com.pesaflow.app.data.ml.TransactionClassifier.trainingData
                            val catData = com.pesaflow.app.data.ml.CategoryClassifier.trainingData
                            val typeReport = com.pesaflow.app.data.ml.ModelEvaluator.evaluate(
                                factory = { com.pesaflow.app.data.ml.TfIdfClassifier() },
                                examples = typeData
                            )
                            val catReport = com.pesaflow.app.data.ml.ModelEvaluator.evaluate(
                                factory = { com.pesaflow.app.data.ml.TfIdfClassifier() },
                                examples = catData
                            )
                            val typeWorst = com.pesaflow.app.data.ml.ModelEvaluator.worstConfusion(typeReport)
                            val catWorst = com.pesaflow.app.data.ml.ModelEvaluator.worstConfusion(catReport)
                            fun pct(d: Double) = (d * 100).toInt()
                            diag = "TYPE v2: n=${typeData.size} (real ${typeData.count { !it.synthetic }}) acc ${pct(typeReport.accuracy)}% macro-F1 ${pct(typeReport.macroF1)}% test ${typeReport.testSize} (real ${typeReport.realTestSize})\n" +
                                "CAT v2: n=${catData.size} (real ${catData.count { !it.synthetic }}) acc ${pct(catReport.accuracy)}% macro-F1 ${pct(catReport.macroF1)}% test ${catReport.testSize} (real ${catReport.realTestSize})\n" +
                                "Feedback logged: ${feedback.size} (opt-in export only; trains future versions, never this ledger)"
                            diagWorst = "Worst confusions — type: ${typeWorst ?: "none"}; category: ${catWorst ?: "none"}"
                        } catch (e: Exception) {
                            diag = "Evaluation unavailable: ${e.message}"
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Model diagnostics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        com.pesaflow.app.data.ml.MlEngine.modelCards().forEach { card ->
                            Text("• $card", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(diag, style = MaterialTheme.typography.bodySmall)
                        if (diagWorst.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(diagWorst, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Training export (opt-in)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Off by default. Only accepted, anonymized rows leave the phone.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = trainingOptIn,
                                onCheckedChange = { viewModel.setTrainingOptIn(it) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = trainingOptIn && feedback.any { it.accepted },
                                onClick = {
                                    exportScope.launch(Dispatchers.IO) {
                                        val json = viewModel.exportTrainingJson()
                                        if (json == null) {
                                            exportMsg = "Opt-in required — nothing exported."
                                        } else {
                                            try {
                                                shareTrainingExport(context, json)
                                                exportMsg = "Export shared (${json.length} chars). Retrain offline, ship weights back."
                                            } catch (e: Exception) {
                                                exportMsg = "Export failed: ${e.message}"
                                            }
                                        }
                                    }
                                }
                            ) { Text("Export") }
                        }
                        if (exportMsg.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(exportMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SkinSectionHeader(title = "CATEGORY RULES", subtitle = "Keyword → category overrides")
            }
            item {
                val rules by viewModel.categoryRules.collectAsState()
                var newKeyword by remember { mutableStateOf("") }
                var newCategory by remember { mutableStateOf("") }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Custom category rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "If an SMS contains a keyword, assign that category — before ML. E.g. \"UNES BOOKSTORE\" → School.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newKeyword,
                            onValueChange = { newKeyword = it },
                            label = { Text("Keyword (e.g. UNES BOOKSTORE)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            label = { Text("Category (e.g. School)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            enabled = newKeyword.isNotBlank() && newCategory.isNotBlank(),
                            onClick = {
                                viewModel.addCategoryRule(newKeyword, newCategory)
                                newKeyword = ""
                                newCategory = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add rule") }
                        if (rules.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            rules.forEach { rule ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "\"${rule.keyword}\" → ${rule.category}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { viewModel.toggleCategoryRule(rule.id, it) }
                                    )
                                    TextButton(onClick = { viewModel.deleteCategoryRule(rule.id) }) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SkinSectionHeader(title = "ABOUT", subtitle = "Version, privacy, support")
            }


            // About
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("PesaPlanner - Free Personal Finance App for Kenyan University Students", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Version 1.0. All data stays on this phone. No ads, no accounts, no cloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDupeConfirm) {
        AlertDialog(
            onDismissRequest = { showDupeConfirm = false },
            title = { Text("Clean duplicates?") },
            text = { Text("Removes the older copy of each duplicated row and keeps the newest. Real repeats (same kibanda, different time) are never touched.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.cleanDuplicateTransactions { n ->
                        dupeResult = if (n > 0) "Removed $n duplicate row(s). ✅" else "Nothing to remove."
                    }
                    showDupeConfirm = false
                }) { Text("Clean") }
            },
            dismissButton = { TextButton(onClick = { showDupeConfirm = false }) { Text("Keep All") } }
        )
    }

    if (showDeleteConfirm) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete all data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This permanently removes all ${transactions.size} transactions. Budgets, bills, debts, meals and goals stay — only the ledger is wiped. This cannot be undone — there is no recovery.")
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Type DELETE to confirm") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllTransactions()
                        showDeleteConfirm = false
                    },
                    enabled = typed == "DELETE",
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) { Text("Delete Everything") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep My Data") } }
        )
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text("Import CSV") },
            text = {
                Text(
                    if (preview.isEmpty()) "No valid transaction rows found. Expected columns: date, description, amount, category."
                    else "Found ${preview.size} transactions. Import them now?"
                )
            },
            confirmButton = {
                if (preview.isNotEmpty()) {
                    Button(onClick = {
                        viewModel.importTransactions(preview)
                        importPreview = null
                    }) { Text("Import ${preview.size}") }
                } else {
                    TextButton(onClick = { importPreview = null }) { Text("OK") }
                }
            },
            dismissButton = { TextButton(onClick = { importPreview = null }) { Text("Cancel") } }
        )
    }
}
    }


@Composable
fun LanguageOptionChip(
    language: AppLanguage,
    isSelected: Boolean,
    onSelect: () -> Unit,
    content: @Composable () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onSelect,
        label = content
    )
}


@Composable
fun ThemePreviewSwatch(
    name: String,
    bg: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                color = bg,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            (if (selected) "✓ " else "") + name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (bg == Color.Black || bg == Color(0xFF121212)) Color.White else Color.Black
        )
    }
}


@Composable
fun ThemeChip(    theme: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onSelect,
        enabled = enabled,
        label = content
    )
}


private fun shareCsvExport(context: Context, transactions: List<Transaction>) {
    val csv = com.pesaflow.app.data.parsers.CsvImporter.buildCsvExport(transactions)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "PesaFlow transactions export")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    context.startActivity(Intent.createChooser(intent, "Export transactions"))
}


private fun shareTrainingExport(context: Context, json: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "PesaFlow training export (schema v1, anonymized)")
        putExtra(Intent.EXTRA_TEXT, json)
    }
    context.startActivity(Intent.createChooser(intent, "Export training data"))
}


private fun hasNotifAccess(context: Context): Boolean {
    val flat = android.provider.Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    ).orEmpty()
    return flat.contains(context.packageName)
}


private fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED


private fun hasNotifPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT < 33) true
    else ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
