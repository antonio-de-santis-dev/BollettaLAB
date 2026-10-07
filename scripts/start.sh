#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v docker >/dev/null || { echo "Installa Docker Engine e il plugin Compose, poi riprova." >&2; exit 1; }
command -v openssl >/dev/null || { echo "Installa openssl per generare la configurazione." >&2; exit 1; }
umask 077
if [ ! -f .env ]; then
 cp .env.example .env
fi
changed=false
for key in ADMIN_PASSWORD INTERNAL_KEY MYSQL_ROOT_PASSWORD UTENTI_DB_PASSWORD PAGAMENTO_DB_PASSWORD LUCE_DB_PASSWORD BUSINESS_DB_PASSWORD GAS_DB_PASSWORD; do
 current=$(sed -n "s/^${key}=//p" .env | tail -1)
 if [[ -z "$current" || "$current" == '""' || "$current" == "''" ]]; then
  secret=$(openssl rand -hex 32)
  if grep -q "^${key}=" .env; then
   sed -i "s/^${key}=.*/${key}=${secret}/" .env
  else
   printf '%s=%s\n' "$key" "$secret" >> .env
  fi
  changed=true
 fi
done
chmod 600 .env
if [ "$changed" = true ]; then
 echo "Configurazione completata. Credenziali amministratore nelle voci ADMIN_EMAIL e ADMIN_PASSWORD del file .env."
fi
docker compose up --build -d --wait
