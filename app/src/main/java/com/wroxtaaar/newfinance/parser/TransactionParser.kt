package com.wroxtaaar.newfinance.parser

import com.wroxtaaar.newfinance.data.Direction
import java.math.BigDecimal

data class ParsedTransaction(val amountMinor:Long,val direction:Direction,val merchant:String?,val accountLast4:String?,val reference:String?,val bank:String?,val eventTime:Long,val confidence:Int)

object TransactionParser {
 private val amountPatterns=listOf(Regex("(?i)(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.\\d{1,2})?)"),Regex("(?i)(?:amount|amt)\\s*[:=]?\\s*(?:rs\\.?|inr|₹)?\\s*([0-9,]+(?:\\.\\d{1,2})?)"))
 private val referencePatterns=listOf(Regex("(?i)(?:utr|rrn|ref(?:erence)?(?:\\s*(?:no|number))?|txn(?:\\s*id)?|transaction\\s*(?:id|no))\\s*[:#-]?\\s*([A-Z0-9]{6,})"))
 private val accountPattern=Regex("(?i)(?:a/c|acct|account)\\s*(?:no\\.?|number)?\\s*[:#-]?\\s*(?:x{2,}|\\*{2,})?([0-9]{4})\\b")
 private val bankPattern=Regex("(?i)\\b(HDFC|ICICI|AXIS|SBI|STATE BANK|KOTAK|IDFC|FEDERAL|YES BANK|PNB|INDUSIND|HSBC|RBL|AU BANK|BANK OF BARODA|BOB)\\b")
 private val merchantPatterns=listOf(Regex("(?i)(?:to|at|towards|merchant)\\s*[:=-]?\\s*([A-Za-z][A-Za-z0-9&.' _-]{2,60})"),Regex("(?i)(?:from|by)\\s*[:=-]?\\s*([A-Za-z][A-Za-z0-9&.' _-]{2,60})"))
 fun parse(body:String,eventTime:Long):ParsedTransaction?{
  val text=body.replace(Regex("\\s+")," ").trim(); if(!looksFinancial(text))return null
  val amountMatch=amountPatterns.firstNotNullOfOrNull{it.find(text)}?:return null
  val amount=amountMatch.groupValues[1].replace(",","").toBigDecimalOrNull()?:return null
  val direction=when{Regex("(?i)\\b(debited|debit|spent|paid|purchase|withdrawn|sent|transferred to|payment made)\\b").containsMatchIn(text)->Direction.DEBIT;Regex("(?i)\\b(credited|credit|received|deposited|refund|reversed|cashback)\\b").containsMatchIn(text)->Direction.CREDIT;else->Direction.UNKNOWN}
  if(direction==Direction.UNKNOWN)return null
  val ref=referencePatterns.firstNotNullOfOrNull{it.find(text)}?.groupValues?.get(1)
  val account=accountPattern.find(text)?.groupValues?.get(1)
  val bank=bankPattern.find(text)?.groupValues?.get(1)?.uppercase()
  val merchant=merchantPatterns.firstNotNullOfOrNull{it.find(text)}?.groupValues?.get(1)?.trim()?.trimEnd('.',',',';')
  var score=40;if(direction!=Direction.UNKNOWN)score+=20;if(ref!=null)score+=20;if(account!=null)score+=10;if(bank!=null)score+=5;if(merchant!=null)score+=5
  return ParsedTransaction(amount.movePointRight(2).longValueExact(),direction,merchant,account,ref,bank,eventTime,score.coerceAtMost(100))
 }
 fun looksFinancial(body:String):Boolean{val t=body.lowercase();val f=listOf("debited","credited","debit","credit","upi","transaction","txn","payment","purchase","withdrawn","transfer","a/c","acct","utr","rrn","inr","rs.","₹");val n=listOf("otp","one time password","offer","loan","pre-approved","win","cash prize");return f.count{t.contains(it)}>=2&&n.none{t.contains(it)}}
}
