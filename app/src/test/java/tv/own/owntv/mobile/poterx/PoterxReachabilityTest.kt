package tv.own.owntv.mobile.poterx

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.mobile.poterx.PoterxReachability.Block
import tv.own.owntv.mobile.poterx.PoterxReachability.Dns
import tv.own.owntv.mobile.poterx.PoterxReachability.Http
import tv.own.owntv.mobile.poterx.PoterxReachability.Tcp
import tv.own.owntv.mobile.poterx.PoterxReachability.Verdict
import tv.own.owntv.mobile.poterx.PoterxReachability.Companion.classify

class PoterxReachabilityTest {

    @Test
    fun `no DNS answer or a bogus one is a DNS block`() {
        assertEquals(Verdict.Blocked(Block.DNS), classify(Dns.FAILED, Tcp.FAILED, Http.Failed(false)))
        assertEquals(Verdict.Blocked(Block.DNS), classify(Dns.BOGUS, Tcp.OK, Http.Answered(200, json = false)))
    }

    @Test
    fun `a dropped or reset connection is an address block`() {
        assertEquals(Verdict.Blocked(Block.CONNECTION), classify(Dns.OK, Tcp.FAILED, Http.Failed(false)))
        assertEquals(Verdict.Blocked(Block.CONNECTION), classify(Dns.OK, Tcp.OK, Http.Failed(reset = true)))
    }

    @Test
    fun `a panel answering JSON is reachable whatever the status`() {
        assertEquals(Verdict.Ok, classify(Dns.OK, Tcp.OK, Http.Answered(200, json = true)))
        assertEquals(Verdict.Ok, classify(Dns.OK, Tcp.OK, Http.Answered(401, json = true)))
    }

    @Test
    fun `a page in the panel's place needs evidence - a 451, a page with 200, or a redirect elsewhere`() {
        assertEquals(Verdict.Blocked(Block.PAGE), classify(Dns.OK, Tcp.OK, Http.Answered(451, json = false)))
        assertEquals(Verdict.Blocked(Block.PAGE), classify(Dns.OK, Tcp.OK, Http.Answered(451, json = false, html = true)))
        assertEquals(Verdict.Blocked(Block.PAGE), classify(Dns.OK, Tcp.OK, Http.Answered(200, json = false, html = true)))
        assertEquals(Verdict.Blocked(Block.PAGE), classify(Dns.OK, Tcp.OK, Http.Answered(302, json = false, redirectElsewhere = true)))
        assertEquals(Verdict.Blocked(Block.PAGE), classify(Dns.OK, Tcp.OK, Http.Answered(307, json = false, html = true, redirectElsewhere = true)))
    }

    @Test
    fun `a 4xx page is the panel's own answer, not a block - the v0_3_6 wrong-password alarm`() {
        // XUI answers wrong credentials, and a call without them, with a "404 Not Found" page.
        assertEquals(Verdict.PanelDown, classify(Dns.OK, Tcp.OK, Http.Answered(404, json = false, html = true)))
        assertEquals(Verdict.PanelDown, classify(Dns.OK, Tcp.OK, Http.Answered(403, json = false, html = true)))
        assertEquals(Verdict.PanelDown, classify(Dns.OK, Tcp.OK, Http.Answered(403, json = false)))
        assertEquals(Verdict.PanelDown, classify(Dns.OK, Tcp.OK, Http.Answered(404, json = false)))
        assertEquals(Verdict.PanelDown, classify(Dns.OK, Tcp.OK, Http.Answered(499, json = false, html = true)))
    }

    @Test
    fun `an empty body, a redirect on the same host, a 5xx or a timeout is the panel's own doing`() {
        // The v0.3.4 false alarm: a panel asked without credentials answered an empty page.
        assertEquals(Verdict.PanelDown, classify(Dns.OK, Tcp.OK, Http.Answered(200, json = false)))
        assertEquals(Verdict.PanelDown, classify(Dns.OK, Tcp.OK, Http.Answered(301, json = false)))
        assertEquals(Verdict.PanelDown, classify(Dns.OK, Tcp.OK, Http.Answered(301, json = false, html = true)))
        assertEquals(Verdict.PanelDown, classify(Dns.OK, Tcp.OK, Http.Answered(502, json = false)))
        assertEquals(Verdict.PanelDown, classify(Dns.OK, Tcp.OK, Http.Failed(reset = false)))
    }

    @Test
    fun `a web page with a 5xx is the panel's error page, not a block page`() {
        // The error page of the proxy in front of a panel that is down: nginx 502/503/504, Cloudflare 52x.
        for (code in listOf(500, 502, 503, 504, 521, 522, 530, 599)) {
            assertEquals("HTML $code", Verdict.PanelDown, classify(Dns.OK, Tcp.OK, Http.Answered(code, json = false, html = true)))
        }
    }

    @Test
    fun `a 5xx never hides a block that came earlier or a JSON answer`() {
        assertEquals(Verdict.Blocked(Block.DNS), classify(Dns.FAILED, Tcp.FAILED, Http.Answered(502, json = false, html = true)))
        assertEquals(Verdict.Blocked(Block.CONNECTION), classify(Dns.OK, Tcp.FAILED, Http.Answered(503, json = false, html = true)))
        assertEquals(Verdict.Ok, classify(Dns.OK, Tcp.OK, Http.Answered(500, json = true)))
    }
}
