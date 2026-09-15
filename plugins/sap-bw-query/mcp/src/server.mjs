import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { TOOL_DEFINITIONS } from "./tool-registry.mjs";
import { createToolHandlers } from "./tool-handlers.mjs";
import { ConnectionStore } from "./connection-store.mjs";
import { DraftStore } from "./draft-state.mjs";
import { BridgeBroker, pipePathForHome } from "./bridge-broker.mjs";
import { StepStore } from "./step-store.mjs";
import { StudioService } from "./studio-service.mjs";
import { SECRET_REJECTION_MESSAGE, SecretRejectedError } from "./secret-guard.mjs";

const HEX40 = /^[0-9a-f]{40}$/;
const PLACEHOLDER = "source-commit";

// Resolves the build provenance. Same-directory lookup works for both source
// (mcp/src/server.mjs -> there is no provenance.json next to it in dev) and the
// bundle (dist/server.mjs -> dist/provenance.json sits beside it).
// `provenanceDir` is optional and exists for DI in tests; in production the
// directory of the current module is used.
export function loadProvenance(provenanceDir) {
  const dir = provenanceDir ?? path.dirname(fileURLToPath(import.meta.url));
  const file = path.join(dir, "provenance.json");
  try {
    if (fs.existsSync(file)) {
      const parsed = JSON.parse(fs.readFileSync(file, "utf8"));
      if (parsed && HEX40.test(parsed.commit)) {
        return {
          commit: parsed.commit,
          source: parsed.source ?? "git-head",
          builtAt: parsed.builtAt ?? null,
          // trusted requires a verifiable commit AND a clean worktree. A dirty
          // worktree (uncommitted changes) downgrades trust so consumers know the
          // artifact does not exactly match the recorded commit.
          dirty: parsed.dirty ?? null,
          trusted: parsed.dirty !== true,
        };
      }
      return { commit: null, source: "parse-error", trusted: false };
    }
  } catch {
    return { commit: null, source: "parse-error", trusted: false };
  }
  // Dev fallback: honor an env override, but never the literal placeholder.
  const envCommit = process.env.BW_AUTOMATION_PLUGIN_COMMIT;
  if (envCommit && envCommit !== PLACEHOLDER && HEX40.test(envCommit)) {
    return { commit: envCommit, source: "env-override", trusted: true };
  }
  if (envCommit === PLACEHOLDER) {
    return { commit: null, source: "env-placeholder-ignored", trusted: false };
  }
  return { commit: null, source: "dev-unpinned", trusted: false };
}

function studioHome() {
  if (process.env.BW_AUTOMATION_HOME) return path.resolve(process.env.BW_AUTOMATION_HOME);
  const local = process.env.LOCALAPPDATA;
  if (!local) throw new Error("LOCALAPPDATA is unavailable");
  return path.join(local, "BWAutomationStudio");
}

export function createMcpServer(handlers) {
  const server = new Server(
    { name: "sap-bw-query", version: "0.3.0" },
    { capabilities: { tools: {} } },
  );
  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: TOOL_DEFINITIONS.map((tool) => ({
      name: tool.name,
      description: `${tool.operationClass}; ${tool.approvalRequired ? "explicit approval required" : "no backend save approval implied"}`,
      inputSchema: tool.inputSchema,
      annotations: {
        readOnlyHint: tool.operationClass !== "mutating tenant" && tool.operationClass !== "destructive",
        destructiveHint: tool.operationClass === "destructive",
        idempotentHint: !["bw_studio_deploy", "bw_studio_rollback", "bw_create_local_draft", "bw_apply_spec_to_draft"].includes(tool.name),
        openWorldHint: tool.operationClass === "read-only tenant" || tool.operationClass === "mutating tenant",
      },
      _meta: { operationClass: tool.operationClass, approvalRequired: tool.approvalRequired },
    })),
  }));
  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const handler = handlers[request.params.name];
    if (!handler) return { isError: true, content: [{ type: "text", text: "Tool is not allow-listed." }] };
    try {
      const result = await handler(request.params.arguments ?? {});
      return { content: [{ type: "text", text: JSON.stringify(result) }] };
    } catch (error) {
      // Secret rejections are deliberately vague so the caller cannot learn
      // whether a value matched. For every other error, surface the real
      // message so operators can diagnose failures instead of seeing a generic
      // "Operation blocked" for unrelated bugs (network, validation, etc.).
      const text = error instanceof SecretRejectedError
        ? SECRET_REJECTION_MESSAGE
        : (typeof error?.message === "string" && error.message.length > 0
          ? error.message
          : "Operation blocked. Inspect password-free local diagnostics for details.");
      return { isError: true, content: [{ type: "text", text }] };
    }
  });
  return server;
}

export async function main() {
  const home = studioHome();
  const broker = new BridgeBroker({ pipePath: process.env.BW_AUTOMATION_PIPE ?? pipePathForHome(home) });
  await broker.start();
  const provenance = loadProvenance();
  const handlers = createToolHandlers({
    studio: new StudioService({ home, script: process.env.BW_STUDIO_SCRIPT }),
    connections: new ConnectionStore({ root: home }),
    drafts: new DraftStore({ root: path.join(home, "drafts") }),
    bridge: broker,
    steps: new StepStore({ root: home }),
    provenance,
  });
  const server = createMcpServer(handlers);
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(() => {
    process.stderr.write("BW Automation MCP failed to start. Run bw_studio_diagnostics; never paste credentials.\n");
    process.exitCode = 1;
  });
}
