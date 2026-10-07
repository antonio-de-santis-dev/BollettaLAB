package it.bollettalab.platform;

import jakarta.persistence.EntityManager;
import java.io.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
@ConditionalOnProperty(name = "platform.simulator", havingValue = "true")
public class PdfAttribution implements ResponseBodyAdvice<Object> {
  final EntityManager em;
  final Environment env;

  public PdfAttribution(EntityManager em, Environment env) {
    this.em = em;
    this.env = env;
  }

  public boolean supports(MethodParameter p, Class<? extends HttpMessageConverter<?>> c) {
    return true;
  }

  public Object beforeBodyWrite(
      Object body,
      MethodParameter p,
      MediaType type,
      Class<? extends HttpMessageConverter<?>> c,
      ServerHttpRequest req,
      ServerHttpResponse res) {
    if (!(body instanceof byte[] bytes) || !MediaType.APPLICATION_PDF.isCompatibleWith(type))
      return body;
    String path = req.getURI().getPath();
    var match =
        java.util.regex.Pattern.compile("/(confronti|business/simulazioni)/(\\d+)/pdf$")
            .matcher(path);
    if (!match.find()) return body;
    String entity =
        "gas".equals(env.getProperty("platform.service"))
            ? "RecordEntity"
            : match.group(1).startsWith("business") ? "BusinessSimulation" : "Confronto";
    var rows =
        em.createQuery("SELECT e FROM " + entity + " e WHERE e.id=:id", ScopedEntity.class)
            .setParameter("id", Long.parseLong(match.group(2)))
            .getResultList();
    if (rows.isEmpty() || rows.get(0).getAuthorName() == null) return body;
    var saved = rows.get(0);
    String label =
        "BollettaLAB · Autore: "
            + saved.getAuthorName()
            + (saved.getCompanyName() == null || saved.getCompanyName().isBlank()
                ? ""
                : " · Impresa: "
                    + saved.getCompanyName()
                    + " · P.IVA: "
                    + Objects.toString(saved.getCompanyVat(), ""));
    label = label.replaceAll("\\p{Cntrl}", " ");
    try (var document = Loader.loadPDF(bytes);
        var input = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf");
        var output = new ByteArrayOutputStream()) {
      if (input == null) throw new IllegalStateException("Font PDF mancante");
      var font = PDType0Font.load(document, input);
      while (font.getStringWidth(label) / 1000 * 7
              > document.getPage(0).getMediaBox().getWidth() - 80
          && label.length() > 30) label = label.substring(0, label.length() - 1);
      for (var page : document.getPages())
        try (var stream =
            new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
          stream.beginText();
          stream.setFont(font, 7);
          stream.setNonStrokingColor(90 / 255f, 110 / 255f, 95 / 255f);
          stream.newLineAtOffset(40, 18);
          stream.showText(label);
          stream.endText();
          if (saved.getCompanyName() != null && saved.getCompanyAddress() != null) {
            String address = saved.getCompanyAddress().replaceAll("\\p{Cntrl}", " ");
            while (font.getStringWidth(address) / 1000 * 6 > page.getMediaBox().getWidth() - 80
                && address.length() > 20) address = address.substring(0, address.length() - 1);
            stream.beginText();
            stream.setFont(font, 6);
            stream.newLineAtOffset(40, 9);
            stream.showText(address);
            stream.endText();
          }
        }
      document.save(output);
      return output.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Impossibile completare l'attribuzione del PDF", e);
    }
  }
}
