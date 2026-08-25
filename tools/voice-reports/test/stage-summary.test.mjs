import assert from "node:assert/strict";
import test from "node:test";
import { buildStageSpokenScript } from "../lib/stage-summary.mjs";

const base = {
  stage_id: "stage-example",
  stage_label: "Stage 4A",
  status: "completed",
  completed: ["商品資料收集功能", "商品基本資料、規格、價格和圖片"],
  testing: { unit: "passed", integration: "passed", e2e: "passed", ci: "passed" },
  incomplete: [],
  risks: [],
  manager_gate: "APPROVE",
  conditions: [],
  next: "會進入下一階段，開始做資料標準化。",
  human_action_required: false,
};

test("completed stage uses 完成 and 核准 and 不需要人工介入", () => {
  const { voiceDecision, script } = buildStageSpokenScript(base);
  assert.equal(voiceDecision, "APPROVED");
  assert.match(script, /Stage 4A 已經完成/);
  assert.match(script, /都已經通過/);
  assert.match(script, /目前沒有未完成的阻擋項目/);
  assert.match(script, /結果為核准/);
  assert.match(script, /目前不需要人工介入/);
  assert.doesNotMatch(script, /部分完成/);
});

test("partial is never spoken as 完成", () => {
  const { script } = buildStageSpokenScript({
    ...base,
    status: "partial",
    manager_gate: "REQUEST_CHANGES",
    incomplete: ["還要補測試證據"],
  });
  assert.match(script, /部分完成/);
  assert.doesNotMatch(script, /已經完成/);
  assert.match(script, /需要先修正/);
});

test("blockers and human escalation stay in the script", () => {
  const { script } = buildStageSpokenScript({
    ...base,
    status: "needs_human",
    manager_gate: "ESCALATE_TO_HUMAN",
    incomplete: ["第三方第一次授權"],
    risks: ["沒有授權就無法繼續對接"],
    human_action_required: true,
    human_action_reason: "廣告平台需要第一次授權。",
  });
  assert.match(script, /需要人工決策/);
  assert.match(script, /目前需要人工介入/);
  assert.match(script, /第一次授權/);
  assert.doesNotMatch(script, /目前不需要人工介入/);
});
