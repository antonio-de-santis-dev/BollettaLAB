# Configurazione Stripe — ultima fase, da svolgere insieme

**Stripe non è ancora integrato.** È una decisione esplicita del progetto: prima completare e provare l'applicazione, poi configurare account, prezzi, pagamenti e webhook con il titolare. I prezzi sono indicati come “da definire”; nessun importo è stato inventato.

Il servizio Pagamento gestisce già wallet, quote, pacchetti, posti agente, transazioni e storico. Solo nei profili `dev` e `test` il checkout simula un acquisto, senza chiamare Stripe e senza addebiti. Negli altri profili il checkout restituisce 503 e non attiva il piano. Non impostare `APP_PROFILE=dev` su un servizio aperto al pubblico: ogni utente di sviluppo può confermare acquisti gratuiti per le prove.

## Passaggi della fase finale

1. Scegliere prezzi e condizioni commerciali: piano mensile, pacchetti extra, posto agente aggiuntivo. Confermare durata dei crediti extra, rinnovo del supplemento agenti, rimborsi e cessazione del piano.
2. Creare account Stripe e completare i dati dell'impresa. Iniziare in modalità test.
3. Implementare l'adapter Stripe nel servizio Pagamento con la libreria Java ufficiale, Checkout per la sottoscrizione/pacchetti e Customer Portal per la gestione.
4. Salvare secret key e webhook signing secret nelle variabili del servizio, senza commit. Collegare prodotti e prezzi ai codici BASE, EXTRA_40, EXTRA_100 e AGENT_1.
5. Verificare firma, importo, valuta, prodotto, cliente e idempotenza degli eventi. Attivare l'account e assegnare le 80 simulazioni **solo dopo l'evento di pagamento confermato**. Non fidarsi del redirect del browser.
6. Gestire rinnovi pagati, pagamenti falliti, annullamenti e rimborsi con una macchina a stati e prove dedicate. Mai assegnare nuovamente crediti sullo stesso evento.
7. Provare flussi positivi e negativi con Stripe CLI e carte test, prima di passare alle chiavi live.

Prima della pubblicazione servono anche dominio HTTPS, cookie Secure, SMTP funzionante, backup e le condizioni d'uso/privacy coerenti con il servizio. Questi punti non sono sostituiti dal provider di prova.
