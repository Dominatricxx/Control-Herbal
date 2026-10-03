# Broker MQTT (opcional)

El broker **no arranca sin un archivo de contraseñas**. Créalo así (una sola vez):

```bash
touch deploy/mosquitto/passwd
docker run --rm -v "$PWD/deploy/mosquitto:/mosquitto/config" eclipse-mosquitto:2 \
  mosquitto_passwd -b /mosquitto/config/passwd herbal 'UNA-CONTRASEÑA-LARGA'
```

`deploy/mosquitto/passwd` está en `.gitignore`. Los puertos se publican solo en `127.0.0.1`.
Para usarlo desde otros equipos, añade TLS y un listener 8883 antes de exponerlo.
