import fs from "node:fs";
import net from "node:net";
import path from "node:path";
import crypto from "node:crypto";
import { assertNoSecrets } from "./secret-guard.mjs";

const ALLOWED_FIELDS = new Set([
  "alias", "systemId", "client", "language", "userId", "mode", "applicationServer", "systemNumber",
  "messageServer", "logonGroup", "sncEnabled", "ssoEnabled",
]);

function safeAlias(alias) {
  if (typeof alias !== "string" || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(alias)) {
    throw new Error("Connection alias must use letters, digits, dot, underscore, or hyphen");
  }
  return alias;
}

function validateConnection(input) {
  assertNoSecrets(input);
  const unknown = Object.keys(input ?? {}).filter((key) => !ALLOWED_FIELDS.has(key));
  if (unknown.length > 0) throw new Error(`Unknown connection field: ${unknown[0]}`);
  for (const field of ["alias", "systemId", "client", "mode"]) {
    if (typeof input?.[field] !== "string" || input[field].trim() === "") throw new Error(`${field} is required`);
  }
  safeAlias(input.alias);
  if (!/^[0-9]{3}$/.test(input.client)) throw new Error("client must be three digits");
  if (!/^[A-Za-z0-9]{3}$/.test(input.systemId)) throw new Error("systemId must contain three letters or digits");
  if (input.mode === "applicationServer") {
    if (!input.applicationServer || !/^[0-9]{2}$/.test(input.systemNumber ?? "")) {
      throw new Error("applicationServer and two-digit systemNumber are required");
    }
  } else if (input.mode === "messageServer") {
    if (!input.messageServer || !input.logonGroup) throw new Error("messageServer and logonGroup are required");
    // systemNumber is optional for messageServer mode but, when present, lets
    // connectionEndpoint() probe a concrete dispatcher port instead of the
    // generic sapms service name. Accept and validate it if supplied.
    if (input.systemNumber !== undefined && !/^[0-9]{2}$/.test(input.systemNumber)) {
      throw new Error("systemNumber, when provided, must be two digits");
    }
  } else throw new Error("mode must be applicationServer or messageServer");
}

function parseAttributes(text) {
  return Object.fromEntries([...text.matchAll(/([A-Za-z][A-Za-z0-9_-]*)\s*=\s*(["'])(.*?)\2/gu)].map((match) => [match[1].toLowerCase(), match[3]]));
}

function truthy(value) {
  return /^(?:1|true|yes|on)$/i.test(String(value ?? ""));
}

export class ConnectionStore {
  #root;
  #now;

  constructor({ root, now = () => new Date().toISOString() } = {}) {
    if (!root) throw new Error("ConnectionStore root is required");
    this.#root = path.resolve(root);
    this.#now = now;
  }

  prepare(input) {
    validateConnection(input);
    const connection = {
      alias: input.alias,
      systemId: input.systemId.toUpperCase(),
      client: input.client,
      language: (input.language ?? "EN").toUpperCase(),
      userId: input.userId ?? "",
      mode: input.mode,
      ...(input.mode === "applicationServer"
        ? { applicationServer: input.applicationServer, systemNumber: input.systemNumber }
        : { messageServer: input.messageServer, logonGroup: input.logonGroup, systemNumber: input.systemNumber ?? "00" }),
      sncEnabled: input.sncEnabled === true,
      ssoEnabled: input.ssoEnabled === true,
      recordedAt: this.#now(),
    };
    const directory = path.join(this.#root, "connections", safeAlias(connection.alias));
    fs.mkdirSync(directory, { recursive: true });
    const file = `${connection.recordedAt.replaceAll(/[^0-9A-Za-z]/g, "")}-${crypto.randomUUID()}.json`;
    fs.writeFileSync(path.join(directory, file), JSON.stringify(connection), { encoding: "utf8", flag: "wx" });
    return structuredClone(connection);
  }

  status(alias) {
    const directory = path.join(this.#root, "connections", safeAlias(alias));
    if (!fs.existsSync(directory)) return { configured: false, alias };
    const files = fs.readdirSync(directory).filter((file) => file.endsWith(".json")).sort();
    if (files.length === 0) return { configured: false, alias };
    // The latest file may be truncated/corrupt (partial write, disk error).
    // Walk backward from the newest until a parseable one is found so a single
    // bad frame cannot crash status(); if nothing parses, report a corrupt alias.
    for (let i = files.length - 1; i >= 0; i -= 1) {
      try {
        const parsed = JSON.parse(fs.readFileSync(path.join(directory, files[i]), "utf8"));
        return { configured: true, ...parsed };
      } catch {
        // try the previous file
      }
    }
    return { configured: false, alias, error: "corrupt" };
  }

  #resolveConfinedLandscapePath(landscapePath) {
    const target = path.resolve(landscapePath);
    const allowedRoots = [path.join(this.#root, "landscapes")];
    const allowDir = process.env.BW_AUTOMATION_LANDSCAPE_ALLOW_DIR;
    if (allowDir && allowDir.trim() !== "") {
      allowedRoots.push(path.resolve(allowDir));
    }
    const isWindows = process.platform === "win32";
    const normalizeForCompare = (value) => (isWindows ? String(value).toLowerCase() : String(value));
    // First line of defense: reject paths that escape lexically. This preserves
    // the LANDSCAPE_PATH_NOT_CONFINED contract for ../etc/passwd and foreign
    // absolute paths (which resolve to non-existent locations on disk).
    const lexicallyConfined = allowedRoots.some((allowed) => {
      const a = normalizeForCompare(allowed);
      const t = normalizeForCompare(target);
      return t === a || t.startsWith(a + path.sep);
    });
    if (!lexicallyConfined) {
      const error = new Error(
        `Landscape path must be inside one of: ${allowedRoots.join(", ")}`,
      );
      error.code = "LANDSCAPE_PATH_NOT_CONFINED";
      throw error;
    }
    // Second line of defense: a path can pass the lexical check yet resolve,
    // via a symlink inside the root, to somewhere outside. Resolve real paths
    // for both target and allowed roots and re-check the prefix. If the target
    // does not exist yet, realpathSync throws ENOENT — let it propagate so the
    // caller sees a clear "no such file" error rather than a silent success.
    const realTarget = fs.realpathSync(target);
    const realRoots = allowedRoots.map((allowed) => {
      try { return fs.realpathSync(allowed); }
      catch { return allowed; /* allowed root may not exist yet */ }
    });
    const reallyConfined = realRoots.some((allowed) => {
      const a = normalizeForCompare(allowed);
      const t = normalizeForCompare(realTarget);
      return t === a || t.startsWith(a + path.sep);
    });
    if (!reallyConfined) {
      const error = new Error(
        `Landscape path must be inside one of: ${allowedRoots.join(", ")}`,
      );
      error.code = "LANDSCAPE_PATH_NOT_CONFINED";
      throw error;
    }
    return realTarget;
  }

  importLandscape(landscapePath, alias) {
    const confinedPath = this.#resolveConfinedLandscapePath(landscapePath);
    const xml = fs.readFileSync(confinedPath, "utf8");
    assertNoSecrets({ landscape: xml });
    const serviceTags = [...xml.matchAll(/<Service\b([^>]*)\/?\s*>/giu)];
    if (serviceTags.length === 0) throw new Error("No SAP GUI service entries were found in the landscape");
    const requestedAlias = alias ? safeAlias(alias) : null;
    const candidates = serviceTags.map((match) => parseAttributes(match[1]));
    const selected = requestedAlias
      ? candidates.find((item) => [item.name, item.systemid, `${item.systemid}-${item.client}`].some((value) => value?.toLowerCase() === requestedAlias.toLowerCase())) ?? candidates[0]
      : candidates[0];
    const systemId = selected.systemid ?? selected.sid;
    const client = selected.client ?? "000";
    const connectionAlias = requestedAlias ?? `${systemId}-${client}`;
    const messageServer = selected.messageserver ?? selected.msserver;
    return this.prepare({
      alias: connectionAlias,
      systemId,
      client,
      language: selected.language ?? "EN",
      mode: messageServer ? "messageServer" : "applicationServer",
      ...(messageServer
        ? { messageServer, logonGroup: selected.logongroup ?? selected.group ?? "PUBLIC", systemNumber: selected.systemnumber ?? selected.sysnr ?? "00" }
        : { applicationServer: selected.server ?? selected.applicationserver, systemNumber: selected.systemnumber ?? selected.sysnr ?? "00" }),
      sncEnabled: truthy(selected.sncop ?? selected.snc),
      ssoEnabled: truthy(selected.sncop ?? selected.snc),
    });
  }
}

export function testReachability({ host, port, timeoutMs = 3000 }) {
  assertNoSecrets({ host, port, timeoutMs });
  return new Promise((resolve) => {
    const startedAt = Date.now();
    const socket = net.createConnection({ host, port: Number(port) });
    let settled = false;
    const finish = (reachable, errorCode = null) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve({ reachable, authenticated: false, latencyMs: Date.now() - startedAt, errorCode });
    };
    socket.setTimeout(timeoutMs, () => finish(false, "TIMEOUT"));
    socket.once("connect", () => finish(true));
    socket.once("error", (error) => finish(false, error.code ?? "NETWORK_ERROR"));
  });
}

export function connectionEndpoint(connection) {
  if (!connection?.configured) throw new Error("Connection alias is not configured");
  if (connection.mode === "messageServer") {
    const suffix = connection.systemNumber ?? "00";
    return { host: connection.messageServer, port: Number(`36${suffix}`) };
  }
  return { host: connection.applicationServer, port: Number(`32${connection.systemNumber}`) };
}
