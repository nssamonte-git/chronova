package noel.chronova.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades from every earlier schema to the current one through the app's own
 * [ChronovaDatabase.create] and its migrations. Each old database is built from the schema
 * Room exported for that version (`schemas/`, added to this test's assets); opening it runs the
 * migrations, and Room then checks the result against the current entities, so a migration that
 * leaves a table or column wrong fails here. The rows written before the upgrade must survive it.
 *
 * Runs as its own test app with its own database file: the installed app's data is never touched.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private var db: ChronovaDatabase? = null

    @Before
    fun clean() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun close() {
        db?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun upgradesFromVersion1() = upgradeFrom(1)

    @Test
    fun upgradesFromVersion2() = upgradeFrom(2)

    @Test
    fun upgradesFromVersion3() = upgradeFrom(3)

    @Test
    fun upgradesFromVersion4() = upgradeFrom(4)

    @Test
    fun upgradesFromVersion5() = upgradeFrom(5)

    private fun upgradeFrom(version: Int) {
        createOldDatabase(version)
        val upgraded = ChronovaDatabase.create(context).also { db = it }
        val sql = upgraded.openHelper.writableDatabase // opening runs the migrations and Room's schema check

        assertEquals(CURRENT_VERSION, sql.version)
        assertEquals(listOf("Launch"), sql.strings("SELECT name FROM projects"))
        assertEquals(listOf("Design"), sql.strings("SELECT title FROM tasks"))
        // v5: existing tasks are not started, have no notes and aren't milestones.
        assertEquals(listOf("0||0"), sql.strings("SELECT progress || '|' || notes || '|' || is_milestone FROM tasks"))
        if (version >= 2) assertEquals(listOf("HOURS"), sql.strings("SELECT scale FROM view_settings"))
        // v6: a remembered view starts with the critical path off.
        if (version >= 2) assertEquals(listOf("0"), sql.strings("SELECT critical_path FROM view_settings"))
        if (version >= 3) assertEquals(listOf("d1"), sql.strings("SELECT id FROM dependencies"))
        // Tables added after the starting version exist and start empty.
        if (version < 2) assertEquals(emptyList<String>(), sql.strings("SELECT scale FROM view_settings"))
        if (version < 3) assertEquals(emptyList<String>(), sql.strings("SELECT id FROM dependencies"))
        assertEquals(emptyList<String>(), sql.strings("SELECT kind FROM notifications"))
    }

    /** A database file exactly as the app at [version] created it, with one row in each table it had. */
    private fun createOldDatabase(version: Int) {
        val schema = testAssets.open("$SCHEMA_DIR/$version.json").use { JSONObject(it.reader().readText()) }.getJSONObject("database")
        val file = context.getDatabasePath(DB_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))

            old.execSQL("INSERT INTO projects VALUES ('p1', 'Launch', '2026-03-02', '2026-03-08', 1000, 0, 1)")
            old.execSQL(
                "INSERT INTO tasks (id, project_id, title, start_millis, end_millis, color, sort_order, updated_at, deleted, dirty) " +
                    "VALUES ('t1', 'p1', 'Design', 0, 3600000, 'BLUE', 0, 1000, 0, 1)",
            )
            if (version >= 2) old.execSQL("INSERT INTO view_settings (project_id, scale, unit_width_dp, snap_minutes, row_height_dp) VALUES ('p1', 'HOURS', 96.0, 15, 56.0)")
            if (version >= 3) old.execSQL("INSERT INTO dependencies VALUES ('d1', 'p1', 't0', 't1', 1000, 0, 1)")
            old.version = version
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.strings(query: String): List<String> =
        query(query).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private companion object {
        const val DB_NAME = "chronova.db"
        const val CURRENT_VERSION = 6
        const val SCHEMA_DIR = "noel.chronova.core.database.ChronovaDatabase"
    }
}
