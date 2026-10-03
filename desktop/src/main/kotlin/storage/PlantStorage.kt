package storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.logging.Level
import java.util.logging.Logger

/** Registro neutro de una planta persistida en disco. */
data class PlantRecord(
    val name: String,
    val type: String,
    val environment: String,
    val isSelected: Boolean = false,
    val lastWateringTime: Long = 0L
)

/**
 * Persistencia local de plantas del escritorio.
 *
 * Mejoras sobre el formato anterior (texto delimitado por '|' sin escape):
 *  - los campos se escapan (\\, \p, \n, \r), por lo que un nombre con '|' o saltos de línea ya
 *    no corrompe el archivo ni desplaza columnas; el formato sigue siendo legible y los archivos
 *    antiguos (sin barras invertidas) se leen igual;
 *  - escritura atómica (archivo temporal + movimiento) para no dejar el archivo a medias;
 *  - permisos 0600 en sistemas POSIX (solo el usuario puede leerlo);
 *  - límites de tamaño de archivo, número de plantas y longitud de campos.
 */
object PlantStorage {

    private val log = Logger.getLogger("ControlHerbal.PlantStorage")

    private const val MAX_FILE_BYTES = 256 * 1024L
    private const val MAX_PLANTS = 200
    private const val MAX_FIELD_CHARS = 120

    var file: File = File(System.getProperty("user.home"), ".controlherbal_plants.txt")

    fun escape(field: String): String =
        field.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n").replace("\r", "\\r")

    fun unescape(field: String): String {
        val sb = StringBuilder(field.length)
        var i = 0
        while (i < field.length) {
            val c = field[i]
            if (c == '\\' && i + 1 < field.length) {
                when (field[i + 1]) {
                    '\\' -> sb.append('\\')
                    'p' -> sb.append('|')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    else -> { sb.append(c); sb.append(field[i + 1]) }
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** Divide una línea por '|' sin escapar. */
    internal fun splitFields(line: String): List<String> = line.split("|")

    fun encode(plants: List<PlantRecord>): String =
        plants.take(MAX_PLANTS).joinToString("\n") {
            listOf(
                escape(it.name.take(MAX_FIELD_CHARS)),
                escape(it.type.take(MAX_FIELD_CHARS)),
                escape(it.environment.take(MAX_FIELD_CHARS)),
                it.isSelected.toString(),
                it.lastWateringTime.toString()
            ).joinToString("|")
        }

    fun decode(text: String): List<PlantRecord> =
        text.lineSequence().take(MAX_PLANTS).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = splitFields(line)
            if (parts.size < 4) return@mapNotNull null
            val last = if (parts.size >= 5) parts[4].toLongOrNull() ?: 0L else 0L
            PlantRecord(
                name = unescape(parts[0]).take(MAX_FIELD_CHARS),
                type = unescape(parts[1]).take(MAX_FIELD_CHARS),
                environment = unescape(parts[2]).take(MAX_FIELD_CHARS),
                isSelected = parts[3].toBoolean(),
                lastWateringTime = last
            )
        }.toList()

    fun save(plants: List<PlantRecord>) {
        try {
            val target = file.toPath()
            val tmp = Files.createTempFile(target.parent ?: File(".").toPath(), ".plants", ".tmp")
            try {
                Files.write(tmp, encode(plants).toByteArray(Charsets.UTF_8))
                restrictPermissions(tmp)
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(tmp)
            }
        } catch (e: Exception) {
            log.log(Level.WARNING, "No se pudieron guardar las plantas: ${e.javaClass.simpleName}")
        }
    }

    fun load(): List<PlantRecord> {
        if (!file.exists()) return emptyList()
        return try {
            if (file.length() > MAX_FILE_BYTES) {
                log.warning("Archivo de plantas demasiado grande; se ignora")
                return emptyList()
            }
            decode(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            log.log(Level.WARNING, "No se pudieron leer las plantas: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    private fun restrictPermissions(path: java.nio.file.Path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
            // Windows u otro sistema sin POSIX: se hereda el ACL del directorio del usuario.
            val f = path.toFile()
            f.setReadable(false, false); f.setReadable(true, true)
            f.setWritable(false, false); f.setWritable(true, true)
        }
    }
}
