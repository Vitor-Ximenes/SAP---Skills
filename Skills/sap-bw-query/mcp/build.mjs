import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";

const HEX40 = /^[0-9a-f]{40}$/;
const PLACEHOLDER = "source-commit";

const root = path.dirname(fileURLToPath(import.meta.url));
const dist = path.join(root, "dist");
fs.mkdirSync(dist, { recursive: true });

const result = await build({
  entryPoints: [path.join(root, "src/server.mjs")],
  outfile: path.join(dist, "server.mjs"),
  bundle: true,
  platform: "node",
  format: "esm",
  target: "node24",
  packages: "bundle",
  sourcemap: false,
  legalComments: "external",
  metafile: true,
});

fs.writeFileSync(path.join(dist, "build-meta.json"), JSON.stringify(result.metafile, null, 2));

// Provenance resolution: refuse to build without a verifiable source commit.
// Order: (a) BW_AUTOMATION_PLUGIN_COMMIT env if set, not the placeholder, and 40-hex;
//        (b) `git rev-parse --verify HEAD` from the current working directory;
//        (c) throw — never silently fall through to a placeholder.
function resolveProvenance() {
  const envCommit = process.env.BW_AUTOMATION_PLUGIN_COMMIT;
  // Normalize to lowercase so an uppercase override is not silently rejected.
  const normalized = envCommit ? envCommit.toLowerCase() : envCommit;
  if (normalized && normalized !== PLACEHOLDER && HEX40.test(normalized)) {
    return { commit: normalized, source: "env-override", dirty: null };
  }
  // Resolve git metadata from the plugin/mcp directory (this file's location),
  // not process.cwd(), so a build started from a parent workspace records the
  // plugin's own commit and worktree state.
  const gitCwd = root;
  const git = spawnSync("git", ["rev-parse", "--verify", "HEAD"], {
    cwd: gitCwd,
    encoding: "utf8",
  });
  const head = (git.stdout ?? "").trim();
  if (git.status === 0 && HEX40.test(head)) {
    // Capture worktree cleanliness. A dirty worktree means the built artifact
    // does not exactly match the recorded commit, so provenance is still
    // emitted but marked dirty for downstream trust decisions.
    const status = spawnSync("git", ["status", "--porcelain"], { cwd: gitCwd, encoding: "utf8" });
    const dirty = status.status === 0 ? (status.stdout ?? "").trim() !== "" : null;
    return { commit: head, source: "git-head", dirty };
  }
  throw new Error(
    "cannot establish provenance; refusing to build without a verifiable commit. " +
      `Set BW_AUTOMATION_PLUGIN_COMMIT to a 40-hex SHA or run from a clean git repo ` +
      `(env=${envCommit ? JSON.stringify(envCommit) : "unset"}, git-status=${git.status}).`,
  );
}

const { commit, source, dirty } = resolveProvenance();
const provenance = {
  commit,
  source,
  // dirty: true = worktree had uncommitted changes; false = clean; null = unknown
  // (only set for git-head source). trusted=true requires a clean worktree.
  ...(dirty !== null ? { dirty } : {}),
  builtAt: new Date().toISOString(),
  note: "emit only from a clean build; never hand-edit",
};
fs.writeFileSync(path.join(dist, "provenance.json"), JSON.stringify(provenance, null, 2) + "\n");
