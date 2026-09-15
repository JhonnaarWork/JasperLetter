/**
 * Conversión entre texto estático y expresión funcional para el toggle del modal de edición de
 * texto. Los tokens $F{}/$P{}/$V{}/$R{} son tratados como "unidades atómicas": al pasar a
 * funcional quedan sin comillas (expresión viva); al pasar a estático quedan como texto literal
 * (visibles, sin evaluarse — es información para el usuario, no una expresión).
 *
 * Formateo acordado con el usuario:
 *   estático "hola mundo"          -> funcional '"hola mundo"'
 *   funcional '"hola "+$F{MUNDO}'  -> estático  "hola $F{MUNDO}"
 */

const JASPER_TOKEN_SPLIT_RE = /(\$[FPVR]\{[^}]*\}|\n)/g;
const JASPER_TOKEN_FULL_RE = /^\$[FPVR]\{[^}]*\}$/;
const QUOTED_JAVA_STRING_RE = /^"([^"\\]|\\.)*"$/;

/** Texto estático -> expresión Java de concatenación, apta para <textFieldExpression>. */
export function staticTextToExpression(text: string): string {
  const pieces = (text ?? '').split(JASPER_TOKEN_SPLIT_RE);
  const terms: string[] = [];

  for (const piece of pieces) {
    if (piece === '') continue;
    if (piece === '\n') {
      terms.push('"\\n"');
    } else if (JASPER_TOKEN_FULL_RE.test(piece)) {
      terms.push(piece);
    } else {
      terms.push(quoteJavaString(piece));
    }
  }

  return terms.length > 0 ? terms.join('+') : '""';
}

/**
 * Expresión funcional -> texto estático literal. Los términos que no sean ni un literal Java
 * entre comillas ni un token $F{}/$P{}/$V{}/$R{} (p.ej. una llamada a método, un ternario) se
 * dejan tal cual como mejor esfuerzo — no debería aparecer con el uso normal de este editor.
 */
export function expressionToStaticText(expr: string): string {
  if (!expr || !expr.trim()) return '';

  return splitTopLevelPlus(expr)
    .map((term) => {
      if (QUOTED_JAVA_STRING_RE.test(term)) {
        return unquoteJavaString(term);
      }
      return term;
    })
    .join('');
}

function quoteJavaString(value: string): string {
  const escaped = value.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n');
  return `"${escaped}"`;
}

function unquoteJavaString(literal: string): string {
  const inner = literal.slice(1, -1);
  let result = '';
  for (let i = 0; i < inner.length; i++) {
    const ch = inner[i];
    if (ch === '\\' && i + 1 < inner.length) {
      const next = inner[i + 1];
      if (next === 'n') {
        result += '\n';
      } else if (next === 't') {
        result += '\t';
      } else if (next === '"' || next === '\\') {
        result += next;
      } else {
        result += next;
      }
      i++;
      continue;
    }
    result += ch;
  }
  return result;
}

/** Divide una expresión Java por los "+" de nivel superior, sin romper los que están dentro de
 *  literales entre comillas. */
function splitTopLevelPlus(expr: string): string[] {
  const terms: string[] = [];
  let current = '';
  let inString = false;

  for (let i = 0; i < expr.length; i++) {
    const ch = expr[i];

    if (inString) {
      current += ch;
      if (ch === '\\' && i + 1 < expr.length) {
        current += expr[i + 1];
        i++;
        continue;
      }
      if (ch === '"') {
        inString = false;
      }
      continue;
    }

    if (ch === '"') {
      inString = true;
      current += ch;
      continue;
    }

    if (ch === '+') {
      terms.push(current.trim());
      current = '';
      continue;
    }

    current += ch;
  }

  if (current.trim()) {
    terms.push(current.trim());
  }
  return terms;
}
