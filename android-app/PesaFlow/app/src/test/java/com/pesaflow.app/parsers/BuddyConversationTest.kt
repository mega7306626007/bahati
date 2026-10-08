package com.pesaflow.app.parsers

import com.pesaflow.app.ui.dashboard.BuddyFollowUpResolver
import com.pesaflow.app.ui.dashboard.BuddyMemory
import com.pesaflow.app.ui.dashboard.BuddyMemoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BuddyConversationTest {

    @Before
    fun resetMemory() {
        BuddyMemory.clear()
    }

    @Test
    fun remembersTopicEntitiesWindowAndTurnCount() {
        BuddyMemory.remember(
            userInput = "How much did I spend on food this week?",
            response = "KSh 500",
            topic = "food",
            entities = BuddyFollowUpResolver.extractEntities("food 500 this week"),
            timeWindow = "THIS_WEEK"
        )

        val state = BuddyMemory.snapshot()

        assertEquals("food", state.lastTopic)
        assertEquals("THIS_WEEK", state.timeWindow)
        assertEquals("500", state.entities["amount"])
        assertEquals(1, state.turnCount)
    }

    @Test
    fun resolvesTimeWindowChangeUsingPreviousTopic() {
        val state = BuddyMemoryState(
            lastTopic = "food",
            timeWindow = "THIS_WEEK",
            turnCount = 1
        )

        assertEquals(
            "food spending last month",
            BuddyFollowUpResolver.resolve("what about last month?", state)
        )
    }

    @Test
    fun resolvesPronounUsingPreviousTopic() {
        val state = BuddyMemoryState(lastTopic = "transport", timeWindow = "YESTERDAY")

        assertEquals(
            "transport spending yesterday",
            BuddyFollowUpResolver.resolve("what about it?", state)
        )
    }

    @Test
    fun explicitTopicSwitchIsNotSwallowedByMemory() {
        val state = BuddyMemoryState(lastTopic = "food")

        assertNull(
            BuddyFollowUpResolver.resolve("and transport?", state)
        )
    }
}
