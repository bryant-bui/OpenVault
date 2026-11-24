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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "encrypted_safe_config")
data class EncryptedSafeConfigEntity(
    @PrimaryKey val id: Int = 1,
    val initialized: Boolean,
    val kdfType: Int,
    val kdfIterations: Int,
    val kdfMemory: Int?,
    val kdfParallelism: Int?,
    val passcodeSalt: String?,
    val passcodeEncryptedSafeKey: String?,
    val recoverySalt: String?,
    val recoveryKdfIterations: Int,
    val recoveryEncryptedSafeKey: String?,
    val autoLockMinutes: Int,
    val failedAttempts: Int,
    val lockedUntilMs: Long?,
    val recoveryRequired: Boolean,
    val usedBytes: Long,
    val itemCount: Int,
    val maxFileBytes: Int,
    val revisionDate: String?,
)

@Entity(tableName = "encrypted_safe_items")
data class EncryptedSafeItemEntity(
    @PrimaryKey val id: String,
    val category: String,
    val encryptedMetadata: String,
    val hasThumbnail: Boolean,
    val blobSizeBytes: Long,
    val thumbSizeBytes: Long,
    val blobSha256: String?,
    val favorite: Boolean,
    val deletedAt: String?,
    val revisionDate: String?,
    val createdAt: String?,
    val updatedAt: String?,
)

@Dao
interface SafeDao {
    @Query("SELECT * FROM encrypted_safe_config WHERE id = 1 LIMIT 1")
    fun observeConfig(): Flow<EncryptedSafeConfigEntity?>

    @Query("SELECT * FROM encrypted_safe_config WHERE id = 1 LIMIT 1")
    suspend fun getConfigOnce(): EncryptedSafeConfigEntity?

    @Upsert
    suspend fun upsertConfig(config: EncryptedSafeConfigEntity)

    @Query("DELETE FROM encrypted_safe_config")
    suspend fun clearConfig()

    @Query("SELECT * FROM encrypted_safe_items ORDER BY favorite DESC, createdAt DESC")
    fun observeItems(): Flow<List<EncryptedSafeItemEntity>>

    @Query("SELECT * FROM encrypted_safe_items")
    suspend fun getAllItemsOnce(): List<EncryptedSafeItemEntity>

    @Upsert
    suspend fun upsertItems(items: List<EncryptedSafeItemEntity>)

    @Upsert
    suspend fun upsertItem(item: EncryptedSafeItemEntity)

    @Query("DELETE FROM encrypted_safe_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("DELETE FROM encrypted_safe_items")
    suspend fun clearItems()

    @Transaction
    suspend fun replaceAllSafeItems(items: List<EncryptedSafeItemEntity>) {
        clearItems()
        if (items.isNotEmpty()) upsertItems(items)
    }

    @Transaction
    suspend fun clearAllSafe() {
        clearConfig()
        clearItems()
    }
}

@Database(
    entities = [
        EncryptedFolderEntity::class,
        EncryptedCipherEntity::class,
        EncryptedSafeConfigEntity::class,
        EncryptedSafeItemEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class OpenVaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun safeDao(): SafeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `encrypted_safe_config` (
                        `id` INTEGER NOT NULL,
                        `initialized` INTEGER NOT NULL,
                        `kdfType` INTEGER NOT NULL,
                        `kdfIterations` INTEGER NOT NULL,
                        `kdfMemory` INTEGER,
                        `kdfParallelism` INTEGER,
                        `passcodeSalt` TEXT,
                        `passcodeEncryptedSafeKey` TEXT,
                        `recoverySalt` TEXT,
                        `recoveryKdfIterations` INTEGER NOT NULL,
                        `recoveryEncryptedSafeKey` TEXT,
                        `autoLockMinutes` INTEGER NOT NULL,
                        `failedAttempts` INTEGER NOT NULL,
                        `lockedUntilMs` INTEGER,
                        `recoveryRequired` INTEGER NOT NULL,
                        `usedBytes` INTEGER NOT NULL,
                        `itemCount` INTEGER NOT NULL,
                        `maxFileBytes` INTEGER NOT NULL,
                        `revisionDate` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `encrypted_safe_items` (
                        `id` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `encryptedMetadata` TEXT NOT NULL,
                        `hasThumbnail` INTEGER NOT NULL,
                        `blobSizeBytes` INTEGER NOT NULL,
                        `thumbSizeBytes` INTEGER NOT NULL,
                        `blobSha256` TEXT,
                        `favorite` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        `revisionDate` TEXT,
                        `createdAt` TEXT,
                        `updatedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): OpenVaultDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                OpenVaultDatabase::class.java,
                "openvault_encrypted.db",
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
