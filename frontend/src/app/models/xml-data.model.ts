/**
 * Contenido XML de arranque para un archivo de datos nuevo de una carta. Función pura,
 * extraída de AppComponent.submitCreateXmlData() (antes una plantilla hardcodeada inline).
 */
export function createStarterXmlData(letterId: string): string {
  return (
    `<?xml version="1.0" encoding="UTF-8"?>\n` +
    `<content>\n` +
    `\t<letterType>${letterId}</letterType>\n` +
    `\t<letterContents>\n` +
    `\t\t<letterTypeData>\n` +
    `\t\t\t<letterData>\n` +
    `\t\t\t\t<idLetterFormat>1001</idLetterFormat>\n` +
    `\t\t\t\t<printDate>${new Date().toISOString().substring(0, 10)}</printDate>\n` +
    `\t\t\t</letterData>\n` +
    `\t\t\t<argumentList>\n` +
    `\t\t\t\t<argumentData>\n` +
    `\t\t\t\t\t<argumentName>LETTER_TYPE</argumentName>\n` +
    `\t\t\t\t\t<argumentValue>${letterId}</argumentValue>\n` +
    `\t\t\t\t</argumentData>\n` +
    `\t\t\t</argumentList>\n` +
    `\t\t</letterTypeData>\n` +
    `\t</letterContents>\n` +
    `\t<language>es</language>\n` +
    `\t<extTemplate>${letterId}</extTemplate>\n` +
    `</content>\n`
  );
}
