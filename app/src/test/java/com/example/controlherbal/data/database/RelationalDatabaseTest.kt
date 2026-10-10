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
 * RelationalDatabaseTest: Verifica que las entidades relacionales (T1 usuarios, T2 user_plantas, T3 plantas)
 * y el sistema de hasheo PBKDF2 llenen correctamente todos sus respectivos campos (sin emojis ni nombres personalizados).
 */
class RelationalDatabaseTest {

    @Test
    fun testUserEntityPopulationAndHashing() {
        val uid = "test_user_uid_12345"
        val nombre = "Carlos"
        val apPaterno = "Gómez"
        val apMaterno = "Pérez"
        val correo = "carlos.gomez@gmail.com"
        val rawPassword = "SecurePassword123!"

        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPasswordWithPbkdf2(rawPassword, salt)
        val saltHex = SecurityUtils.bytesToHex(salt)

        val user = UserEntity(
            userId = uid,
            nombre = nombre,
            apPaterno = apPaterno,
            apMaterno = apMaterno,
            correo = correo,
            passwordHash = hash,
            passwordSalt = saltHex,
            mfaEnabled = true
        )

        assertEquals(uid, user.userId)
        assertEquals(nombre, user.nombre)
        assertEquals(apPaterno, user.apPaterno)
        assertEquals(apMaterno, user.apMaterno)
        assertEquals(correo, user.correo)
        assertNotNull(user.passwordHash)
        assertNotNull(user.passwordSalt)
        assertTrue(user.mfaEnabled)

        val saltBytes = SecurityUtils.hexToBytes(user.passwordSalt!!)
        val verifyHash = SecurityUtils.hashPasswordWithPbkdf2(rawPassword, saltBytes)
        assertEquals(user.passwordHash, verifyHash)
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
