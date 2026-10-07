# Prove manuali

Avvia con `./scripts/start.sh` e apri http://localhost:8091. Il profilo predefinito è di sviluppo: i pagamenti sono simulati e non addebitano denaro. Usa solo dati sintetici.

## Privato

1. Welcome → Inizia con le simulazioni → Persona privata.
2. Nome: Mario Rossi; email: mario.rossi@example.test; password: PasswordTest123!.
3. Accedi dal login unico. Prima dell'attivazione il saldo è 0 e le simulazioni portano alla pagina piano.
4. Conferma il piano base di prova: saldo 80. Ripetere l'attivazione nello stesso mese non assegna altre 80 simulazioni.
5. Progetto Luce: crea offerta prezzo fisso, monoraria, 0,10 €/kWh, PCV 120 €/anno. Parametri: coefficiente perdite e tutte le altre quote 0, fonte “Prova sintetica”. Bolletta cliente Mario Test, POD IT001E12345678, potenza 3 kW, totale precedente 200 €, IVA 10%, altre partite 0, un mese gennaio 2026 con F1 100, F2 200, F3 300 kWh.
6. Confronto atteso: imponibile 70 €, IVA 7 €, totale 77 €, risparmio nel periodo 123 €. Il saldo scende a 79.
7. Apri lo storico e scarica il PDF: il saldo resta 79. Cambia o elimina l'offerta originale: lo snapshot storico conserva il risultato.
8. Progetto Gas → apri esempi → GENNAIO-FEBBRAIO → Carica esempio e calcola. Totale atteso 392,61 €, raw 392,605125844. Consuma una simulazione; le tariffe storiche demo sono da validare, non prezzi commerciali attuali.
9. Progetto Luce Business: usa partita IVA 12345678901 e i dati elettrici precedenti, senza fonti ufficiali. Con IVA 10% e parametri a zero il totale di regressione è 77 €. Per un'offerta business reale inserisci aliquote e tariffe pertinenti alla fornitura.

## Impresa e agenti

1. Crea impresa: titolare Luca Bianchi, ragione sociale Impresa Test SRL, partita IVA 12345678901, Via Test 12, email titolare@example.test, password PasswordTest123!.
2. Attiva il piano base di prova. Crea inviti per agente1@example.test e agente2@example.test. Apri i link mostrati **solo in sviluppo** e scegli le password personali.
3. Un terzo invito viene rifiutato. Acquista un posto agente di prova: il terzo invito diventa disponibile.
4. L'agente entra dallo stesso login del titolare. Una sua simulazione riduce il saldo condiviso; storico e PDF indicano autore e dati dell'impresa.
5. L'agente 2 non vede né può aprire via URL il confronto dell'agente 1. Il titolare vede entrambi.
6. Blocca l'agente: anche la sua sessione esistente non deve più accedere alle API. Sblocca e fai un nuovo login.

## Admin, quota e recupero

1. Leggi ADMIN_EMAIL e ADMIN_PASSWORD nel tuo `.env`; accedi dal login normale. Non esiste un token amministratore da inserire nel browser.
2. Blocca/sblocca un privato; l'accesso deve essere revocato. Modifica le quote con motivazione e verifica il saldo.
3. Riduci a zero il saldo di un account attivo. Il calcolo viene rifiutato e compare il popup piano/pacchetti. Lo storico e il PDF restano disponibili gratuitamente.
4. Compra 40 simulazioni di prova: il saldo aumenta di 40. Una simulazione successiva consuma una sola unità.
5. Reset password: il link monouso sostituisce la password e revoca le sessioni precedenti. Non vengono mostrate le vecchie password.
6. Elimina un account sintetico: per privato/titolare spariscono dati e credenziali nei cinque servizi. Se un servizio è indisponibile, l'account resta DELETING e bloccato; ripeti l'eliminazione dopo il ripristino.

## Aspetto e navigazione

Prova desktop e telefono, navigazione da tastiera, Escape sui popup, zoom 200% e preferenza riduci movimento. Controlla tutte e sette le sezioni dei tre simulatori. Il confronto offre il download PDF; non presenta CSV, JSON, stampa o duplicazione del cliente. Le fonti ufficiali globali sono consultabili dagli utenti e aggiornabili dall'admin; i parametri privati restano modificabili nel rispettivo account.
