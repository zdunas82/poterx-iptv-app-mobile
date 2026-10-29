package tv.own.owntv.mobile.poterx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PoterxPrivacyTest {

    @Test
    fun `every URL host is masked in text that leaves the phone`() {
        val out = PoterxPrivacy.hideEveryHost("GET http://panel.example.net:8080/live/u/p/1.ts failed")
        assertFalse(out, out.contains("panel.example.net"))
        assertFalse(out, out.contains("8080"))
    }

    @Test
    fun `a bare IP address and its port are masked`() {
        val out = PoterxPrivacy.hideEveryHost("failed to connect to /203.0.113.7:8080 (port 8080) after 10000ms")
        assertFalse(out, out.contains("203.0.113.7"))
        assertEquals("failed to connect to /••• (port 8080) after 10000ms", out)
    }

    @Test
    fun `version numbers are left alone`() {
        assertEquals("PoterxMobile/0.4.0 on Android 14", PoterxPrivacy.hideEveryHost("PoterxMobile/0.4.0 on Android 14"))
    }
}
