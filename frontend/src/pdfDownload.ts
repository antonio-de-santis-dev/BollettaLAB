export function pdfFilename(
  header: unknown,
  fallback = "Anteprima.pdf",
): string {
  if (typeof header !== "string") return fallback;
  const extended = header.match(/filename\*=UTF-8''([^;]+)/i);
  if (extended) {
    try {
      return decodeURIComponent(extended[1]);
    } catch {
      return fallback;
    }
  }
  return (
    header.match(/filename="([^"]+)"/i)?.[1] ??
    header.match(/filename=([^;]+)/i)?.[1]?.trim() ??
    fallback
  );
}
export function savePdf(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
