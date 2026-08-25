import assert from "node:assert/strict";
import test from "node:test";
import { buildDailySpokenScript } from "../lib/daily-summary.mjs";

test("daily summary names CI failure and human action", () => {
  const script = buildDailySpokenScript({
    date: "2026-08-25",
    completed: ["兩個程式變更"],
    testing: { unit: "passed", integration: "passed", e2e: "passed", ci: "failed" },
    position: { kind: "stage", label: "Stage 6" },
    incomplete: ["執行階段還沒合併"],
    blockers: ["自動檢查失敗"],
    human_action_required: true,
    human_action_reason: "需要新的正式環境金鑰。",
    next: "會先停下來，等金鑰備好再繼續。",
  });
  assert.match(script, /自動檢查沒有通過/);
  assert.match(script, /目前的阻擋問題是自動檢查失敗/);
  assert.match(script, /目前需要人工介入/);
  assert.doesNotMatch(script, /今天不需要人工介入/);
});

test("healthy daily summary says no human action", () => {
  const script = buildDailySpokenScript({
    date: "2026-08-25",
    completed: ["兩個程式變更", "Stage 4A 並通過 Manager Gate"],
    testing: { unit: "passed", integration: "passed", e2e: "passed", ci: "passed" },
    position: { kind: "stage", label: "Stage 4B" },
    incomplete: ["商品資料標準化還在開發"],
    blockers: [],
    human_action_required: false,
    next: "會完成商品資料標準化與分析介面。",
  });
  assert.match(script, /都已經通過/);
  assert.match(script, /目前沒有阻擋問題/);
  assert.match(script, /今天不需要人工介入/);
});
