import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CampaignCreateView } from "./campaign-create-view";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

describe("CampaignCreateView", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    push.mockReset();
  });

  it("creates a Campaign and navigates to its server-assigned identity", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ campaignUuid: "79be8758-1f0d-4ca5-bad6-f51aa923cdb9" }), {
        status: 201,
      }),
    );
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignCreateView />);
    fireEvent.change(screen.getByLabelText("活動名稱"), { target: { value: "Launch" } });
    fireEvent.change(screen.getByLabelText("總預算"), { target: { value: "100.0000" } });
    fireEvent.change(screen.getByLabelText("幣別"), { target: { value: "USD" } });
    fireEvent.click(screen.getByRole("button", { name: "建立 Campaign" }));
    await waitFor(() =>
      expect(push).toHaveBeenCalledWith("/campaigns/79be8758-1f0d-4ca5-bad6-f51aa923cdb9"),
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/campaigns",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
      }),
    );
  });

  it("shows the backend create error and stays on the form", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ message: "Campaign name already exists" }), { status: 409 }),
      ),
    );
    render(<CampaignCreateView />);
    fireEvent.change(screen.getByLabelText("活動名稱"), { target: { value: "Launch" } });
    fireEvent.click(screen.getByRole("button", { name: "建立 Campaign" }));
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent("Campaign name already exists"),
    );
    expect(push).not.toHaveBeenCalled();
  });
});
