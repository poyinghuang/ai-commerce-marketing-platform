import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DashboardWorkbench } from "./dashboard-workbench";

const outputUuid = "a54e5b68-8cd7-43ef-8ee0-95bbba6c3190";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("DashboardWorkbench", () => {
  it("renders seven stub regions and does not POST on load", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(emptyDashboard()));
    vi.stubGlobal("fetch", fetchMock);
    render(<DashboardWorkbench />);
    await screen.findByRole("heading", { name: "今日待辦" });
    for (const name of ["商品與資料完整度", "素材待審核", "Campaign 狀態", "KPI Overview", "AI 建議", "異常事件"]) {
      expect(screen.getByRole("heading", { name })).toBeInTheDocument();
    }
    expect(screen.getByText("目前沒有待辦。")).toBeInTheDocument();
    expect(screen.getByText("KPI Overview is unavailable.")).toBeInTheDocument();
    expect(fetchMock.mock.calls.every(([, init]) => !init || !init.method || init.method === "GET")).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith("/api/dashboard", expect.objectContaining({ cache: "no-store" }));
  });

  it("requires a second confirm before approve and keeps blocked approve disabled", async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url === "/api/dashboard") {
        return Promise.resolve(json(dashboardWithReview({ approvalBlocked: false, version: 3 })));
      }
      if (url.endsWith("/approve")) return Promise.resolve(json({ reviewStatus: "APPROVED" }));
      return Promise.resolve(json({}));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<DashboardWorkbench />);
    fireEvent.click(await screen.findByRole("button", { name: "Approve output" }));
    expect(fetchMock.mock.calls.every(([url]) => !String(url).includes("/approve"))).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Confirm approve" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      `/api/ai-generation-outputs/${outputUuid}/approve`,
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "If-Match": 'W/"3"' }),
        body: "{}",
      }),
    ));
  });

  it("disables blocked approve and rejects only after reason plus confirm", async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url === "/api/dashboard") {
        return Promise.resolve(json(dashboardWithReview({ approvalBlocked: true, version: 1, blockerCount: 1 })));
      }
      if (url.endsWith("/reject")) return Promise.resolve(json({ reviewStatus: "REJECTED" }));
      return Promise.resolve(json({}));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<DashboardWorkbench />);
    expect(await screen.findByRole("button", { name: "Approve output" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Reject output" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Rejection reason"), { target: { value: "Unsafe claim" } });
    fireEvent.click(screen.getByRole("button", { name: "Reject output" }));
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes("/reject"))).toBe(false);
    fireEvent.click(screen.getByRole("button", { name: "Confirm reject" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      `/api/ai-generation-outputs/${outputUuid}/reject`,
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "If-Match": 'W/"1"' }),
        body: JSON.stringify({ reason: "Unsafe claim" }),
      }),
    ));
  });
});

function emptyDashboard() {
  const section = { available: true, items: [], truncated: false, totalElements: 0 };
  return {
    generatedAt: "2026-08-24T00:00:00Z",
    todos: section, products: section, reviews: section, campaigns: section,
    platformCampaigns: section, anomalies: section, kpis: { available: false },
  };
}

function dashboardWithReview(review: { approvalBlocked: boolean; version: number; blockerCount?: number }) {
  return {
    ...emptyDashboard(),
    kpis: { available: true, windowStart: "2026-08-23T16:00:00Z", windowEnd: "2026-08-24T16:00:00Z", timezone: "Asia/Taipei", currency: "TWD", eligibleCampaignCount: 0, presentCampaignCount: 0, incomplete: false },
    reviews: {
      available: true,
      truncated: false,
      totalElements: 1,
      items: [{
        generationOutputUuid: outputUuid,
        productUuid: "d4476a19-30ed-48d9-a518-f9b111bd0911",
        generationType: "TEXT",
        reviewStatus: "PENDING_REVIEW",
        version: review.version,
        blockerCount: review.blockerCount ?? 0,
        approvalBlocked: review.approvalBlocked,
        href: "/products/d4476a19-30ed-48d9-a518-f9b111bd0911?tab=creative-factory",
      }],
    },
  };
}

function json(value: unknown) {
  return new Response(JSON.stringify(value), { status: 200, headers: { "Content-Type": "application/json" } });
}
