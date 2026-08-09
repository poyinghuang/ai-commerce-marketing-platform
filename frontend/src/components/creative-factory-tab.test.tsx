import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CreativeFactoryTab } from "./creative-factory-tab";

const productUuid = "d4476a19-30ed-48d9-a518-f9b111bd0911";
const planUuid = "79be8758-1f0d-4ca5-bad6-f51aa923cdb9";

describe("CreativeFactoryTab", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("loads server allowlists and creates a three-variation text batch", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === "/api/ai-budget/status") return Promise.resolve(json({
        currency: "USD", maximumJobCost: 10, maximumBatchCost: 20, maximumDailyCost: 100,
        modelProfiles: ["STANDARD"], textTemplateKeys: ["copy.default"],
      }));
      if (url.includes("/creative-plans")) return Promise.resolve(json({
        content: [{ creativePlanUuid: planUuid, planName: "Launch plan" }], page: 0, size: 100,
        totalElements: 1, totalPages: 1,
      }));
      if (init?.method === "POST") return Promise.resolve(json({ generationBatchUuid: "batch" }, 201));
      return Promise.resolve(json([]));
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<CreativeFactoryTab productUuid={productUuid} productArchived={false} />);
    await screen.findByRole("button", { name: "Create text batch" });
    await waitFor(() => expect(screen.getByLabelText("Template")).toHaveValue("copy.default"));
    fireEvent.click(screen.getByRole("button", { name: "Create text batch" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      `/api/products/${productUuid}/ai-generation-batches`,
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ creativePlanUuid: planUuid, templateKey: "copy.default", modelProfile: "STANDARD", variationCount: 3 }),
      }),
    ));
  });

  it("does not expose generation controls for an archived Product", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => {
      if (url === "/api/ai-budget/status") return Promise.resolve(json({ modelProfiles: [], textTemplateKeys: [] }));
      if (url.includes("/creative-plans")) return Promise.resolve(json({ content: [], totalElements: 0, totalPages: 0 }));
      return Promise.resolve(json([]));
    }));
    render(<CreativeFactoryTab productUuid={productUuid} productArchived />);
    expect(screen.getByText("Archived Products cannot start AI generation.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create text batch" })).not.toBeInTheDocument();
  });
});

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json" } });
}
