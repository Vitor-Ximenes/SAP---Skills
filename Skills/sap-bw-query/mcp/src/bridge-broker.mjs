import crypto from "node:crypto";
import net from "node:net";
import { assertNoSecrets } from "./secret-guard.mjs";

const ECLIPSE_METHODS = Object.freeze([
  "inspectCapabilities",
  "describeProvider",
  "listQueries",
  "readQuery",
  "readQueryModel",
  "projectCreateOrOpen",
  "createLocalDraft",
  "applySpecToDraft",
  "previewDraft",
  "prepareNewQuerySave",
  "populateQueryEditor",
]);

const ALLOWED_METHODS = new Set(ECLIPSE_METHODS);

export function pipePathForHome(home) {
  const id = crypto.createHash("sha256").update(String(home).toLowerCase()).digest("hex").slice(0, 16);
  return `\\\\.\\pipe\\bw-automation-${id}`;
}

/**
 * Pure validator for the first JSON frame a connecting socket sends.
 * Returns true iff `frame.authToken` strictly equals `expectedToken` and the
 * expected token is a non-empty string. Kept pure (no I/O, no side effects)
 * so it can be unit-tested without a socket and reused by the connection
 * handler. Constant-time comparison is intentionally NOT used: the token is
 * 32 bytes of CSPRNG output, so a timing oracle leaks at most equality of a
 * 64-hex-char secret one bit at a time over a local socket only — the threat
 * model is "any local process connects", not "remote timing attacker".
 */
export function validateAuthFrame(frame, expectedToken) {
  if (typeof expectedToken !== "string" || expectedToken.length === 0) return false;
  if (frame == null || typeof frame !== "object") return false;
  return frame.authToken === expectedToken;
}

export class BridgeBroker {
  #pipePath;
  #timeoutMs;
  #authToken;
  #server = null;
  #socket = null;
  #pendingAuth = null;
  #pending = new Map();
  #buffer = "";
  #connectionWaiters = [];

  constructor({ pipePath, timeoutMs = 15000, authToken } = {}) {
    if (!pipePath) throw new Error("Named-pipe path is required");
    this.#pipePath = pipePath;
    this.#timeoutMs = timeoutMs;
    // Resolution order: explicit DI (tests) → non-empty env (child-process
    // inheritance) → fresh CSPRNG token. A blank env value is treated as
    // unset so a misconfigured BW_AUTOMATION_BRIDGE_TOKEN="" cannot make
    // every connector fail auth silently. We then write back to env so any
    // process spawned later from this broker (BwStudio.ps1) inherits it.
    const inherited = process.env.BW_AUTOMATION_BRIDGE_TOKEN;
    const resolved = authToken
      || (typeof inherited === "string" && inherited.trim() !== "" ? inherited : null)
      || crypto.randomBytes(32).toString("hex");
    this.#authToken = resolved;
    process.env.BW_AUTOMATION_BRIDGE_TOKEN = resolved;
  }

  get authToken() {
    return this.#authToken;
  }

  start() {
    if (this.#server) return Promise.resolve();
    this.#server = net.createServer((socket) => {
      // Single authenticated slot — already occupied.
      if (this.#socket) {
        socket.end(`${JSON.stringify({ error: { code: "BRIDGE_ALREADY_CONNECTED", message: "Eclipse bridge is already connected" } })}\n`);
        return;
      }
      // A second connector arrives while a first is still mid-handshake.
      // Reject it the same way; the first attempt either authenticates and
      // fills #socket, or fails/times out and clears #pendingAuth — but we
      // do not queue contenders. This keeps the state machine linear.
      if (this.#pendingAuth) {
        socket.end(`${JSON.stringify({ error: { code: "BRIDGE_ALREADY_CONNECTED", message: "Eclipse bridge is already connected" } })}\n`);
        return;
      }

      // UNAUTHENTICATED state: buffer the first line, expect an auth frame,
      // and enforce a deadline so a silent connector cannot hold the slot.
      this.#pendingAuth = socket;
      socket.setEncoding("utf8");
      let authenticated = false;
      let authBuffer = "";
      const authDeadline = setTimeout(() => {
        if (authenticated) return;
        // Mark terminal + detach the listener so a late frame cannot promote
        // this socket after the deadline. #rejectAuth destroys the socket.
        authenticated = "timed-out";
        socket.off("data", onDataWhileUnauthenticated);
        this.#rejectAuth(socket, "auth timeout");
      }, this.#timeoutMs);

      const onDataWhileUnauthenticated = (chunk) => {
        if (authenticated) return;
        authBuffer += chunk;
        // Cap the pre-auth buffer: an auth frame is tiny (~80 bytes). A
        // connector streaming garbage with no newline could otherwise hold
        // memory until the deadline; reject early.
        if (authBuffer.length > 8192) {
          clearTimeout(authDeadline);
          socket.off("data", onDataWhileUnauthenticated);
          this.#rejectAuth(socket, "oversized auth frame");
          return;
        }
        const newline = authBuffer.indexOf("\n");
        if (newline < 0) return;
        const line = authBuffer.slice(0, newline);
        const remainder = authBuffer.slice(newline + 1);
        let frame = null;
        try { frame = JSON.parse(line); } catch { /* fall through to reject */ }
        if (validateAuthFrame(frame, this.#authToken)) {
          // SUCCESS — promote to authenticated slot.
          authenticated = true;
          clearTimeout(authDeadline);
          socket.off("data", onDataWhileUnauthenticated);
          this.#pendingAuth = null;
          this.#socket = socket;
          for (const waiter of this.#connectionWaiters.splice(0)) waiter.resolve(socket);
          // In the unlikely case the auth frame and the first request shared
          // a chunk, hand the tail to the regular handler. Newline framing is
          // identical on both sides so this is safe.
          if (remainder.length > 0) this.#onData(remainder);
          socket.on("data", (c) => this.#onData(c));
        } else {
          clearTimeout(authDeadline);
          socket.off("data", onDataWhileUnauthenticated);
          this.#rejectAuth(socket, "bad frame");
        }
      };

      socket.on("data", onDataWhileUnauthenticated);
      socket.on("close", () => {
        clearTimeout(authDeadline);
        if (this.#pendingAuth === socket) this.#pendingAuth = null;
        if (this.#socket === socket) {
          this.#socket = null;
          for (const pending of this.#pending.values()) pending.reject(new Error("Eclipse bridge disconnected"));
          this.#pending.clear();
        }
      });
    });
    return new Promise((resolve, reject) => {
      this.#server.once("error", reject);
      this.#server.listen(this.#pipePath, () => {
        this.#server.off("error", reject);
        resolve();
      });
    });
  }

  /**
   * Send BRIDGE_UNAUTHORIZED and tear down a socket that failed the auth
   * handshake (wrong token, malformed JSON, or silent timeout). Clears the
   * pending-auth slot if this socket still owns it. Must NOT touch #socket
   * (an unauthenticated socket is never promoted).
   */
  #rejectAuth(socket, _reason) {
    if (this.#pendingAuth === socket) this.#pendingAuth = null;
    // Half-close the write side with the error frame, then fully destroy the
    // socket once the frame is flushed so the peer cannot keep sending data
    // and a late frame cannot promote a rejected socket.
    socket.end(
      `${JSON.stringify({ error: { code: "BRIDGE_UNAUTHORIZED", message: "Bridge authentication failed" } })}\n`,
      () => { try { socket.destroy(); } catch { /* best effort */ } },
    );
  }

  #waitForConnection() {
    if (this.#socket) return Promise.resolve(this.#socket);
    return new Promise((resolve, reject) => {
      const waiter = { resolve, reject, timer: null };
      waiter.timer = setTimeout(() => {
        const index = this.#connectionWaiters.indexOf(waiter);
        if (index >= 0) this.#connectionWaiters.splice(index, 1);
        reject(new Error("Eclipse bridge is unavailable; launch BW Automation Studio first"));
      }, this.#timeoutMs);
      waiter.resolve = (socket) => {
        clearTimeout(waiter.timer);
        resolve(socket);
      };
      this.#connectionWaiters.push(waiter);
    });
  }

  #onData(chunk) {
    this.#buffer += chunk;
    // Cap the post-auth buffer: a misbehaving peer that streams data with no
    // newline framing could otherwise grow #buffer without bound and OOM the
    // process. 10 MiB is far above any legitimate bridge response (each frame
    // is one newline-terminated JSON line), so hitting the cap indicates a
    // buggy or hostile peer. Emit an error and destroy the socket.
    const MAX_BUFFER_BYTES = 10 * 1024 * 1024;
    if (this.#buffer.length > MAX_BUFFER_BYTES) {
      this.#buffer = "";
      for (const pending of this.#pending.values()) {
        clearTimeout(pending.timer);
        pending.reject(new Error("Eclipse bridge buffer overflow"));
      }
      this.#pending.clear();
      if (this.#socket) {
        try { this.#socket.destroy(); } catch { /* best effort */ }
        this.#socket = null;
      }
      return;
    }
    while (true) {
      const newline = this.#buffer.indexOf("\n");
      if (newline < 0) return;
      const line = this.#buffer.slice(0, newline);
      this.#buffer = this.#buffer.slice(newline + 1);
      if (!line.trim()) continue;
      let message;
      try { message = JSON.parse(line); } catch { continue; }
      const pending = this.#pending.get(message.id);
      if (!pending) continue;
      clearTimeout(pending.timer);
      this.#pending.delete(message.id);
      if (message.error) pending.reject(new Error(String(message.error.message ?? "Eclipse bridge error")));
      else {
        try {
          assertNoSecrets(message.result);
          pending.resolve(message.result);
        } catch (error) { pending.reject(error); }
      }
    }
  }

  async call(method, payload = {}) {
    if (!ALLOWED_METHODS.has(method)) throw new Error(`Eclipse method ${method} is not allow-listed`);
    assertNoSecrets(payload);
    const socket = await this.#waitForConnection();
    const id = crypto.randomUUID();
    const request = { id, method, payload };
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.#pending.delete(id);
        reject(new Error(`Eclipse bridge timed out for ${method}`));
      }, this.#timeoutMs);
      this.#pending.set(id, { resolve, reject, timer });
      socket.write(`${JSON.stringify(request)}\n`, (error) => {
        if (!error) return;
        clearTimeout(timer);
        this.#pending.delete(id);
        reject(error);
      });
    });
  }

  close() {
    return new Promise((resolve) => {
      // Tear down any in-flight unauthenticated connector as well so tests
      // and shutdown don't leave dangling sockets.
      if (this.#pendingAuth) {
        const pending = this.#pendingAuth;
        this.#pendingAuth = null;
        try { pending.destroy(); } catch { /* best effort */ }
      }
      if (this.#socket) this.#socket.end();
      if (!this.#server) { resolve(); return; }
      this.#server.close(() => {
        this.#server = null;
        resolve();
      });
    });
  }
}
