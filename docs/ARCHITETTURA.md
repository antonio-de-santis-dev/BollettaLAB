# Architettura BollettaLAB

Cinque applicazioni Spring Boot indipendenti: utenti, pagamento, luce, luce-business e gas. Un frontend React e un reverse proxy espongono un'unica origine web. MySQL contiene cinque database con utenti distinti. Nessun servizio legge direttamente le tabelle di un altro.

La libreria platform-common contiene contratti, contesto autenticato e protezioni comuni; non è un sesto microservizio. L'autenticazione usa sessioni opache persistenti con cookie HttpOnly e revoca verificata dal servizio Utenti. Il saldo e il registro dei consumi appartengono al servizio Pagamento.

## Decisioni operative per la versione di prova

- 80 crediti per periodo mensile; impresa con saldo condiviso tra titolare e due agenti; i tre simulatori consumano lo stesso saldo.
- I crediti mensili non si accumulano. Crediti extra separati, senza scadenza nella versione di prova e utilizzabili con piano attivo. Regole e prezzi commerciali restano da confermare prima di Stripe.
- Agente: proprio storico. Titolare: tutto lo storico dell'impresa. Dati preparatori condivisi nell'impresa.
- Account in attesa di pagamento: può accedere al proprio onboarding, ma non effettuare simulazioni.
- Nessun rinnovo viene accreditato dalla sola data: è necessario un evento di pagamento valido per il nuovo periodo.
- Provider di prova consentito esclusivamente nel profilo dev/test. Produzione senza Stripe configurato: checkout disabilitato.

## Consumo

Prenotazione atomica, calcolo e persistenza nel simulatore, conferma del consumo. Identificativo di richiesta persistente per ripetizioni. Errori certi rimborsano la prenotazione; esiti incerti richiedono riconciliazione. Storico e PDF non consumano crediti.

## Branch

Ogni implementazione nasce dal main aggiornato in un branch descrittivo. Verifica prima dell'integrazione. Le integrazioni sono autorizzate dall'incarico dell'utente. Stripe reale viene configurato insieme all'utente nell'ultima fase.
