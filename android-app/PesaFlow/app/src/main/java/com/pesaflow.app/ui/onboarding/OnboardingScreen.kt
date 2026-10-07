package com.pesaflow.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.viewmodels.FinanceViewModel

/** Single onboarding step data. Pure data — no composable calls here. */
private data class OnboardingStep(
    val key: String,
    val title: String = "",
    val body: List<String> = emptyList(),
    val options: List<String> = emptyList(),
    val factKey: String = "",
    // Per-answer provenance: identity/housing/income are hard facts,
    // location/transport/food/goals are softer self-reports.
    val confidence: Double = 1.0,
    val evidenceLevel: String = "CONFIRMED",
    // Conditional follow-up: shown only when answers[conditionalOn] == conditionalValue.
    val conditionalOn: String = "",
    val conditionalValue: String = "",
    val isFinal: Boolean = false
)

private fun onboardingStep(
    key: String,
    title: String = "",
    body: List<String> = emptyList(),
    options: List<String> = emptyList(),
    factKey: String = "",
    confidence: Double = 1.0,
    evidenceLevel: String = "CONFIRMED",
    conditionalOn: String = "",
    conditionalValue: String = "",
    isFinal: Boolean = false
): OnboardingStep {
    return OnboardingStep(
        key = key, title = title, body = body, options = options, factKey = factKey,
        confidence = confidence, evidenceLevel = evidenceLevel,
        conditionalOn = conditionalOn, conditionalValue = conditionalValue, isFinal = isFinal
    )
}

private fun isVisible(step: OnboardingStep, answers: Map<String, String>): Boolean {
    if (step.conditionalOn.isBlank()) return true
    return answers[step.conditionalOn] == step.conditionalValue
}

private fun nextVisibleIndex(steps: List<OnboardingStep>, from: Int, answers: Map<String, String>): Int {
    var i = from + 1
    while (i < steps.size && !isVisible(steps[i], answers)) i++
    return i
}

/** Minimal carousel: one step at a time with selectable options, Next / Skip / Finish. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarouselOnboarding(
    steps: List<OnboardingStep>,
    onAnswer: (stepKey: String, factKey: String, value: String, confidence: Double, evidenceLevel: String) -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    var index by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf(mapOf<String, String>()) }
    // If the entry step is conditional-hidden (re-entry edge), jump forward.
    val step = steps.getOrNull(index)?.takeIf { isVisible(it, answers) } ?: run {
        val next = nextVisibleIndex(steps, index, answers)
        if (next >= steps.size) {
            onDone()
            return
        }
        index = next
        return
    }
    var selected by remember(step.key) { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            step.title.ifBlank { step.key },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        step.body.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (step.options.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                step.options.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { selected = if (selected == option) null else option },
                        label = { Text(option) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val sel = selected
                var updated = answers
                if (sel != null && step.factKey.isNotBlank() && sel != "Skip") {
                    onAnswer(step.key, step.factKey, sel, step.confidence, step.evidenceLevel)
                    updated = answers + (step.factKey to sel)
                    answers = updated
                }
                val next = nextVisibleIndex(steps, index, updated)
                if (step.isFinal || next >= steps.size) {
                    onDone()
                } else {
                    index = next
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (step.isFinal) "Start PesaFlow" else "Next")
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(viewModel: FinanceViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE) }
    var welcomed by remember { mutableStateOf(prefs.getBoolean("onboarding_welcomed", false)) }

    fun markWelcomed() {
        prefs.edit().putBoolean("onboarding_welcomed", true).apply()
        welcomed = true
    }

    LaunchedEffect(welcomed) {
        prefs.edit().putBoolean("onboarding_welcomed", welcomed).apply()
    }

    if (!welcomed) {
        CarouselOnboarding(
            steps = listOf(
                onboardingStep(
                    key = "id",
                    title = "Who are you?",
                    body = listOf("Are you a student, employed, self-employed, or a combination? You can skip any question."),
                    options = listOf("Student", "Employed", "Self-employed", "Combination"),
                    factKey = "identity",
                    confidence = 1.0,
                    evidenceLevel = "CONFIRMED"
                ),
                onboardingStep(
                    key = "location",
                    title = "Where do you study or work?",
                    body = listOf("Where do you study or work?"),
                    options = listOf("UoN", "Kenyatta", "Moi", "Other"),
                    factKey = "university",
                    confidence = 0.9,
                    evidenceLevel = "OBSERVED"
                ),
                onboardingStep(
                    key = "housing",
                    title = "Do you rent, live with family, stay in a hostel, or use another arrangement?",
                    body = listOf("Accommodation type determines rent, transport and food budgeting."),
                    options = listOf("Rent", "Family", "Hostel", "Other"),
                    factKey = "accommodation",
                    confidence = 0.9,
                    evidenceLevel = "CONFIRMED"
                ),
                onboardingStep(
                    key = "transport",
                    title = "How do you normally travel?",
                    body = listOf("Transport costs are a major daily/weekly expense. Tell us your mode so we can estimate fares."),
                    options = listOf("Walking", "Matatu", "Boda-boda", "Uber/Bolt", "Other"),
                    factKey = "transport.primaryMode",
                    confidence = 0.8,
                    evidenceLevel = "OBSERVED"
                ),
                onboardingStep(
                    key = "food",
                    title = "Do you cook, buy prepared meals, or combine both?",
                    body = listOf("Food is usually the largest variable expense. Your answer shapes the meal planner."),
                    options = listOf("Self-cooked", "Prepared meals", "Combine both"),
                    factKey = "food.preferences",
                    confidence = 0.8,
                    evidenceLevel = "OBSERVED"
                ),
                onboardingStep(
                    key = "rent-followup",
                    title = "Approximate rent and utilities?",
                    body = listOf("If you rent, tell us the monthly amount and whether you pay utilities."),
                    options = listOf("KSh 5k", "KSh 10k", "KSh 15k"),
                    factKey = "rent.monthly",
                    confidence = 0.9,
                    evidenceLevel = "CONFIRMED",
                    conditionalOn = "accommodation",
                    conditionalValue = "Rent"
                ),
                onboardingStep(
                    key = "income",
                    title = "How often do you receive money?",
                    body = listOf("Income regularity shapes safe-to-spend and goal forecasts."),
                    options = listOf("Once per semester", "Once per month", "Twice per month", "Weekly", "Irregular"),
                    factKey = "income.frequency",
                    confidence = 0.9,
                    evidenceLevel = "CONFIRMED"
                ),
                onboardingStep(
                    key = "goals",
                    title = "What are your main financial goals?",
                    body = listOf("Goals drive scenario analysis and recommendation ranking."),
                    options = listOf("Save device", "Emergency reserve", "Pay rent/fees", "Savings habit", "Other"),
                    factKey = "goal.primary",
                    confidence = 0.8,
                    evidenceLevel = "OBSERVED"
                ),
                onboardingStep(
                    key = "finish",
                    title = "Almost done — skip any question you prefer",
                    body = listOf("The app will keep learning from your actual spending. You can update anything later in Settings."),
                    isFinal = true
                )
            ),
            onAnswer = { _, factKey, value, confidence, evidenceLevel ->
                if (factKey.isNotBlank()) {
                    viewModel.saveUserContextFact(
                        key = factKey,
                        value = value,
                        source = "USER_ENTERED",
                        confidence = confidence,
                        evidenceLevel = evidenceLevel
                    )
                }
            },
            onDone = {
                markWelcomed()
                onDone()
            },
            onSkip = {
                markWelcomed()
                onDone()
            }
        )
        return
    }

    // Already welcomed (e.g. process death after partial flow) — finish immediately.
    LaunchedEffect(Unit) {
        onDone()
    }
}
