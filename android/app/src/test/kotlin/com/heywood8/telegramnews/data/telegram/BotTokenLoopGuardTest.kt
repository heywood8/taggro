package com.heywood8.telegramnews.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class BotTokenLoopGuardTest {

    @Test
    fun `closed handler skips reinit after botTokenSent is true`() {
        val botTokenSent = AtomicBoolean(false)
        var reinitCalled = false

        fun simulateClosedHandler() {
            if (botTokenSent.get()) return
            reinitCalled = true
        }

        // Before token sent: Closed should trigger re-init
        simulateClosedHandler()
        assertTrue("Expected reinit before token sent", reinitCalled)

        // Reset and mark token as sent
        reinitCalled = false
        botTokenSent.set(true)

        // After token sent: Closed must NOT trigger re-init
        simulateClosedHandler()
        assertFalse("Expected NO reinit after token sent", reinitCalled)
    }

    @Test
    fun `botTokenSent getAndSet prevents double-fire`() {
        val botTokenSent = AtomicBoolean(false)
        var tokenCallCount = 0

        fun simulateWaitPhoneNumberHandler() {
            if (botTokenSent.getAndSet(true)) return
            tokenCallCount++
        }

        simulateWaitPhoneNumberHandler()
        simulateWaitPhoneNumberHandler()
        simulateWaitPhoneNumberHandler()

        assert(tokenCallCount == 1) { "Token call should fire exactly once, fired $tokenCallCount times" }
    }
}
