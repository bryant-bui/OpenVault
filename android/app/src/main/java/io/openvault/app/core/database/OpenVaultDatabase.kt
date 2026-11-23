package io.openvault.app.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Stores ONLY Bitwarden Type 2 encrypted folder names. Zero plaintext on disk.
 */
@Entity(tableName = "encrypted_folders")
data class EncryptedFolderEntity(
    @PrimaryKey val id: String,
    val encryptedName: String,
    val revisionDate: String?,
)

/**
 * Stores ONLY Bitwarden Type 2 encrypted CipherDto JSON blobs. Zero plaintext on disk.
 */
@Entity(tableName = "encrypted_ciphers")
data class EncryptedCipherEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val type: Int,
    val encryptedJson: String,
    val favorite: Boolean,
    val deletedDate: String?,
    val revisionDate: String?,
    val creationDate: String?,
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM encrypted_folders ORDER BY id ASC")
    fun observeFolders(): Flow<List<EncryptedFolderEntity>>

    @Query("SELECT * FROM encrypted_ciphers ORDER BY favorite DESC, revisionDate DESC")
    fun observeCiphers(): Flow<List<EncryptedCipherEntity>>

    @Query("SELECT * FROM encrypted_folders")
    suspend fun getAllFoldersOnce(): List<EncryptedFolderEntity>

    @Query("SELECT * FROM encrypted_ciphers")
    suspend fun getAllCiphersOnce(): List<EncryptedCipherEntity>

    @Upsert
    suspend fun upsertFolders(folders: List<EncryptedFolderEntity>)

    @Upsert
    suspend fun upsertFolder(folder: EncryptedFolderEntity)

    @Upsert
    suspend fun upsertCiphers(ciphers: List<EncryptedCipherEntity>)

    @Upsert
    suspend fun upsertCipher(cipher: EncryptedCipherEntity)

    @Query("DELETE FROM encrypted_folders WHERE id = :id")
    suspend fun deleteFolderById(id: String)

    @Query("DELETE FROM encrypted_ciphers WHERE id = :id")
    suspend fun deleteCipherById(id: String)

    @Query("DELETE FROM encrypted_folders")
    suspend fun clearFolders()

    @Query("DELETE FROM encrypted_ciphers")
    suspend fun clearCiphers()

    @Transaction
    suspend fun replaceAllSyncData(
        folders: List<EncryptedFolderEntity>,
        ciphers: List<EncryptedCipherEntity>,
    ) {
        clearFolders()
        clearCiphers()
        if (folders.isNotEmpty()) upsertFolders(folders)
        if (ciphers.isNotEmpty()) upsertCiphers(ciphers)
    }

    @Transaction
    suspend fun clearAll() {
        clearFolders()
        clearCiphers()
    }
}

@Database(
    entities = [EncryptedFolderEntity::class, EncryptedCipherEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class OpenVaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        fun create(context: Context): OpenVaultDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                OpenVaultDatabase::class.java,
                "openvault_encrypted.db",
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
