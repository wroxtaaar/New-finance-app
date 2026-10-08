package com.wroxtaaar.newfinance.parser

import com.wroxtaaar.newfinance.data.Direction
import java.math.BigDecimal
import java.util.Locale

data class ParsedTransaction(
    val amountMinor: Long,
    val direction: Direction,
    val merchant: String?,
    val accountLast4: String?,
    val reference: String?,
    val bank: String?,
    val eventTime: Long,
    val confidence: Int
)

object TransactionParser {
    private val amountPatterns = listOf(
        Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.\d{1,2})?)\b"""),
        Regex("""(?i)\b(?:amount|amt)\s*[:=]?\s*(?:rs\.?|inr|₹)?\s*([0-9,]+(?:\.\d{1,2})?)\b""")
    )

    private val referencePatterns = listOf(
        Regex("""(?i)\b(?:utr|rrn)\s*[:#-]?\s*([A-Z0-9]{6,})\b"""),
        Regex("""(?i)\bref(?:erence)?(?:\s*(?:no|number))?\s*[:#-]?\s*([A-Z0-9]{6,})\b"""),
        Regex("""(?i)\btxn(?:\s*(?:id|no))?\s*[:#-]?\s*([A-Z0-9]{6,})\b"""),
        Regex("""(?i)\btransaction\s*(?:id|no)\s*[:#-]?\s*([A-Z0-9]{6,})\b"""),
        Regex("""(?i)\bpaytm\s*ref\s*[:#-]?\s*([A-Z0-9]{6,})\b"""),
        Regex("""(?i)\bUPI/P2[AM]/([0-9]{8,18})\b"""),
        Regex("""(?i)\bUPI-(?:[^-]*-){3}([0-9]{10,18})-Payment\b""")
    )

    private val accountPatterns = listOf(
        Regex("""(?i)\b(?:a/c|acct|account)\s*(?:no\.?|number)?\s*[:#-]?\s*(?:x+|\*+)?([0-9]{4})\b"""),
        Regex("""(?i)\b(?:credit\s+card|card)\s*(?:(?:no\.?|number)\s*)?(?:ending\s*(?:with)?\s*)?[:#-]?\s*(?:x+|\*+)?([0-9]{4})\b"""),
        Regex("""(?i)\bcard\s+ending\s+(?:with\s+)?(?:x+|\*+)?([0-9]{4})\b"""),
        Regex("""(?i)\bHDFC\s+Bank\s+(?:x+|\*+)([0-9]{4})\b""")
    )

    private val bankPatterns = listOf(
        "AXIS" to Regex("""(?i)\baxis(?:\s+bank)?\b"""),
        "HDFC" to Regex("""(?i)\bhdfc(?:\s+bank)?\b"""),
        "ICICI" to Regex("""(?i)\bicici(?:\s+bank)?\b"""),
        "SBI" to Regex("""(?i)\bsbi(?:\s+card)?(?:\s+bank)?\b"""),
        "INDUSIND" to Regex("""(?i)\bindusind(?:\s+bank)?\b"""),
        "HSBC" to Regex("""(?i)\bhsbc(?:\s+india)?\b"""),
        "STANDARD CHARTERED" to Regex("""(?i)\b(?:standard\s+chartered|stanchart)\b""")
    )

    fun parse(
        body: String,
        eventTime: Long,
        sender: String? = null
    ): ParsedTransaction? {
        val text = normalize(body)
        if (text.isBlank() || isKnownNonTransaction(text)) return null

        val direction = detectDirection(text) ?: return null
        val amount = extractAmount(text) ?: return null

        val reference = extractReference(text)
        val accountLast4 = extractAccountLast4(text)
        val bank = detectBank(text, sender)
        val merchant = extractMerchant(text)

        var score = 45
        score += 15
        score += 15
        if (reference != null) score += 20
        if (accountLast4 != null) score += 10
        if (bank != null) score += 5
        if (merchant != null) score += 5

        return ParsedTransaction(
            amountMinor = amount.movePointRight(2).longValueExact(),
            direction = direction,
            merchant = merchant,
            accountLast4 = accountLast4,
            reference = reference,
            bank = bank,
            eventTime = eventTime,
            confidence = score.coerceAtMost(100)
        )
    }

    fun looksFinancial(body: String): Boolean {
        val text = normalize(body)
        return text.isNotBlank() &&
            !isKnownNonTransaction(text) &&
            detectDirection(text) != null &&
            extractAmount(text) != null
    }

    private fun normalize(body: String): String =
        body.replace('\r', ' ')
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isKnownNonTransaction(text: String): Boolean {
        val t = text.lowercase(Locale.US)

        val exclusions = listOf(
            "one time password",
            "otp",
            "e-statement",
            "statement",
            "total amount due",
            "minimum amount due",
            "min amt due",
            "payable by",
            "payment due",
            "payment failed",
            "transaction failed",
            "payment unsuccessful",
            "transaction unsuccessful",
            "could not be processed",
            "has been declined",
            "transaction was declined",
            "txn was declined",
            "has been rejected",
            "loan",
            "pre-approved",
            "pre approved",
            "credit limit",
            "limit enhancement",
            "higher credit limit",
            "logged in",
            "login",
            "registration",
            "application",
            "reward points",
            "e-voucher",
            "voucher code",
            "excess cashback",
            "will be reversed"
        )

        return exclusions.any(t::contains)
    }

    private fun extractAmount(text: String): BigDecimal? =
        amountPatterns
            .firstNotNullOfOrNull { it.find(text) }
            ?.groupValues
            ?.get(1)
            ?.replace(",", "")
            ?.toBigDecimalOrNull()

    private fun detectDirection(text: String): Direction? {
        val t = text.lowercase(Locale.US)

        val creditSignals = listOf(
            "credited",
            "deposited",
            "received payment",
            "payment received",
            "payment of",
            "refund",
            "reversed"
        )
        val debitSignals = listOf(
            "debited",
            "debit",
            "spent",
            "withdrawn",
            "transferred to",
            "payment made",
            "purchase",
            "used at",
            "thank you for using"
        )

        val credit = creditSignals.any(t::contains)
        val debit = debitSignals.any(t::contains)

        return when {
            t.contains("is reversed") -> Direction.CREDIT
            credit && !debit -> Direction.CREDIT
            debit && !credit -> Direction.DEBIT
            t.contains("spent") || t.contains("debited") ||
                t.contains("debit") || t.contains("withdrawn") || t.contains("used at") ||
                t.contains("thank you for using") -> Direction.DEBIT
            t.contains("credited") || t.contains("deposited") ||
                t.contains("refund") || t.contains("reversed") -> Direction.CREDIT
            else -> null
        }
    }

    private fun extractReference(text: String): String? {
        for (pattern in referencePatterns) {
            val match = pattern.find(text) ?: continue
            return match.groupValues[1].uppercase(Locale.US)
        }
        return null
    }

    private fun extractAccountLast4(text: String): String? {
        for (pattern in accountPatterns) {
            val match = pattern.find(text) ?: continue
            return match.groupValues[1]
        }
        return null
    }

    private fun detectBank(text: String, sender: String?): String? {
        val s = sender.orEmpty().uppercase(Locale.US)

        return when {
            "AXISBK" in s || "AXISMR" in s -> "AXIS"
            "HDFCBK" in s || "HDFCBN" in s -> "HDFC"
            "ICICIT" in s || "ICICIB" in s -> "ICICI"
            "SBICRD" in s || "MYSBIC" in s -> "SBI"
            "INDUSB" in s || "INDUSA" in s -> "INDUSIND"
            "HSBCIM" in s || "HSBCIN" in s -> "HSBC"
            "SCBANK" in s -> "STANDARD CHARTERED"
            else -> bankPatterns.firstOrNull { it.second.containsMatchIn(text) }?.first
        }
    }

    private fun cleanMerchant(raw: String): String? {
        val value = raw.trim().trim('.', ',', ';', '-', ' ')

        if (value.isBlank()) return null

        val lower = value.lowercase(Locale.US)
        val invalid = listOf(
            "not you",
            "call on",
            "sms block",
            "avl limit",
            "available limit",
            "payment from phone"
        )
        if (invalid.any(lower::contains)) return null

        return value
            .replace(Regex("\\s+"), " ")
            .take(80)
    }

    private fun extractMerchant(text: String): String? {
        // Most specific bank/payment formats first. This prevents a generic
        // "at ..." match from swallowing trailing status text.
        Regex("""(?i)\bfor\s+UPI-\d{8,18}-([A-Za-z][A-Za-z0-9 &.'_/-]{1,80})(?=\.|,|\s+To dispute|\s*$)""")
            .find(text)
            ?.let { return cleanMerchant(it.groupValues[1]) }

        Regex("""(?i)\bused\s+at\s+([A-Za-z0-9][A-Za-z0-9 &.'*_-]{1,80})\s+for\s+(?:rs\.?|inr|₹)""")
            .find(text)
            ?.let { return cleanMerchant(it.groupValues[1]) }

        Regex("""(?i)\bat\s+([A-Za-z0-9][A-Za-z0-9 &.'*_-]{1,80})\s+is\s+reversed\b""")
            .find(text)
            ?.let { return cleanMerchant(it.groupValues[1]) }

        Regex("""(?i)\bUPI-([^-]+)-[^-]+-[^-]+-[0-9]{10,18}-Payment\b""")
            .find(text)
            ?.let { return cleanMerchant(it.groupValues[1]) }

        Regex("""(?i)\brefund\s+of\s+(?:rs\.?|inr|₹)\s*[0-9,]+(?:\.\d{1,2})?\s+from\s+(.+?)\s+(?:has\s+been\s+)?credited\b""")
            .find(text)
            ?.let { return cleanMerchant(it.groupValues[1]) }

        Regex("""(?i)\bUPI/P2[AM]/[0-9]{8,18}/([^/]+)""")
            .find(text)
            ?.let { return cleanMerchant(it.groupValues[1]) }

        Regex("""(?i)\bSpent\s+.*?\s+INR\s+[0-9,]+(?:\.\d{1,2})?\s+\d{1,2}[-/]\d{1,2}[-/]\d{2,4}\s+\d{1,2}:\d{2}(?::\d{2})?(?:\s+IST)?\s+(.+?)(?=\s+(?:Avl|Available)\b|$)""")
            .find(text)
            ?.let { return cleanMerchant(it.groupValues[1]) }

        Regex("""(?i)\bto\s+VPA\s+([^\s(]+)""")
            .find(text)
            ?.let { return cleanMerchant(it.groupValues[1]) }

        Regex("""(?i)\b(?:at|on)\s+([A-Za-z0-9][A-Za-z0-9 &.'*_-]{2,80})(?=\s+(?:avl|available|on|for|if|to|is\b)|[.,]|$)""")
            .find(text)
            ?.let { return cleanMerchant(it.groupValues[1]) }

        Regex("""(?i)\bfor\s+FT-\s*[0-9]+\s*-\s*[0-9X]+\s*-\s*(.+?)\s*-\s*SAL\b""")
            .find(text)
            ?.let { return cleanMerchant(it.groupValues[1]) }

        return null

    }
}
