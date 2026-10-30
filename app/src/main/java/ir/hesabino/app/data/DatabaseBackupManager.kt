package ir.hesabino.app.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Database-level backup foundation.
 *
 * The caller must ensure no write operation is in progress while exporting/importing.
 * Import replaces the local database atomically at file level and removes SQLite
 * sidecar files so the next open starts from a clean database state.
 */
class DatabaseBackupManager(context: Context) {
    private val appContext = context.applicationContext

    fun exportTo(output: OutputStream) {
        val database = HesabinoDatabase(appContext)
        database.close()
        val source = appContext.getDatabasePath("hesabino.db")
        require(source.exists()) { "فایل دیتابیس برای پشتیبان‌گیری وجود ندارد." }
        source.inputStream().use { input -> input.copyTo(output) }
        output.flush()
    }

    fun importFrom(input: InputStream) {
        val database = HesabinoDatabase(appContext)
        database.close()

        val databaseFile = appContext.getDatabasePath("hesabino.db")
        val parent = databaseFile.parentFile ?: error("مسیر دیتابیس معتبر نیست.")
        if (!parent.exists()) parent.mkdirs()

        val temporary = File(parent, "hesabino.db.importing")
        input.use { source -> temporary.outputStream().use { target -> source.copyTo(target) } }

        require(temporary.length() > 0) { "فایل پشتیبان خالی است." }

        File(parent, "hesabino.db-wal").delete()
        File(parent, "hesabino.db-shm").delete()
        databaseFile.delete()

        require(temporary.renameTo(databaseFile)) {
            temporary.delete()
            "بازیابی دیتابیس انجام نشد."
        }
    }
}
