package com.wroxtaaar.newfinance.engine

import android.content.Context
import com.wroxtaaar.newfinance.data.*
import com.wroxtaaar.newfinance.parser.ParsedTransaction
import com.wroxtaaar.newfinance.parser.TransactionParser
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

class TransactionEngine(context: Context) {
    private val db = AppDatabase.get(context)

    suspend fun ingest(
        source: Source,
        sourceEventId: String,
        packageOrSender: String,
        body: String,
        eventTime: Long
    ) {
        val rawId = db.rawEvents().insert(
            RawEvent(
                source = source.name,
                sourceEventId = sourceEventId,
                packageOrSender = packageOrSender,
                body = body,
                eventTime = eventTime
            )
        )

        if (rawId == -1L) return

        val parsed = TransactionParser.parse(body, eventTime, packageOrSender)
        if (parsed == null) {
            if (TransactionParser.looksFinancial(body)) {
                db.candidates().insert(
                    ParserCandidate(
                        rawEventId = rawId,
                        reason = "UNPARSED_FINANCIAL_MESSAGE",
                        body = body
                    )
                )
            }
            return
        }

        val key = stableKey(
            reference = parsed.reference,
            amount = parsed.amountMinor,
            direction = parsed.direction,
            account = parsed.accountLast4,
            bank = parsed.bank,
            time = parsed.eventTime,
            merchant = parsed.merchant
        )

        if (db.transactions().byStableKey(key) != null) return

        val reference = parsed.reference?.uppercase(Locale.US)
        val exactReference = if (reference.isNullOrBlank()) {
            null
        } else {
            db.transactions().byReference(reference)
        }

        if (
            exactReference != null &&
            exactReference.amountMinor == parsed.amountMinor &&
            exactReference.direction == parsed.direction.name &&
            banksCompatible(exactReference.bank, parsed.bank) &&
            accountsCompatible(exactReference.accountLast4, parsed.accountLast4)
        ) {
            merge(exactReference, source, rawId, parsed)
            return
        }

        val nearby = db.transactions()
            .inWindow(
                parsed.eventTime - 3 * 60_000L,
                parsed.eventTime + 3 * 60_000L
            )
            .firstOrNull {
                it.amountMinor == parsed.amountMinor &&
                    it.direction == parsed.direction.name &&
                    banksCompatible(it.bank, parsed.bank) &&
                    accountsCompatible(it.accountLast4, parsed.accountLast4) &&
                    likelySameEvent(it, parsed)
            }

        if (nearby != null) {
            merge(nearby, source, rawId, parsed)
            return
        }

        db.transactions().insert(
            TransactionEntity(
                amountMinor = parsed.amountMinor,
                direction = parsed.direction.name,
                merchant = parsed.merchant,
                accountLast4 = parsed.accountLast4,
                reference = reference,
                bank = parsed.bank,
                eventTime = parsed.eventTime,
                stableKey = key,
                confidence = parsed.confidence,
                sources = source.name,
                rawEventIds = rawId.toString()
            )
        )
    }

    private suspend fun merge(
        old: TransactionEntity,
        source: Source,
        rawId: Long,
        parsed: ParsedTransaction
    ) {
        val mergedMerchant = chooseBetterMerchant(old.merchant, parsed.merchant)
        val mergedReference = old.reference ?: parsed.reference?.uppercase(Locale.US)
        val mergedBank = old.bank ?: parsed.bank
        val mergedAccount = old.accountLast4 ?: parsed.accountLast4

        val mergedSources = (
            old.sources.split(",").filter(String::isNotBlank) + source.name
        ).distinct().joinToString(",")

        val mergedIds = (
            old.rawEventIds.split(",").filter(String::isNotBlank) + rawId.toString()
        ).distinct().joinToString(",")

        db.transactions().update(
            old.copy(
                merchant = mergedMerchant,
                accountLast4 = mergedAccount,
                reference = mergedReference,
                bank = mergedBank,
                sources = mergedSources,
                rawEventIds = mergedIds,
                confidence = maxOf(old.confidence, parsed.confidence)
            )
        )
    }

    private fun likelySameEvent(
        old: TransactionEntity,
        parsed: ParsedTransaction
    ): Boolean {
        val timeClose = abs(old.eventTime - parsed.eventTime) <= 3 * 60_000L
        if (!timeClose) return false

        val sameAccount = !old.accountLast4.isNullOrBlank() &&
            !parsed.accountLast4.isNullOrBlank() &&
            old.accountLast4 == parsed.accountLast4

        val sameMerchant = merchantCompatible(old.merchant, parsed.merchant)
        val bothHaveMerchant = !old.merchant.isNullOrBlank() &&
            !parsed.merchant.isNullOrBlank()

        return if (old.accountLast4.isNullOrBlank() || parsed.accountLast4.isNullOrBlank()) {
            bothHaveMerchant && sameMerchant
        } else {
            sameAccount && sameMerchant
        }
    }

    private fun banksCompatible(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true
        return norm(a) == norm(b)
    }

    private fun accountsCompatible(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true
        return a == b
    }

    private fun merchantCompatible(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true

        val x = norm(a)
        val y = norm(b)

        return x == y || x.contains(y) || y.contains(x)
    }

    private fun chooseBetterMerchant(a: String?, b: String?): String? {
        if (a.isNullOrBlank()) return b
        if (b.isNullOrBlank()) return a
        return if (b.length > a.length) b else a
    }

    private fun norm(value: String?): String =
        value.orEmpty()
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]"), "")

    private fun stableKey(
        reference: String?,
        amount: Long,
        direction: Direction,
        account: String?,
        bank: String?,
        time: Long,
        merchant: String?
    ): String {
        val basis = if (!reference.isNullOrBlank()) {
            "REF|" + norm(reference) + "|" + amount + "|" + direction.name
        } else {
            "TX|" + amount + "|" + direction.name + "|" +
                norm(bank) + "|" + norm(account) + "|" + norm(merchant) + "|" +
                (time / 30_000L)
        }

        return MessageDigest
            .getInstance("SHA-256")
            .digest(basis.toByteArray())
            .joinToString("") { String.format("%02x", it) }
    }
}
