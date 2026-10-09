#!/usr/bin/env bash
# Alertas por correo (Cloud Logging + Cloud Monitoring) sobre los eventos de seguridad de las Cloud Functions.
#
#   ./scripts/setup-alerts.sh <PROJECT_ID> <correo@dominio.com>
#
# Crea (si no existen) dos métricas basadas en logs y dos políticas de alerta:
#   1. mfa_reset_via_backup_code -> aviso INMEDIATO: alguien restableció el 2FA con un código de respaldo.
#   2. >= 10 eventos backup_redeem_failed / _blocked / _challenge en 10 min -> posible fuerza bruta.
# Requiere gcloud autenticado con permisos de Logging y Monitoring.
set -euo pipefail

PROJECT="${1:?Uso: $0 <PROJECT_ID> <correo>}"
EMAIL="${2:?Uso: $0 <PROJECT_ID> <correo>}"
[[ "$EMAIL" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || { echo "Correo no válido" >&2; exit 2; }
command -v gcloud >/dev/null || { echo "Falta gcloud" >&2; exit 2; }

BASE='resource.type="cloud_run_revision"'   # las Cloud Functions de 2.ª generación se ejecutan sobre Cloud Run

ensure_metric() { # nombre, descripción, filtro
  if gcloud logging metrics describe "$1" --project "$PROJECT" >/dev/null 2>&1; then
    echo "Métrica $1: ya existe"
  else
    gcloud logging metrics create "$1" --project "$PROJECT" --description "$2" --log-filter "$3"
  fi
}

ensure_metric herbal_mfa_reset "2FA restablecido con código de respaldo" \
  "$BASE AND jsonPayload.message=\"mfa_reset_via_backup_code\""
ensure_metric herbal_backup_attack "Fallos, bloqueos y retos al canjear códigos de respaldo" \
  "$BASE AND (jsonPayload.message=\"backup_redeem_failed\" OR jsonPayload.message=\"backup_redeem_blocked\" OR jsonPayload.message=\"backup_redeem_challenge\")"

CHANNEL="$(gcloud beta monitoring channels list --project "$PROJECT" \
  --filter="type=email AND labels.email_address=$EMAIL" --format='value(name)' | head -n1)"
if [[ -z "$CHANNEL" ]]; then
  CHANNEL="$(gcloud beta monitoring channels create --project "$PROJECT" --display-name "Control Herbal seguridad" \
    --type email --channel-labels "email_address=$EMAIL" --format='value(name)')"
fi
echo "Canal de notificación: $CHANNEL"

policy() { # nombre visible, métrica, umbral, ventana(s)
  cat <<JSON
{
  "displayName": "$1",
  "combiner": "OR",
  "conditions": [{
    "displayName": "$1",
    "conditionThreshold": {
      "filter": "metric.type=\"logging.googleapis.com/user/$2\" AND resource.type=\"cloud_run_revision\"",
      "comparison": "COMPARISON_GT",
      "thresholdValue": $3,
      "duration": "0s",
      "aggregations": [{"alignmentPeriod": "${4}s", "perSeriesAligner": "ALIGN_SUM"}],
      "trigger": {"count": 1}
    }
  }],
  "notificationChannels": ["$CHANNEL"],
  "alertStrategy": {"autoClose": "1800s"}
}
JSON
}

ensure_policy() {
  if gcloud alpha monitoring policies list --project "$PROJECT" --filter="displayName=\"$1\"" --format='value(name)' | grep -q .; then
    echo "Política '$1': ya existe"
  else
    f="$(mktemp)"; policy "$1" "$2" "$3" "$4" > "$f"
    gcloud alpha monitoring policies create --project "$PROJECT" --policy-from-file "$f"
    rm -f "$f"
  fi
}

ensure_policy "Control Herbal: 2FA restablecido con código de respaldo" herbal_mfa_reset 0 300
ensure_policy "Control Herbal: posible fuerza bruta en recuperación 2FA" herbal_backup_attack 9 600
echo "Listo. Comprueba las métricas en Logging > Métricas basadas en registros."
