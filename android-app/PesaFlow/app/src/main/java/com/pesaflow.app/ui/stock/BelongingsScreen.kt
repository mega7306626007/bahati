package com.pesaflow.app.ui.stock

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.R
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintThingsViolet


private val BELONGING_CATS = listOf("Clothes", "Books", "Shoes", "Electronics", "Other")
private val PRIORITIES = listOf("Must-have" to 1, "Nice" to 2, "Dream" to 3)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BelongingsScreen(viewModel: FinanceViewModel) {
    val items by viewModel.belongings.collectAsState()
    val goals by viewModel.savingsGoals.collectAsState()

    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Clothes") }
    var haveIt by remember { mutableStateOf(false) }
    var cost by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(2) }
    var catFilter by remember { mutableStateOf("All") }
    // Proof ticks: brief ✓ that reverts so actions stay tappable.
    val ackScope = rememberCoroutineScope()
    var acked by remember { mutableStateOf(setOf<String>()) }
    fun ack(key: String) {
        acked = acked + key
        ackScope.launch { kotlinx.coroutines.delay(2000); acked = acked - key }
    }

    val needs = items.filter { it.status == "NEED" && (catFilter == "All" || it.category == catFilter) }.sortedWith(compareBy({ it.priority }, { it.estCost }))
    val haves = items.filter { it.status == "HAVE" && (catFilter == "All" || it.category == catFilter) }.sortedBy { it.name.lowercase() }
    val needTotal = needs.sumOf { it.estCost }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintThingsViolet, bgRes = R.drawable.bg_things_carry)
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("My Things 🎒", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AtmosphereBand(
                workspace = AtmoWorkspace.THINGS,
                title = "Own it all",
                subtitle = "Have · need · save for it"
            )
            // Summary: what you lack and what it costs to own it all
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        if (items.isEmpty()) "Nothing tracked yet — list what you have and what you need below."
                        else "${haves.size} have · ${needs.size} need · KSh ${needTotal.toInt()} to own it all",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (needs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Top of the list: ${needs.take(3).joinToString(", ") { it.name }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Savings ↔ things loop: fully saved needs ask to flip to HAVE.
                    val funded = needs.filter { n ->
                        n.estCost > 0 && goals.any { g ->
                            g.title.equals(n.name, ignoreCase = true) &&
                                g.targetAmount > 0 && g.currentAmount >= g.targetAmount
                        }
                    }
                    if (funded.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        funded.forEach { f ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "💰 ${f.name} fully saved — bought it?",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.markBelonging(f, "HAVE") }) { Text("Got it ✓") }
                            }
                        }
                    }
                }
            }

            // Add form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Add a thing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("What? (e.g. Lab coat)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BELONGING_CATS.forEach { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.take(5)) })
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !haveIt, onClick = { haveIt = false }, label = { Text("I need it") })
                        FilterChip(selected = haveIt, onClick = { haveIt = true }, label = { Text("I have it") })
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Costs about (KSh)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PRIORITIES.forEach { (label, p) ->
                            FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(label.take(6)) })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                viewModel.addBelonging(name, category, cost.toDoubleOrNull() ?: 0.0, priority, if (haveIt) "HAVE" else "NEED")
                                name = ""
                                cost = ""
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Add", color = MaterialTheme.colorScheme.onPrimary) }
                }
            }

            Text("Filter", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = catFilter == "All", onClick = { catFilter = "All" }, label = { Text("All") })
                BELONGING_CATS.forEach { c ->
                    FilterChip(selected = catFilter == c, onClick = { catFilter = c }, label = { Text(c.take(5)) })
                }
            }
            if (items.isNotEmpty()) {
                val ownedFrac = haves.size.toFloat() / items.size.toFloat()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            haves.size.toString() + " of " + items.size + " owned (" + (ownedFrac * 100).toInt() + "%)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { ownedFrac },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
            // NEED list: priority order, each with a path to reality
            if (needs.isNotEmpty()) {
                Text("Need (${needs.size}) 🎯", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                needs.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        "${item.category} · ${PRIORITIES.firstOrNull { it.second == item.priority }?.first ?: ""}" +
                                            if (item.estCost > 0) " · KSh ${item.estCost.toInt()}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.deleteBelonging(item.id) }) {
                                    Text("Drop", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.markBelonging(item, "HAVE") }, shape = RoundedCornerShape(12.dp)) {
                                    Text("Got it ✓")
                                }
                                if (item.estCost > 0) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.addSavingsGoal(item.name, item.estCost, 90)
                                            ack(item.id)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text(if (item.id in acked) "Goal set ✓" else "Save for this 🎯") }
                                }
                            }
                        }
                    }
                }
            }

            // HAVE list
            if (haves.isNotEmpty()) {
                Text("Have (${haves.size}) ✅", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                haves.forEach { item ->
                    Card(
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
                                Text(item.name, color = MaterialTheme.colorScheme.onSurface)
                                Text(item.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.markBelonging(item, "NEED") }) { Text("Need again?") }
                            TextButton(onClick = { viewModel.deleteBelonging(item.id) }) {
                                Text("Drop", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
    }
