package com.example.taskflow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun detectsNewerSemanticVersions() {
        assertTrue(UpdateChecker.isNewer("1.0.4", "1.0.3"))
        assertTrue(UpdateChecker.isNewer("1.1", "1.0.9"))
        assertTrue(UpdateChecker.isNewer("2", "1.9.9"))
    }

    @Test fun ignoresCurrentAndOlderVersions() {
        assertFalse(UpdateChecker.isNewer("1.0.3", "1.0.3"))
        assertFalse(UpdateChecker.isNewer("1.0.2", "1.0.3"))
    }
}
