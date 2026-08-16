package com.ahmety.uygulama.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Şema göçleri.
 *
 * Uygulama telefona kurulu olduğu için artık `fallbackToDestructiveMigration`
 * kullanmıyoruz — o, sürüm her değiştiğinde girilmiş tüm veriyi silerdi.
 * Her şema değişikliği buraya bir göç eklemek zorunda.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // İçe aktarılan görevin kaynaktaki kimliği; mükerrer aktarımı engelliyor.
        db.execSQL("ALTER TABLE task ADD COLUMN externalId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_task_externalId ON task (externalId)")
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vocab_progress (
                word TEXT NOT NULL PRIMARY KEY,
                status TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vocab_progress_status ON vocab_progress (status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vocab_progress_deletedAt ON vocab_progress (deletedAt)")
    }
}

internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
