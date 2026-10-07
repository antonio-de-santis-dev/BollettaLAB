# Stato della consegna

## Implementato

Cinque microservizi Spring Boot (Utenti, Pagamento, Luce, Luce Business, Gas), libreria comune, frontend React unico, cinque database MySQL con utenze separate, proxy Nginx e avvio Docker sulla porta 8091. Nessuna versione scaricabile Windows.

La Welcome, il login unico, la registrazione privata/impresa, l'attivazione dopo checkout, i due agenti iniziali, il saldo condiviso, i pacchetti di prova, il popup a quota zero, storico, PDF e le interfacce titolare/admin sono implementati. Password BCrypt, sessioni opache revocabili, link monouso, controlli di autorizzazione sul server e isolamento delle query. Autore e dati dell'impresa sono salvati nelle entità e riportati nei PDF.

Le fonti ufficiali elettriche sono dati globali amministrati dall'admin; utenti e agenti le consultano. I dati privati dei simulatori e i parametri manuali gas appartengono all'account/impresa. I motori originali e le relative regressioni sono conservati. Il gas non introduce feed automatici fiscali non verificati.

Una simulazione prima prenota la quota, poi salva risultato e ricevuta nella stessa transazione locale, infine conferma il consumo. Le ricevute pendenti vengono riconciliate. Dopo un'interruzione, il servizio Pagamento verifica il motore prima di liberare una prenotazione: non rimborsa alla cieca se il motore è indisponibile o ancora al lavoro. I retry mantengono la stessa chiave finché non arriva una risposta confermata.

## Verifiche eseguite localmente

- 150 test Java: tre motori, API, snapshot/PDF, utenti, quote concorrenti, autorizzazioni, UTF-8 del risultato ripetuto e isolamento account/agenti.
- 54 test frontend e build TypeScript/Vite.
- Cinque servizi avviati insieme per prove HTTP: tre simulatori, importo luce 77 €, gas 392,61 €, PDF, quota condivisa, retry idempotenti, limite due agenti, isolamento e revoca sessioni.
- Due prove Chromium: Welcome/registrazione/login/navigazione mobile e impresa/agenti/PDF/quota zero/storico.

Le prove Java e HTTP locali usano **H2 in modalità MySQL**. La verifica separata su **MySQL 8.4 reale**, con i cinque servizi e il frontend in Docker, ha superato avvio, regressioni HTTP e le due prove Chromium. Anche i job Java e React sono verdi: [esecuzione CI verificata](https://github.com/antonio-de-santis-dev/BollettaLAB/actions/runs/37640156987), commit `232fbffb63beef008ff7d472404b803f75d08f83`. Docker non è disponibile nell’ambiente locale: la prova Docker/MySQL è stata eseguita dal runner GitHub Actions.

## Prima dell'uso commerciale

Stripe è volutamente l'ultima fase, ancora da implementare/configurare insieme. Il checkout funziona solo come provider di prova in `dev`/`test`; negli altri profili non assegna piani e non incassa denaro. Prezzi e termini commerciali non sono definiti.

Le 80 simulazioni scadono alla fine del periodo mensile: la nuova assegnazione richiede un nuovo pagamento. In sviluppo si conferma manualmente il rinnovo dopo la scadenza; non esiste un rinnovo gratuito automatico. I crediti extra e i posti agente aggiuntivi restano nell'account durante le prove, con termini da confermare prima della fase Stripe. Lo storico si conserva anche a quota esaurita.

Prima di esporre l'app pubblicamente: ambiente senza provider di prova, Stripe e webhook verificati, HTTPS con cookie Secure, SMTP per verifica/inviti/reset, backup, condizioni d'uso/privacy e politica di conservazione. I log operativi completi sono disponibili in Docker; il pannello admin mostra attività degli account e movimenti delle quote.

Le migrazioni sono per una nuova installazione MySQL. I database PostgreSQL precedenti non sono stati forniti e non sono stati importati: la migrazione dei dati reali richiede una lavorazione controllata distinta. Non sono inclusi dati o credenziali reali.
