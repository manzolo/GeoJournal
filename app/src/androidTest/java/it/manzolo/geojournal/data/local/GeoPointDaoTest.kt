package it.manzolo.geojournal.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.manzolo.geojournal.data.local.db.AppDatabase
import it.manzolo.geojournal.data.local.db.GeoPointDao
import it.manzolo.geojournal.data.local.db.GeoPointEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeoPointDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: GeoPointDao

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.geoPointDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun makeEntity(
        id: String,
        title: String,
        lat: Double = 45.0,
        lon: Double = 9.0
    ) = GeoPointEntity(
        id = id,
        title = title,
        latitude = lat,
        longitude = lon
    )

    // ── Test ──────────────────────────────────────────────────────────────────

    @Test
    fun getAll_listaVuotaAllinizio() = runBlocking {
        val result = dao.getAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun insert_getById_puntoSalvatoVienRecuperatoCorrettamente() = runBlocking {
        val entity = makeEntity("1", "Duomo")
        dao.insert(entity)

        val retrieved = dao.getById("1")
        assertNotNull(retrieved)
        assertEquals("Duomo", retrieved!!.title)
        assertEquals(1, retrieved.id.length.coerceAtLeast(1))
    }

    @Test
    fun delete_puntoEliminatoSparisceDallaLista() = runBlocking {
        val entity = makeEntity("1", "Da eliminare")
        dao.insert(entity)
        dao.delete(entity)

        val result = dao.getAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun getAll_restituisceAllIPuntiInseriti() = runBlocking {
        dao.insert(makeEntity("1", "Punto A"))
        dao.insert(makeEntity("2", "Punto B"))
        dao.insert(makeEntity("3", "Punto C"))

        val result = dao.getAll()
        assertEquals(3, result.size)
    }

    @Test
    fun observeAll_flowEmetteAggiornamentoDopolInsert() = runBlocking {
        dao.insert(makeEntity("1", "Primo"))

        val result = dao.observeAll().first()
        assertEquals(1, result.size)
        assertEquals("Primo", result[0].title)
    }

    @Test
    fun update_modificaIlTitoloDiUnPuntoEsistente() = runBlocking {
        dao.insert(makeEntity("1", "Titolo originale"))

        val updated = makeEntity("1", "Titolo aggiornato")
        dao.update(updated)

        val retrieved = dao.getById("1")
        assertEquals("Titolo aggiornato", retrieved!!.title)
    }

    @Test
    fun getById_restituisceNullPerIdInesistente() = runBlocking {
        val result = dao.getById("id-che-non-esiste")
        assertNull(result)
    }
}
