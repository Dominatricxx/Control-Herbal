import os
import time
import random
from datetime import datetime, timezone
from influxdb_client import InfluxDBClient, Point
from influxdb_client.client.write_api import SYNCHRONOUS
import paho.mqtt.client as mqtt

# Configuración de conexión (con tu token de API definitivo integrado)
MQTT_HOST = os.getenv("MQTT_HOST", "mqtt-broker")
MQTT_PORT = int(os.getenv("MQTT_PORT", 1883))
INFLUX_URL = f"http://{os.getenv('INFLUX_HOST', 'influxdb')}:8086"
INFLUX_TOKEN = os.getenv("INFLUX_TOKEN", "BTNhYuQexOa4Odsfs0ZOjMTHFv7f30Y0ri9S2NmXBWhZWJm6-XXGHlBjPvUhC-bfxfXdoVCd4fN-yRdMBVgjrA==")
INFLUX_ORG = os.getenv("INFLUX_ORG", "herbal-org")
INFLUX_BUCKET = os.getenv("INFLUX_BUCKET", "telemetry-bucket")

# Inicializar cliente de InfluxDB con el token de acceso
influx_client = InfluxDBClient(url=INFLUX_URL, token=INFLUX_TOKEN, org=INFLUX_ORG)
write_api = influx_client.write_api(write_options=SYNCHRONOUS)

def on_connect(client, _userdata, _flags, rc, _properties=None):
    # Compatibilidad paho-mqtt v1 (int) y v2 (ReasonCode)
    success = (rc == 0) if isinstance(rc, int) else (hasattr(rc, "is_failure") and not rc.is_failure)
    if success:
        print(f"Conectado al broker MQTT exitosamente (código: {rc})")
        client.subscribe("control/herbal/sensors")
    else:
        print(f"Error al conectar con el broker MQTT: {rc}")

def on_message(_client, _userdata, msg):
    try:
        payload = msg.payload.decode("utf-8", errors="ignore")
        print(f"Mensaje recibido en [{msg.topic}]: {payload}")

        parts = payload.split(",")
        if len(parts) < 2:
            print(f"Payload inválido (se esperaban al menos 2 valores separados por coma): {payload}")
            return

        temp = float(parts[0].strip())
        humidity = float(parts[1].strip())

        point = Point("invernadero") \
            .tag("ubicacion", "prototipo_1") \
            .field("temperatura", temp) \
            .field("humedad", humidity) \
            .time(datetime.now(timezone.utc))

        write_api.write(bucket=INFLUX_BUCKET, org=INFLUX_ORG, record=point)
        print("-> Datos guardados en InfluxDB exitosamente.")
    except Exception as parse_err:
        print(f"Error procesando el payload: {parse_err}")

# Compatibilidad para paho-mqtt v1.x y v2.x
try:
    from paho.mqtt.enums import CallbackAPIVersion
    mqtt_client = mqtt.Client(CallbackAPIVersion.VERSION2)
except (ImportError, AttributeError):
    mqtt_client = mqtt.Client()

mqtt_client.on_connect = on_connect
mqtt_client.on_message = on_message

print("Iniciando puente de telemetría...")
try:
    mqtt_client.connect(MQTT_HOST, MQTT_PORT, 60)
except Exception as conn_err:
    print(f"No se pudo conectar al broker MQTT ({MQTT_HOST}:{MQTT_PORT}): {conn_err}")

mqtt_client.loop_start()

try:
    while True:
        temp_simulada = round(random.uniform(22.0, 28.5), 2)
        humedad_simulada = round(random.uniform(55.0, 75.0), 2)

        payload_mqtt = f"{temp_simulada},{humedad_simulada}"
        mqtt_client.publish("control/herbal/sensors", payload_mqtt)
        print(f"Enviado a MQTT -> Temperatura: {temp_simulada}°C | Humedad: {humedad_simulada}%")

        time.sleep(5)
except KeyboardInterrupt:
    print("\nDeteniendo servicio...")
finally:
    print("Limpiando recursos...")
    mqtt_client.loop_stop()
    mqtt_client.disconnect()
    influx_client.close()
    print("Puente de telemetría detenido.")