"use client";

import { useEffect, useState } from "react";

type HealthState = "checking" | "up" | "down";

export function BackendHealth() {
  const [state, setState] = useState<HealthState>("checking");

  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/backend-health", { cache: "no-store", signal: controller.signal })
      .then((response) => {
        if (!response.ok) throw new Error("Backend is unavailable");
        return response.json() as Promise<{ status: string }>;
      })
      .then((health) => setState(health.status === "UP" ? "up" : "down"))
      .catch(() => {
        if (!controller.signal.aborted) setState("down");
      });
    return () => controller.abort();
  }, []);

  const label = state === "checking" ? "正在確認 Backend" : state === "up" ? "Backend 正常" : "Backend 無法連線";
  return (
    <div className="health" data-state={state} role="status">
      <span className="health-dot" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
