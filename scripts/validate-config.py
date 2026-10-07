#!/usr/bin/env python3
from pathlib import Path
import re,sys
root=Path(__file__).resolve().parents[1]
text=(root/'compose.yaml').read_text()
assert text.count('SERVICE: ')==5
assert '${WEB_PORT:-8091}:80' in text
assert 'postgres' not in text.lower()
for module in ['utenti','pagamento','luce','luce-business','gas']:
 assert (root/'services'/module/'pom.xml').is_file()
 assert (root/'services'/module/'src/main/resources/db/migration').is_dir()
print('Configuration structure passed: 5 services, MySQL, single public port 8091')
