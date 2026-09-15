// Canonical secret detector for the Node/MCP side. Defense-in-depth mirrors
// exist in the Eclipse plugin, but the coverage is NOT identical across layers:
//   - JSON-key layer: SECRET_KEY_NAMES (below) is mirrored exactly by
//     BridgeLoop.SECRET_KEYS in Java (same 13 keys).
//   - Labeled-text layer: JS LABELED_SECRET is intentionally NARROWER than the
//     Java StepJournal.LABELED_SECRET (JS matches 7 labels; Java also matches
//     authorization/accesstoken/accesskey/bearer/authtoken/refreshtoken). The
//     Java side scans untrusted journal text and so keeps the broader regex.
// When widening SECRET_KEY_NAMES here, update BridgeLoop.SECRET_KEYS too.
const SECRET_KEY_NAMES = new Set([
  "password",
  "passwd",
  "pwd",
  "secret",
  "token",
  "apikey",
  "credential",
  "authorization",
  "accesstoken",
  "accesskey",
  "bearer",
  "authtoken",
  "refreshtoken",
]);

const SECRET_SUFFIXES = [
  "password",
  "passwd",
  "secret",
  "token",
  "credential",
  "apikey",
  "authorization",
  "accesstoken",
  "accesskey",
  "authtoken",
  "refreshtoken",
];

// Zero-width and invisible joiner/BOM characters used to bypass key matching.
const ZERO_WIDTH_CHARS = /[\u200B\u200C\u200D\uFEFF\u2060]/g;

// Catches bare HTTP-auth-scheme string values such as "Bearer abc", "Basic dXN...", "token xyz".
// Accepts whitespace, colon, or equals as the scheme/token separator (some
// tooling emits "Bearer=abc" or "token:xyz"). A mandatory non-alphanumeric
// separator prevents false positives on words like "tokenizer" or "basicConfig".
const BEARER_TOKEN_PATTERN = /^(?:bearer|basic|token)(?:\s+|\s*[:=]\s*)\S+/iu;

const LABELED_SECRET = /\b(?:password|passwd|pwd|secret|token|api[\s_-]*key|credential)\s*[:=]\s*\S+/iu;

export const SECRET_REJECTION_MESSAGE =
  "Do not paste passwords into AI. Treat the exposed password as compromised and rotate it immediately. "
  + "Revoke related sessions if applicable, and do not reuse the password.";

export class SecretRejectedError extends Error {
  constructor(paths = []) {
    super(SECRET_REJECTION_MESSAGE);
    this.name = "SecretRejectedError";
    this.code = "SECRET_REJECTED";
    this.paths = paths.map(() => "[REDACTED_PATH]");
  }
}

function normalizedKey(key) {
  // NFKC first (folds fullwidth/compat forms), then drop zero-width chars,
  // then strip non-alphanumerics, then lowercase.
  return String(key)
    .normalize("NFKC")
    .replaceAll(ZERO_WIDTH_CHARS, "")
    .replaceAll(/[^a-zA-Z0-9]/g, "")
    .toLowerCase();
}

function isSecretKey(key) {
  const normalized = normalizedKey(key);
  if (SECRET_KEY_NAMES.has(normalized)) return true;
  return SECRET_SUFFIXES.some((suffix) => normalized.endsWith(suffix));
}

function visit(value, path, findings, seen) {
  if (typeof value === "string") {
    if (LABELED_SECRET.test(value) || BEARER_TOKEN_PATTERN.test(value)) findings.push(path || "$");
    return;
  }
  if (value === null || typeof value !== "object") return;
  if (seen.has(value)) return;
  seen.add(value);

  if (Array.isArray(value)) {
    value.forEach((item, index) => visit(item, `${path}[${index}]`, findings, seen));
    return;
  }

  for (const [key, child] of Object.entries(value)) {
    const childPath = path ? `${path}.${key}` : key;
    if (isSecretKey(key)) findings.push(childPath);
    else visit(child, childPath, findings, seen);
  }
}

export function findSecretPaths(value) {
  const findings = [];
  visit(value, "", findings, new WeakSet());
  return [...new Set(findings)];
}

export function assertNoSecrets(value) {
  const findings = findSecretPaths(value);
  if (findings.length > 0) throw new SecretRejectedError(findings);
  return value;
}

export function sanitizeForLog(value, seen = new WeakSet()) {
  if (typeof value === "string") {
    return (LABELED_SECRET.test(value) || BEARER_TOKEN_PATTERN.test(value)) ? "[REDACTED]" : value;
  }
  if (value === null || typeof value !== "object") return value;
  if (seen.has(value)) return "[CIRCULAR]";
  seen.add(value);

  if (Array.isArray(value)) return value.map((item) => sanitizeForLog(item, seen));

  return Object.fromEntries(Object.entries(value).map(([key, child]) => [
    key,
    isSecretKey(key) ? "[REDACTED]" : sanitizeForLog(child, seen),
  ]));
}
