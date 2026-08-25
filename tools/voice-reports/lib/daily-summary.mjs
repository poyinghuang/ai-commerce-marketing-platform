import {
  humanActionSpeech,
  stripUnspeakable,
  testingSpeech,
} from "./mapping.mjs";

function paragraph(lines) {
  return lines.filter(Boolean).join("\n\n");
}

export function buildDailySpokenScript(input) {
  const completed = (input.completed ?? []).map((item) => stripUnspeakable(item));
  const incomplete = (input.incomplete ?? []).map((item) => stripUnspeakable(item));
  const blockers = (input.blockers ?? []).map((item) => stripUnspeakable(item));

  const completedSpeech =
    completed.length > 0
      ? `今天完成了${completed.join("，")}。`
      : "今天沒有已合併或已關閉的階段成果。";

  const position = input.position?.label
    ? `專案目前在 ${stripUnspeakable(input.position.label)}。`
    : "專案目前位置沒有寫在今日證據裡。";

  const incompleteSpeech =
    incomplete.length > 0
      ? `還沒做完的工作包括${incomplete.join("，")}。`
      : "目前沒有進行中的未完成項目。";

  const blockerSpeech =
    blockers.length > 0
      ? `目前的阻擋問題是${blockers.join("，")}。`
      : "目前沒有阻擋問題。";

  const script = paragraph([
    completedSpeech,
    testingSpeech(input.testing),
    position,
    incompleteSpeech,
    blockerSpeech,
    humanActionSpeech({
      humanActionRequired: Boolean(input.human_action_required),
      humanActionReason: input.human_action_reason,
      daily: true,
    }),
    `下一步${stripUnspeakable(input.next)}`,
  ]);

  return stripUnspeakable(script);
}

export function renderDailyMarkdown(input, generatedAt) {
  const script = buildDailySpokenScript(input);
  const evidence = input.evidence_index ?? [];
  const frontMatter = [
    "---",
    "kind: daily",
    `date: ${input.date}`,
    "timezone: Asia/Taipei",
    `human_action_required: ${Boolean(input.human_action_required)}`,
    `generated_at: ${generatedAt}`,
    "---",
    "",
  ].join("\n");

  return `${frontMatter}# 每日專案語音摘要 ${input.date}

## 朗讀稿

${script}

## 證據索引

${evidence.length > 0 ? evidence.map((item) => `- ${item}`).join("\n") : "- None"}
`;
}
