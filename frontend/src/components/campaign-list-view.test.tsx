import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CampaignListView } from "./campaign-list-view";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }), useSearchParams: () => new URLSearchParams() }));

describe("CampaignListView", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
  it("renders loading then empty state", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, sort: { field: "updatedAt", direction: "desc" } }), { status: 200 })));
    render(<CampaignListView />);
    expect(screen.getByRole("status")).toHaveTextContent("載入 Campaigns");
    await waitFor(() => expect(screen.getByText("目前沒有 Campaign。")).toBeInTheDocument());
  });
  it("renders backend errors", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "offline" }), { status: 503 })));
    render(<CampaignListView />);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("offline"));
  });
});
