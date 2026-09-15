#!/usr/bin/env node

const CONFIG_PATH_MARKERS = [
  "/package.json",
  "/package-lock.json",
  "/pnpm-lock.yaml",
  "/yarn.lock",
  "/bun.lock",
  "/bun.lockb",
  "/deno.json",
  "/deno.lock",
  "/.mcp.json",
  "/mcp.json",
  "/.npmrc",
  "/.yarnrc.yml",
  "/pnpm-workspace.yaml",
  "/renovate.json",
  "/dependabot.yml",
  "/dependabot.yaml"
];

const EXECUTABLE_PACKAGE_COMMANDS = new Set(["npx", "bunx", "uvx"]);
const PACKAGE_FLAGS_WITH_VALUE = new Set(["--package", "-p", "--package-manager"]);
const IGNORE_EXEC_TOKENS = new Set([
  "npm",
  "pnpm",
  "yarn",
  "bun",
  "node",
  "python",
  "python3",
  "bash",
  "sh",
  "cmd",
  "powershell",
  "pwsh"
]);

function readStdin() {
  return new Promise((resolve) => {
    let data = "";
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", (chunk) => {
      data += chunk;
    });
    process.stdin.on("end", () => resolve(data));
    process.stdin.on("error", () => resolve(""));
  });
}

function printJson(value) {
  process.stdout.write(`${JSON.stringify(value)}\n`);
}

function empty() {
  printJson({});
}

function normalizePath(filePath) {
  const normalized = (filePath || "").replace(/\\/g, "/").toLowerCase();
  return normalized.startsWith("/") ? normalized : `/${normalized}`;
}

function collectStrings(value, out = [], depth = 0) {
  if (depth > 6 || out.length > 200) return out;
  if (typeof value === "string") {
    out.push(value);
    return out;
  }
  if (Array.isArray(value)) {
    for (const item of value) collectStrings(item, out, depth + 1);
    return out;
  }
  if (value && typeof value === "object") {
    for (const nested of Object.values(value)) collectStrings(nested, out, depth + 1);
  }
  return out;
}

function toolContent(payload) {
  const toolInput = payload.tool_input || {};
  const toolResponse = payload.tool_response || {};
  const targeted = [
    toolInput.content,
    toolInput.new_string,
    toolInput.old_string,
    toolInput.edits,
    toolInput.diff,
    toolResponse.content,
    toolResponse.text
  ].filter((value) => value !== undefined);
  if (targeted.length > 0) {
    return collectStrings(targeted).join("\n").slice(0, 200000);
  }
  return collectStrings([toolInput, toolResponse]).join("\n").slice(0, 200000);
}

function filePath(payload) {
  const toolInput = payload.tool_input || {};
  const toolResponse = payload.tool_response || {};
  return normalizePath(toolInput.file_path || toolInput.filePath || toolInput.path || toolResponse.file_path || toolResponse.filePath || "");
}

function isRelevantPath(pathLower, contentLower) {
  if (CONFIG_PATH_MARKERS.some((marker) => pathLower.endsWith(marker))) return true;
  return contentLower.includes('"mcpservers"') || contentLower.includes('"dependencies"') || contentLower.includes('"devdependencies"');
}

function stripQuotes(token) {
  return token.replace(/^['"]|['"]$/g, "").replace(/[;,]$/g, "");
}

function shellTokens(command) {
  const tokens = [];
  const pattern = /"([^"\\]*(?:\\.[^"\\]*)*)"|'([^'\\]*(?:\\.[^'\\]*)*)'|(\S+)/g;
  for (const match of command.matchAll(pattern)) {
    tokens.push(stripQuotes(match[1] ?? match[2] ?? match[3] ?? ""));
  }
  return tokens.filter(Boolean);
}

function looksLikePackageSpec(token) {
  if (!token || token.startsWith("-") || token.startsWith(".") || token.startsWith("/") || token.includes("=")) return false;
  if (token.includes("${") || token.includes("$(") || token.includes(":") || token.includes("\\")) return false;
  if (IGNORE_EXEC_TOKENS.has(token.toLowerCase())) return false;
  if (/^(https?|git|file):/i.test(token)) return false;
  return /^(?:@[a-z0-9._-]+\/)?[a-z0-9._-]+(?:@[a-z0-9][a-z0-9._+-]*)?$/i.test(token);
}

function hasExactVersionPin(spec) {
  const token = stripQuotes(spec);
  if (!looksLikePackageSpec(token)) return true;
  const versionMatch = token.startsWith("@")
    ? token.match(/^@[a-z0-9._-]+\/[a-z0-9._-]+@(.+)$/i)
    : token.match(/^[a-z0-9._-]+@(.+)$/i);
  if (!versionMatch) return false;
  return /^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/.test(versionMatch[1]);
}

function packageSpecName(spec) {
  const token = stripQuotes(spec);
  if (token.startsWith("@")) {
    const atIndex = token.indexOf("@", 1);
    return atIndex === -1 ? token : token.slice(0, atIndex);
  }
  const atIndex = token.indexOf("@");
  return atIndex === -1 ? token : token.slice(0, atIndex);
}

function findLatestRisks(text) {
  const risks = [];
  if (/(?:^|[\s"'`])(?:@[a-z0-9._-]+\/)?[a-z0-9._-]+@latest\b/i.test(text) || /["']latest["']/.test(text)) {
    risks.push("Floating dependency version detected: @latest or latest. Pin SAP and MCP executable packages to an exact version.");
  }
  return risks;
}

function extractMcpPackageSpecsFromJson(text) {
  const specs = [];
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    return specs;
  }

  const servers = parsed.mcpServers || parsed;
  if (!servers || typeof servers !== "object") return specs;

  for (const server of Object.values(servers)) {
    if (!server || typeof server !== "object") continue;
    const command = String(server.command || "").toLowerCase();
    const args = Array.isArray(server.args) ? server.args.map(String) : [];
    if (!["npx", "bunx", "uvx"].includes(command)) continue;

    for (let index = 0; index < args.length; index += 1) {
      const arg = stripQuotes(args[index]);
      if (!arg || arg.startsWith("-")) {
        if (PACKAGE_FLAGS_WITH_VALUE.has(arg) && args[index + 1]) {
          specs.push(args[index + 1]);
          index += 1;
        }
        continue;
      }
      if (looksLikePackageSpec(arg)) {
        specs.push(arg);
        break;
      }
    }
  }
  return specs;
}

function findBareExecutableRisks(text, pathLower) {
  const risks = [];
  const specs = new Set(extractMcpPackageSpecsFromJson(text));

  const tokens = shellTokens(text);
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index].toLowerCase();
    const next = tokens[index + 1]?.toLowerCase();

    if (EXECUTABLE_PACKAGE_COMMANDS.has(token) || (token === "pnpm" && next === "dlx") || (token === "yarn" && next === "dlx")) {
      const start = token === "pnpm" || token === "yarn" ? index + 2 : index + 1;
      for (let specIndex = start; specIndex < tokens.length; specIndex += 1) {
        const candidate = tokens[specIndex];
        if (!candidate) continue;
        if (candidate.startsWith("-")) {
          if (PACKAGE_FLAGS_WITH_VALUE.has(candidate) && tokens[specIndex + 1]) {
            specs.add(tokens[specIndex + 1]);
            specIndex += 1;
          }
          continue;
        }
        if (looksLikePackageSpec(candidate)) specs.add(candidate);
        break;
      }
    }
  }

  for (const spec of specs) {
    if (!hasExactVersionPin(spec)) {
      const name = packageSpecName(spec);
      const source = pathLower.endsWith("/.mcp.json") || pathLower.endsWith("/mcp.json") ? "MCP executable" : "Executable package";
      risks.push(`${source} is not exact-pinned: ${spec}. Use an exact version such as ${name}@x.y.z.`);
    }
  }

  return risks;
}

function findCredentialLiteralRisks(text) {
  const risks = [];
  const markers = ["${", "<", "your_", "your-", "placeholder", "changeme", "example", "dummy", "test", "xxxx", "redacted"];
  const pattern = /["']?([A-Za-z0-9_-]*(?:password|passwd|secret|api[_-]?key|apikey|client[_-]?secret|clientsecret|access[_-]?token|accesstoken|token)[A-Za-z0-9_-]*)["']?\s*[:=]\s*["']([^"'\n\r]{8,})["']/gi;
  for (const match of text.matchAll(pattern)) {
    const key = match[1];
    const value = match[2].trim().toLowerCase();
    if (markers.some((marker) => value.includes(marker))) continue;
    if (/^[A-Z0-9_]+$/.test(match[2].trim())) continue;
    risks.push(`Credential-like literal detected for ${key}. Move secrets to environment variables, service bindings, or a secret manager.`);
    break;
  }
  return risks;
}

function detectRisks(payload) {
  const toolName = payload.tool_name || "";
  const toolInput = payload.tool_input || {};
  const command = typeof toolInput.command === "string" ? toolInput.command : "";
  const pathLower = filePath(payload);
  const content = toolName === "Bash" ? command : toolContent(payload);
  const contentLower = content.toLowerCase();

  if (toolName === "Bash" && !/(?:\bnpx\b|\bbunx\b|\buvx\b|\bpnpm\s+dlx\b|\byarn\s+dlx\b|\bnpm\s+(?:install|add|exec)\b|\bpnpm\s+add\b|\byarn\s+add\b|\bbun\s+add\b)/i.test(command)) {
    return [];
  }

  if (toolName !== "Bash" && !isRelevantPath(pathLower, contentLower)) {
    return [];
  }

  const risks = [
    ...findLatestRisks(content),
    ...findBareExecutableRisks(content, pathLower),
    ...findCredentialLiteralRisks(content)
  ];

  return [...new Set(risks)];
}

async function main() {
  const raw = await readStdin();
  if (!raw.trim()) return empty();

  let payload;
  try {
    payload = JSON.parse(raw);
  } catch {
    return empty();
  }

  const event = payload.hook_event_name || "";
  if (event !== "PreToolUse" && event !== "PostToolUse") return empty();

  const risks = detectRisks(payload);
  if (risks.length === 0) return empty();

  const context = [
    "DEPENDENCY SECURITY RISK - stop and review before proceeding.",
    "",
    ...risks.map((risk) => `- ${risk}`),
    "",
    "Use exact package pins for executable MCP/package tools, keep secrets out of config files, and document any exception in the dependency review notes."
  ].join("\n");

  const hookSpecificOutput = {
    hookEventName: event,
    additionalContext: context
  };

  if (event === "PreToolUse") {
    hookSpecificOutput.permissionDecision = "deny";
    hookSpecificOutput.permissionDecisionReason = "Dependency or MCP security risk detected; user confirmation is required before this tool call can proceed.";
  }

  return printJson({ hookSpecificOutput });
}

await main();
