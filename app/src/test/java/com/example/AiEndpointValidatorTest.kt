package com.example

import com.example.data.ai.AiEndpointValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Custom endpoint validation.
 *
 * A custom endpoint is a credential destination, so these are security tests as much as parsing
 * tests: no credentials in the URL, no fragments, no plain HTTP to a public host, and a normalized
 * identity that consent can be tied to.
 */
class AiEndpointValidatorTest {

    @Test
    fun `an https host becomes a v1 base with derived chat and model paths`() {
        val endpoint = AiEndpointValidator.validateCustomBaseUrl("https://api.example.com").getOrThrow()
        assertEquals("https://api.example.com/v1", endpoint.baseUrl)
        assertEquals("https://api.example.com/v1/chat/completions", endpoint.chatCompletionsUrl)
        assertEquals("https://api.example.com/v1/models", endpoint.modelsUrl)
        assertTrue(endpoint.isHttps)
        assertFalse(endpoint.isLocal)
    }

    @Test
    fun `a typed v1 path and a trailing slash normalize to the same base`() {
        val bare = AiEndpointValidator.validateCustomBaseUrl("https://api.example.com").getOrThrow()
        val v1 = AiEndpointValidator.validateCustomBaseUrl("https://api.example.com/v1").getOrThrow()
        val slash = AiEndpointValidator.validateCustomBaseUrl("https://api.example.com/v1/").getOrThrow()
        assertEquals(bare.baseUrl, v1.baseUrl)
        assertEquals(v1.baseUrl, slash.baseUrl)
    }

    @Test
    fun `a full chat completions URL is trimmed back to its base`() {
        val endpoint = AiEndpointValidator.validateCustomBaseUrl(
            "https://api.example.com/openai/v1/chat/completions"
        ).getOrThrow()
        assertEquals("https://api.example.com/openai/v1", endpoint.baseUrl)
        assertEquals("https://api.example.com/openai/v1/chat/completions", endpoint.chatCompletionsUrl)
    }

    @Test
    fun `a models URL is trimmed back to its base`() {
        val endpoint = AiEndpointValidator.validateCustomBaseUrl(
            "https://api.example.com/v1/models"
        ).getOrThrow()
        assertEquals("https://api.example.com/v1", endpoint.baseUrl)
    }

    @Test
    fun `plain http is accepted for loopback and private network hosts`() {
        listOf(
            "http://localhost:11434/v1",
            "http://127.0.0.1:8080/v1",
            "http://[::1]:8080/v1",
            "http://192.168.1.10:8000/v1",
            "http://10.0.0.5/v1",
            "http://172.16.0.2/v1",
            "http://169.254.1.1/v1"
        ).forEach { url ->
            val result = AiEndpointValidator.validateCustomBaseUrl(url)
            assertTrue("$url should be allowed locally", result.isSuccess)
            assertFalse("$url is not encrypted", result.getOrThrow().isHttps)
        }
    }

    @Test
    fun `plain http to a public host is rejected`() {
        val result = AiEndpointValidator.validateCustomBaseUrl("http://api.example.com/v1")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("HTTPS"))
    }

    @Test
    fun `plain http to a public ip is rejected`() {
        assertTrue(AiEndpointValidator.validateCustomBaseUrl("http://8.8.8.8/v1").isFailure)
        assertTrue(AiEndpointValidator.validateCustomBaseUrl("http://203.0.113.10/v1").isFailure)
    }

    @Test
    fun `credentials in the URL are rejected because the key field is the only credential path`() {
        val result = AiEndpointValidator.validateCustomBaseUrl("https://user:secret@api.example.com/v1")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("API key"))
    }

    @Test
    fun `a fragment is rejected`() {
        assertTrue(
            AiEndpointValidator.validateCustomBaseUrl("https://api.example.com/v1#fragment").isFailure
        )
    }

    @Test
    fun `non http schemes and blank input are rejected`() {
        assertTrue(AiEndpointValidator.validateCustomBaseUrl("").isFailure)
        assertTrue(AiEndpointValidator.validateCustomBaseUrl("   ").isFailure)
        assertTrue(AiEndpointValidator.validateCustomBaseUrl("ftp://api.example.com/v1").isFailure)
        assertTrue(AiEndpointValidator.validateCustomBaseUrl("api.example.com/v1").isFailure)
    }

    @Test
    fun `the consent identity is stable across harmless URL variations`() {
        val a = AiEndpointValidator.endpointIdentity("https://api.example.com")
        val b = AiEndpointValidator.endpointIdentity("https://api.example.com/v1/")
        val c = AiEndpointValidator.endpointIdentity("https://api.example.com/v1/chat/completions")
        assertEquals(a, b)
        assertEquals(b, c)

        // A different path is a different destination and must not inherit the old consent.
        assertTrue(
            a != AiEndpointValidator.endpointIdentity("https://api.example.com/other/v1")
        )
    }

    @Test
    fun `local host detection does not trust a public hostname`() {
        assertTrue(AiEndpointValidator.isLocalHost("localhost"))
        assertTrue(AiEndpointValidator.isLocalHost("127.0.0.1"))
        assertTrue(AiEndpointValidator.isLocalHost("192.168.0.10"))
        assertTrue(AiEndpointValidator.isLocalHost("::1"))
        assertFalse(AiEndpointValidator.isLocalHost("example.com"))
        assertFalse(AiEndpointValidator.isLocalHost("8.8.8.8"))
    }

    @Test
    fun `display host is best effort and never throws`() {
        assertEquals("api.example.com", AiEndpointValidator.displayHost("https://api.example.com/v1"))
        assertEquals("not a url", AiEndpointValidator.displayHost("not a url"))
    }
}
