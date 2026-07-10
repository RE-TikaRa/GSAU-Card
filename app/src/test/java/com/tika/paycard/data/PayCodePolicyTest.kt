package com.tika.paycard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayCodePolicyTest {

    @Test
    fun `付款码一分钟失效且提前刷新`() {
        assertEquals(60_000L, PayCodePolicy.VALIDITY_MS)
        assertEquals(30_000L, PayCodePolicy.REFRESH_INTERVAL_MS)
        assertTrue(PayCodePolicy.REFRESH_INTERVAL_MS < PayCodePolicy.VALIDITY_MS)
    }
}
