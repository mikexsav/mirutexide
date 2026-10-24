/** Fast, local Java editing assistance. No network and no claim of Java type resolution. */
export type JavaSuggestion = {
  label: string;
  insertText: string;
  kind: "snippet" | "method" | "field" | "variable" | "class" | "keyword";
  detail: string;
  description: string;
  sort?: string;
};
const item = (
  label: string,
  insertText: string,
  kind: JavaSuggestion["kind"],
  detail: string,
  description = "",
): JavaSuggestion => ({ label, insertText, kind, detail, description });
export const javaSnippets: JavaSuggestion[] = [
  item(
    "sout",
    'System.out.println(${1:""});$0',
    "snippet",
    "Вывод в консоль · Tab",
    "Вставляет System.out.println. Tab переключает поля шаблона.",
  ),
  item(
    "serr",
    'System.err.println(${1:""});$0',
    "snippet",
    "Вывод ошибки · Tab",
  ),
  item(
    "psvm",
    "public static void main(String[] args) {\n\t$0\n}",
    "snippet",
    "Точка входа main · Tab",
  ),
  item(
    "fori",
    "for (int ${1:i} = 0; ${1:i} < ${2:n}; ${1:i}++) {\n\t$0\n}",
    "snippet",
    "Цикл for со счётчиком · Tab",
  ),
  item(
    "foreach",
    "for (${1:Integer} ${2:value} : ${3:values}) {\n\t$0\n}",
    "snippet",
    "Обход коллекции · Tab",
  ),
  item("if", "if (${1:condition}) {\n\t$0\n}", "snippet", "Условие if"),
  item(
    "ifelse",
    "if (${1:condition}) {\n\t${2}\n} else {\n\t$0\n}",
    "snippet",
    "Ветвление if / else",
  ),
  item("while", "while (${1:condition}) {\n\t$0\n}", "snippet", "Цикл while"),
  item(
    "trycatch",
    "try {\n\t${1}\n} catch (${2:Exception} ${3:ex}) {\n\t${3:ex}.printStackTrace();\n}$0",
    "snippet",
    "Обработка исключений",
  ),
  item("class", "public class ${1:Main} {\n\t$0\n}", "snippet", "Класс Java"),
  item(
    "method",
    "private static ${1:void} ${2:method}(${3}) {\n\t$0\n}",
    "snippet",
    "Новый статический метод",
  ),
];
const method = (name: string, args: string, signature: string) =>
  item(name, `${name}(${args})$0`, "method", signature);
const members: Record<string, JavaSuggestion[]> = {
  System: [
    item("out", "out", "field", "PrintStream System.out"),
    item("err", "err", "field", "PrintStream System.err"),
    item("in", "in", "field", "InputStream System.in"),
  ],
  PrintStream: [
    method("println", '${1:""}', "void println(value)"),
    method("print", '${1:""}', "void print(value)"),
    method(
      "printf",
      '${1:"%s"}, ${2:value}',
      "PrintStream printf(format, args)",
    ),
  ],
  Math: [
    method("abs", "${1:value}", "abs(number)"),
    method("max", "${1:a}, ${2:b}", "max(a, b)"),
    method("min", "${1:a}, ${2:b}", "min(a, b)"),
    method("sqrt", "${1:value}", "double sqrt(double)"),
    method("pow", "${1:base}, ${2:exponent}", "double pow(base, exponent)"),
    method("round", "${1:value}", "round(number)"),
  ],
  String: [
    method("length", "", "int length()"),
    method("isEmpty", "", "boolean isEmpty()"),
    method("isBlank", "", "boolean isBlank()"),
    method("substring", "${1:start}, ${2:end}", "String substring(start, end)"),
    method("equals", "${1:other}", "boolean equals(Object)"),
    method("contains", "${1:text}", "boolean contains(text)"),
    method("trim", "", "String trim()"),
    method("toUpperCase", "", "String toUpperCase()"),
    method("toLowerCase", "", "String toLowerCase()"),
    method("charAt", "${1:index}", "char charAt(int)"),
    method("split", '${1:" "}', "String[] split(regex)"),
  ],
  List: [
    method("add", "${1:value}", "boolean add(element)"),
    method("get", "${1:index}", "E get(int)"),
    method("size", "", "int size()"),
    method("isEmpty", "", "boolean isEmpty()"),
    method("contains", "${1:value}", "boolean contains(element)"),
    method("remove", "${1:index}", "E remove(int)"),
    method("clear", "", "void clear()"),
    method("sort", "${1:Comparator.naturalOrder()}", "void sort(comparator)"),
    method("iterator", "", "Iterator<E> iterator()"),
  ],
  Set: [
    method("add", "${1:value}", "boolean add(element)"),
    method("contains", "${1:value}", "boolean contains(element)"),
    method("size", "", "int size()"),
    method("isEmpty", "", "boolean isEmpty()"),
    method("remove", "${1:value}", "boolean remove(element)"),
  ],
  Map: [
    method("put", "${1:key}, ${2:value}", "V put(key, value)"),
    method("get", "${1:key}", "V get(key)"),
    method(
      "getOrDefault",
      "${1:key}, ${2:defaultValue}",
      "V getOrDefault(key, default)",
    ),
    method("containsKey", "${1:key}", "boolean containsKey(key)"),
    method("keySet", "", "Set<K> keySet()"),
    method("entrySet", "", "Set<Entry<K,V>> entrySet()"),
    method("size", "", "int size()"),
  ],
  Scanner: [
    method("nextInt", "", "int nextInt()"),
    method("hasNextInt", "", "boolean hasNextInt()"),
    method("nextLine", "", "String nextLine()"),
    method("hasNextLine", "", "boolean hasNextLine()"),
    method("nextDouble", "", "double nextDouble()"),
    method("close", "", "void close()"),
  ],
  Arrays: [
    method("sort", "${1:array}", "void sort(array)"),
    method("toString", "${1:array}", "String toString(array)"),
    method("asList", "${1:values}", "List<T> asList(values)"),
    method("copyOf", "${1:array}, ${2:length}", "copyOf(array, length)"),
  ],
  Collections: [
    method("sort", "${1:list}", "void sort(list)"),
    method("reverse", "${1:list}", "void reverse(list)"),
    method("max", "${1:values}", "T max(collection)"),
    method("min", "${1:values}", "T min(collection)"),
    method("emptyList", "", "List<T> emptyList()"),
  ],
};
const keywords = [
  "public",
  "private",
  "protected",
  "static",
  "final",
  "void",
  "int",
  "long",
  "double",
  "float",
  "boolean",
  "char",
  "byte",
  "short",
  "return",
  "new",
  "throw",
  "throws",
  "break",
  "continue",
  "switch",
  "case",
  "default",
  "else",
  "interface",
  "implements",
  "extends",
  "null",
  "true",
  "false",
];
const classes = [
  "System",
  "String",
  "Math",
  "Integer",
  "Long",
  "Double",
  "Boolean",
  "Object",
  "Scanner",
  "List",
  "ArrayList",
  "LinkedList",
  "Set",
  "HashSet",
  "TreeSet",
  "Map",
  "HashMap",
  "TreeMap",
  "Arrays",
  "Collections",
  "Comparator",
  "Optional",
  "Exception",
  "StringBuilder",
];
/** Mask comments/literals while keeping exact offsets, including Java text blocks. */
export function javaContext(source: string, offset: number) {
  let state: "code" | "line" | "block" | "string" | "char" | "text" = "code";
  let masked = "";
  for (let i = 0; i < Math.min(offset, source.length); i++) {
    const c = source[i],
      next = source[i + 1];
    if (state === "code") {
      if (c === "/" && next === "/") {
        state = "line";
        masked += "  ";
        i++;
      } else if (c === "/" && next === "*") {
        state = "block";
        masked += "  ";
        i++;
      } else if (source.slice(i, i + 3) === '"""') {
        state = "text";
        masked += "   ";
        i += 2;
      } else if (c === '"' || c === "'") {
        state = c === '"' ? "string" : "char";
        masked += " ";
      } else masked += c;
    } else if (state === "line") {
      if (c === "\n") {
        state = "code";
        masked += "\n";
      } else masked += " ";
    } else if (state === "block") {
      if (c === "*" && next === "/") {
        state = "code";
        masked += "  ";
        i++;
      } else masked += c === "\n" ? "\n" : " ";
    } else if (state === "text") {
      if (source.slice(i, i + 3) === '"""') {
        state = "code";
        masked += "   ";
        i += 2;
      } else masked += c === "\n" ? "\n" : " ";
    } else {
      if (c === "\\") {
        masked += "  ";
        i++;
      } else if (c === (state === "string" ? '"' : "'") || c === "\n") {
        state = "code";
        masked += c === "\n" ? "\n" : " ";
      } else masked += " ";
    }
  }
  return { masked: masked.slice(0, offset), inCode: state === "code" };
}
const scopes = (text: string) => {
  const stack: number[] = [];
  for (let i = 0; i < text.length; i++) {
    if (text[i] === "{") stack.push(i);
    else if (text[i] === "}") stack.pop();
  }
  return stack;
};
function symbols(masked: string) {
  const active = scopes(masked);
  const result = new Map<string, string>();
  let index = 0;
  const stack: number[] = [];
  const pattern =
    /(?:^|[;{}]|\bfor\s*\()\s*(?:(?:public|private|protected|static|final|volatile|transient)\s+)*([\p{L}_$][\p{L}\p{N}_$.]*(?:\s*<[^;{}=()]*>)?(?:\[\])?)\s+([\p{L}_$][\p{L}\p{N}_$]*)(?=\s*[=;,:)])/gu;
  for (const m of masked.matchAll(pattern)) {
    const at = m.index! + m[0].lastIndexOf(m[2]);
    for (; index < at; index++) {
      if (masked[index] === "{") stack.push(index);
      else if (masked[index] === "}") stack.pop();
    }
    if (
      !["return", "throw", "new", "case", "break", "continue"].includes(m[1]) &&
      stack.every((s, i) => active[i] === s)
    )
      result.set(m[2], m[1].replace(/\s/g, ""));
  }
  for (const brace of active) {
    const header = masked
      .slice(Math.max(0, brace - 600), brace)
      .match(/([\w$]+)\s*\(([^()]*)\)\s*(?:throws\s+[\w.,\s]+)?$/);
    if (
      header &&
      !["if", "for", "while", "switch", "catch", "synchronized"].includes(
        header[1],
      )
    ) {
      for (const p of header[2].matchAll(
        /([\w$.]+(?:<[^>]+>)?(?:\[\])?)\s+([\w$]+)(?=\s*[,)]|\s*$)/g,
      ))
        result.set(p[2], p[1]);
    }
  }
  return result;
}
function family(type: string) {
  const base = type.replace(/<.*>/g, "").split(".").pop()!;
  if (base.endsWith("[]")) return "Array";
  if (
    ["ArrayList", "LinkedList", "Collection", "Iterable", "List"].includes(base)
  )
    return "List";
  if (["HashSet", "TreeSet", "Set"].includes(base)) return "Set";
  if (["HashMap", "TreeMap", "Map"].includes(base)) return "Map";
  return base;
}
function declaredMethods(source: string, cursor: string): JavaSuggestion[] {
  const owner = scopes(cursor)[0];
  if (owner === undefined) return [];
  const full = javaContext(source, source.length).masked;
  const result: JavaSuggestion[] = [];
  const re =
    /(?:^|[;{}])\s*(?:(?:public|private|protected|static|final|synchronized|abstract|native|default)\s+)*([\w$.[\]<>?, ]+?)\s+([\w$]+)\s*\(([^()]*)\)\s*(?:throws\s+[\w.,\s]+)?(?:\{|;)/g;
  for (const m of full.matchAll(re)) {
    if (/^(return|throw|new|case)\b/.test(m[1].trim())) continue;
    const path = scopes(full.slice(0, m.index! + 1));
    if (path.length !== 1 || path[0] !== owner) continue;
    const params: string[] = [];
    let depth = 0,
      start = 0;
    for (let i = 0; i <= m[3].length; i++) {
      if (m[3][i] === "<") depth++;
      else if (m[3][i] === ">") depth--;
      if (i === m[3].length || (m[3][i] === "," && depth === 0)) {
        const name = m[3]
          .slice(start, i)
          .trim()
          .match(/([\w$]+)\s*(?:\[\])?$/)?.[1];
        if (name) params.push(name);
        start = i + 1;
      }
    }
    result.push(
      item(
        m[2],
        m[2] +
          "(" +
          params.map((p, i) => "${" + (i + 1) + ":" + p + "}").join(", ") +
          ")$0",
        "method",
        m[1].trim() + " " + m[2] + "(" + m[3] + ")",
        "Метод из текущего класса. Точная проверка типов выполняется javac.",
      ),
    );
  }
  return result;
}
export function getJavaSuggestions(
  source: string,
  offset: number,
): JavaSuggestion[] {
  const { masked, inCode } = javaContext(source, offset);
  if (!inCode) return [];
  const vars = symbols(masked);
  const access = masked.match(
    /([\p{L}_$][\p{L}\p{N}_$]*(?:\.[\p{L}_$][\p{L}\p{N}_$]*)*)\.\s*([\p{L}\p{N}_$]*)$/u,
  );
  if (access) {
    const receiver = access[1];
    const prefix = access[2].toLowerCase();
    const type =
      receiver === "System.out" || receiver === "System.err"
        ? "PrintStream"
        : vars.has(receiver)
          ? family(vars.get(receiver)!)
          : receiver;
    const available =
      type === "Array"
        ? [item("length", "length", "field", "int array.length")]
        : (members[type] ?? []);
    return available
      .filter((s) => s.label.toLowerCase().startsWith(prefix))
      .map((s) =>
        source.slice(offset).trimStart().startsWith("(") && s.kind === "method"
          ? { ...s, insertText: s.label }
          : s,
      );
  }
  const prefix = masked.match(/[\p{L}_$][\p{L}\p{N}_$]*$/u)?.[0] ?? "";
  if (!prefix) return [];
  const candidates = [
    ...javaSnippets,
    ...declaredMethods(source, masked),
    ...[...vars].map(([name, type]) =>
      item(name, name, "variable", `${type} · текущая область`),
    ),
    ...classes.map((name) =>
      item(name, name, "class", `${name} · Java standard library`),
    ),
    ...keywords.map((name) => item(name, name, "keyword", "Java keyword")),
  ];
  const unique = new Map<string, JavaSuggestion>();
  for (const s of candidates)
    if (
      s.label.toLowerCase().startsWith(prefix.toLowerCase()) &&
      !unique.has(s.label)
    )
      unique.set(s.label, s);
  return [...unique.values()].map((s, i) => ({
    ...s,
    sort: String(i).padStart(3, "0"),
  }));
}
