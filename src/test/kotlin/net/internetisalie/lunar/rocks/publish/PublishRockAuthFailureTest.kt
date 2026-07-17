package net.internetisalie.lunar.rocks.publish

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * BUG-376: [PublishRockAction.isAuthFailure] detects authentication-related upload failures so the
 * stored API key can be cleared and the user re-prompted. Pure function — no platform needed.
 */
class PublishRockAuthFailureTest {

    @Test
    fun exitZeroIsNotAuthFailure() {
        assertFalse(PublishRockAction.isAuthFailure(0, "Invalid API key"))
    }

    @Test
    fun invalidApiKeyDetected() {
        assertTrue(PublishRockAction.isAuthFailure(1, "Error: Invalid API key"))
    }

    @Test
    fun authenticationFailedDetected() {
        assertTrue(PublishRockAction.isAuthFailure(1, "Authentication failed for user"))
    }

    @Test
    fun unauthorizedDetected() {
        assertTrue(PublishRockAction.isAuthFailure(1, "HTTP 401 Unauthorized"))
    }

    @Test
    fun forbiddenDetected() {
        assertTrue(PublishRockAction.isAuthFailure(1, "HTTP 403 Forbidden"))
    }

    @Test
    fun authKeyComboDetected() {
        assertTrue(PublishRockAction.isAuthFailure(1, "Bad auth key provided"))
    }

    @Test
    fun genericErrorNotAuthFailure() {
        assertFalse(PublishRockAction.isAuthFailure(1, "Error: package not found"))
    }

    @Test
    fun networkErrorNotAuthFailure() {
        assertFalse(PublishRockAction.isAuthFailure(1, "Connection refused"))
    }

    @Test
    fun emptyStderrNotAuthFailure() {
        assertFalse(PublishRockAction.isAuthFailure(1, ""))
    }
}
