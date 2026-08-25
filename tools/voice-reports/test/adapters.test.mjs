import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createNotificationAdapter } from "../lib/notify.mjs";
import { createTtsAdapter } from "../lib/tts.mjs";
import { formatStatusJson } from "../lib/status.mjs";

test("missing TTS provider skips and does not throw", async () => {
  const adapter = createTtsAdapter({ provider: "openai" });
  const result = await adapter.synthesize("測試");
  assert.equal(result.skipped, true);
  assert.equal(result.reason, "tts_provider_not_wired");
});

test("noop TTS skips", async () => {
  const adapter = createTtsAdapter({ provider: "noop" });
  const result = await adapter.synthesize("測試");
  assert.equal(result.skipped, true);
  assert.equal(result.provider, "noop");
});

test("file TTS adapter writes spoken text", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "voice-tts-"));
  try {
    const { mkdir, writeFile } = await import("node:fs/promises");
    const adapter = createTtsAdapter({
      provider: "file",
      fs: { mkdir, writeFile },
      path,
      cwd: dir,
    });
    const result = await adapter.synthesize("今天不需要人工介入。");
    assert.equal(result.skipped, false);
    const written = await readFile(result.path, "utf8");
    assert.equal(written, "今天不需要人工介入。");
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("notification default is noop and skippable", async () => {
  const adapter = createNotificationAdapter({});
  const result = await adapter.deliver({ title: "daily", body: "測試" });
  assert.equal(result.skipped, true);
});

test("status snapshot requires human reason when action is required", () => {
  assert.throws(() =>
    formatStatusJson({
      schema_version: 1,
      project: "demo",
      milestone: "m",
      current_stage: "s",
      stage_status: "needs_human",
      active_pr: null,
      ci_status: "passing",
      manager_gate: "ESCALATE_TO_HUMAN",
      blockers: [],
      human_action_required: true,
      next_stage: "wait",
      last_updated: "2026-08-25T00:00:00+08:00",
      evidence: { sources: ["test"] },
    }),
  );
});
