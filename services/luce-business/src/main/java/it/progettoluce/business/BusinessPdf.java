package it.progettoluce.business;

import static it.progettoluce.business.BusinessModels.*;

import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.text.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Component;

@Component
public class BusinessPdf {
  public byte[] genera(Simulazione s) throws IOException {
    try (var doc = new PDDocument();
        var output = new ByteArrayOutputStream();
        var layout = new Layout(doc)) {
      doc.getDocumentInformation().setTitle("Simulazione business #" + s.id());
      var i = s.dati().input();
      var r = s.dati().risultato();
      layout.title("Simulazione business #" + s.id());
      layout.line(i.ragioneSociale() + " · POD " + i.pod());
      layout.line(
          "Partita IVA: "
              + (i.partitaIva() == null || i.partitaIva().isBlank()
                  ? "non indicata"
                  : i.partitaIva()));
      layout.line(
          "Riferimento "
              + i.dataRiferimento()
              + " · Potenza "
              + i.potenzaKw().toPlainString()
              + " kW");
      layout.line(
          "Periodo " + r.mesi().get(0).mese() + " - " + r.mesi().get(r.mesi().size() - 1).mese());
      if (r.mesi().stream().anyMatch(m -> !m.profilo().dati().verificato()))
        layout.title("BOZZA · profili non verificati");
      layout.title("Riepilogo del periodo");
      layout.line("Fattura precedente: " + euro(i.fatturaPrecedente()));
      layout.line("Totale simulato: " + euro(r.totale()));
      layout.line(
          (r.risparmioPeriodo().signum() < 0 ? "Maggior costo: " : "Risparmio: ")
              + euro(r.risparmioPeriodo().abs()));
      layout.line(
          (r.risparmioAnnualizzato().signum() < 0
                  ? "Maggior costo annualizzato: "
                  : "Risparmio annualizzato: ")
              + euro(r.risparmioAnnualizzato().abs()));
      for (var e : r.categorie().entrySet())
        layout.line(
            e.getKey()
                + ": "
                + euro(e.getValue())
                + " · incidenza "
                + (r.incidenze().get(e.getKey()) == null
                    ? "n/d"
                    : r.incidenze().get(e.getKey()) + "%"));
      layout.title("Imponibile e IVA per aliquota");
      for (var v : r.iva())
        layout.line(
            "IVA "
                + v.aliquota().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString()
                + "% · base "
                + euro(v.imponibile())
                + " · imposta "
                + euro(v.imposta()));
      layout.line(
          "Imponibile: "
              + euro(r.imponibile())
              + " · IVA: "
              + euro(r.totaleIva())
              + " · esenti: "
              + euro(r.esenti()));
      layout.title("Input, profili e versioni utilizzati");
      for (var mese : r.mesi()) {
        var m =
            i.mesi().stream().filter(x -> x.mese().equals(mese.mese())).findFirst().orElseThrow();
        var p = mese.profilo();
        layout.line(
            m.mese()
                + " · F1 "
                + m.f1()
                + " / F2 "
                + m.f2()
                + " / F3 "
                + m.f3()
                + " kWh · quote fisse "
                + m.quoteFisse());
        layout.line(
            p.dati().nome()
                + " · ID "
                + p.id()
                + " · versione "
                + p.versione()
                + " · decorrenza "
                + p.dati().dal()
                + " - "
                + (p.dati().al() == null ? "aperta" : p.dati().al()));
        layout.line(
            "Fonte: "
                + p.dati().fonte()
                + " · perdite "
                + p.dati().perdite()
                + " · arrotondamento intero "
                + p.dati().arrotondaPerdite());
        layout.line(
            "Verifica: "
                + (p.dati().verificato() ? p.dati().notaVerifica() : "profilo non verificato"));
        if (m.pun() != null)
          for (var f : it.progettoluce.offerte.Fascia.values())
            if (m.pun().valore(f) != null)
              layout.line("PUN " + f + ": " + m.pun().valore(f) + " €/kWh");
        layout.line("Imponibile mensile indicativo: " + euro(mese.imponibile()));
      }
      layout.title("Dettaglio delle componenti");
      for (var line : r.righe()) {
        layout.line(line.mese() + " · " + line.descrizione() + " [" + line.categoria() + "]");
        layout.line(
            "Quantità "
                + line.quantita().stripTrailingZeros().toPlainString()
                + " × "
                + line.corrispettivo().stripTrailingZeros().toPlainString()
                + " "
                + line.unita()
                + " = "
                + line.imponibile().stripTrailingZeros().toPlainString()
                + " € · "
                + (line.esente()
                    ? "esente"
                    : "IVA "
                        + line.aliquotaIva()
                            .multiply(new BigDecimal("100"))
                            .stripTrailingZeros()
                            .toPlainString()
                        + "%"));
      }
      layout.title("Regole e limiti");
      layout.line(
          "Righe a 8 decimali HALF_UP; imponibile e IVA arrotondati a centesimi per aliquota."
              + " Totale = somma basi IVA + imposte + esenti.");
      for (var a : r.avvisi()) layout.line(a);
      layout.line(
          "Motore "
              + s.dati().versioneMotore()
              + " · dati salvati il "
              + s.creataIl()
              + ". Nessun ricalcolo con tariffe correnti.");
      layout.finish();
      doc.save(output);
      return output.toByteArray();
    }
  }

  static String euro(BigDecimal n) {
    return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ITALY)).format(n)
        + " €";
  }

  static class Layout implements AutoCloseable {
    final PDDocument doc;
    final PDType0Font font, bold;
    PDPageContentStream stream;
    float y;
    boolean finished;

    Layout(PDDocument d) throws IOException {
      doc = d;
      try (var f = BusinessPdf.class.getResourceAsStream("/fonts/DejaVuSans.ttf");
          var b = BusinessPdf.class.getResourceAsStream("/fonts/DejaVuSans-Bold.ttf")) {
        font = PDType0Font.load(d, f);
        bold = PDType0Font.load(d, b);
      }
      page();
    }

    void page() throws IOException {
      if (stream != null) stream.close();
      var p = new PDPage(PDRectangle.A4);
      doc.addPage(p);
      stream = new PDPageContentStream(doc, p);
      y = 785;
      text("PROGETTO LUCE / BUSINESS", bold, 12, new Color(25, 77, 61));
      y -= 22;
    }

    void title(String s) throws IOException {
      if (y < 110) page();
      y -= 10;
      write(s, bold, 12);
      y -= 5;
    }

    void line(String s) throws IOException {
      write(s, font, 9);
      y -= 3;
    }

    String safe(String s) throws IOException {
      var b = new StringBuilder();
      for (int cp : s.codePoints().toArray()) {
        String ch = new String(Character.toChars(cp));
        if (Character.isISOControl(cp) || Character.isWhitespace(cp)) {
          b.append(' ');
          continue;
        }
        try {
          font.encode(ch);
          b.append(ch);
        } catch (IllegalArgumentException e) {
          b.append('?');
        }
      }
      return b.toString();
    }

    void write(String raw, PDFont f, float size) throws IOException {
      String s = safe(raw);
      StringBuilder row = new StringBuilder();
      for (String word : s.split(" ")) {
        for (int offset = 0; offset < word.length(); ) {
          int cp = word.codePointAt(offset);
          offset += Character.charCount(cp);
          String ch = new String(Character.toChars(cp));
          if (f.getStringWidth(row + ch) / 1000 * size > 505 && !row.isEmpty()) {
            if (y < 60) page();
            text(row.toString(), f, size, Color.DARK_GRAY);
            row.setLength(0);
          }
          row.append(ch);
        }
        if (f.getStringWidth(row + " ") / 1000 * size > 505) {
          if (y < 60) page();
          text(row.toString(), f, size, Color.DARK_GRAY);
          row.setLength(0);
        } else row.append(' ');
      }
      if (!row.isEmpty()) {
        if (y < 60) page();
        text(row.toString(), f, size, Color.DARK_GRAY);
      }
    }

    void text(String s, PDFont f, float size, Color c) throws IOException {
      stream.beginText();
      stream.setFont(f, size);
      stream.setNonStrokingColor(c);
      stream.newLineAtOffset(45, y);
      stream.showText(s);
      stream.endText();
      y -= size + 6;
    }

    void finish() throws IOException {
      if (finished) return;
      stream.close();
      stream = null;
      for (int i = 0; i < doc.getNumberOfPages(); i++) {
        try (var footer =
            new PDPageContentStream(
                doc, doc.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
          footer.beginText();
          footer.setFont(font, 8);
          footer.newLineAtOffset(45, 30);
          footer.showText(
              "Simulazione parametrica business · " + (i + 1) + " / " + doc.getNumberOfPages());
          footer.endText();
        }
      }
      finished = true;
    }

    public void close() throws IOException {
      if (stream != null) stream.close();
    }
  }
}
