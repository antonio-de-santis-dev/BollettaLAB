# BollettaLAB

Web app unica con React, Java 17 / Spring Boot e **MySQL**. Integra Progetto Luce, Progetto Luce Business e Progetto Gas, con i motori e le sezioni dei programmi originali.

## Avvio con Docker

Prerequisiti: Docker Engine con Compose v2 e `openssl`. Prima esecuzione:

```bash
git clone https://github.com/antonio-de-santis-dev/BollettaLAB.git
cd BollettaLAB
./scripts/start.sh
```

Apri **http://localhost:8091**. Lo script genera automaticamente `.env` con password casuali e chiave interna: non occorre inventare un token amministratore. Le credenziali dell'amministratore sono nelle voci `ADMIN_EMAIL` e `ADMIN_PASSWORD` del file `.env`. La porta si cambia con `WEB_PORT`; aggiorna anche `PUBLIC_URL`.

Dopo il primo avvio:

```bash
git pull --ff-only origin main
docker compose up --build -d --wait
docker compose logs --tail=100 utenti pagamento luce luce-business gas
```

I cinque backend e MySQL non espongono porte sull'host. Nginx serve React e instrada le API sullo stesso dominio. Ogni servizio ha il proprio database e utente MySQL; il volume `mysql_data` conserva i dati tra riavvii. Cambiare le password del file `.env` dopo l'inizializzazione richiede aggiornare anche gli utenti MySQL. Non eliminare il volume per aggiornare l'applicazione.

## Funzionamento

- Registrazione privata oppure impresa; login unico e area scelta dalle credenziali.
- Titolare + **due agenti inclusi**, invitati con link e password personale.
- **80 simulazioni per mese pagato**, saldo condiviso dai tre simulatori e, per l'impresa, da titolare e agenti.
- Un confronto salvato consuma una simulazione. Errori non consumano crediti; retry della stessa richiesta non consumano due volte.
- A zero compare il popup per modificare il piano o acquistare simulazioni. Storico e PDF restano disponibili.
- Gli agenti vedono i propri confronti; il titolare quelli dell'impresa. Autore e dati dell'impresa sono conservati e attribuiti nei PDF.
- Admin: blocco/sblocco, eliminazione account, variazione quote con motivazione, reset delle password e attività recenti. Le password non sono leggibili.
- I simulatori conservano confronto, bollette, offerte, parametri, fonti, impostazioni PDF e storico. Il modulo business conserva anche il motore avanzato e i relativi profili.

**Pagamenti:** il profilo Docker predefinito `dev` usa acquisti simulati, chiaramente indicati nell'interfaccia. Non ci sono addebiti. Stripe viene configurato insieme nell'ultima fase; i checkout reali restano disattivati. Non esporre il profilo `dev` al pubblico. Vedi [docs/STRIPE.md](docs/STRIPE.md).

## Documentazione

- [Specifiche](docs/SPECIFICA.md)
- [Architettura e decisioni](docs/ARCHITETTURA.md)
- [Skill applicate](docs/SKILL.md) e [sistema grafico](docs/DESIGN.md)
- [Prove manuali](docs/TEST_MANUALI.md)
- [Stato e limiti della consegna](docs/CONSEGNA.md)

## Sviluppo e verifiche

```bash
mvn -B verify
cd frontend
npm ci
npm run build
npm test
```

I test Java usano H2 in modalità MySQL per regressioni dei motori, isolamento e concorrenza; la CI esegue anche lo stack Docker con MySQL 8.4 e prove HTTP/browser. Per ripetere i test sull'applicazione in Docker:

```bash
python3 scripts/smoke.py
cd frontend
APP_URL=http://localhost:8091 TEST_ADMIN_EMAIL=... TEST_ADMIN_PASSWORD=... npm run test:e2e
```

Le migrazioni Flyway definiscono il nuovo schema MySQL. Non importano automaticamente i dati di database PostgreSQL esistenti: occorre pianificare un'importazione controllata dei dati reali, con verifica degli snapshot e assegnazione degli account. Nessuna credenziale o bolletta reale è inclusa nel repository.
