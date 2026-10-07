# BollettaLAB Windows portabile

Branch dedicato: `vaio-windos`. Riprende il metodo della versione Windows di ProgettoLuce: runtime Java incluso, launcher PowerShell e comandi Avvia/Ferma. Qui il pacchetto contiene i cinque microservizi, React compilato, MySQL Community 8.4.11 x64 e un ingresso HTTP Java locale che sostituisce Nginx solo nel pacchetto Windows.

## Scaricare e avviare

In GitHub apri **Actions → BollettaLAB Windows portable**, seleziona l'ultima esecuzione verde di `vaio-windos`, quindi l'artifact **BollettaLAB-Windows-x64**. GitHub richiede accesso per scaricare gli artifact. Dentro trovi `BollettaLAB-Windows-x64.zip` e `SHA256.txt`: è lo ZIP interno che puoi girare al collega. Gli artifact durano 30 giorni; si possono rigenerare con Run workflow sul branch.

1. Estrai tutto lo ZIP in una cartella locale scrivibile, ad esempio `Documenti\BollettaLAB`.
2. Esegui `Avvia BollettaLAB.cmd`. Attendi l'inizializzazione del database e dei servizi; il browser si apre su **http://127.0.0.1:8091**.
3. `Credenziali Admin.cmd` mostra email e password iniziale generate sul PC. Accedi normalmente, oppure registra un privato/impresa.
4. Usa `Ferma BollettaLAB.cmd` prima di spegnere o copiare i dati. Chiudere il browser non arresta i processi.

Il destinatario non deve installare JDK/Java, Docker, MySQL, Node o Maven. Nessun servizio di Windows è registrato; non sono richiesti privilegi amministrativi dal launcher. Occorre Windows 10/11 x64 aggiornato e un browser moderno. Consigliati almeno 8 GB RAM e 4 GB liberi; il consumo effettivo varia con uso e dati. Le DLL MSVC ridistribuibili sono collocate accanto agli eseguibili, non installate globalmente.

## Dati e sicurezza locale

- MySQL ascolta su `127.0.0.1:33079`; backend su `127.0.0.1:8101–8105`; ingresso web su `127.0.0.1:8091`.
- Il launcher genera password casuali, chiave interna e credenziali admin in `data/install.json`. Ogni dominio usa database e utenza separati. Questi dati non sono nello ZIP distribuito.
- Il gateway respinge Host/Origin estranei, non inoltra chiavi interne dal browser e rende irraggiungibili i percorsi `/internal`. Gli endpoint di arresto dei servizi sono condizionali e protetti dalla chiave interna.
- Il doppio avvio della stessa cartella riutilizza l'istanza; una seconda copia trova le porte occupate. Prima dell'arresto ogni PID viene verificato con percorso eseguibile e istante di creazione, per non terminare processi estranei.
- I servizi vengono arrestati prima di MySQL. Il database viene chiuso con mysqladmin, senza arresto forzato. Per Java un processo bloccato può essere terminato dal launcher solo dopo verifica della sua proprietà.
- Il pacchetto usa il profilo `dev`: pagamenti simulati, nessun addebito, link di verifica/invito/reset disponibili per prove. Non è un server per clienti remoti né una distribuzione commerciale pronta per internet.

L'account e i dati Docker/server sono separati: non sono copiati automaticamente nel pacchetto. La password mostrata è quella iniziale; dopo il reset serve la password nuova.

## Backup e aggiornamenti

Ferma l'app e copia l'intera cartella `data`, che include database e configurazione. Non copiare file MySQL mentre il server è acceso. Conserva il backup con accesso limitato: contiene credenziali e dati personali.

Per aggiornare, ferma la vecchia copia, estrai il nuovo ZIP in una cartella nuova e trasferisci `data`. Le migrazioni Flyway verranno eseguite al nuovo avvio; crea sempre un backup prima. Non sovrascrivere `install.json` e non eliminare il database per risolvere errori. I log sono in `logs`.

## Costruzione e verifica

Solo sul **builder** servono Git, JDK 17, Maven, Node 22 e le DLL x64 ridistribuibili di Visual Studio 2022. Il workflow usa un runner Windows 2022 e scarica MySQL da `cdn.mysql.com`, con versione fissata e SHA256 annotato in `VERSIONE.txt`. Le licenze del runtime e MySQL rimangono incluse nelle loro cartelle; `licenses/COMPONENTI.txt` elenca componenti e sorgenti MySQL corrispondenti.

```powershell
mvn -B -DskipTests package
cd frontend
npm ci
npm run build
cd ..
.\scripts\package-windows.ps1
```

La CI estrae lo ZIP in un percorso con spazi, avvia il pacchetto con PATH senza tool di sviluppo, verifica doppio avvio e accesso admin, esegue le regressioni HTTP dei tre simulatori e due prove browser, poi verifica arresto, riavvio e persistenza di un account. Il runner possiede strumenti di sviluppo per **costruire e testare**; il launcher usa esclusivamente eseguibili inclusi e PowerShell di Windows.

I test gateway verificano percorsi SPA, JavaScript, Cookie, PDF, UTF-8, idempotenza, limite dimensione richieste, blocco delle origini estranee e protezione dell'arresto. Lo ZIP viene pubblicato solo se le prove del pacchetto passano. Il test su runner non sostituisce una prova su ogni modello di VAIO/Windows del destinatario.

Per le simulazioni usa `DATI-DI-TEST.md`, incluso nello ZIP, o [TEST_MANUALI.md](TEST_MANUALI.md).

## Risultato verificato

Il pacchetto del commit `c65ff919cdfa99623e6ae2042b634aef787c2022` ha superato le prove Windows: build, sei test della libreria comune, estrazione in percorso con spazi, avvio con PATH senza tool, doppio avvio, login admin, regressioni HTTP sui tre simulatori, due prove Chromium, arresto e riavvio con account conservato. [Esecuzione Windows e artifact ZIP](https://github.com/antonio-de-santis-dev/BollettaLAB/actions/runs/37648194253). Le successive modifiche alla documentazione non cambiano il programma incluso in questo ZIP.
