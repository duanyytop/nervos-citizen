package org.nervos.gw.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Identity::class], version = 1)
abstract class IdentityDatabase : RoomDatabase() {

    abstract fun identityDao(): IdentityDao

    companion object {

        @Volatile private var INSTANCE: IdentityDatabase? = null

        fun instance(context: Context): IdentityDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context.applicationContext,
                IdentityDatabase::class.java, "identity")
                .build()
    }
}