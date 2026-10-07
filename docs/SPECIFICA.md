# Piattaforma unica per simulazioni energetiche

## Specifica funzionale e tecnica per lo sviluppo

**Versione:** 1.0 — 7 ottobre 2026  
**Stato:** documentazione di progetto; sviluppo della nuova piattaforma non ancora iniziato.  
**Destinatari:** proprietario del progetto, sviluppatori backend/frontend e assistenti di sviluppo.  
**Nome definitivo della piattaforma:** da scegliere.

Questo documento raccoglie le richieste dell’utente, le decisioni confermate e le proposte tecniche per realizzare una piattaforma unica a partire dai tre simulatori esistenti. Le proposte non costituiscono approvazioni implicite: i punti elencati come aperti devono essere risolti prima di implementare la relativa regola commerciale.

## 1. Obiettivo e vincoli

Realizzare una web application con frontend React, backend Java Spring Boot, database MySQL e architettura a microservizi. La piattaforma offrirà:

- simulazioni luce domestica;
- simulazioni luce per attività commerciali e imprese;
- simulazioni gas domestico;
- account privati, imprese e agenti;
- abbonamenti, quote mensili e acquisto di ulteriori simulazioni;
- storico delle simulazioni e download dei PDF;
- interfaccia amministrativa per la gestione della piattaforma.

La struttura funzionale e il linguaggio grafico dei programmi esistenti costituiscono il riferimento. La nuova applicazione deve essere avviabile con Docker. È stato richiesto successivamente un pacchetto Windows opzionale per prove locali senza JDK o Docker installati, sviluppato nel branch `vaio-windos`: vedere [WINDOWS_PORTABILE.md](WINDOWS_PORTABILE.md).

## 2. Decisioni confermate

| Argomento | Decisione |
| --- | --- |
| Backend | Java Spring Boot. |
| Frontend | React. |
| Database | MySQL; migrazione delle parti attualmente basate su PostgreSQL. |
| Architettura | Cinque microservizi di dominio: ProgettoLuce, ProgettoLuceBusiness, ProgettoGas, Utenti, Pagamento. |
| Account privato | Un account individuale. |
| Account impresa | Un account titolare con possibilità di creare account agenti. |
| Agenti inclusi nel piano base | **Due agenti oltre al titolare**, quindi tre account complessivi. Il valore “21” nell’ultimo messaggio è stato chiarito dall’utente scegliendo due agenti. |
| Agenti aggiuntivi | Da ottenere tramite pagamento di un supplemento. Prezzi e modalità da definire. |
| Quota base | **80 simulazioni per mese**. |
| Consumo | Ogni nuova simulazione decrementa il saldo di una unità. |
| Saldo zero | Blocco delle nuove simulazioni e popup per modifica piano o acquisto simulazioni. |
| Storico | Possibilità di riaprire le simulazioni effettuate. |
| Login | **Un’unica pagina email/password**, senza selettori visivi Privato/Impresa/Titolare/Agente/Admin. |
| Area dopo il login | Determinata dal backend in base al ruolo effettivo dell’account. |
| Attivazione | Accesso ai simulatori disattivato fino alla conferma del pagamento. |
| Pagamenti | **Stripe** come provider. |
| Configurazione Stripe | **Ultima fase**, da svolgere insieme all’utente con guida manuale. |
| Confronto | Download PDF nella posizione concordata nei simulatori; non introdurre CSV, JSON, Stampa o Duplica dati cliente. |
| Welcome | Pagina pubblica con presentazione accattivante, card ed effetti 3D e due pulsanti principali. |

## 3. Progetti di origine e riuso

Repository di riferimento:

- ProgettoLuce: https://github.com/antonio-de-santis-dev/ProgettoLuce
- ProgettoGas: https://github.com/antonio-de-santis-dev/ProgettoGas
- ProgettoLuceBusiness: branch `simulatore-business` della repository ProgettoLuce.

Per la luce domestica occorre confrontare `main` e `integrazioneAPI`: le funzionalità relative alle fonti ufficiali sono presenti nel branch dedicato e devono essere considerate nel trasferimento. Non usare la distribuzione Windows come base della nuova piattaforma.

### 3.1 Regole per il trasferimento

1. Verificare nuovamente branch, commit e stato dei file all’inizio dello sviluppo.
2. Riutilizzare le logiche di calcolo validate, senza riscriverle arbitrariamente.
3. Preservare unità, precisione, arrotondamenti e risultati dei casi di prova.
4. Integrare autorizzazioni, appartenenza dei dati e consumo dei crediti.
5. Uniformare navigazione, componenti e PDF mantenendo le differenze tra i tre domini.
6. Mantenere le repository di origine come riferimento; non modificarle durante il trasferimento senza un incarico specifico.

### 3.2 Aspetti da verificare e correggere

| Progetto | Aspetti emersi nell’analisi precedente |
| --- | --- |
| Luce domestica | Integrazione delle fonti ufficiali; regola di gestione dei totali negativi prodotti da rettifiche elevate; precisione coerente dei timestamp. |
| Luce Business | Presenza di due percorsi di calcolo e due renderizzazioni PDF: definire un percorso di utilizzo coerente e il rapporto tra le funzionalità dei due motori. |
| Gas | Gestione della transizione dei profili pubblicati allo stato archiviato e della validità temporale dei profili successivi. |
| Tutti | Aggiungere utenti, autorizzazioni, isolamento tra account, paginazione dello storico e consumo centralizzato delle simulazioni. |

Questi rilievi sono una base per la verifica del codice, non una dichiarazione che la nuova piattaforma abbia già risolto i problemi. Non ridurre le capacità del simulatore Business per uniformarlo a quello domestico.

## 4. Architettura proposta

Un unico frontend React comunica con un ingresso API comune. I servizi eseguono controlli di autorizzazione anche sulle proprie API: nascondere un pulsante nel frontend non protegge i dati.

| Servizio | Dati e responsabilità |
| --- | --- |
| ProgettoLuce | Bollette luce domestica, offerte, parametri, fonti del dominio, confronti, snapshot e impostazioni PDF. |
| ProgettoLuceBusiness | Bollette business, offerte, profili tariffari, confronti, snapshot e PDF business. |
| ProgettoGas | Bollette gas, offerte, profili, fonti del dominio, confronti, snapshot e PDF gas. |
| Utenti | Identità, password, sessioni, ruoli, imprese, appartenenze, inviti e stato degli account. |
| Pagamento | Catalogo piani/pacchetti, abbonamenti, ordini, transazioni, quote, registro crediti e posti agente acquistati. |

Il servizio Pagamento è l’unica autorità sul saldo dei crediti e sui diritti acquistati. Utenti gestisce le appartenenze degli agenti e verifica il limite dei posti concesso da Pagamento. Nessun simulatore mantiene un saldo autonomo modificabile.

API Gateway, reverse proxy, raccolta dei log e strumenti di monitoraggio sono infrastruttura. Non costituiscono ulteriori microservizi commerciali.

### 4.1 Comunicazione e coerenza

- API interne autenticate con timeout espliciti.
- Identificativo univoco delle operazioni per riconoscere i tentativi ripetuti.
- Eventi affidabili per pagamento confermato, cambio diritti e cancellazione account, quando necessari.
- Recupero delle operazioni interrotte tramite riconciliazione e, dove opportuno, outbox transazionale.
- Nessuna transazione SQL unica estesa ai cinque servizi.
- Nessuna dipendenza da letture o join diretti sui database di altri servizi.

Un broker di messaggi è una scelta tecnica da valutare; non deve essere aggiunto automaticamente se le esigenze iniziali possono essere soddisfatte da API e operazioni persistenti recuperabili.

## 5. MySQL e modello dei dati

Proposta iniziale: un’istanza MySQL con cinque database e cinque utenti applicativi separati:

| Database proposto | Proprietario |
| --- | --- |
| `progetto_luce` | ProgettoLuce |
| `progetto_luce_business` | ProgettoLuceBusiness |
| `progetto_gas` | ProgettoGas |
| `utenti` | Utenti |
| `pagamento` | Pagamento |

I nomi sono una convenzione proposta. Ogni servizio possiede le proprie migrazioni Flyway. Credenziali e segreti devono essere configurati fuori dal codice e non inseriti nella repository.

### 5.1 Identificativi e isolamento

Ogni dato privato dei simulatori deve essere collegato allo spazio del privato o dell’impresa, mediante un identificativo comune, per esempio `workspaceId`. Ogni simulazione conserva anche `createdByUserId`.

- Per il privato lo spazio appartiene al singolo account.
- Per l’impresa lo spazio è condiviso tra titolare e agenti autorizzati.
- Il backend ricava identità e appartenenza dalla sessione autenticata.
- Un identificativo passato dal client non autorizza l’accesso a un’altra impresa.
- Liste, ricerche, dettagli, aggiornamenti, cancellazioni e PDF devono applicare lo stesso isolamento.

I cataloghi ufficiali possono essere globali e consultabili dagli utenti autorizzati. Offerte, bollette e personalizzazioni dell’utente devono avere una visibilità esplicita. La modifica delle fonti ufficiali globali sarà riservata ai soggetti autorizzati.

### 5.2 Migrazione da PostgreSQL

La migrazione comprende:

- driver e configurazioni JPA;
- nuovi script di migrazione compatibili con MySQL;
- identificativi, sequenze e generazione delle chiavi;
- query native e costrutti specifici PostgreSQL;
- trattamento JSON, vincoli, indici e campi testuali;
- timestamp e precisione temporale, con salvataggio coerente in UTC;
- valori economici `DECIMAL` e calcolo Java `BigDecimal`;
- collazioni, unicità delle email e gestione consistente delle maiuscole;
- test di integrazione su un database MySQL reale.

Non modificare retroattivamente migrazioni già applicate nei vecchi programmi. Per la nuova piattaforma definire una nuova baseline. Un eventuale trasferimento di dati storici richiede una mappatura esplicita del proprietario: i dati senza utente nei prototipi non diventano automaticamente visibili a tutti.

## 6. Identità, ruoli e accesso

Ruoli iniziali proposti nel codice:

| Ruolo | Area |
| --- | --- |
| `PRIVATE_USER` | Area privato. |
| `COMPANY_OWNER` | Area titolare impresa. |
| `AGENT` | Area agente collegato all’impresa. |
| `PLATFORM_ADMIN` | Pannello amministratore della piattaforma. |

La pagina di login contiene email, password e recupero password. **Non deve chiedere all’utente in quale area entrare.** Dopo l’autenticazione il backend restituisce ruolo e stato dell’account; il frontend apre l’area corretta.

Il ruolo Admin non è selezionabile nella registrazione pubblica. L’agente nasce tramite invito del titolare, non tramite registrazione pubblica come impresa.

Proposta per la prima versione: ogni identità ha un ruolo operativo e, se agente, appartiene a una sola impresa. Un futuro utente con più imprese o ruoli richiede una specifica separata.

### 6.1 Stati da distinguere

- Email da verificare.
- Pagamento da completare.
- Account attivo.
- Account bloccato.
- Account in cancellazione o cancellato.
- Abbonamento valido, scaduto o con pagamento da regolarizzare.

Il blocco amministrativo e la scadenza commerciale non sono lo stesso evento. Un utente in attesa di pagamento può autenticarsi per completarlo, ma non usare i simulatori.

Le password non devono essere salvate in chiaro o in forma recuperabile. Il recupero accesso avviene con token temporanei monouso. Bloccare un utente deve invalidare o rendere inutilizzabili le sue sessioni per le operazioni protette.

## 7. Registrazione

### 7.1 Persona privata

Campi iniziali: nome, cognome, email, password e conferma password. Raccogliere i dati di fatturazione necessari nella fase appropriata, senza richiedere campi privi di uno scopo definito.

### 7.2 Impresa

Campi iniziali: ragione sociale, partita IVA, paese/indirizzo dell’impresa, nome e cognome del titolare, email e password. Codice fiscale, PEC e codice destinatario vanno previsti secondo il sistema di fatturazione che sarà adottato.

### 7.3 Percorso

1. Scelta privata/impresa nella registrazione.
2. Compilazione dei campi pertinenti.
3. Verifica email, proposta tecnica.
4. Scelta del piano.
5. Creazione di un ordine dal backend.
6. Pagamento.
7. Conferma verificata lato server.
8. Attivazione dell’accesso ai simulatori e assegnazione della quota.

Importo, valuta e prodotto acquistato sono stabiliti dal backend. Il browser non può decidere il prezzo o il numero di crediti da assegnare.

## 8. Imprese e agenti

Il piano base consente **un titolare e due agenti inclusi**. Il titolare può eseguire simulazioni e non occupa uno dei due posti agente.

Funzioni richieste per il titolare:

- invitare e registrare nuovi agenti entro il limite disponibile;
- vedere agenti attivi, invitati e bloccati;
- bloccare e riattivare gli agenti;
- vedere quante simulazioni ha eseguito ogni agente;
- consultare lo storico aziendale e filtrarlo per agente e simulatore;
- eseguire simulazioni;
- gestire il piano e acquistare capacità aggiuntiva.

Proposta: gli inviti pendenti riservano un posto fino alla scadenza o revoca. Un agente bloccato mantiene il proprio posto fino alla rimozione dell’appartenenza. Definire questa politica prima dello sviluppo per evitare inviti illimitati o conteggi ambigui.

Ogni agente sceglie la propria password. La disattivazione dell’agente non elimina lo storico e la rimozione dell’appartenenza non deve rendere le simulazioni aziendali inaccessibili al titolare.

L’acquisto di ulteriori posti aggiorna il limite solo dopo conferma del pagamento. Va definito se il supplemento è mensile e cosa accade ai posti aggiuntivi quando viene annullato.

## 9. Simulazioni e credito mensile

### 9.1 Quota confermata

Il piano base include **80 simulazioni mensili**. Ogni nuova simulazione completata consuma un credito.

Proposte da confermare:

- il saldo è unico per i tre simulatori;
- per l’impresa le 80 simulazioni sono condivise tra titolare e agenti;
- il mese coincide con il periodo di abbonamento, non necessariamente con il mese di calendario;
- i crediti mensili non usati non si accumulano;
- i crediti acquistati separatamente hanno una politica distinta, ancora da scegliere.

Esempio della proposta di saldo condiviso: 80 iniziali, una simulazione del titolare, due dell’agente A e una dell’agente B lasciano 76 crediti alla stessa impresa.

### 9.2 Operazioni che consumano crediti

| Operazione | Consumo proposto |
| --- | --- |
| Preparazione o salvataggio di bollette, offerte e bozze | 0 |
| Nuovo confronto completato e salvato | 1 |
| Riapertura dello storico | 0 |
| Nuovo download PDF dello stesso risultato | 0 |
| Nuovo calcolo con dati modificati | 1 |
| Errore di validazione o calcolo non riuscito | 0 |
| Ripetizione tecnica della medesima richiesta | Nessun secondo addebito |

L’unità proposta è un confronto salvato, non un singolo mese della bolletta o una singola riga di costo. Se in futuro un confronto comprende molte offerte, definire esplicitamente il consumo prima di introdurre tale funzione.

### 9.3 Prenotazione e completamento

1. Verificare account, appartenenza e diritto di accesso.
2. Validare la richiesta.
3. Prenotare atomicamente un credito con identificativo univoco.
4. Calcolare e salvare la simulazione nel servizio competente.
5. Confermare il consumo nel registro crediti.
6. Pubblicare il risultato come completato.

In caso di fallimento certo, rilasciare il credito prenotato. Un timeout con esito sconosciuto richiede riconciliazione: non rimborsare alla cieca se il risultato può essere già stato salvato. Le operazioni interrotte devono poter essere recuperate senza duplicare risultati o consumi.

Con un solo credito disponibile e due richieste contemporanee, deve passare una sola prenotazione.

### 9.4 Saldo zero e rinnovo

Il backend blocca nuovi confronti a saldo zero. Il frontend mostra il popup con “Modifica piano” e “Compra simulazioni”. Riapertura e PDF dei risultati già pagati non devono consumare ulteriori crediti.

Al rinnovo la quota viene assegnata una sola volta dopo la conferma del pagamento del periodo. Un cambio di data o una notifica ripetuta non deve generare crediti gratuiti o duplicati. Politiche di accesso allo storico dopo scadenza dell’abbonamento e di utilizzo dei crediti extra senza abbonamento attivo sono da confermare.

## 10. Storico e PDF

Lo storico deve essere paginato e filtrabile per simulatore, data e autore nei limiti delle autorizzazioni.

Ogni simulazione completata conserva uno snapshot dei dati utilizzati, del risultato, della versione del motore e dei riferimenti ai profili tariffari. Le successive modifiche delle offerte non devono alterare i risultati già salvati.

Per le simulazioni aziendali conservare anche i dati dell’impresa e dell’agente rilevanti al momento della generazione. Il PDF deve riprodurre il risultato storico, non ricalcolarlo automaticamente con tariffe aggiornate.

Uniformare le impostazioni PDF dei percorsi Business. Conservare le sezioni e le personalizzazioni concordate nei programmi esistenti. Un nuovo calcolo genera un nuovo risultato; non sovrascrive silenziosamente lo storico precedente.

La modifica o cancellazione delle simulazioni completate richiede una politica esplicita. Non introdurre liberamente funzioni che cancellano la tracciabilità del credito consumato.

## 11. Welcome e frontend

### 11.1 Welcome pubblica

Prima pagina della web app, con:

- presentazione chiara della piattaforma;
- card dedicate ai tre simulatori;
- effetti di profondità e animazioni 3D leggere;
- spiegazione del percorso registrazione, piano e simulazione;
- pulsante evidente “Inizia con le simulazioni — Registrati”;
- pulsante evidente “Accedi”.

Desktop e telefono devono avere una navigazione coerente. Le animazioni non devono ostacolare lettura, interazione o preferenze di movimento ridotto. Il nome pubblico definitivo è da scegliere; “Welcome” è il nome funzionale della pagina.

### 11.2 Aree dopo il login

| Area | Contenuti |
| --- | --- |
| Privato | Dashboard, accesso ai tre simulatori, saldo, storico, piano e profilo. |
| Agente | Dashboard, impresa di appartenenza, simulatori, saldo disponibile e proprio storico. |
| Impresa | Dashboard, simulatori, agenti, consumi per agente, storico aziendale e abbonamento. |
| Admin | Gestione utenti/imprese, piani, crediti, pagamenti e registri dell’applicazione. |

Il titolare vede lo storico di tutti gli agenti della propria impresa. Proposta iniziale: un agente vede solo le proprie simulazioni; eventuale collaborazione su dati di altri agenti richiede una regola aggiuntiva.

Un saldo chiaramente visibile e messaggi coerenti devono essere comuni ai tre simulatori. Riutilizzare componenti di navigazione, form, tabelle, modali e notifiche per evitare tre applicazioni graficamente divergenti.

## 12. Amministrazione

Funzionalità richieste:

- cercare utenti e imprese;
- bloccare e riattivare profili;
- gestire o correggere gli agenti di un’impresa;
- aumentare e diminuire i crediti;
- consultare piani, pagamenti e relativi stati;
- avviare il recupero dell’accesso;
- consultare log tecnici e operazioni amministrative;
- richiedere cancellazioni definitive.

Ogni modifica sensibile registra autore, data, destinatario, motivo e variazione effettuata. Le rettifiche del saldo sono movimenti del registro crediti, non modifiche invisibili a un numero.

L’admin può inviare un reset password, correggere l’email secondo una procedura verificata e revocare le sessioni. **Non può leggere la password esistente**. Nessun ruolo richiede di memorizzare password in chiaro.

Proposte tecniche: autenticazione a due fattori per l’admin, conferma esplicita delle cancellazioni e tracciamento di ogni accesso amministrativo ai dati di un altro account.

La cancellazione definitiva deve propagarsi ai servizi interessati ed essere recuperabile in caso di interruzione. Prima di implementarla definire quali dati eliminare, anonimizzare o conservare per esigenze contabili. Il registro di audit non deve essere liberamente riscrivibile dall’interfaccia amministrativa ordinaria.

## 13. Stripe: ultima fase di integrazione

**Provider scelto dall’utente: Stripe.** L’attivazione e configurazione dell’account Stripe saranno l’ultima fase, svolta insieme all’utente con istruzioni manuali.

Durante lo sviluppo precedente a questa fase, il servizio Pagamento deve avere interfacce definite e un provider simulato utilizzabile esclusivamente nell’ambiente di sviluppo/test. Un evento di pagamento simulato non può attivare account in produzione.

Questo consente di verificare registrazione, attivazione, quote e posti agente prima di gestire credenziali reali. La preparazione del dominio Pagamento avviene durante lo sviluppo; la connessione e validazione finale con Stripe avvengono alla fine.

### 13.1 Componenti proposti

- Stripe Checkout per acquisti e avvio degli abbonamenti.
- Stripe Billing per ricorrenze e gestione dei piani.
- Portale cliente Stripe per le funzioni di gestione compatibili con le politiche commerciali.
- SDK Java ufficiale `stripe-java` nel microservizio Pagamento.
- Webhook verificati per confermare gli eventi di pagamento.

I dati della carta sono raccolti dal provider. Il servizio Pagamento conserva gli identificativi e gli stati necessari, non i numeri completi delle carte.

### 13.2 Conferma e assegnazione diritti

Il ritorno del browser alla pagina di successo non è prova sufficiente del pagamento. Il backend verifica la notifica, il riferimento all’ordine e lo stato effettivo del pagamento o dell’abbonamento.

- Primo pagamento valido: attivazione commerciale e quota iniziale.
- Rinnovo valido: quota del nuovo periodo, assegnata una sola volta.
- Pacchetto aggiuntivo valido: accredito della quantità acquistata una sola volta.
- Supplemento agenti valido: aggiornamento del limite acquistato.
- Pagamento fallito o ancora in elaborazione: nessuna assegnazione anticipata.
- Notifiche duplicate o fuori ordine: nessun duplicato e nessuna regressione impropria dello stato.
- Rimborso o contestazione: applicazione della politica da definire, tenendo conto dei crediti già consumati.

Gestire le richieste ripetute con chiavi di idempotenza e gli eventi ricevuti con identificativi univoci persistenti.

### 13.3 Sessione di configurazione guidata finale

1. Creazione o completamento dell’account Stripe da parte dell’utente.
2. Impostazioni aziendali e di incasso richieste dal provider.
3. Ambiente di test e credenziali di test.
4. Prodotti/prezzi per privato, impresa, pacchetti e supplementi approvati.
5. Endpoint webhook e segreto di verifica.
6. Collegamento delle variabili d’ambiente, senza pubblicare segreti in Git.
7. Test di pagamento riuscito, fallito, ripetuto, rinnovo e cancellazione.
8. Verifica completa dell’assegnazione crediti e attivazione account.
9. Configurazione dell’ambiente reale e passaggio in produzione dopo le verifiche.

### 13.4 Costi e fatturazione

Stripe applica commissioni: non è un servizio di incasso a costo zero. Le tariffe di Payments e Billing vanno verificate nel listino ufficiale al momento della configurazione e considerate nei prezzi della piattaforma.

La gestione dei documenti fiscali italiani deve essere definita separatamente. Non assumere che una ricevuta di pagamento o un documento Stripe equivalga automaticamente all’intero flusso di fatturazione richiesto dall’attività.

## 14. API: organizzazione proposta

Gli endpoint sotto sono una bozza di contratto, non API già implementate.

| Area | Operazioni indicative |
| --- | --- |
| Autenticazione | Registrazione, verifica email, login, logout, rinnovo sessione, recupero password. |
| Profilo | Identità corrente, ruolo, stato, impresa e permessi effettivi. |
| Impresa | Dati impresa, elenco agenti, inviti, blocco/riattivazione e consumi. |
| Simulatori | Bollette, offerte, parametri/profili, confronti, storico e PDF nei rispettivi servizi. |
| Pagamento pubblico autenticato | Catalogo, piano corrente, saldo, ordini e accesso alla gestione abbonamento. |
| Pagamento interno | Prenotazione, conferma, rilascio e riconciliazione dei crediti. |
| Admin | Gestione account, rettifiche motivate, consultazione pagamenti e audit. |
| Provider | Endpoint webhook con autenticità verificata e deduplicazione. |

Gli endpoint interni di consumo crediti non devono essere accessibili liberamente dal browser. Tutte le operazioni applicano controlli su ruolo e appartenenza. I messaggi di errore devono distinguere saldo esaurito, account bloccato, abbonamento non valido e dati errati.

## 15. Docker, configurazione e gestione operativa

Proposta: Docker Compose per ambiente locale con frontend, ingresso API, cinque servizi e MySQL. Esporre una sola porta web configurabile della nuova piattaforma; le porte 8088/8089/8090 dei prototipi non costituiscono un requisito del nuovo prodotto.

- Volumi persistenti per MySQL.
- Controlli di salute e avvio deterministico.
- Configurazioni distinte per sviluppo, test e produzione.
- Ambiente di sviluppo avviabile prima della configurazione Stripe.
- Errori chiari quando manca una configurazione necessaria in produzione.
- Log con identificativo della richiesta, servizio e contesto autorizzato.
- Nessuna password, token, segreto webhook o dato carta nei log.
- Backup e verifica di ripristino dei dati persistenti.

Versioni esatte di Java, Spring Boot, React e MySQL devono essere scelte e documentate all’inizio dello sviluppo in modo coerente tra i servizi. Non aggiornare versioni arbitrariamente durante l’integrazione dei simulatori.

## 16. Ordine di sviluppo

| Fase | Risultato |
| --- | --- |
| 1. Chiusura delle regole | Risoluzione dei punti aperti e definizione dei contratti principali. |
| 2. Fondamenta | Struttura dei cinque servizi, MySQL, migrazioni, frontend comune e Docker. |
| 3. Utenti | Registrazione, login unico, ruoli, recupero accesso e isolamento dei dati. |
| 4. Piani e crediti | Dominio Pagamento, provider di test, quota mensile e prenotazione dei consumi. |
| 5. Simulatori | Migrazione dei tre programmi, preservazione dei risultati e integrazione con utenti/crediti. |
| 6. Impresa | Inviti, due agenti inclusi, storico aziendale e consumi per agente. |
| 7. Admin e grafica | Gestione amministrativa, audit, Welcome e verifica responsive delle aree. |
| 8. Verifica completa | Flussi end-to-end, concorrenza, recupero errori, persistenza e documentazione di avvio. |
| 9. Stripe insieme all’utente | Configurazione guidata, test del provider reale e preparazione della pubblicazione. |

Non pubblicare una piattaforma con pagamenti simulati attivi in produzione. La configurazione Stripe finale non elimina la necessità di progettare prima le regole di pagamento e i contratti del servizio.

## 17. Criteri di accettazione

### Accesso

- La login non contiene selettori di area o ruolo.
- Ogni account viene indirizzato alla propria area in base alle credenziali verificate.
- Un account senza pagamento confermato non può eseguire simulazioni.
- Un utente bloccato non può continuare a operare tramite una sessione precedente.
- Gli endpoint rifiutano l’accesso ai dati di altri privati o imprese.

### Impresa

- Il titolare può creare due agenti inclusi nel piano base.
- Il terzo agente aggiuntivo è impedito senza un diritto acquistato sufficiente.
- Il titolare esegue simulazioni senza occupare un posto agente.
- Il blocco dell’agente impedisce nuove operazioni senza cancellare lo storico.
- I risultati aziendali riportano impresa e agente autore.

### Crediti

- La quota base del periodo mensile pagato è 80.
- Un nuovo confronto completato consuma un solo credito.
- Riapertura e nuovo download PDF consumano zero crediti.
- Un tentativo fallito non lascia un consumo definitivo ingiustificato.
- Due richieste sull’ultimo credito non producono due simulazioni completate.
- Una richiesta ripetuta non duplica consumo o risultato.
- A zero crediti compare il popup previsto e il backend blocca il nuovo confronto.
- Un pagamento o rinnovo notificato due volte non assegna due volte la quota.

### Calcolo e storico

- I casi di prova dei simulatori producono i risultati economici attesi anche con MySQL.
- Le modifiche successive alle tariffe non cambiano i risultati storici.
- Ogni PDF riproduce la simulazione salvata.
- Il percorso Business utilizza coerentemente le impostazioni PDF previste.
- Le sezioni dei simulatori restano coerenti con i programmi di origine.

### Admin e operatività

- Rettifiche dei crediti e blocchi sono tracciati con motivo e autore.
- Il recupero password funziona senza rendere leggibile la password originale.
- Le cancellazioni hanno una politica e una procedura coerente tra i servizi.
- Docker avvia l’ambiente di sviluppo senza credenziali Stripe reali.
- Riavviare i container non cancella utenti, crediti o storico.
- Il provider di test non può essere usato per attivare diritti in produzione.

## 18. Decisioni ancora aperte

Questi punti non sono stati confermati dall’utente e non devono essere trattati come scelte definitive:

| Punto | Proposta iniziale / scelta necessaria |
| --- | --- |
| Saldo tra simulatori | Proposta: unico saldo per i tre simulatori. |
| Saldo dell’impresa | Proposta: 80 condivise tra titolare e due agenti, con conteggio per autore. |
| Periodo mensile | Proposta: periodo dell’abbonamento a partire dalla data di attivazione. |
| Crediti mensili inutilizzati | Proposta: non si accumulano al periodo successivo. |
| Crediti extra | Scegliere scadenza, eventuale accumulo e necessità di abbonamento attivo. |
| Prezzi e pacchetti | Definire prezzi privato/impresa, quantità dei pacchetti e supplementi agenti. |
| Posti agente | Confermare trattamento degli inviti pendenti, agenti bloccati e riduzione dei posti acquistati. |
| Pagamento non riuscito | Definire eventuale periodo di tolleranza e funzioni disponibili. |
| Abbonamento scaduto | Proposta: storico e PDF accessibili; nuove simulazioni sospese. |
| Cambio piano | Definire decorrenza, quota aggiuntiva e gestione del prezzo durante il periodo. |
| Rimborsi/contestazioni | Definire effetti su crediti residui, crediti già consumati e accesso. |
| Visibilità fra agenti | Proposta: ciascuno vede il proprio storico; titolare vede tutto quello aziendale. |
| Dati condivisi aziendali | Definire visibilità e modifica di offerte, bollette e profili tra gli agenti. |
| Totali negativi | Definire la regola economica corretta per rettifiche e crediti nelle bollette. |
| Percorso Business | Decidere come integrare le funzionalità dei due percorsi attuali senza perderne capacità. |
| Eliminazione e conservazione | Definire dati da cancellare, anonimizzare e conservare. |
| Documenti fiscali | Definire il flusso di fatturazione e gli strumenti necessari. |
| Repository nuova | Scegliere repository di destinazione e strategia dei branch. |
| Nome e pubblicazione | Scegliere nome pubblico, dominio e ambiente di hosting. |

## 19. Istruzioni per chi svilupperà

1. Leggere l’intero documento e distinguere requisiti confermati da proposte.
2. Analizzare il codice attuale dei tre simulatori prima di spostarlo o modificarlo.
3. Conservare i casi di calcolo come regressioni significative.
4. Non cambiare i numeri o gli arrotondamenti per far coincidere artificialmente un risultato.
5. Non introdurre funzioni visibili estranee alla richiesta senza concordarle.
6. Non aggiungere selettori di ruolo alla pagina di accesso.
7. Usare cinque servizi di dominio e separare i loro database.
8. Centralizzare in Pagamento il saldo e i diritti acquistati.
9. Verificare autorizzazioni lato backend per ogni operazione sui dati.
10. Configurare Stripe con l’utente come ultima fase; usare prima un provider di test isolato.
11. Consegnare istruzioni di avvio, configurazione, test e gestione dei dati.
12. Documentare i punti irrisolti e non presentare come completato ciò che non è stato verificato.

## 20. Riferimenti tecnici

- Libreria Java ufficiale Stripe: https://github.com/stripe/stripe-java
- Stripe Checkout: https://stripe.com/payments/checkout
- Webhook degli abbonamenti: https://docs.stripe.com/billing/subscriptions/webhooks
- Idempotenza Stripe: https://docs.stripe.com/api/idempotent_requests
- Prezzi Stripe Italia: https://stripe.com/it/pricing
- Prezzi Stripe Billing Italia: https://stripe.com/it/billing/pricing

I riferimenti sono utili per l’integrazione; versioni SDK, dettagli degli eventi e tariffe vanno verificati quando si realizza la configurazione finale.
