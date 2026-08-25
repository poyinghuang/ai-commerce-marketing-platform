import assert from "node:assert/strict";
import test from "node:test";
import { collectDailyEvidence } from "../lib/collect-daily.mjs";

test("collector does not invent passing CI when gh is unavailable", async () => {
  const evidence = await collectDailyEvidence({
    date: "2026-08-25",
    exec: async () => {
      throw new Error("unavailable");
    },
  });
  assert.equal(evidence.testing.ci, "not_run");
  assert.equal(evidence.completed.length, 0);
  assert.match(evidence.evidence_index.join(" "), /not verified/);
});

test("collector maps latest main failure to failed CI", async () => {
  const evidence = await collectDailyEvidence({
    date: "2026-08-25",
    exec: async (command) => {
      if (command.startsWith("git log")) {
        return { stdout: "docs(stage06): define decision engine specification" };
      }
      if (command.startsWith("gh run list")) {
        return { stdout: JSON.stringify([{ conclusion: "failure", displayTitle: "CI" }]) };
      }
      if (command.startsWith("gh pr list")) {
        return { stdout: JSON.stringify([{ number: 73, isDraft: true, state: "open" }]) };
      }
      throw new Error(command);
    },
    statusSnapshot: {
      current_stage: "stage-06-runtime",
      blockers: ["自動檢查失敗"],
      human_action_required: false,
      next_stage: "先修自動檢查",
    },
  });
  assert.equal(evidence.testing.ci, "failed");
  assert.match(evidence.incomplete[0], /73/);
  assert.equal(evidence.blockers[0], "自動檢查失敗");
});
