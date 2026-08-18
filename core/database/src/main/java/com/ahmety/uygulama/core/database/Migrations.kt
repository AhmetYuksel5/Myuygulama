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

/**
 * Kelime tekrar programı: kelime başına kademe, sıradaki tarih ve sayaçlar.
 *
 * Göçün en kritik satırı en alttaki: çalışılmakta olan kelimelerin hepsine
 * aynı tarihi vermiyoruz. Verseydik güncellemeden sonraki ilk açılışta yüzlerce
 * kelime birden "vadesi geldi" görünür, kullanıcı daha başlamadan vazgeçerdi.
 * Bunun yerine yığın 1-5 güne yayılıyor.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vocab_progress ADD COLUMN box INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vocab_progress ADD COLUMN dueAt INTEGER")
        db.execSQL("ALTER TABLE vocab_progress ADD COLUMN lastReviewedAt INTEGER")
        db.execSQL("ALTER TABLE vocab_progress ADD COLUMN introducedAt INTEGER")
        db.execSQL("ALTER TABLE vocab_progress ADD COLUMN reviewCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vocab_progress ADD COLUMN lapseCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vocab_progress ADD COLUMN postponeCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vocab_progress ADD COLUMN revealCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_vocab_progress_dueAt ON vocab_progress (dueAt)",
        )
        db.execSQL("UPDATE vocab_progress SET introducedAt = updatedAt")
        db.execSQL("UPDATE vocab_progress SET box = 7 WHERE status = 'KNOWN'")
        db.execSQL(
            """
            UPDATE vocab_progress
               SET box = 1,
                   reviewCount = 1,
                   dueAt = updatedAt + 86400000 * (1 + ABS(RANDOM() % 5))
             WHERE status IN ('LEARNING', 'UNSURE')
            """.trimIndent(),
        )
    }
}

internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
