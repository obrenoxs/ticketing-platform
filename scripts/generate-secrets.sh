#!/usr/bin/env bash
# Gera os segredos fixos e os acrescenta ao .env.
# Nunca sobrescreve um segredo que já existe (trocar a chave de CPF invalida os dados gravados).
set -euo pipefail

ENV_FILE="${1:-.env}"
touch "$ENV_FILE"

for name in CPF_ENCRYPTION_KEY CPF_HMAC_KEY JWT_SIGNING_KEY; do
  if grep -q "^${name}=" "$ENV_FILE"; then
    echo "skip: ${name} already set in ${ENV_FILE}"
  else
    echo "${name}=$(openssl rand -base64 32)" >> "$ENV_FILE"
    echo "created: ${name}"
  fi
done