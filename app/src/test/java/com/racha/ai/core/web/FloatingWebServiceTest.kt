package com.racha.ai.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingWebServiceTest {
    @Test
    fun acceptsHttpAndHttpsLinks() {
        assertEquals(
            "https://example.com/path?q=racha",
            FloatingWebService.normalizeHttpUrl(" https://example.com/path?q=racha ")
        )
        assertEquals(
            "http://example.com",
            FloatingWebService.normalizeHttpUrl("http://example.com")
        )
    }

    @Test
    fun rejectsUnsafeOrMalformedLinks() {
        assertNull(FloatingWebService.normalizeHttpUrl("javascript:alert(1)"))
        assertNull(FloatingWebService.normalizeHttpUrl("file:///sdcard/private.txt"))
        assertNull(FloatingWebService.normalizeHttpUrl("content://contacts/1"))
        assertNull(FloatingWebService.normalizeHttpUrl("https://"))
        assertNull(FloatingWebService.normalizeHttpUrl(null))
    }
}
