import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { assertNoSecrets } from "./secret-guard.mjs";

const pluginRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const ACTION_FIELDS = Object.freeze({
  Status: {},
  Deploy: {
    artifactPath: "ArtifactPath",
    manifestPath: "ManifestPath",
    signaturePath: "SignaturePath",
    trustPolicyPath: "TrustPolicyPath",
    releaseChannelUrl: "ReleaseChannelUrl",
  },
  Launch: { workspacePath: "WorkspacePath", connectionAlias: "ConnectionAlias" },
  Rollback: { targetVersion: "TargetVersion" },
  Diagnostics: {},
});

export class StudioService {
  #home;
  #script;

  constructor({ home, script = path.join(pluginRoot, "scripts/BwStudio.ps1") }) {
    this.#home = path.resolve(home);
    this.#script = path.resolve(script);
  }

  run(action, input = {}) {
    assertNoSecrets(input);
    const fields = ACTION_FIELDS[action];
    if (!fields) throw new Error(`Studio action ${action} is not allow-listed`);
    const unknown = Object.keys(input).filter((key) => !(key in fields));
    if (unknown.length > 0) throw new Error(`Unknown studio input field ${unknown[0]}`);
    const args = ["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", this.#script, "-Action", action, "-Json"];
    for (const [key, parameter] of Object.entries(fields)) {
      if (input[key] !== undefined && input[key] !== null && input[key] !== "") args.push(`-${parameter}`, String(input[key]));
    }
    return new Promise((resolve, reject) => {
      const child = spawn("powershell.exe", args, {
        windowsHide: true,
        env: { ...process.env, BW_AUTOMATION_HOME: this.#home },
        stdio: ["ignore", "pipe", "pipe"],
      });
      let stdout = "";
      let stderr = "";
      // Cap stdout/stderr so a runaway child cannot OOM the process. 5 MiB is
      // well above any legitimate JSON response from BwStudio.ps1; if a stream
      // exceeds the cap we stop accumulating and note the truncation so the
      // error surface stays informative instead of crashing the host.
      const MAX_OUTPUT_BYTES = 5 * 1024 * 1024;
      let stdoutTruncated = false;
      let stderrTruncated = false;
      child.stdout.setEncoding("utf8").on("data", (chunk) => {
        if (stdoutTruncated) return;
        if (stdout.length + chunk.length > MAX_OUTPUT_BYTES) {
          stdout += chunk.slice(0, Math.max(0, MAX_OUTPUT_BYTES - stdout.length));
          stdoutTruncated = true;
          return;
        }
        stdout += chunk;
      });
      child.stderr.setEncoding("utf8").on("data", (chunk) => {
        if (stderrTruncated) return;
        if (stderr.length + chunk.length > MAX_OUTPUT_BYTES) {
          stderr += chunk.slice(0, Math.max(0, MAX_OUTPUT_BYTES - stderr.length));
          stderrTruncated = true;
          return;
        }
        stderr += chunk;
      });
      child.once("error", reject);
      child.once("close", (code) => {
        if (code !== 0) { reject(new Error("Portable studio action failed; inspect bw_studio_diagnostics locally.")); return; }
        if (stdoutTruncated) { reject(new Error("Portable studio response exceeded 5 MiB and was truncated; inspect bw_studio_diagnostics locally.")); return; }
        try { resolve(JSON.parse(stdout)); }
        catch { reject(new Error("Portable studio returned an invalid response")); }
      });
    });
  }
}
