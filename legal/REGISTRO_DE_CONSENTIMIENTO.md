# Registro Maestro de Consentimientos y Permisos

Versión 2026.11 · Control Herbal

Este documento registra los consentimientos otorgados por la persona usuaria, su alcance legal, finalidades y mecanismos de revocación dentro de la plataforma Control Herbal.

---

## 1. Consentimiento Legal Obligatorio (Aviso de Privacidad y Términos)

- **Finalidad**: Permitir el uso básico de la aplicación, almacenamiento local de sensores, diagnóstico botánico local y autenticación segura.
- **Momento de otorgamiento**: Se solicita al abrir la aplicación por primera vez o ante actualizaciones de versión de los documentos legales.
- **Mecanismo de aceptación**: Selección explícita de casilla no premarcada con acceso previo a la lectura completa del documento.
- **Datos asociados**: Versión del documento aceptada, marca temporal (timestamp ISO 8601) y firma hash guardada exclusivamente en el almacenamiento privado del dispositivo.
- **Revocación**: Eliminación de la cuenta o borrado total de datos locales desde el menú de Privacidad.

---

## 2. Consentimiento para Servicios de Inteligencia Artificial (Google / Firebase AI Logic)

- **Finalidad**: Procesamiento de imágenes botánicas mediante cámara/galería y consultas conversacionales de asistencia botánica utilizando modelos generativos (Gemini en Google Cloud vía Firebase AI Logic).
- **Datos compartidos**: Exclusivamente fotografías del espécimen, texto de la consulta e indicador del tipo de planta. **Se excluyen expresamente**: nombre personalizado dado por el usuario, correo electrónico, identificadores únicos de hardware o geolocalización.
- **Momento de otorgamiento**: Se solicita de forma independiente antes de ejecutar por primera vez una función basada en IA.
- **Carácter**: Estrictamente voluntario. Si se rechaza, la aplicación mantiene su funcionamiento normal para monitoreo de sensores y diario de cuidados sin IA.
- **Revocación**: Menú de navegación -> **Perfil** -> **Privacidad e IA** -> Desactivar el interruptor *"Enviar fotos y texto a la IA (Google)"*. Tras revocarse, la aplicación volverá a solicitar permiso explícito si el usuario intenta utilizar la IA de nuevo.

---

## 3. Registro de Permisos de Dispositivo

- **Cámara (`android.permission.CAMERA`)**: Utilizada únicamente para captura directa de fotografías de especímenes botánicos. No se realiza grabación de video ni transmisión en segundo plano.
- **Notificaciones (`android.permission.POST_NOTIFICATIONS`)**: Utilizada exclusivamente para alertas de riesgo (IRH crítico) y recordatorios de tareas de cuidado del calendario.

---

## 4. Garantías y Derechos ARCO

El usuario mantiene en todo momento el control de sus consentimientos. Conforme a los principios de minimización de datos y responsabilidad proactiva, no se realiza perfilado comercial, venta de datos ni rastreo entre aplicaciones de terceros.
