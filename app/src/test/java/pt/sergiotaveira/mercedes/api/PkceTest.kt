package pt.sergiotaveira.mercedes.api

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PkceTest {
    @Test
    fun verifierIsUrlSafeAndLongEnough() {
        val v = Pkce.verifier()
        assertTrue(v.length in 43..128, "length ${v.length}")
        assertTrue(Regex("^[A-Za-z0-9\\-._~]+$").matches(v), v)
    }

    @Test
    fun challengeMatchesRfc7636Example() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }
}
