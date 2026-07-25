const RESET = "\u001b[0m";
const DIM = "\u001b[2m";
const BOLD = "\u001b[1m";
const GREEN = "\u001b[32m";
const YELLOW = "\u001b[33m";
const CYAN = "\u001b[36m";
const BRIGHT_RED = "\u001b[91m";
const BRIGHT_WHITE = "\u001b[97m";

const colorsEnabled = !process.env.NO_COLOR;

function color(code: string, value: string): string {
  return colorsEnabled ? `${code}${value}${RESET}` : value;
}

function levelColor(level: string): string {
  switch (level.toUpperCase()) {
    case "TRACE":
      return DIM;
    case "DEBUG":
      return CYAN;
    case "INFO":
      return GREEN;
    case "WARN":
    case "WARNING":
      return YELLOW;
    case "ERROR":
    case "FATAL":
      return `${BOLD}${BRIGHT_RED}`;
    default:
      return BRIGHT_WHITE;
  }
}

function statusColor(status: number): string {
  if (status >= 500) return `${BOLD}${BRIGHT_RED}`;
  if (status >= 400) return YELLOW;
  if (status >= 300) return CYAN;
  if (status >= 200) return GREEN;
  return BRIGHT_WHITE;
}

function renderValue(value: unknown, key?: string): string {
  if (Array.isArray(value)) {
    return `${color(DIM, "[")}${value.map((item) => renderValue(item)).join(color(DIM, ","))}${color(DIM, "]")}`;
  }

  if (value !== null && typeof value === "object") {
    return renderObject(value as Record<string, unknown>);
  }

  const rendered = JSON.stringify(value);
  if (key === "level" && typeof value === "string") {
    return color(levelColor(value), rendered);
  }
  if (key === "status" && typeof value === "number") {
    return color(statusColor(value), rendered);
  }
  if (key === "timestamp" || key === "@timestamp") {
    return color(DIM, rendered);
  }
  if (key === "message") {
    return color(BRIGHT_WHITE, rendered);
  }
  if (typeof value === "number") {
    return color(YELLOW, rendered);
  }
  if (typeof value === "boolean" || value === null) {
    return color(CYAN, rendered);
  }
  return rendered;
}

function renderObject(record: Record<string, unknown>): string {
  const fields = Object.entries(record).map(([key, value]) => {
    const renderedKey = color(DIM, JSON.stringify(key));
    return `${renderedKey}${color(DIM, ":")}${renderValue(value, key)}`;
  });
  return `${color(DIM, "{")}${fields.join(color(DIM, ","))}${color(DIM, "}")}`;
}

export function colorizeJsonLogLine(line: string): string {
  try {
    const parsed = JSON.parse(line);
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      return line;
    }
    return renderObject(parsed as Record<string, unknown>);
  } catch {
    return line;
  }
}

if (import.meta.main) {
  const decoder = new TextDecoder();
  let pending = "";

  for await (const chunk of Bun.stdin.stream()) {
    pending += decoder.decode(chunk, { stream: true });
    const lines = pending.split(/\r?\n/);
    pending = lines.pop() ?? "";
    for (const line of lines) {
      console.log(colorizeJsonLogLine(line));
    }
  }

  pending += decoder.decode();
  if (pending.length > 0) {
    console.log(colorizeJsonLogLine(pending));
  }
}
