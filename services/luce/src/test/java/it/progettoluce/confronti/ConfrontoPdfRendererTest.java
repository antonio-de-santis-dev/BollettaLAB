package it.progettoluce.confronti;

import static org.assertj.core.api.Assertions.*;

import it.progettoluce.bollette.*;
import it.progettoluce.calcolo.RisultatoCalcolo;
import it.progettoluce.offerte.*;
import it.progettoluce.parametri.ParametriRequest;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class ConfrontoPdfRendererTest {
  @Test
  void generaPdfConImportiAccentiFontEDataSalvata() throws Exception {
    byte[] bytes =
        new ConfrontoPdfRenderer()
            .genera(esempio("Mario Rossi – città", 10, "Nota di prova", false));
    assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");
    try (var pdf = Loader.loadPDF(bytes)) {
      String text = new PDFTextStripper().getText(pdf);
      assertThat(text)
          .contains(
              "Mario Rossi – città",
              "77,00 €",
              "123,00 €",
              "1.476,00 €",
              "POD-DEMO",
              "10/01/2026",
              "ARERA",
              "Simulazione parametrica");
      for (var page : pdf.getPages())
        for (var font : page.getResources().getFontNames())
          assertThat(page.getResources().getFont(font).isEmbedded()).isTrue();
    }
    Path path = Path.of("target/pdf-verifica/confronto-esempio.pdf");
    Files.createDirectories(path.getParent());
    Files.write(path, bytes);
  }

  @Test
  void impaginaTutteLeRigheENoteLungheSenzaPerdereTesto() throws Exception {
    byte[] bytes =
        new ConfrontoPdfRenderer()
            .genera(
                esempio(
                    "Cliente " + "Lungo ".repeat(20),
                    120,
                    "Condizione commerciale lunga ".repeat(60) + "FINE-NOTE",
                    false));
    try (var pdf = Loader.loadPDF(bytes)) {
      assertThat(pdf.getNumberOfPages()).isGreaterThan(3);
      String text = new PDFTextStripper().getText(pdf);
      assertThat(text).contains("Voce 119", "FINE-NOTE", "0,12345678", "599,999996");
      assertThat(text.split("Corrispettivo", -1).length).isGreaterThan(2);
    }
    Files.createDirectories(Path.of("target/pdf-verifica"));
    Files.write(Path.of("target/pdf-verifica/confronto-lungo.pdf"), bytes);
  }

  @Test
  void gestisceMaggiorCostoTotaleZeroECaratteriNonSupportati() throws Exception {
    byte[] bytes = new ConfrontoPdfRenderer().genera(esempio("Èléonora Иванова 中文", 10, "", true));
    try (var pdf = Loader.loadPDF(bytes)) {
      String text = new PDFTextStripper().getText(pdf);
      assertThat(text)
          .contains(
              "Èléonora Иванова",
              "Maggior costo",
              "Non disponibile",
              "caratteri non disponibili",
              "77,00 €");
    }
  }

  @Test
  void quattroStiliConLogoEContattiMantengonoImportiEDettaglio() throws Exception {
    var image =
        new java.awt.image.BufferedImage(240, 80, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    var graphics = image.createGraphics();
    graphics.setColor(java.awt.Color.BLUE);
    graphics.fillRect(0, 0, 240, 80);
    graphics.dispose();
    var encoded = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", encoded);
    String logo =
        "data:image/png;base64," + Base64.getEncoder().encodeToString(encoded.toByteArray());
    for (var stile : PdfPersonalizzazione.Stile.values()) {
      var options =
          new PdfPersonalizzazione(
              stile,
              "#D97706",
              logo,
              new PdfPersonalizzazione.Consulente(
                  "Studio Energia",
                  "Consulente energetico",
                  "consulente@example.com",
                  "+39 000 000 0000",
                  "Via Esempio 12",
                  true));
      var bytes =
          new ConfrontoPdfRenderer()
              .genera(esempio("Mario Rossi", 10, "Nota di prova", false), options);
      try (var pdf = Loader.loadPDF(bytes)) {
        String text = new PDFTextStripper().getText(pdf);
        assertThat(text)
            .contains(
                "Studio Energia",
                "DATI DIMOSTRATIVI",
                "77,00 €",
                "123,00 €",
                "600",
                "Energia:",
                "Via Esempio 12",
                "Componente azzerata 9");
        boolean hasImage = false;
        for (var name : pdf.getPage(0).getResources().getXObjectNames())
          hasImage |=
              pdf.getPage(0).getResources().getXObject(name)
                  instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
        assertThat(hasImage).isTrue();
        if (stile == PdfPersonalizzazione.Stile.SINTESI)
          assertThat(text.indexOf("Quanto puoi risparmiare"))
              .isLessThan(text.indexOf("Cliente e proposta"));
      }
      Files.createDirectories(Path.of("target/pdf-verifica"));
      Files.write(Path.of("target/pdf-verifica/editor-" + stile + ".pdf"), bytes);
    }
  }

  @Test
  void anteprimaRenderizzaOgniPaginaDelPdfScaricabile() throws Exception {
    byte[] bytes = new ConfrontoPdfRenderer().genera(esempio("Cliente anteprima", 10, "", false));
    var preview = PdfAnteprima.da(bytes);
    assertThat(Base64.getDecoder().decode(preview.pdfBase64())).isEqualTo(bytes);
    try (var pdf = Loader.loadPDF(bytes)) {
      assertThat(preview.pagine()).hasSize(pdf.getNumberOfPages());
    }
    for (String page : preview.pagine()) {
      assertThat(page).startsWith("data:image/png;base64,");
      byte[] png = Base64.getDecoder().decode(page.substring(page.indexOf(',') + 1));
      var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
      assertThat(image.getWidth()).isGreaterThan(800);
      assertThat(image.getHeight()).isGreaterThan(1200);
    }
  }

  @Test
  void rifiutaLoghiCorrottiFormatiNonAmmessiEDimensioniEccessive() throws Exception {
    for (String data :
        List.of(
            "https://example.com/logo.png",
            "data:image/svg+xml;base64,PHN2Zz4=",
            "data:image/png;base64,bm9uLXVuaW1tYWdpbmU="))
      assertThatThrownBy(() -> PdfLogo.leggi(data)).isInstanceOf(IllegalArgumentException.class);
    var large =
        new java.awt.image.BufferedImage(4097, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var encoded = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(large, "png", encoded);
    assertThatThrownBy(
            () ->
                PdfLogo.leggi(
                    "data:image/png;base64,"
                        + Base64.getEncoder().encodeToString(encoded.toByteArray())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ConfrontoService.Risposta esempio(
      String cliente, int count, String note, boolean perdita) {
    var z = BigDecimal.ZERO;
    var mesi = List.of(new MeseRequest("2026-01", b("100"), b("200"), b("300"), null));
    var bolletta =
        new BollettaRequest(
            cliente,
            "POD-DEMO",
            "Fornitore Demo",
            b("3"),
            perdita ? z : b("200"),
            b("0.1"),
            z,
            z,
            mesi,
            0L);
    var offerta =
        new OffertaResponse(
            1L,
            0L,
            "Gestore Demo",
            "Luce Fissa Test",
            TipoOfferta.PREZZO_FISSO,
            TipoTariffa.MONORARIA,
            new Prezzi(b("0.1"), null, null, null, null),
            null,
            b("120"),
            true,
            note,
            List.of(new OffertaResponse.VoceDTO(1L, "ENERGIA", Fascia.F0, b("0.1"), false)));
    var parametri =
        new ParametriRequest(
            "Profilo test", "Dati sintetici, gennaio 2026", z, false, z, z, z, z, z, z, z, 0L);
    var righe = new ArrayList<RisultatoCalcolo.Riga>();
    for (int i = 0; i < count; i++) {
      boolean lunga = count > 10;
      righe.add(
          new RisultatoCalcolo.Riga(
              "2026-01",
              lunga
                  ? "Voce " + i
                  : i == 0
                      ? "Energia F0"
                      : i == 1 ? "Commercializzazione (PCV)" : "Componente azzerata " + i,
              "ENERGIA",
              i == 1 && !lunga ? "€/mese" : "€/kWh",
              lunga ? b("599.999996") : i == 1 ? b("1") : b("600"),
              lunga ? b("0.12345678") : i == 0 ? b("0.1") : i == 1 ? b("10") : z,
              lunga ? b("59.9999996") : i == 0 ? b("60") : i == 1 ? b("10") : z));
    }
    var categorie = new LinkedHashMap<String, BigDecimal>();
    categorie.put("ENERGIA", b("70"));
    categorie.put("TRASPORTO", z);
    categorie.put("ONERI", z);
    categorie.put("IMPOSTE", z);
    categorie.put("ALTRE_PARTITE", z);
    var risultato =
        new RisultatoCalcolo(
            righe,
            categorie,
            b("70"),
            b("7"),
            z,
            b("77"),
            perdita ? b("-77") : b("123"),
            perdita ? null : b("61.5"),
            perdita ? b("-924") : b("1476"),
            1,
            "Proiezione indicativa: risparmio periodo × 12 / mesi. Non considera stagionalità o"
                + " variazioni future del PUN.");
    return new ConfrontoService.Risposta(
        42L,
        Instant.parse("2026-01-10T10:00:00Z"),
        new ConfrontoService.Snapshot("1.0", 1L, bolletta, offerta, parametri, risultato));
  }

  private static BigDecimal b(String value) {
    return new BigDecimal(value);
  }
}
