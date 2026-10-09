# Guía legal y de cumplimiento (para quien opera el proyecto)

Los documentos de `legal/` son **plantillas técnicas redactadas a partir de lo que el código realmente hace**. No son asesoría legal. Parten de México (LFPDPPP vigente desde el 21 de marzo de 2025; autoridad: Secretaría Anticorrupción y Buen Gobierno) e incluyen un anexo para el RGPD. Si operas desde otro país, o vas a ofrecer el servicio a terceros de forma comercial, haz que una persona abogada especialista los revise antes de publicar.

## 1. Antes de publicar: rellena los marcadores

Los documentos usan marcadores `{{...}}`. La compilación de publicación falla mientras queden sin rellenar:

```bash
./gradlew :app:assembleRelease -Pherbal.requireLegal=true
python scripts/audit_privacy.py --strict
```

{{RESPONSABLE_NOMBRE}}: nombre o razón social de quien decide sobre los datos.
{{RESPONSABLE_DOMICILIO}}: domicilio para notificaciones (el aviso de privacidad lo exige).
{{CONTACTO_PRIVACIDAD}}: correo donde se atienden solicitudes de derechos ARCO.
{{FECHA_VIGENCIA}}: fecha a partir de la cual rige esta versión.
{{REGION_FIREBASE}}: región de tu Realtime Database (Consola de Firebase, Realtime Database, datos).
{{JURISDICCION}}: ley y tribunales aplicables (ciudad y estado o país).

Para cambiar el texto de cualquier documento: edítalo, sube su "Versión" **y** `LEGAL_VERSION` en `AppConstants.kt` a la vez (la compilación comprueba que coincidan). Al subir la versión, la app pide aceptar de nuevo.

## 2. Qué hace la app (y debe seguir siendo cierto)

- Pide aceptar Aviso de Privacidad y Términos en la primera pantalla, con casilla vacía, y no deja usar la app sin aceptar.
- Pide el consentimiento de IA por separado, antes del primer uso, y se retira desde Menú, Privacidad y accesibilidad.
- Permite exportar datos locales, borrarlos y eliminar la cuenta desde la app.
- No usa analítica, publicidad ni cookies. `scripts/audit_privacy.py` lo hace cumplir en CI: si alguien añade uno, hay que actualizar primero los documentos.

## 3. Atender derechos ARCO (procedimiento interno)

1. Recibes la solicitud en {{CONTACTO_PRIVACIDAD}}; verifica la identidad pidiendo que se escriba desde el correo de la cuenta.
2. Acceso o portabilidad: la persona puede exportar sus datos locales desde la app. Los datos de la nube son su historial de riego y su rol; extrae `/watering_history` y `/roles/<uid>` desde la consola.
3. Cancelación: la app elimina el historial de riego y la cuenta. **Queda pendiente borrar manualmente `/roles/<uid>`** desde la consola de Firebase (solo contiene UID y rol; las reglas impiden que un cliente lo haga).
4. Responde por escrito dentro de los plazos de la ley vigente y conserva constancia.

## 4. Si hay una vulneración de seguridad

Contener el acceso (desactiva cuentas, rota claves), evaluar qué datos y a quién afectó, avisar a las personas afectadas cuando afecte significativamente sus derechos, y documentar lo ocurrido. Las reglas y roles de `firebase/` limitan el alcance de un acceso indebido.

## 5. Publicación en Google Play (borrador de la sección "Seguridad de los datos")

Verifica cada respuesta contra la política vigente de Play antes de enviarla.

- Información personal, correo electrónico: se recopila; obligatoria; finalidad: administración de la cuenta y funcionalidad de la app; no se comparte con terceros distintos de los proveedores que procesan los datos en tu nombre (Google/Firebase).
- Identificadores de usuario (UID): se recopilan; mismas finalidades.
- Fotos (opcional): se envían para análisis de IA con consentimiento; no se almacenan en el servidor del proyecto.
- Otro contenido generado por la persona (texto del asistente): igual que fotos; opcional.
- Identificadores de dispositivo (ID de instalación de Firebase): los trata Google para operar sus servicios.
- Ubicación, contactos, mensajes, salud, finanzas, audio: no se recopilan.
- Rendimiento de la app y diagnósticos (fallos): no se recopilan.
- Prácticas de seguridad: datos cifrados en tránsito, sí; se puede solicitar la eliminación de los datos, sí (en la app y por correo).
- Necesitas una URL pública con la política de privacidad. Puedes publicar `legal/AVISO_DE_PRIVACIDAD.md` en GitHub Pages; si esa página usa cookies no esenciales, actualiza la Política de Cookies antes.

## 6. Otros pendientes que no se pueden resolver en el código

- Elegir y añadir una licencia para el código (`docs/LICENCIAS.md`, sección 1.1).
- Documentar la autoría de la ilustración y de las capturas (`docs/LICENCIAS.md`, sección 1.2).
- Confirmar la edad mínima (aquí 18 años) y la jurisdicción aplicable.
- Si el servicio se ofrece a la Unión Europea: valorar un representante en la UE y un acuerdo de tratamiento de datos con Google (sus condiciones estándar suelen bastar).
- Probar con TalkBack y revisar el tráfico de red real (`docs/ACCESIBILIDAD.md` y `docs/AUDITORIA_PRIVACIDAD_TERCEROS.md`).
