// ========================================================================
// CONTROLADOR HERBAL - RESPUESTA RÁPIDA Y SENSIBILIDAD AJUSTADA
// ========================================================================
// - Muestreo cada 1 segundo (anterior 3s)
// - Ventana de mediana reducida a 3 muestras
// - Curva de corrección de luz más agresiva (potencia 2.5)
// - Sin alertas de exceso de luz aislado
// ========================================================================

#include <DHT.h>
#include <WiFi.h>
#include <WiFiMulti.h>
#include <FirebaseESP32.h>
#include <Preferences.h>
#include "addons/TokenHelper.h"

// ----------------------------- CONFIGURACIÓN DE REDES -----------------------------
WiFiMulti wifiMulti;
const char* redes[][2] = {
  {"Internet 1", "Password"},
  {"Internet 2", "Password"},
};
const int numRedes = sizeof(redes) / sizeof(redes[0]);

// ----------------------------- CONFIGURACIÓN DE FIREBASE -----------------------------
#define FIREBASE_HOST   "controlherbal-97558-default-rtdb.firebaseio.com"
#define FIREBASE_API_KEY "AIzaSyCCTQmeObzKv24qT265O2eNQOA_HblTcgc"

FirebaseData firebaseData;
FirebaseConfig firebaseConfig;
FirebaseAuth firebaseAuth;

// ----------------------------- PINES Y SENSORES -----------------------------
#define DHTPIN          4
#define DHTTYPE         DHT11
#define LDRPIN          34
#define SOIL_PIN        35
#define LED_AZUL        12
#define BOTON_CAL       0

#define PLANTA_LUZ      1
#define PLANTA_HIBRIDA  2
#define PLANTA_SOMBRA   3

DHT dht(DHTPIN, DHTTYPE);
Preferences preferences;

// Rangos óptimos (sin cambios)
struct Rangos {
  float tempMin, tempMax;
  float humMin, humMax;
  float luzMin, luzMax;
  float soilMin, soilMax;
};

const Rangos rangos[4] = {
  {0,0,0,0,0,0,0,0},
  {15.0, 32.0, 40.0, 60.0, 60.0, 100.0, 25.0, 70.0},
  {15.0, 32.0, 40.0, 60.0, 30.0, 70.0, 25.0, 70.0},
  {15.0, 32.0, 40.0, 60.0, 10.0, 40.0, 25.0, 70.0}
};

// Coeficientes del IRH
const float COEF_HUM_AMB = 0.25;
const float COEF_TEMP    = 0.25;
const float COEF_LUZ     = 0.15;
const float COEF_SUELO    = 0.35;

const float IRH_OPTIMO      = 25.0;
const float IRH_ADVERTENCIA = 50.0;
const float IRH_RIESGO      = 75.0;
const float IRH_CRITICO     = 100.0;
const float PRED_MIN_HORAS  = 0.5;
const float PRED_MAX_HORAS  = 6.0;

// ----------------------------- ESTRUCTURAS -----------------------------
struct Lectura {
  float temp, humAmb, luz, luzRaw, suelo;
  unsigned long ts;
};
struct Tendencias { float dtTemp, dtHum, dtLuz, dtSuelo; };
struct Prediccion {
  float irh, tiempoSequia, tiempoSombra;
  String accion, urgencia;
  int horasPred;
};

Lectura actual, anterior;
Tendencias tend;
Prediccion pred;
float tempC = 25.0, humAmb = 50.0, luzPct = 50.0, luzRawPct = 50.0, sueloPct = 50.0;
int tipoPlanta = PLANTA_HIBRIDA;
unsigned long lastTipoRead = 0;
const unsigned long TIPO_INTERVALO = 60000;

bool sensoresOk = true;
bool errorCom = false;
unsigned long ciclo = 0;

// Calibración LDR persistente
int ldrMin = 4095, ldrMax = 0;
bool calibrado = false;
unsigned long lastCal = 0;
const int VENTANA = 3;            // Ventana más pequeña (3 muestras) para respuesta rápida
int ldrBuffer[VENTANA], ldrIdx = 0;
int sueloBuffer[VENTANA], sueloIdx = 0, sueloCnt = 0;

// ----------------------------- PROTOTIPOS -----------------------------
void conectarWiFiMulti();
void conectarFirebase();
void leerTipoPlanta();
void leerSensores();
void calcularTendencias();
float estresHumedadAmb(float h);
float estresTemp(float t);
float estresLuz(float l);
float estresSuelo(float s);
String generarRecomendacion();
void aplicarTeorema();
void actualizarLED();
float leerDHT11Promedio();
int leerLDRMediana();
float corregirLuz(float pct);
bool leerSoil();
int leerSoilRaw();
void calibrarLDR(const char* motivo);
void guardarCalibracion();
void cargarCalibracion();
void enviarFirebase();

// ----------------------------- SETUP -----------------------------
void setup() {
  Serial.begin(115200);
  delay(2000);
  Serial.println("\n🌱 CONTROLADOR HERBAL - RESPUESTA RÁPIDA (1s)");
  pinMode(LED_AZUL, OUTPUT);
  digitalWrite(LED_AZUL, LOW);
  pinMode(LDRPIN, INPUT);
  pinMode(SOIL_PIN, INPUT);
  pinMode(BOTON_CAL, INPUT_PULLUP);
  dht.begin();

  for (int i = 0; i < VENTANA; i++) ldrBuffer[i] = analogRead(LDRPIN);
  for (int i = 0; i < VENTANA; i++) sueloBuffer[i] = 0;

  cargarCalibracion();
  bool forzar = (digitalRead(BOTON_CAL) == LOW);
  unsigned long ahora = millis() / 1000;
  bool expirado = !calibrado || ldrMin >= ldrMax || (lastCal != 0 && ahora - lastCal > 86400);
  if (forzar || expirado) {
    calibrarLDR(forzar ? "botón" : "automática");
    lastCal = ahora;
    guardarCalibracion();
  } else {
    Serial.printf("Calibración LDR cargada: min=%d max=%d\n", ldrMin, ldrMax);
  }

  for (int i = 0; i < numRedes; i++) wifiMulti.addAP(redes[i][0], redes[i][1]);
  conectarWiFiMulti();
  conectarFirebase();
  leerTipoPlanta();

  actual = {0,0,0,0,0,0};
  anterior = actual;
  tend = {0,0,0,0};
  pred = {0,0,0,"","",0};
}

void loop() {
  ciclo++;
  leerSensores();
  aplicarTeorema();
  enviarFirebase();
  actualizarLED();
  if (millis() - lastTipoRead > TIPO_INTERVALO) {
    leerTipoPlanta();
    lastTipoRead = millis();
  }
  delay(1000);   // Ahora 1 segundo para respuesta rápida
}

// ----------------------------- CONEXIÓN (sin cambios) -----------------------------
void conectarWiFiMulti() {
  Serial.print("Conectando a la mejor red WiFi...");
  if (wifiMulti.run() == WL_CONNECTED) {
    Serial.println("\n✅ Conectado. IP: " + WiFi.localIP().toString());
    errorCom = false;
  } else {
    Serial.println("\n❌ No se pudo conectar a ninguna red.");
    errorCom = true;
  }
}

void conectarFirebase() {
  firebaseConfig.api_key = FIREBASE_API_KEY;
  firebaseConfig.database_url = FIREBASE_HOST;
  firebaseAuth.user.email = "";
  firebaseAuth.user.password = "";
  if (Firebase.signUp(&firebaseConfig, &firebaseAuth, "", "")) {
    Serial.println("✅ Firebase autenticado");
    errorCom = false;
  } else {
    Serial.printf("❌ Firebase error: %s\n", firebaseConfig.signer.signupError.message.c_str());
    errorCom = true;
    return;
  }
  Firebase.begin(&firebaseConfig, &firebaseAuth);
  Firebase.reconnectWiFi(true);
}

void leerTipoPlanta() {
  if (WiFi.status() != WL_CONNECTED) return;
  if (Firebase.getInt(firebaseData, "/config/tipoPlanta")) {
    int nuevo = firebaseData.intData();
    if (nuevo >= 1 && nuevo <= 3 && nuevo != tipoPlanta) {
      tipoPlanta = nuevo;
      Serial.printf("🌿 Tipo planta actualizado: %d\n", tipoPlanta);
    }
  }
}

// ----------------------------- LECTURA RÁPIDA DE SENSORES -----------------------------
float leerDHT11Promedio() {
  // Reducido a 2 lecturas para mayor velocidad (antes 3)
  float sumT = 0, sumH = 0;
  int n = 0;
  for (int i = 0; i < 2; i++) {
    float t = dht.readTemperature();
    float h = dht.readHumidity();
    if (!isnan(t) && !isnan(h)) {
      sumT += t; sumH += h; n++;
    }
    delay(30);
  }
  if (n == 0) {
    Serial.println("⚠️ DHT11 falló, usando valores por defecto (25°C, 50%)");
    tempC = 25.0;
    humAmb = 50.0;
    return -1;
  }
  tempC = sumT / n;
  humAmb = sumH / n;
  return 0;
}

int leerLDRMediana() {
  int raw = analogRead(LDRPIN);
  ldrBuffer[ldrIdx] = raw;
  ldrIdx = (ldrIdx + 1) % VENTANA;
  int temp[VENTANA];
  memcpy(temp, ldrBuffer, sizeof(temp));
  // Ordenación simple para ventana pequeña
  for (int i = 0; i < VENTANA - 1; i++) {
    for (int j = i + 1; j < VENTANA; j++) {
      if (temp[i] > temp[j]) {
        int aux = temp[i];
        temp[i] = temp[j];
        temp[j] = aux;
      }
    }
  }
  return temp[VENTANA / 2];
}

// Nueva función de corrección con potencia 2.5 (más agresiva)
float corregirLuz(float pct) {
  // pct es el porcentaje lineal (0-100) obtenido del mapeo raw->%
  // Aplicamos una curva que comprime los valores altos: (x/100)^2.5 * 100
  return constrain(pow(pct / 100.0, 2.5) * 100.0, 0, 100);
}

int leerSoilRaw() {
  return analogRead(SOIL_PIN);
}

bool leerSoil() {
  int raw = leerSoilRaw();
  sueloBuffer[sueloIdx] = raw;
  sueloIdx = (sueloIdx + 1) % VENTANA;
  if (sueloCnt < VENTANA) sueloCnt++;
  int temp[VENTANA];
  for (int i = 0; i < sueloCnt; i++) temp[i] = sueloBuffer[i];
  for (int i = 0; i < sueloCnt - 1; i++) {
    for (int j = i + 1; j < sueloCnt; j++) {
      if (temp[i] > temp[j]) {
        int aux = temp[i];
        temp[i] = temp[j];
        temp[j] = aux;
      }
    }
  }
  int mediana = temp[sueloCnt / 2];
  sueloPct = constrain(map(mediana, 4095, 0, 0, 100), 0, 100);
  return true;
}

void leerSensores() {
  Serial.println("\n📡 Nueva lectura");
  anterior = actual;

  bool dhtOk = (leerDHT11Promedio() != -1);
  int rawLdr = leerLDRMediana();
  int minR = (ldrMin < 4095) ? ldrMin : 200;
  int maxR = (ldrMax > 0) ? ldrMax : 3000;
  // Si el rango es muy estrecho, lo ampliamos artificialmente para evitar saturación
  if (maxR - minR < 500) {
    maxR = minR + 500;
  }
  float pctLin = constrain(map(rawLdr, minR, maxR, 0, 100), 0, 100);
  luzRawPct = pctLin;
  luzPct = corregirLuz(pctLin);   // Corrección agresiva
  
  bool soilOk = leerSoil();
  if (sueloPct < 0 || sueloPct > 100) {
    sueloPct = 50.0;
    soilOk = false;
  }
  sensoresOk = dhtOk && soilOk;
  actual = {tempC, humAmb, luzPct, luzRawPct, sueloPct, millis()};

  // Clasificación de tipo de luz (informativo)
  String tipoLuz = "Indeterminado";
  if (luzPct < 40 || tempC < 28.0) {
    tipoLuz = "🌤️ Nublado / Luz moderada";
  } else if (luzPct > 60 && tempC > 32.0) {
    tipoLuz = "☀️ Sol directo (calor elevado)";
  } else {
    tipoLuz = "🌥️ Luz variable";
  }
  
  Serial.printf("Temp: %.1f°C, Hum amb: %.1f%%, Luz: %.1f%%, Suelo: %.1f%% | %s\n", 
                tempC, humAmb, luzPct, sueloPct, tipoLuz.c_str());
  if (sensoresOk && anterior.ts > 0) calcularTendencias();
}

void calcularTendencias() {
  float dt = (actual.ts - anterior.ts) / 3600000.0;
  if (dt > 0) {
    tend.dtTemp  = (actual.temp - anterior.temp) / dt;
    tend.dtHum   = (actual.humAmb - anterior.humAmb) / dt;
    tend.dtLuz   = (actual.luz - anterior.luz) / dt;
    tend.dtSuelo = (actual.suelo - anterior.suelo) / dt;
    Serial.printf("Tendencias: ΔT=%.2f, ΔH=%.2f, ΔL=%.2f, ΔS=%.2f\n",
                  tend.dtTemp, tend.dtHum, tend.dtLuz, tend.dtSuelo);
  }
}

// ----------------------------- ESTRÉS (sin cambios) -----------------------------
float estresHumedadAmb(float h) {
  float minH = rangos[tipoPlanta].humMin;
  float maxH = rangos[tipoPlanta].humMax;
  if (h < minH) return constrain((minH - h) / minH * 100, 0, 100);
  if (h > maxH) return constrain((h - maxH) / (100 - maxH) * 100, 0, 100);
  return 0;
}

float estresTemp(float t) {
  float minT = rangos[tipoPlanta].tempMin;
  float maxT = rangos[tipoPlanta].tempMax;
  if (t < minT) return constrain((minT - t) / minT * 100, 0, 100);
  if (t > maxT) {
    if (t <= 40.0) return constrain((t - maxT) / (40.0 - maxT) * 100, 0, 100);
    else return constrain(100 + (t - 40.0) * 5, 100, 200);
  }
  return 0;
}

float estresLuz(float l) {
  float minL = rangos[tipoPlanta].luzMin;
  float maxL = rangos[tipoPlanta].luzMax;
  if (l < minL) return constrain((minL - l) / minL * 100, 0, 100);
  if (l > maxL) {
    if (tipoPlanta == PLANTA_LUZ)
      return constrain((l - maxL) / (100 - maxL) * 50, 0, 50);
    else if (tipoPlanta == PLANTA_HIBRIDA)
      return constrain((l - maxL) / (100 - maxL) * 80, 0, 80);
    else
      return constrain((l - maxL) / (100 - maxL) * 150, 0, 150);
  }
  return 0;
}

float estresSuelo(float s) {
  float minS = rangos[tipoPlanta].soilMin;
  float maxS = rangos[tipoPlanta].soilMax;
  if (s < minS) return constrain((minS - s) / minS * 100, 0, 100);
  if (s > maxS) return constrain((s - maxS) / (100 - maxS) * 100, 0, 100);
  return 0;
}

// ----------------------------- RECOMENDACIÓN (sin alertas de luz aislada) -----------------------------
String generarRecomendacion() {
  float eSuelo = estresSuelo(sueloPct);
  if (eSuelo > 80) return "💧 SUELO EXTREMO: " + String((sueloPct < rangos[tipoPlanta].soilMin) ? "RIEGO INMEDIATO" : "DRENAJE URGENTE");
  float eTemp = estresTemp(tempC);
  if (eTemp > 80) return "🔥 TEMPERATURA EXTREMA: sombra y riego";
  
  if (eSuelo > 40) return (sueloPct < rangos[tipoPlanta].soilMin) ? "🌿 Suelo seco, aumentar riego" : "🌊 Suelo saturado, reducir riego";
  if (eTemp > 40) return "🌡️ Estrés térmico, sombra parcial";
  if (humAmb < rangos[tipoPlanta].humMin) return "💧 Baja humedad ambiente, rocíe";
  if (humAmb > rangos[tipoPlanta].humMax) return "💨 Alta humedad, ventile";
  if (tempC < rangos[tipoPlanta].tempMin) return "❄️ Frío, proteja la planta";
  if (luzPct < rangos[tipoPlanta].luzMin) return "🌑 Poca luz, acerque a ventana";
  return "✅ Condiciones óptimas";
}

// ----------------------------- TEOREMA IRH -----------------------------
void aplicarTeorema() {
  float eH = estresHumedadAmb(humAmb);
  float eT = estresTemp(tempC);
  float eL = estresLuz(luzPct);
  float eS = estresSuelo(sueloPct);

  float tendH = 0, tendT = 0, tendL = 0, tendS = 0;
  if (tend.dtHum < -5) tendH = 15;
  else if (tend.dtHum < -2) tendH = 8;
  else if (tend.dtHum > 10) tendH = 5;
  if (tend.dtTemp > 2) tendT = 15;
  else if (tend.dtTemp > 1) tendT = 8;
  else if (tend.dtTemp < -2) tendT = 10;
  if (tend.dtLuz > 10) tendL = 15;
  else if (tend.dtLuz > 5) tendL = 8;
  if (tend.dtSuelo < -2) tendS = 15;
  else if (tend.dtSuelo > 5) tendS = 5;

  float irh = eH * COEF_HUM_AMB + eT * COEF_TEMP + eL * COEF_LUZ + eS * COEF_SUELO;
  irh += tendH * 0.05 + tendT * 0.05 + tendL * 0.05 + tendS * 0.1;
  pred.irh = constrain(irh, 0, 100);
  pred.accion = generarRecomendacion();

  // Predicción de sequía
  if (tend.dtSuelo < -2 && sueloPct < rangos[tipoPlanta].soilMin) {
    float horas = (rangos[tipoPlanta].soilMin - sueloPct) / (abs(tend.dtSuelo) + 0.001);
    pred.tiempoSequia = constrain(horas, PRED_MIN_HORAS, PRED_MAX_HORAS);
  } else if (tend.dtTemp > 0.5 && luzPct > 70 && sueloPct < rangos[tipoPlanta].soilMin) {
    pred.tiempoSequia = 1.0;
  } else {
    pred.tiempoSequia = PRED_MAX_HORAS;
  }

  // Alerta de sombra/riego solo si hay sequía inminente (<=1h) y luz alta
  if (pred.tiempoSequia <= 1.0 && luzPct > 70) {
    pred.accion = "⚠️ SEQUÍA INMINENTE en " + String(pred.tiempoSequia, 1) + " horas. Proporcione sombra y riego urgente.";
  } else if (pred.tiempoSequia <= 2.0 && luzPct > 80 && sueloPct < rangos[tipoPlanta].soilMin) {
    pred.accion = "☀️ Riesgo de sequía en " + String(pred.tiempoSequia, 1) + "h. Considere sombra parcial y riego.";
  }

  // Predicción de sombra (para Firebase)
  if (tend.dtLuz > 10 && tend.dtTemp > 0 && sueloPct < rangos[tipoPlanta].soilMin) {
    float horas = (rangos[tipoPlanta].soilMin - sueloPct) / (abs(tend.dtSuelo) + 0.001);
    pred.tiempoSombra = constrain(horas, PRED_MIN_HORAS, PRED_MAX_HORAS);
  } else if (luzPct > 80 && tempC > rangos[tipoPlanta].tempMax && sueloPct < rangos[tipoPlanta].soilMin) {
    pred.tiempoSombra = 1.0;
  } else {
    pred.tiempoSombra = PRED_MAX_HORAS;
  }

  pred.horasPred = min(pred.tiempoSequia, pred.tiempoSombra);
  String seq = (pred.tiempoSequia <= PRED_MAX_HORAS) ? String(pred.tiempoSequia,1)+"h" : "Sin riesgo";
  String som = (pred.tiempoSombra <= PRED_MAX_HORAS) ? String(pred.tiempoSombra,1)+"h" : "Sin necesidad";
  
  if (pred.irh > IRH_CRITICO) pred.urgencia = "CRÍTICA";
  else if (pred.irh > IRH_RIESGO) pred.urgencia = "ALTA";
  else if (pred.irh > IRH_ADVERTENCIA) pred.urgencia = "MODERADA";
  else pred.urgencia = "BAJA";

  Serial.printf("🧮 IRH=%.1f (%s) | Sequía=%s Sombra=%s | Recom: %s\n",
                pred.irh, pred.urgencia.c_str(), seq.c_str(), som.c_str(), pred.accion.c_str());
}

// ----------------------------- CALIBRACIÓN LDR -----------------------------
void calibrarLDR(const char* motivo) {
  Serial.printf("Calibrando LDR (%s) por 20 segundos...\n", motivo);
  int minVal = 4095, maxVal = 0;
  unsigned long start = millis();
  while (millis() - start < 20000) {
    int raw = analogRead(LDRPIN);
    if (raw > 10 && raw < 4085) {
      if (raw < minVal) minVal = raw;
      if (raw > maxVal) maxVal = raw;
    }
    delay(100);
  }
  ldrMin = (minVal == 4095) ? 200 : minVal;
  ldrMax = (maxVal == 0) ? ldrMin + 1 : maxVal;
  if (ldrMin >= ldrMax) ldrMax = ldrMin + 1;
  calibrado = true;
  Serial.printf("Calibración LDR finalizada: min=%d max=%d\n", ldrMin, ldrMax);
}

void guardarCalibracion() {
  preferences.begin("herbal", false);
  preferences.putInt("min", ldrMin);
  preferences.putInt("max", ldrMax);
  preferences.putBool("ok", calibrado);
  preferences.putULong("last", lastCal);
  preferences.end();
}

void cargarCalibracion() {
  preferences.begin("herbal", true);
  ldrMin = preferences.getInt("min", 4095);
  ldrMax = preferences.getInt("max", 0);
  calibrado = preferences.getBool("ok", false);
  lastCal = preferences.getULong("last", 0);
  preferences.end();
}

// ----------------------------- ENVÍO A FIREBASE -----------------------------
void enviarFirebase() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("⚠️ WiFi perdido. Reconectando...");
    if (wifiMulti.run() == WL_CONNECTED) {
      Serial.println("✅ WiFi reconectado.");
      errorCom = false;
    } else {
      errorCom = true;
      return;
    }
  }
  FirebaseJson json;
  json.set("temp", tempC);
  json.set("hum", humAmb);
  json.set("luz", luzPct);
  json.set("luz_raw", luzRawPct);
  json.set("soil", sueloPct);
  json.set("irh", pred.irh);
  json.set("seq", pred.tiempoSequia);
  json.set("somb", pred.tiempoSombra);
  json.set("acc", pred.accion);
  json.set("tipo", tipoPlanta);
  json.set("boot", (int)ciclo);
  json.set("sensores_ok", sensoresOk ? 1 : 0);
  json.set("timestamp", millis());

  if (Firebase.updateNode(firebaseData, "/sensor", json)) {
    Serial.println("✅ Datos enviados a Firebase");
    errorCom = false;
  } else {
    Serial.println("❌ Error Firebase: " + firebaseData.errorReason());
    errorCom = true;
  }
}

// ----------------------------- LED AZUL -----------------------------
void actualizarLED() {
  if (sensoresOk && !errorCom) {
    digitalWrite(LED_AZUL, HIGH);
  } else {
    static unsigned long lastToggle = 0;
    static bool estado = false;
    if (millis() - lastToggle >= 200) {
      lastToggle = millis();
      estado = !estado;
      digitalWrite(LED_AZUL, estado);
    }
  }
}