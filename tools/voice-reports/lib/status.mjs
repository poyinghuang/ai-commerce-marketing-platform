const REQUIRED = [
  "schema_version",
  "project",
  "milestone",
  "current_stage",
  "stage_status",
  "active_pr",
  "ci_status",
  "manager_gate",
  "blockers",
  "human_action_required",
  "next_stage",
  "last_updated",
  "evidence",
];

export function assertStatusSnapshot(snapshot) {
  if (snapshot.schema_version !== 1) {
    throw new Error("status.json schema_version must be 1");
  }
  for (const key of REQUIRED) {
    if (!(key in snapshot)) {
      throw new Error(`status.json missing ${key}`);
    }
  }
  if (!Array.isArray(snapshot.blockers)) {
    throw new Error("status.json blockers must be an array");
  }
  if (!snapshot.evidence || !Array.isArray(snapshot.evidence.sources)) {
    throw new Error("status.json evidence.sources must be an array");
  }
  if (snapshot.human_action_required && !snapshot.human_action_reason) {
    throw new Error("human_action_reason is required when human_action_required is true");
  }
  return snapshot;
}

export function formatStatusJson(snapshot) {
  return `${JSON.stringify(assertStatusSnapshot(snapshot), null, 2)}\n`;
}
