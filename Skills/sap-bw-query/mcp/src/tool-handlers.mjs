import fs from "node:fs";
import { assertNoSecrets, sanitizeForLog, SecretRejectedError } from "./secret-guard.mjs";
import { resolveAndValidateSpec } from "./query-spec.mjs";
import { normalizeProviderMetadata } from "./provider-metadata.mjs";
import { connectionEndpoint, testReachability } from "./connection-store.mjs";
import { verifyPopulation, summarizeVerification } from "./populate-verify.mjs";
import { runRules, normalizeFromSpec, normalizeFromModel } from "./query-rules.mjs";
import { markResponseUntrusted } from "./untrusted-content.mjs";
import { TOOL_DEFINITIONS } from "./tool-registry.mjs";

function wrap(name, steps, handler) {
  return async (input = {}) => {
    try {
      assertNoSecrets(input);
      steps.append?.({ tool: name, status: "STARTED", input: sanitizeForLog(input) });
      const result = await handler(input);
      steps.append?.({ tool: name, status: "COMPLETED" });
      return result;
    } catch (error) {
      steps.append?.({
        tool: name,
        status: "BLOCKED",
        sticky: error instanceof SecretRejectedError,
        message: error instanceof SecretRejectedError ? error.message : "Operation blocked; inspect local diagnostics.",
      });
      throw error;
    }
  };
}

export function createToolHandlers({
  studio,
  connections,
  drafts,
  bridge,
  steps = {},
  provenance = { commit: null, source: "dev-unpinned", trusted: false },
}) {
  const raw = {
    bw_studio_status: async () => {
      const status = await studio.run("Status", {});
      return { ...status, provenance };
    },
    bw_studio_deploy: async (input) => {
      // Finding #2 (Node half): refuse to forward an unsigned-bundle deploy unless the
      // human has opted in via BW_AUTOMATION_ALLOW_UNSIGNED_BUNDLE=1 at launch. Signed
      // manifests (any keyId other than "LOCAL-UNSIGNED") pass without the opt-in.
      //
      // Critical hardening: a manifest path is mandatory. Without it the manifest-read block
      // below was previously skipped, keyId stayed undefined, and the handler fell through to
      // studio.run with NO gate applied — letting a prompt-injected caller deploy arbitrary
      // code by simply omitting manifestPath. Reject hard before any studio.run call.
      if (!input?.manifestPath) {
        const err = new Error("A deploy manifest path is required; refusing to forward an unverified deploy.");
        err.code = "MANIFEST_UNREADABLE";
        throw err;
      }
      let keyId;
      try {
        const parsed = JSON.parse(fs.readFileSync(input.manifestPath, "utf8"));
        keyId = parsed?.keyId;
      } catch {
        const err = new Error("The deploy manifest could not be read or parsed. Refusing to forward the deploy.");
        err.code = "MANIFEST_UNREADABLE";
        throw err;
      }
      if (keyId === "LOCAL-UNSIGNED" && process.env.BW_AUTOMATION_ALLOW_UNSIGNED_BUNDLE !== "1") {
        const err = new Error(
          "Deploying an unsigned local bundle is disabled by default. To allow it, set BW_AUTOMATION_ALLOW_UNSIGNED_BUNDLE=1 in the studio's launch environment AND configure a signed key in config/trusted-publishers.json. Unsigned bundles execute arbitrary code as the user.",
        );
        err.code = "UNSIGNED_BUNDLE_NOT_ALLOWED";
        throw err;
      }
      return studio.run("Deploy", input);
    },
    bw_studio_launch: (input) => studio.run("Launch", input),
    bw_studio_rollback: (input) => studio.run("Rollback", input),
    bw_studio_diagnostics: () => studio.run("Diagnostics", {}),
    bw_connection_prepare: ({ connection }) => connections.prepare(connection),
    bw_connection_import_landscape: ({ landscapePath, alias }) => connections.importLandscape(landscapePath, alias),
    bw_connection_test_reachability: async ({ alias, timeoutMs }) => {
      const endpoint = connectionEndpoint(connections.status(alias));
      // Test seam: ConnectionStore does not implement reachability; tests inject a fake.
      // Falls through to the real TCP testReachability in production.
      return connections.reachability ? connections.reachability({ ...endpoint, timeoutMs }) : testReachability({ ...endpoint, timeoutMs });
    },
    bw_project_create_or_open: async (input) => {
      const connection = connections.status(input.alias);
      const result = await bridge.call("projectCreateOrOpen", { ...input, connection });
      return connection.ssoEnabled === true
        ? result
        : { ...result, userActionRequired: true, instruction: "Enter the password only in the native SAP login dialog. Passwords are never accepted by the automation." };
    },
    bw_connection_status: ({ alias }) => connections.status(alias),
    bw_inspect_capabilities: (input) => bridge.call("inspectCapabilities", input),
    // TODO(security): these four return BW-controlled free-text (descriptions, formulas).
    // markResponseUntrusted is the mandatory whole-payload layer. The per-field
    // wrapUntrustedValue helper (untrusted-content.mjs) is bundled for these call sites
    // and should be applied to the high-risk free-text fields once the harness
    // convention for unwrapping is settled. See finding #4.
    bw_describe_provider: async (input) => markResponseUntrusted(await bridge.call("describeProvider", input)),
    bw_list_queries: async (input) => markResponseUntrusted(await bridge.call("listQueries", input)),
    bw_read_query: async (input) => markResponseUntrusted(await bridge.call("readQuery", input)),
    bw_read_query_model: async (input) => markResponseUntrusted(await bridge.call("readQueryModel", input)),
    bw_review_query: async ({ alias, project, technicalName }) => {
      // Read-only best-practices review of an OPEN query. Reuses the Task-A deep-read bridge
      // call (no new bridge method) and runs the shared rule engine over the deep model.
      const model = await bridge.call("readQueryModel", { alias, project, technicalName });
      if (!model || model.found !== true) {
        return {
          found: false,
          userActionRequired: model?.userActionRequired,
          instruction: model?.instruction,
          findings: [],
        };
      }
      // Only mark the final review object when BW content was actually read (found === true).
      // The early `found !== true` branch above returns unwrapped, since no BW content is surfaced.
      return markResponseUntrusted({
        found: true,
        technicalName: model.technicalName ?? technicalName ?? null,
        provider: model.provider ?? null,
        findings: runRules(normalizeFromModel(model)),
        serializationIssues: model.serializationIssues ?? [],
        readOnly: true,
      });
    },
    bw_resolve_and_validate_spec: async ({ spec, alias }) => {
      let providerMetadata = null;
      if (alias !== undefined) {
        try {
          const described = await bridge.call("describeProvider", {
            alias,
            project: spec?.target?.project,
            provider: spec?.target?.provider,
          });
          providerMetadata = normalizeProviderMetadata(described);
        } catch {
          providerMetadata = { available: false, reason: "BRIDGE_UNAVAILABLE", instruction: "Launch the studio and open the BW project, then rerun validation with the alias to verify names against the provider." };
        }
      }
      const result = resolveAndValidateSpec(spec, { providerMetadata });
      // Additive best-practices review of a draft spec; only meaningful for a valid spec.
      const bestPractices = result.valid === true ? runRules(normalizeFromSpec(spec)) : [];
      return { ...result, bestPractices };
    },
    bw_create_local_draft: async ({ spec }) => {
      const draft = drafts.create(spec);
      await bridge.call("createLocalDraft", draft);
      return draft;
    },
    bw_apply_spec_to_draft: async ({ draftId, spec }) => {
      const draft = drafts.apply(draftId, spec);
      await bridge.call("applySpecToDraft", draft);
      return draft;
    },
    bw_preview_draft: async ({ draftId }) => {
      const draft = drafts.get(draftId);
      return bridge.call("previewDraft", draft);
    },
    bw_prepare_new_query_save: async ({ draftId }) => {
      const draft = drafts.get(draftId);
      const existing = await bridge.call("listQueries", {
        alias: null,
        project: draft.spec.target.project,
        provider: draft.spec.target.provider,
      });
      const prepared = drafts.prepareSave(draftId, { existingTechnicalNames: existing.technicalNames ?? [] });
      await bridge.call("prepareNewQuerySave", prepared);
      return prepared;
    },
    bw_populate_query_editor: async ({ draftId }) => {
      const draft = drafts.get(draftId);
      if (draft.state !== "SAVE_PENDING_HUMAN") {
        throw new Error("Run bw_prepare_new_query_save first, confirm in Eclipse, and finish the native wizard before populating the editor.");
      }
      const result = await bridge.call("populateQueryEditor", {
        id: draft.id,
        spec: draft.spec,
        specHash: draft.specHash,
        confirmationBinding: {
          technicalName: draft.spec.technicalName,
          provider: draft.spec.target.provider,
          specHash: draft.specHash,
        },
      });
      const output = { ...result, saved: false };
      if (result?.populated === true) {
        try {
          const model = await bridge.call("readQueryModel", {
            alias: null,
            project: draft.spec.target.project,
            technicalName: draft.spec.technicalName,
          });
          output.verification = summarizeVerification(verifyPopulation(draft.spec, model));
        } catch {
          output.verification = { status: "UNAVAILABLE", checks: [] };
        }
      }
      return output;
    },
  };

  const expected = new Set(TOOL_DEFINITIONS.map((tool) => tool.name));
  if (Object.keys(raw).some((name) => !expected.has(name)) || Object.keys(raw).length !== expected.size) {
    throw new Error("Tool handler surface does not match the approved registry");
  }
  return Object.fromEntries(Object.entries(raw).map(([name, handler]) => [name, wrap(name, steps, handler)]));
}
