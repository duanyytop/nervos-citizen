package org.nervos.gw.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identity")
    fun getAll(): List<Identity>

    @Query("SELECT * FROM identity WHERE public_key LIKE :publicKey LIMIT 1")
    fun findByPublicKey(publicKey: String): Identity?

    @Query("SELECT * FROM identity WHERE passport_number LIKE :passportNumber LIMIT 1")
    fun findByPassportNumber(passportNumber: String): Identity?

    @Query("SELECT * FROM identity WHERE algorithm LIKE :algorithm LIMIT 1")
    fun findByAlgorithm(algorithm: String): List<Identity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg identity: Identity)

    @Delete
    fun delete(identity: Identity)
}