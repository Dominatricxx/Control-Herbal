# Registro de cambios: privacidad, consentimiento, accesibilidad y licencias

## 1. Documentos y consentimiento
- `legal/`: Aviso de Privacidad integral (LFPDPPP, con anexo RGPD), Términos de Servicio, Política de Cookies y Registro de Consentimientos. Fuente única; se empaquetan como assets de la app.
- Primera pantalla de consentimiento (`ConsentActivity`): casilla sin marcar, documentos a un toque, versión y fecha guardadas localmente, y nueva petición cuando cambia la versión.
- Consentimiento de IA independiente, con texto específico y aviso de seguridad sobre plantas tóxicas; revocable desde la app.
- Pantalla Privacidad y accesibilidad: documentos, interruptor de IA, paletas, exportar datos, borrar datos locales y eliminar cuenta (borra historial de riego en la nube, cuenta y datos locales).
- Formularios: enlaces al aviso y términos en el inicio de sesión; advertencia y límite de 50 caracteres en el nombre de la planta.
- Gradle: `verifyLegalDocs` (versión coherente; con `-Pherbal.requireLegal=true` exige rellenar los marcadores).

## 2. Minimización de datos
- Eliminados permisos `CAMERA`, `VIBRATE` y `ACCESS_WIFI_STATE`; `AD_ID` removido; analítica, SSAID y Crashlytics desactivados explícitamente.
- La IA ya no recibe el nombre de la planta (solo el tipo). Fotos reducidas, sin EXIF y borradas tras usarse.
- Eliminadas dependencias sin uso (`play-services-wearable`, `androidx.wear`, `percentlayout`, `legacy-support-v4`, `recyclerview` del reloj).
- Regla de Firebase para que el propietario pueda borrar su historial al eliminar la cuenta.

## 3. Auditoría de terceros
- `scripts/audit_privacy.py` (28 familias de SDK prohibidas, lista blanca de 8 permisos y de dominios, sin WebView/cookies/IDs de dispositivo/ubicación, coherencia de documentos). Probado por mutación (5 de 5 detectadas).
- Informe: `docs/AUDITORIA_PRIVACIDAD_TERCEROS.md`.

## 4. Accesibilidad
- 4 paletas de estado y 7 colores de gráficos verificados con simulación de protanopia, deuteranopia y tritanopia (CIEDE2000) y contraste WCAG; `scripts/audit_palettes.py`.
- Estados con símbolo y texto, series con trazo distinto, IRH con estado real (antes rojo fijo).
- Texto alternativo en todas las imágenes, objetivos táctiles de 48 dp, 26 contrastes de texto corregidos, texto del reloj de 5–9 sp a mínimo 12 sp; `scripts/audit_a11y.py`.
- Informe: `docs/ACCESIBILIDAD.md`.

## 5. Licencias y marcas
- Sustituido `org.json:json` (JSON License, no libre) por Gson (Apache-2.0).
- `THIRD_PARTY_NOTICES.md`, `docs/LICENCIAS.md` (hallazgos: sin LICENSE, imágenes sin procedencia, nombre y marcas).

## 6. Pruebas añadidas o ejecutadas
- Pruebas unitarias: `MarkdownLiteTest`, `ConsentAndPaletteTest` (17 con `SecurityUtilsTest`, todas pasan con arnés local).
- Reglas de Firebase: 20 escenarios en el evaluador local.
- CI: nuevo trabajo `privacy-accessibility-audit` en `verify.yml`.

## 7. Pendiente que depende de ti
Ver `docs/LEGAL.md` sección 6 y `docs/LICENCIAS.md` sección 1: rellenar marcadores, elegir licencia, documentar autoría de imágenes, revisar los textos con una persona abogada, probar con TalkBack y revisar tráfico de red real.
