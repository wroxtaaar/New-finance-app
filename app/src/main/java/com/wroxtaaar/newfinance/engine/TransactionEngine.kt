package com.wroxtaaar.newfinance.engine

import android.content.Context
import com.wroxtaaar.newfinance.data.*
import com.wroxtaaar.newfinance.parser.TransactionParser
import java.security.MessageDigest
import java.util.Locale

class TransactionEngine(context:Context){
 private val db=AppDatabase.get(context)
 suspend fun ingest(source:Source,sourceEventId:String,packageOrSender:String,body:String,eventTime:Long){
  val rawId=db.rawEvents().insert(RawEvent(source=source.name,sourceEventId=sourceEventId,packageOrSender=packageOrSender,body=body,eventTime=eventTime))
  if(rawId==-1L)return
  val p=TransactionParser.parse(body,eventTime) ?: run{db.candidates().insert(ParserCandidate(rawEventId=rawId,reason="UNPARSED_OR_NON_TRANSACTION",body=body));return}
  val key=stableKey(p.reference,p.amountMinor,p.direction,p.accountLast4,p.eventTime,p.merchant)
  if(db.transactions().byStableKey(key)!=null)return
  val ref=p.reference?.uppercase(Locale.US)
  val exactRef=if(ref.isNullOrBlank())null else db.transactions().byReference(ref)
  if(exactRef!=null && exactRef.amountMinor==p.amountMinor && exactRef.direction==p.direction.name){merge(exactRef,source,rawId,p);return}
  val nearby=db.transactions().inWindow(p.eventTime-5*60_000,p.eventTime+5*60_000).firstOrNull{it.amountMinor==p.amountMinor&&it.direction==p.direction.name&&compatible(it.accountLast4,p.accountLast4)&&merchantCompatible(it.merchant,p.merchant)}
  if(nearby!=null){merge(nearby,source,rawId,p);return}
  db.transactions().insert(TransactionEntity(amountMinor=p.amountMinor,direction=p.direction.name,merchant=p.merchant,accountLast4=p.accountLast4,reference=ref,bank=p.bank,eventTime=p.eventTime,stableKey=key,confidence=p.confidence,sources=source.name,rawEventIds=rawId.toString()))
 }
 private suspend fun merge(old:TransactionEntity,source:Source,rawId:Long,p:com.wroxtaaar.newfinance.parser.ParsedTransaction){
  val mergedMerchant=if(old.merchant.isNullOrBlank())p.merchant else old.merchant
  val mergedRef=old.reference?:p.reference?.uppercase(Locale.US)
  val mergedBank=old.bank?:p.bank
  val mergedSources=(old.sources.split(",").filter{it.isNotBlank()}+source.name).distinct().joinToString(",")
  val mergedIds=(old.rawEventIds.split(",").filter{it.isNotBlank()}+rawId).distinct().joinToString(",")
  db.transactions().update(old.copy(merchant=mergedMerchant,reference=mergedRef,bank=mergedBank,sources=mergedSources,rawEventIds=mergedIds,confidence=maxOf(old.confidence,p.confidence)))
 }
 private fun compatible(a:String?,b:String?)=a==null||b==null||a==b
 private fun merchantCompatible(a:String?,b:String?):Boolean{if(a.isNullOrBlank()||b.isNullOrBlank())return true;val x=norm(a);val y=norm(b);return x==y||x.contains(y)||y.contains(x)}
 private fun norm(v:String?)=v.orEmpty().uppercase(Locale.US).replace(Regex("[^A-Z0-9]"),"")
 private fun stableKey(ref:String?,amount:Long,direction:Direction,account:String?,time:Long,merchant:String?):String{val basis=if(!ref.isNullOrBlank())"REF|${norm(ref)}|$amount|${direction.name}" else "TX|$amount|${direction.name}|${norm(account)}|${norm(merchant)}|${time/60_000}";return MessageDigest.getInstance("SHA-256").digest(basis.toByteArray()).joinToString(""){String.format("%02x",it)}}
}
