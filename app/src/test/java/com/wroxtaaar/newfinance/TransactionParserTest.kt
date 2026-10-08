package com.wroxtaaar.newfinance

import com.wroxtaaar.newfinance.capture.AllowList
import com.wroxtaaar.newfinance.data.Direction
import com.wroxtaaar.newfinance.parser.TransactionParser
import org.junit.Assert.*
import org.junit.Test

class TransactionParserTest {
    @Test
    fun gmailIsAllowedForNotificationCapture() {
        assertTrue(AllowList.allowedPackage("com.google.android.gm"))
        assertTrue(AllowList.isEmailPackage("com.google.android.gm"))
        assertFalse(AllowList.allowedPackage("com.microsoft.office.outlook"))
        assertFalse(AllowList.allowedPackage("com.google.android.apps.gmail"))
    }

    @Test
    fun unrelatedEmailNotificationIsNotMarkedAsTransactionCandidate() {
        assertFalse(
            AllowList.looksLikeEmailTransaction(
                "Amazon order update: your package will arrive tomorrow."
            )
        )
    }

    @Test
    fun indusCreditCardPaymentReceivedIsCredit() {
        val p = TransactionParser.parse(
            "Dear Customer, thank you for your Payment of INR 388.00 towards your " +
                "IndusInd Bank Credit Card on 04/08/2023.",
            1,
            "VM-INDUSB"
        )

        assertNotNull(p)
        assertEquals(Direction.CREDIT, p!!.direction)
        assertEquals(38800L, p.amountMinor)
    }

    @Test
    fun bankCardBillPaymentProcessedIsDebit() {
        val p = TransactionParser.parse(
            "UPDATE: Your ICICI Bank Credit Card bill payment of Rs. 100.00 for " +
                "XXXXXXXXXXXX0005 has been processed successfully.",
            1,
            "CP-HDFCBK"
        )

        assertNotNull(p)
        assertEquals(Direction.DEBIT, p!!.direction)
        assertEquals(10000L, p.amountMinor)
    }

    @Test
    fun standingInstructionPaymentIsDebit() {
        val p = TransactionParser.parse(
            "Dear Customer, we have successfully processed the payment of INR 199.00 " +
                "for Youtube, as per the Standing Instruction XsvmYIE05N, on 14/08/2025 " +
                "for your ICICI Bank Credit Card 8001.",
            1,
            "VM-ICICIT-S"
        )

        assertNotNull(p)
        assertEquals(Direction.DEBIT, p!!.direction)
        assertEquals(19900L, p.amountMinor)
        assertEquals("8001", p.accountLast4)
    }

    @Test
    fun hdfcAmtSentIsDebit() {
        val p = TransactionParser.parse(
            "Amt Sent Rs.300.00\nFrom HDFC Bank A/C *9591\nTo MOTI AUTO PARTS\nOn 22-02\nRef 405385689759",
            1,
            "AD-HDFCBK"
        )

        assertNotNull(p)
        assertEquals(30000L, p!!.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("9591", p.accountLast4)
        assertEquals("405385689759", p.reference)
        assertEquals("MOTI AUTO PARTS", p.merchant)
    }

    @Test
    fun realDebitAlertMentioningOtpIsNotRejected() {
        val p = TransactionParser.parse(
            "ALERT:Rs.100.00 spent via Debit Card xx0610 at HDFCBILLPAY on Nov 4 2023 11:42AM without PIN/OTP.Not you?",
            1,
            "AD-HDFCBK"
        )

        assertNotNull(p)
        assertEquals(Direction.DEBIT, p!!.direction)
        assertEquals(10000L, p.amountMinor)
        assertEquals("0610", p.accountLast4)
    }

    @Test
    fun refundMentioningStatementIsStillCredit() {
        val p = TransactionParser.parse(
            "Dear Customer, refund of INR 2 from ONE MOBIKWIK SYSTEM has been credited " +
                "to your ICICI Bank Credit Card XX1012 on 23-JUL-23 and will be adjusted in the coming statement.",
            1,
            "JM-ICICIB"
        )

        assertNotNull(p)
        assertEquals(Direction.CREDIT, p!!.direction)
        assertEquals(200L, p.amountMinor)
        assertEquals("1012", p.accountLast4)
    }

    @Test
    fun scheduledMandateIsNotATransaction() {
        assertNull(
            TransactionParser.parse(
                "For the upcoming mandate set for 16-09-26, INR 59.00 will be debited from your A/c towards Google.",
                1,
                "AX-AXISBK-S"
            )
        )
    }

    @Test
    fun debitFacilityAlertIsNotATransaction() {
        assertNull(
            TransactionParser.parse(
                "A transaction on your HDFC Bank Account ending 9591 Amt: 30000.00. " +
                    "For security reasons, UPI debit facility is temporarily blocked.",
                1,
                "AX-HDFCBK"
            )
        )
    }

    @Test
    fun axisUpiDebit() {
        val p = TransactionParser.parse(
            "Debit\nINR 3,570.00\nA/c no. XX3370\n05-02-23 20:06:02\n" +
                "UPI/P2A/340265702638/ABHISHEK/Axis Bank",
            1,
            "VM-AXISBK"
        )

        assertNotNull(p)
        assertEquals(357000L, p!!.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("3370", p.accountLast4)
        assertEquals("340265702638", p.reference)
        assertEquals("AXIS", p.bank)
        assertEquals("ABHISHEK", p.merchant)
    }

    @Test
    fun axisCardSpend() {
        val p = TransactionParser.parse(
            "Spent\nCard no. XX0116\nINR 276\n31-01-23 15:22:16\nCRED\nAvl Lmt INR 33192.32",
            1,
            "VM-AXISBK"
        )

        assertNotNull(p)
        assertEquals(27600L, p!!.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("0116", p.accountLast4)
        assertEquals("AXIS", p.bank)
        assertEquals("CRED", p.merchant)
    }

    @Test
    fun axisCredit() {
        val p = TransactionParser.parse(
            "INR 1,000.00 credited to A/c no. XX3370 on 06-02-23 at 10:31:30 IST. " +
                "Info- UPI/P2A/303726523465/ITISHREE/Axis Bank",
            1,
            "AD-AXISBK"
        )

        assertNotNull(p)
        assertEquals(100000L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("3370", p.accountLast4)
        assertEquals("303726523465", p.reference)
        assertEquals("ITISHREE", p.merchant)
    }

    @Test
    fun axisReversalIsCredit() {
        val p = TransactionParser.parse(
            "Txn of INR 34.44 on Axis Bank Credit Card no. XX0116 on 29-01-23 " +
                "22:29:54 at UBERINDIASY is reversed.",
            1,
            "TM-AxisBK"
        )

        assertNotNull(p)
        assertEquals(3444L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("0116", p.accountLast4)
        assertEquals("UBERINDIASY", p.merchant)
    }

    @Test
    fun hdfcUpiDebit() {
        val p = TransactionParser.parse(
            "HDFC Bank: Rs 50.00 debited from a/c **9591 on 27-01-23 " +
                "to VPA q268874095@ybl(UPI Ref No 302719101372).",
            1,
            "BP-HDFCBK"
        )

        assertNotNull(p)
        assertEquals(5000L, p!!.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("9591", p.accountLast4)
        assertEquals("302719101372", p.reference)
        assertEquals("HDFC", p.bank)
        assertEquals("q268874095@ybl", p.merchant)
    }

    @Test
    fun hdfcCreditUppiNumberReferenceIsExtracted() {
        val p = TransactionParser.parse(
            "Credit Alert! Rs. 10.00 credited to HDFC Bank A/c XX9591 on 08-10-26 " +
                "from VPA 9205971964@axl (UPI 011669760795).",
            1,
            "AD-HDFCBK"
        )

        assertNotNull(p)
        assertEquals(1000L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("9591", p.accountLast4)
        assertEquals("011669760795", p.reference)
        assertEquals("HDFC", p.bank)
    }

    @Test
    fun iciciUppiNumberNameExtractsReferenceAndMerchant() {
        val p = TransactionParser.parse(
            "ICICI Bank Credit Card XX0005 debited for INR 500.00 on 27-Jun-24 " +
                "for UPI-417901673771-GUPTAPAI. To dispute call 18001080.",
            1,
            "JD-ICICIT"
        )

        assertNotNull(p)
        assertEquals(50000L, p!!.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("0005", p.accountLast4)
        assertEquals("417901673771", p.reference)
        assertEquals("GUPTAPAI", p.merchant)
    }

    @Test
    fun reversalDoesNotUseStatusWordAsReference() {
        val p = TransactionParser.parse(
            "Txn of INR 34.44 on Axis Bank Credit Card no. XX0116 on 29-01-23 " +
                "22:29:54 at UBERINDIASY is reversed.",
            1,
            "TM-AxisBK"
        )

        assertNotNull(p)
        assertEquals(Direction.CREDIT, p!!.direction)
        assertNull(p.reference)
    }

    @Test
    fun declinedDoesNotUseStatusWordAsReference() {
        val p = TransactionParser.parse(
            "TXN DECLINED: Rs.150.09 on 15-09-2025 at UBER INDIA SYSTE PVT L " +
                "on HDFC Bank Credit Card 5304. Reason: Online usage disabled.",
            1,
            "TM-HDFCBK"
        )

        assertNull(p)
    }

    @Test
    fun maskedAccountWithExtraDigitsUsesLastFour() {
        val p = TransactionParser.parse(
            "Debit INR 2000.00 A/c no. XX133370 12-06-2023 20:28:37 " +
                "ATM-WDL/YOGRAJ NAGA Bal INR 15077.74.",
            1,
            "JD-AXISBK"
        )

        assertNotNull(p)
        assertEquals(Direction.DEBIT, p!!.direction)
        assertEquals(200000L, p.amountMinor)
        assertEquals("3370", p.accountLast4)
    }

    @Test
    fun creditBalanceNoticeIsNotACompletedTransaction() {
        assertNull(
            TransactionParser.parse(
                "Your Axis Bank Credit Card no. XX 9861 has a credit balance of INR 1008.48/-. " +
                    "The amount will be credited to your Savings Account if not used within 7 days.",
                1,
                "JM-AXISBK"
            )
        )
    }

    @Test
    fun hdfcEmailReferenceNoIsExtractedAsFullReference() {
        val p = TransactionParser.parse(
            "View. Account update for your HDFC Bank A/c " +
                "We're writing to inform you that Rs.2.00 has been successfully credited " +
                "to your HDFC Bank account ending in 9591. UPI Reference No.: 491022183543 " +
                "For more details on Service charges and Fees, click here.",
            1,
            "com.google.android.gm"
        )

        assertNotNull(p)
        assertEquals(200L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("9591", p.accountLast4)
        assertEquals("491022183543", p.reference)
        assertEquals("HDFC", p.bank)
    }

    @Test
    fun referenceWordItselfIsNeverCapturedAsReference() {
        val p = TransactionParser.parse(
            "Credit Alert! Rs.2.00 credited to HDFC Bank A/c XX9591 " +
                "on 08-10-26 from VPA 9205971964@axl (UPI 491022183543).",
            1,
            "VM-HDFCBK-S"
        )

        assertNotNull(p)
        assertEquals("491022183543", p!!.reference)
    }

    @Test
    fun hdfcUpdateDebitExtractsReference() {
        val p = TransactionParser.parse(
            "UPDATE: INR 5,000.00 debited from HDFC Bank XX9591 on 10-FEB-23. " +
                "Info: UPI-ABDUL WASIQ-9205971964@ybl-UTIB0003614-340727357930-Payment from Phone.",
            1,
            "BP-HDFCBK"
        )

        assertNotNull(p)
        assertEquals(500000L, p!!.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("9591", p.accountLast4)
        assertEquals("340727357930", p.reference)
        assertEquals("ABDUL WASIQ", p.merchant)
    }

    @Test
    fun hdfcSalaryCredit() {
        val p = TransactionParser.parse(
            "Salary Credited! INR 37,714.00 to HDFC Bank A/c XX9591 " +
                "for FT-314283281-XXXXXXXX4548-SNAP-ON BUSINESS SOLUTIONS INDIA PVT LTD-SAL FROM SNAPON JAN 23.",
            1,
            "JM-HDFCBK"
        )

        assertNotNull(p)
        assertEquals(3771400L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("9591", p.accountLast4)
        assertEquals("SNAP-ON BUSINESS SOLUTIONS INDIA PVT LTD", p.merchant)
    }

    @Test
    fun hdfcCreditCardPayment() {
        val p = TransactionParser.parse(
            "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 1794.00 RECEIVED TOWARDS " +
                "YOUR CREDIT CARD ENDING 5304 THROUGH IMPS ON 15-7-2023.",
            1,
            "VD-HDFCBK"
        )

        assertNotNull(p)
        assertEquals(179400L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("5304", p.accountLast4)
        assertEquals("HDFC", p.bank)
    }

    @Test
    fun iciciCardDebit() {
        val p = TransactionParser.parse(
            "ICICI Bank Credit Card XX1012 debited for INR 127.55 on 26-Apr-23 " +
                "for UPI-339289912616-Israr. To dispute call 18002662.",
            1,
            "JD-ICICIT"
        )

        assertNotNull(p)
        assertEquals(12755L, p!!.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("1012", p.accountLast4)
        assertEquals("ICICI", p.bank)
        assertEquals("Israr", p.merchant)
    }

    @Test
    fun iciciCardPaymentReceived() {
        val p = TransactionParser.parse(
            "Dear Customer, Payment of INR 1,000 has been received towards " +
                "your ICICI Bank Credit Card XX1012 on 01-FEB-23 through UPI.",
            1,
            "TM-ICICIB"
        )

        assertNotNull(p)
        assertEquals(100000L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("1012", p.accountLast4)
    }

    @Test
    fun sbiCardSpend() {
        val p = TransactionParser.parse(
            "Rs.1,000.00 spent on your SBI Credit Card ending 0065 at RAJ FILLING STATI 1 on 30/03/24.",
            1,
            "VM-SBICRD"
        )

        assertNotNull(p)
        assertEquals(100000L, p!!.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("0065", p.accountLast4)
        assertEquals("SBI", p.bank)
        assertEquals("RAJ FILLING STATI 1", p.merchant)
    }

    @Test
    fun sbiCardPaymentReceived() {
        val p = TransactionParser.parse(
            "We have received payment of Rs.3,005.00 via BBPS & the same has been credited " +
                "to your SBI Credit Card. Your available limit is Rs.63,000.36.",
            1,
            "VK-SBICRD"
        )

        assertNotNull(p)
        assertEquals(300500L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("SBI", p.bank)
    }

    @Test
    fun sbiAnnualFeeReversal() {
        val p = TransactionParser.parse(
            "Dear SBI Cardholder, as per SR No 1418771393825, Rs.1499.00 has been reversed " +
                "towards Annual Fee on 10/01/2026 for Card No XXXX0065.",
            1,
            "BP-MYSBIC-S"
        )

        assertNotNull(p)
        assertEquals(149900L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("0065", p.accountLast4)
    }

    @Test
    fun indusCardSpend() {
        val p = TransactionParser.parse(
            "INR 387.96 spent on IndusInd Card XX3526 on 09-07-2023 07:54:34 pm " +
                "at BOOKMYSHOW COM. Avl Lmt: INR 59,612.04.",
            1,
            "VM-INDUSB"
        )

        assertNotNull(p)
        assertEquals(38796L, p!!.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("3526", p.accountLast4)
        assertEquals("INDUSIND", p.bank)
        assertEquals("BOOKMYSHOW COM", p.merchant)
    }

    @Test
    fun indusRefund() {
        val p = TransactionParser.parse(
            "Dear Customer, refund of INR 2 from ONE MOBIKWIK SYSTEM Gurgaon IN has been " +
                "credited to your IndusInd Bank Credit Card XX3526.",
            1,
            "JD-INDUSA-S"
        )

        assertNotNull(p)
        assertEquals(200L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("3526", p.accountLast4)
        assertEquals("ONE MOBIKWIK SYSTEM Gurgaon IN", p.merchant)
    }

    @Test
    fun hsbcCardSpend() {
        val p = TransactionParser.parse(
            "Your HSBC credit card xxxxx5145 is used at bookmyshow for INR 334.96 on 24/08/24. " +
                "Available limit - INR 169665.04; outstanding - INR 334.96.",
            1,
            "JM-HSBCIN"
        )

        assertNotNull(p)
        assertEquals(33496L, p!!.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("5145", p.accountLast4)
        assertEquals("HSBC", p.bank)
        assertEquals("bookmyshow", p.merchant)
    }

    @Test
    fun hsbcPaymentReceived() {
        val p = TransactionParser.parse(
            "Dear Customer, we have received a payment of INR 886 for credit card ending 5145 on 10/10/24.",
            1,
            "TM-HSBCIM"
        )

        assertNotNull(p)
        assertEquals(88600L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("5145", p.accountLast4)
    }

    @Test
    fun standardCharteredCardSpend() {
        val p = TransactionParser.parse(
            "Thank you for using StanChart Credit Card No XX7519 on 10/10/24 " +
                "for INR 300.00 at DEOBAND SERVICE STATIO. Avl Limit: INR 135,101.00.",
            1,
            "TM-SCBANK"
        )

        assertNotNull(p)
        assertEquals(30000L, p!!.amountMinor)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("7519", p.accountLast4)
        assertEquals("STANDARD CHARTERED", p.bank)
        assertEquals("DEOBAND SERVICE STATIO", p.merchant)
    }

    @Test
    fun otpRejected() {
        assertNull(
            TransactionParser.parse(
                "123456 is SECRET OTP for txn of INR 34.44 on Axis Bank card XX0116.",
                1,
                "TM-AXISBK"
            )
        )
    }

    @Test
    fun statementRejected() {
        assertNull(
            TransactionParser.parse(
                "E-statement of your Axis Bank Credit Card no. XX0116 has been generated. " +
                    "Total Amount Due INR 2602.90, Minimum Amount Due INR 131.",
                1,
                "VM-AXISBK"
            )
        )
    }

    @Test
    fun declinedRejected() {
        assertNull(
            TransactionParser.parse(
                "Transaction of INR 119.00 on ICICI Bank Credit Card XX8001 is declined " +
                    "due to incorrect CVV.",
                1,
                "TM-ICICIT"
            )
        )
    }

    @Test
    fun paymentFailedRejected() {
        assertNull(
            TransactionParser.parse(
                "Payment Failed! AutoPay for Axis Bank Credit Card Bill of Rs. 100.00 could not be processed.",
                1,
                "AX-HDFCBK"
            )
        )
    }

    @Test
    fun cashbackOfferRejected() {
        assertNull(
            TransactionParser.parse(
                "Spend Rs. 5000 with a minimum of 3 transactions and get Rs. 100 cashback.",
                1,
                "JM-INDUSB-P"
            )
        )
    }

    @Test
    fun realCashbackCredit() {
        val p = TransactionParser.parse(
            "Congratulations! Cashback of INR 424 has been credited to your " +
                "Airtel Axis Bank Credit Card XX9206 towards your spends during JAN'26.",
            1,
            "AD-AXISBK-S"
        )

        assertNotNull(p)
        assertEquals(42400L, p!!.amountMinor)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("9206", p.accountLast4)
        assertEquals("AXIS", p.bank)
    }
}
