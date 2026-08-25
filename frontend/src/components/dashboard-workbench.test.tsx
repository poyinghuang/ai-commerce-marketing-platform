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
    expect(screen.queryByRole("heading", { name: "優化建議" })).not.toBeInTheDocument();
  });

  it("loads pending recommendations without POST and requires confirm for generate and approve", async () => {
    const recommendationUuid = "b61f6c79-9de8-54f0-9ff1-06cccb7d4201";
    const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => {
      if (url === "/api/dashboard") return Promise.resolve(json(emptyDashboard()));
      if (url.startsWith("/api/decision-recommendations?") ) {
        return Promise.resolve(json({
          content: [{
            recommendationUuid, platformCampaignUuid: "00000000-0000-4000-8000-0000000000c1",
            campaignUuid: "00000000-0000-4000-8000-0000000000c2", campaignName: "Demo",
            recommendationType: "INCREASE_BUDGET", status: "PENDING",
            reasonSummary: "Campaign-grain ROAS is at or above 3.000000 on the canonical previous Taipei day.",
            riskSummary: "Approval records the operator decision only. It does not change desired state, Ad Set budget, creatives, or metrics, and it does not call a platform adapter.",
            evidence: { roas: "4.000000" }, href: "/platforms/meta", version: 0,
            warnings: ["DETERMINISTIC_FAKE_ONLY", "NO_REAL_PROVIDER_OR_SPEND", "NULL_METRICS_MEAN_UNKNOWN", "APPROVAL_DOES_NOT_EXECUTE"],
          }],
          page: 0, size: 20, totalElements: 1, totalPages: 1,
        }));
      }
      if (url === "/api/decision-recommendations/generate") {
        return Promise.resolve(json({ createdCount: 0, items: [], warnings: [] }));
      }
      if (url.endsWith("/approve")) return Promise.resolve(json({ status: "APPROVED" }));
      if (url.endsWith("/reject")) return Promise.resolve(json({ status: "REJECTED" }));
      return Promise.resolve(json({}));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<DashboardWorkbench stage6Enabled />);
    await screen.findByRole("heading", { name: "優化建議" });
    for (const name of ["今日待辦", "商品與資料完整度", "素材待審核", "Campaign 狀態", "KPI Overview", "AI 建議", "異常事件"]) {
      expect(screen.getByRole("heading", { name })).toBeInTheDocument();
    }
    expect(screen.getByText("Demo", { exact: false })).toBeInTheDocument();
    expect(fetchMock.mock.calls.every(([, init]) => !init || !init.method || init.method === "GET")).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith("/api/decision-recommendations?status=PENDING", expect.objectContaining({ cache: "no-store" }));
    fireEvent.click(screen.getByRole("button", { name: "Generate suggestions" }));
    expect(fetchMock.mock.calls.every(([url]) => url !== "/api/decision-recommendations/generate")).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Confirm generate suggestions" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      "/api/decision-recommendations/generate",
      expect.objectContaining({ method: "POST", cache: "no-store" }),
    ));
    const generateInit = fetchMock.mock.calls.find(([url]) => url === "/api/decision-recommendations/generate")?.[1] as RequestInit;
    expect(generateInit.headers).toBeUndefined();
    expect(generateInit.body).toBeUndefined();
    fireEvent.click(await screen.findByRole("button", { name: "Approve suggestion" }));
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes("/approve"))).toHaveLength(0);
    fireEvent.click(screen.getByRole("button", { name: "Confirm approve suggestion" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      `/api/decision-recommendations/${recommendationUuid}/approve`,
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "If-Match": 'W/"0"' }),
        body: "{}",
      }),
    ));
    expect(await screen.findByText("Ads state did not change. Approval records the operator decision only.")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Rejection reason"), { target: { value: "Not a useful suggestion" } });
    fireEvent.click(screen.getByRole("button", { name: "Reject suggestion" }));
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes("/reject"))).toHaveLength(0);
    fireEvent.click(screen.getByRole("button", { name: "Confirm reject suggestion" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      `/api/decision-recommendations/${recommendationUuid}/reject`,
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "If-Match": 'W/"0"' }),
        body: JSON.stringify({ reason: "Not a useful suggestion" }),
      }),
    ));
  });

  it("requires a second confirm before approve and keeps blocked approve disabled", async () => {
    const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => {
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
    const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => {
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
