package com.wroxtaaar.newfinance

import com.wroxtaaar.newfinance.capture.AllowList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowListTest {
    @Test
    fun corpusSenderVariantsAreAllowed() {
        assertTrue(AllowList.allowedSender("AD-AXISBK-S"))
        assertTrue(AllowList.allowedSender("BP-HDFCBK"))
        assertTrue(AllowList.allowedSender("JD-ICICIT-S"))
        assertTrue(AllowList.allowedSender("BP-MYSBIC-S"))
        assertTrue(AllowList.allowedSender("JD-INDUSA-S"))
        assertTrue(AllowList.allowedSender("TM-HSBCIM"))
        assertTrue(AllowList.allowedSender("JD-SCBANK-S"))
    }

    @Test
    fun unrelatedSendersRemainBlocked() {
        assertFalse(AllowList.allowedSender("TM-POSTPE"))
        assertFalse(AllowList.allowedSender("TX-SLCEIT"))
        assertFalse(AllowList.allowedSender("VM-MOBIKW"))
        assertFalse(AllowList.allowedSender("AD-AIRBLK"))
    }
}
