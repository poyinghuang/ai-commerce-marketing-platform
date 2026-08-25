function nextTaipeiDate(date) {
  const utc = Date.parse(`${date}T00:00:00+08:00`);
  if (Number.isNaN(utc)) {
    throw new Error(`Invalid date: ${date}`);
  }
  return new Date(utc + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

async function capture(exec, command) {
  try {
    const result = await exec(command);
    return { ok: true, stdout: String(result.stdout ?? result).trim() };
  } catch (error) {
    return { ok: false, stdout: "", error: error instanceof Error ? error.message : String(error) };
  }
}

export async function collectDailyEvidence({ date, exec, statusSnapshot = null }) {
  const until = nextTaipeiDate(date);
  const git = await capture(
    exec,
    `git log origin/main --since="${date} 00:00:00 +0800" --until="${until} 00:00:00 +0800" --pretty=format:%s`,
  );
  const runs = await capture(exec, "gh run list --branch main --limit 5 --json conclusion,displayTitle,databaseId,headSha");
  const prs = await capture(exec, "gh pr list --state open --json number,title,isDraft,state");

  const subjects = git.ok && git.stdout ? git.stdout.split("\n").filter(Boolean) : [];
  const completed = subjects.length > 0 ? subjects.map((subject) => `主線提交：${subject}`) : [];

  let ci = "not_verified";
  if (runs.ok && runs.stdout) {
    try {
      const parsed = JSON.parse(runs.stdout);
      const latest = parsed[0];
      if (latest?.conclusion === "success") {
        ci = "passed";
      } else if (latest?.conclusion === "failure") {
        ci = "failed";
      } else if (latest) {
        ci = "not_verified";
      }
    } catch {
      ci = "not_verified";
    }
  }

  const testing =
    ci === "passed"
      ? { unit: "passed", integration: "passed", e2e: "passed", ci: "passed" }
      : ci === "failed"
        ? { unit: "not_run", integration: "not_run", e2e: "not_run", ci: "failed" }
        : { unit: "not_run", integration: "not_run", e2e: "not_run", ci: "not_run" };

  const openPrs = [];
  if (prs.ok && prs.stdout) {
    try {
      openPrs.push(...JSON.parse(prs.stdout));
    } catch {
      // keep empty; do not invent PR state
    }
  }

  const incomplete = openPrs.map((pr) => {
    const draft = pr.isDraft ? "草稿" : "審查中";
    return `第 ${pr.number} 號變更仍是${draft}`;
  });

  return {
    date,
    completed,
    testing,
    position: {
      kind: "stage",
      label: statusSnapshot?.current_stage ?? "目前階段見專案狀態檔",
    },
    incomplete,
    blockers: statusSnapshot?.blockers ?? [],
    human_action_required: Boolean(statusSnapshot?.human_action_required),
    human_action_reason: statusSnapshot?.human_action_reason ?? null,
    next: statusSnapshot?.next_stage ?? "下一步以專案狀態檔與 Manager Gate 為準。",
    evidence_index: [
      git.ok ? `git log origin/main for ${date} Asia/Taipei` : "git log not verified",
      runs.ok ? "gh run list --branch main" : "GitHub Actions not verified",
      prs.ok ? "gh pr list --state open" : "GitHub pull requests not verified",
    ],
  };
}
