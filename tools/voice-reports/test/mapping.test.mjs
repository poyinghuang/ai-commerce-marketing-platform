import assert from "node:assert/strict";
import test from "node:test";
import {
  assertHonestStatus,
  speakManagerDecision,
  stripUnspeakable,
  testingSpeech,
  voiceManagerDecision,
} from "../lib/mapping.mjs";

test("APPROVE without conditions is spoken as 核准, not 附條件核准", () => {
  const decision = voiceManagerDecision({ managerGate: "APPROVE", conditions: [] });
  assert.equal(decision, "APPROVED");
  assert.match(speakManagerDecision(decision), /核准/);
  assert.doesNotMatch(speakManagerDecision(decision), /附條件/);
});

test("APPROVE with conditions is not spoken as plain 核准", () => {
  const decision = voiceManagerDecision({
    managerGate: "APPROVE",
    conditions: ["下一步不得自動執行廣告操作"],
  });
  assert.equal(decision, "APPROVED_WITH_CONDITIONS");
  assert.match(speakManagerDecision(decision), /附條件核准/);
});

test("REQUEST_CHANGES is not spoken as 否決 or 核准", () => {
  const decision = voiceManagerDecision({ managerGate: "REQUEST_CHANGES" });
  assert.equal(decision, "NEEDS_CHANGES");
  const speech = speakManagerDecision(decision);
  assert.doesNotMatch(speech, /否決/);
  assert.doesNotMatch(speech, /結果為核准/);
});

test("partial must not be claimed completed while changes are requested", () => {
  assert.throws(() =>
    assertHonestStatus({ status: "completed", voiceDecision: "NEEDS_CHANGES" }),
  );
});

test("failed tests are named instead of 全部通過", () => {
  const speech = testingSpeech({
    unit: "passed",
    integration: "failed",
    e2e: "passed",
    ci: "passed",
  });
  assert.match(speech, /整合測試沒有通過/);
  assert.doesNotMatch(speech, /都已經通過/);
});

test("unrun tests are not reported as passed", () => {
  const speech = testingSpeech({
    unit: "passed",
    integration: "passed",
    e2e: "not_run",
    ci: "passed",
  });
  assert.match(speech, /尚未驗證/);
  assert.doesNotMatch(speech, /都已經通過/);
});

test("UUID URL and labeled SHA are stripped without eating words like stage06", () => {
  const spoken = stripUnspeakable(
    "完成 Head 50fcd8b71656381b612e25bafe1e14b5bce8ddfe 與 stage06，見 https://github.com/example/pr/73 與 3d3b7b3b-1111-2222-3333-444444444444。",
  );
  assert.doesNotMatch(spoken, /50fcd8b/);
  assert.doesNotMatch(spoken, /https:\/\//);
  assert.doesNotMatch(spoken, /3d3b7b3b-1111-2222-3333-444444444444/);
  assert.match(spoken, /stage06/);
});
