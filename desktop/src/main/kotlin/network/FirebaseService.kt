package network

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Scanner

class FirebaseService {
    private val dbUrl = "https://controlherbal-97558-default-rtdb.firebaseio.com/sensor.json"

    data class SensorData(val temp: Double, val hum: Double, val luz: Int, val soil: Double)

    suspend fun fetchSensorData(): SensorData? = withContext(Dispatchers.IO) {
        try {
            val url = URL(dbUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connect()

            if (conn.responseCode == 200) {
                val scanner = Scanner(conn.inputStream)
                val response = StringBuilder()
                while (scanner.hasNext()) response.append(scanner.nextLine())
                scanner.close()

                val json = JSONObject(response.toString())
                return@withContext SensorData(
                    temp = json.optDouble("temp", 0.0),
                    hum = json.optDouble("hum", 0.0),
                    luz = json.optInt("luz", 0),
                    soil = json.optDouble("soil", 0.0)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
