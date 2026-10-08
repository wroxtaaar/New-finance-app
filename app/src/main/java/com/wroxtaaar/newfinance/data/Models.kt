package com.wroxtaaar.newfinance.data

import androidx.room.*

enum class Source { SMS, NOTIFICATION }
enum class Direction { DEBIT, CREDIT, UNKNOWN }

@Entity(tableName="raw_events", indices=[Index(value=["source","sourceEventId"], unique=true)])
data class RawEvent(@PrimaryKey(autoGenerate=true) val id:Long=0,val source:String,val sourceEventId:String,val packageOrSender:String,val body:String,val eventTime:Long,val receivedAt:Long=System.currentTimeMillis())

@Entity(tableName="transactions", indices=[Index(value=["stableKey"],unique=true),Index(value=["reference"]),Index(value=["eventTime"])])
data class TransactionEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val amountMinor:Long,val direction:String,val merchant:String?,val accountLast4:String?,val reference:String?,val bank:String?,val eventTime:Long,val stableKey:String,val confidence:Int,val sources:String,val rawEventIds:String)

@Entity(tableName="parser_candidates")
data class ParserCandidate(@PrimaryKey(autoGenerate=true) val id:Long=0,val rawEventId:Long,val reason:String,val body:String,val createdAt:Long=System.currentTimeMillis())
