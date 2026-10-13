/** Names are limited to 150 characters: the suffix replaces the end of a long name. */
export function copyName(name: string, suffix = " · copia", max = 150) {
  return name.slice(0, Math.max(0, max - suffix.length)).trimEnd() + suffix;
}
