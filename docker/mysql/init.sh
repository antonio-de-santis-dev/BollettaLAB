#!/bin/bash
set -eu
# Generated passwords use hex characters, avoiding SQL quoting ambiguity.
for key in UTENTI_DB_PASSWORD PAGAMENTO_DB_PASSWORD LUCE_DB_PASSWORD BUSINESS_DB_PASSWORD GAS_DB_PASSWORD; do
 value="${!key}"
 [[ "$value" =~ ^[a-zA-Z0-9_-]{24,128}$ ]] || { echo "Database password must use 24-128 letters, digits, underscore or dash" >&2; exit 1; }
done
mysql --user=root --password="$MYSQL_ROOT_PASSWORD" <<SQL
CREATE DATABASE utenti CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE pagamento CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE luce CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE luce_business CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE gas CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'utenti'@'%' IDENTIFIED BY '$UTENTI_DB_PASSWORD'; GRANT ALL ON utenti.* TO 'utenti'@'%';
CREATE USER 'pagamento'@'%' IDENTIFIED BY '$PAGAMENTO_DB_PASSWORD'; GRANT ALL ON pagamento.* TO 'pagamento'@'%';
CREATE USER 'luce'@'%' IDENTIFIED BY '$LUCE_DB_PASSWORD'; GRANT ALL ON luce.* TO 'luce'@'%';
CREATE USER 'luce_business'@'%' IDENTIFIED BY '$BUSINESS_DB_PASSWORD'; GRANT ALL ON luce_business.* TO 'luce_business'@'%';
CREATE USER 'gas'@'%' IDENTIFIED BY '$GAS_DB_PASSWORD'; GRANT ALL ON gas.* TO 'gas'@'%';
SQL
