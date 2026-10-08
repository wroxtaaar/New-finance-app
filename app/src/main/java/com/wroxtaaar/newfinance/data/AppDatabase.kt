package com.wroxtaaar.newfinance.data

import android.content.Context
import androidx.room.*

@Database(entities=[RawEvent::class,TransactionEntity::class,ParserCandidate::class],version=1,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){
 abstract fun rawEvents():RawEventDao
 abstract fun transactions():TransactionDao
 abstract fun candidates():CandidateDao
 companion object { @Volatile private var instance:AppDatabase?=null; fun get(context:Context):AppDatabase=instance?:synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"finance_detector.db").build().also{instance=it}} }
}
