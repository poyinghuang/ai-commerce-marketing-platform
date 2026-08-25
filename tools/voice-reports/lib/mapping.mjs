export const STAGE_STATUS = {
  not_started: "尚未開始",
  in_progress: "進行中",
  partial: "部分完成",
  completed: "完成",
  blocked: "阻塞",
  needs_human: "需要人工決策",
};

export const GATE_TO_VOICE = {
  APPROVE: "APPROVED",
  REQUEST_CHANGES: "NEEDS_CHANGES",
  ESCALATE_TO_HUMAN: "ESCALATE_TO_HUMAN",
  Pending: "PENDING",
};

export function voiceManagerDecision({ managerGate, conditions = [], rejected = false }) {
  if (rejected) {
    return "REJECTED";
  }
  if (managerGate === "ESCALATE_TO_HUMAN") {
    return "ESCALATE_TO_HUMAN";
  }
  if (managerGate === "REQUEST_CHANGES") {
    return "NEEDS_CHANGES";
  }
  if (managerGate === "APPROVE" && conditions.length > 0) {
    return "APPROVED_WITH_CONDITIONS";
  }
  if (managerGate === "APPROVE") {
    return "APPROVED";
  }
  return "PENDING";
}

export function speakManagerDecision(voiceDecision) {
  switch (voiceDecision) {
    case "APPROVED":
      return "Manager Review 結果為核准。";
    case "APPROVED_WITH_CONDITIONS":
      return "Manager Review 結果為附條件核准。";
    case "NEEDS_CHANGES":
      return "Manager Review 還沒有核准，需要先修正。";
    case "ESCALATE_TO_HUMAN":
      return "目前需要人工決策。";
    case "REJECTED":
      return "Manager Review 結果為否決。";
    default:
      return "Manager Review 還沒有完成。";
  }
}

export function speakStageStatus(status) {
  const label = STAGE_STATUS[status];
  if (!label) {
    throw new Error(`Unknown stage_status: ${status}`);
  }
  return label;
}

export function assertHonestStatus({ status, voiceDecision }) {
  if (status === "completed" && voiceDecision === "NEEDS_CHANGES") {
    throw new Error("Cannot mark a stage completed while Manager Decision is REQUEST_CHANGES");
  }
  if (status === "completed" && voiceDecision === "PENDING") {
    throw new Error("Cannot mark a stage completed while Manager Decision is pending");
  }
  if (status === "completed" && (voiceDecision === "ESCALATE_TO_HUMAN" || voiceDecision === "REJECTED")) {
    throw new Error("Cannot mark a stage completed under escalation or rejection");
  }
}

const URL_PATTERN = /https?:\/\/\S+/gi;
const UUID_PATTERN = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;
const FULL_SHA_PATTERN = /\b[0-9a-f]{40}\b/gi;
const LABELED_SHA_PATTERN = /\b(?:commit|sha|head)\s+[0-9a-f]{7,40}\b/gi;

export function stripUnspeakable(text) {
  return String(text)
    .replace(URL_PATTERN, "")
    .replace(UUID_PATTERN, "")
    .replace(LABELED_SHA_PATTERN, "")
    .replace(FULL_SHA_PATTERN, "")
    .replace(/[^\S\n]{2,}/g, " ")
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export function testingSpeech(testing = {}) {
  const keys = ["unit", "integration", "e2e", "ci"];
  const labels = {
    unit: "單元測試",
    integration: "整合測試",
    e2e: "瀏覽器測試",
    ci: "自動檢查",
  };
  const failed = keys.filter((key) => testing[key] === "failed");
  if (failed.length > 0) {
    return `${failed.map((key) => labels[key]).join("、")}沒有通過。`;
  }
  const missing = keys.filter((key) => testing[key] !== "passed");
  if (missing.length > 0) {
    return `${missing.map((key) => labels[key]).join("、")}尚未驗證，不能說全部通過。`;
  }
  return "單元測試、整合測試、瀏覽器測試和自動檢查都已經通過。";
}

export function humanActionSpeech({ humanActionRequired, humanActionReason, daily = false }) {
  if (humanActionRequired) {
    const reason = humanActionReason ? stripUnspeakable(humanActionReason) : "原因沒有寫在摘要輸入裡。";
    return `目前需要人工介入。原因是${reason}`;
  }
  return daily ? "今天不需要人工介入。" : "目前不需要人工介入。";
}
