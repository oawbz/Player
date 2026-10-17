package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test

class AuthModeTest {
    @Test fun restoresExistingAccountsWithoutChangingExplicitAnonymousMode() {
        assertEquals(AuthMode.ANONYMOUS, AuthMode.stored(null, "", ""))
        assertEquals(AuthMode.BASIC, AuthMode.stored(null, "user", ""))
        assertEquals(AuthMode.ANONYMOUS, AuthMode.stored("ANONYMOUS", "user", "secret"))
    }
    @Test fun credentialsDoNotAppearInModelDiagnostics() {
        val value = Connection("id", "name", "https://example.invalid/", "private-user", "private-password", AuthMode.BASIC)
        assertFalse(value.toString().contains("private-user")); assertFalse(value.toString().contains("private-password"))
    }
}
