const DECIMAL_PATTERN = /^(-?)(\d+)(?:\.(\d+))?$/;

function toCents(value) {
  const match = String(value ?? "0").match(DECIMAL_PATTERN);
  if (!match) return 0n;
  const [, sign, integer, fraction = ""] = match;
  const cents = BigInt(integer) * 100n + BigInt((fraction + "00").slice(0, 2));
  return sign === "-" ? -cents : cents;
}

function fromCents(cents) {
  const negative = cents < 0n;
  const absolute = negative ? -cents : cents;
  const integer = absolute / 100n;
  const fraction = String(absolute % 100n).padStart(2, "0");
  const decimal = fraction === "00" ? String(integer) : `${integer}.${fraction}`;
  return negative ? `-${decimal}` : decimal;
}

export function addMoney(...values) {
  return fromCents(values.reduce((sum, value) => sum + toCents(value), 0n));
}

export function subtractMoney(minuend, subtrahend) {
  return fromCents(toCents(minuend) - toCents(subtrahend));
}

export function formatWon(value) {
  const exact = fromCents(toCents(value));
  const negative = exact.startsWith("-");
  const unsigned = negative ? exact.slice(1) : exact;
  const [integer, fraction] = unsigned.split(".");
  const grouped = BigInt(integer).toLocaleString("ko-KR");
  return `${negative ? "-" : ""}${grouped}${fraction ? `.${fraction}` : ""}원`;
}

export function formatProfileDate(value) {
  if (!value) return "날짜 없음";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "날짜 없음";
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}
