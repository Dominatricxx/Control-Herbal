package com.example.controlherbal.data.database

import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.data.database.entity.PlantCatalogEntity
import com.example.controlherbal.data.database.entity.UserEntity
import com.example.controlherbal.data.database.entity.UserPlantEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entidades relacionales (usuarios / plantas). Las contraseñas no se persisten en Room:
 * Firebase Auth es la única fuente de credenciales. PBKDF2 sigue disponible en SecurityUtils
 * por si hace falta en otro contexto, y se verifica aquí de forma aislada.
 */
class RelationalDatabaseTest {

    @Test
    fun testUserEntityWithoutLocalPassword() {
        val uid = "test_user_uid_12345"
        val user = UserEntity(
            userId = uid,
            nombre = "Carlos",
            apPaterno = "Gómez",
            apMaterno = "Pérez",
            correo = "carlos.gomez@gmail.com",
            passwordHash = null,
            passwordSalt = null,
            mfaEnabled = true,
        )

        assertEquals(uid, user.userId)
        assertEquals("Carlos", user.nombre)
        assertEquals(null, user.passwordHash)
        assertEquals(null, user.passwordSalt)
        assertTrue(user.mfaEnabled)
    }

    @Test
    fun testPbkdf2UtilityStillWorks() {
        val rawPassword = "SecurePassword123!"
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPasswordWithPbkdf2(rawPassword, salt)
        assertNotNull(hash)
        assertEquals(hash, SecurityUtils.hashPasswordWithPbkdf2(rawPassword, salt))
    }

    @Test
    fun testPlantCatalogEntityPopulation() {
        val catalogItem = PlantCatalogEntity(
            idPlanta = 1,
            nombreComun = "Áloe Vera",
            nombreCientifico = "Aloe barbadensis",
            idTipo = 2,
            nombreTipo = "Suculenta",
            idCategoria = 1,
            nombreCategoria = "Medicinal",
            idAmbiente = 1,
            tipoAmbiente = "Luz Directa",
            tempMin = 15.0,
            tempMax = 32.0,
            humMin = 20.0,
            humMax = 60.0,
            sueloMin = 10.0,
            sueloMax = 40.0
        )

        assertEquals(1, catalogItem.idPlanta)
        assertEquals("Áloe Vera", catalogItem.nombreComun)
        assertEquals("Aloe barbadensis", catalogItem.nombreCientifico)
        assertEquals("Suculenta", catalogItem.nombreTipo)
        assertEquals("Medicinal", catalogItem.nombreCategoria)
        assertEquals("Luz Directa", catalogItem.tipoAmbiente)
        assertEquals(15.0, catalogItem.tempMin, 0.01)
        assertEquals(32.0, catalogItem.tempMax, 0.01)
    }

    @Test
    fun testUserPlantEntityPopulation() {
        val userPlant = UserPlantEntity(
            idUserPlant = 10,
            userId = "test_user_uid_12345",
            idPlantaCatalogo = 1,
            cantidad = 2,
            isSelected = true,
            aiDiagnosis = "Planta saludable",
            aiRecommendation = "Regar cada 10 días"
        )

        assertEquals(10, userPlant.idUserPlant)
        assertEquals("test_user_uid_12345", userPlant.userId)
        assertEquals(1, userPlant.idPlantaCatalogo)
        assertEquals(2, userPlant.cantidad)
        assertTrue(userPlant.isSelected)
        assertEquals("Planta saludable", userPlant.aiDiagnosis)
        assertEquals("Regar cada 10 días", userPlant.aiRecommendation)
    }
}
