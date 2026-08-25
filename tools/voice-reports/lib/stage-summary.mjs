import {
  assertHonestStatus,
  humanActionSpeech,
  speakManagerDecision,
  speakStageStatus,
  stripUnspeakable,
  testingSpeech,
  voiceManagerDecision,
} from "./mapping.mjs";

function paragraph(lines) {
  return lines.filter(Boolean).join("\n\n");
}

export function buildStageSpokenScript(input) {
  const voiceDecision = voiceManagerDecision({
    managerGate: input.manager_gate,
    conditions: input.conditions ?? [],
    rejected: Boolean(input.rejected),
  });
  assertHonestStatus({ status: input.status, voiceDecision });

  const statusLabel = speakStageStatus(input.status);
  const completed = (input.completed ?? []).map((item) => stripUnspeakable(item));
  const incomplete = (input.incomplete ?? []).map((item) => stripUnspeakable(item));
  const risks = (input.risks ?? []).map((item) => stripUnspeakable(item));
  const conditions = (input.conditions ?? []).map((item) => stripUnspeakable(item));

  const completedSpeech =
    completed.length > 0
      ? `這個階段主要完成了${completed.join("，")}。`
      : "這個階段沒有列出已完成重點。";

  const incompleteSpeech =
    incomplete.length > 0
      ? `還沒做完的項目包括${incomplete.join("，")}。`
      : "目前沒有未完成的阻擋項目。";

  const riskSpeech =
    risks.length > 0
      ? `可能影響下一階段的風險是${risks.join("，")}。`
      : "目前沒有影響下一階段的阻擋問題。";

  const conditionSpeech =
    voiceDecision === "APPROVED_WITH_CONDITIONS" && conditions.length > 0
      ? `條件是${conditions.join("，")}。`
      : "";

  const script = paragraph([
    `${stripUnspeakable(input.stage_label)} 已經${statusLabel}。`,
    completedSpeech,
    testingSpeech(input.testing),
    incompleteSpeech,
    riskSpeech,
    speakManagerDecision(voiceDecision),
    conditionSpeech,
    `下一步${stripUnspeakable(input.next)}`,
    humanActionSpeech({
      humanActionRequired: Boolean(input.human_action_required),
      humanActionReason: input.human_action_reason,
    }),
  ]);

  return { voiceDecision, script: stripUnspeakable(script) };
}

export function renderStageMarkdown(input, generatedAt) {
  const { voiceDecision, script } = buildStageSpokenScript(input);
  const evidence = input.evidence_index ?? [];
  const frontMatter = [
    "---",
    "kind: stage-completion",
    `stage_id: ${input.stage_id}`,
    `status: ${input.status}`,
    `manager_gate: ${input.manager_gate}`,
    `voice_manager_decision: ${voiceDecision}`,
    `human_action_required: ${Boolean(input.human_action_required)}`,
    `generated_at: ${generatedAt}`,
    "---",
    "",
  ].join("\n");

  return `${frontMatter}# ${input.stage_label} 語音摘要

## 朗讀稿

${script}

## 證據索引

${evidence.length > 0 ? evidence.map((item) => `- ${item}`).join("\n") : "- None"}
`;
}
