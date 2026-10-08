package com.wroxtaaar.newfinance.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface RawEventDao { @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(event:RawEvent):Long; @Query("SELECT * FROM raw_events ORDER BY eventTime DESC LIMIT 500") fun recent():Flow<List<RawEvent>> }
@Dao interface TransactionDao {
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(tx:TransactionEntity):Long
 @Update suspend fun update(tx:TransactionEntity)
 @Query("SELECT * FROM transactions ORDER BY eventTime DESC") fun all():Flow<List<TransactionEntity>>
 @Query("SELECT * FROM transactions WHERE stableKey=:key LIMIT 1") suspend fun byStableKey(key:String):TransactionEntity?
 @Query("SELECT * FROM transactions WHERE reference=:ref LIMIT 1") suspend fun byReference(ref:String):TransactionEntity?
 @Query("SELECT * FROM transactions WHERE eventTime BETWEEN :from AND :to") suspend fun inWindow(from:Long,to:Long):List<TransactionEntity>
}
@Dao interface CandidateDao { @Insert suspend fun insert(candidate:ParserCandidate):Long; @Query("SELECT * FROM parser_candidates ORDER BY createdAt DESC") fun all():Flow<List<ParserCandidate>> }
