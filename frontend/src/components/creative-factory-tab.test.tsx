import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CreativeFactoryTab } from "./creative-factory-tab";

const productUuid = "d4476a19-30ed-48d9-a518-f9b111bd0911";
const planUuid = "79be8758-1f0d-4ca5-bad6-f51aa923cdb9";
const sourceAssetUuid = "3848e96f-9c0f-4d6e-a153-b0ceadb28540";

describe("CreativeFactoryTab", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("loads server allowlists and creates a three-variation text batch", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === "/api/ai-budget/status") return Promise.resolve(json({
        currency: "USD", maximumJobCost: 10, maximumBatchCost: 20, maximumDailyCost: 100,
        modelProfiles: ["STANDARD"], textTemplateKeys: ["copy.default"],
        imageModelProfiles: ["STANDARD_IMAGE"], imageTemplateKeys: ["image.background-composite-v1"],
        imageWorkflowKeys: ["background-composite-v1"],
      }));
      if (url.includes("/creative-plans")) return Promise.resolve(json({
        content: [{ creativePlanUuid: planUuid, planName: "Launch plan" }], page: 0, size: 100,
        totalElements: 1, totalPages: 1,
      }));
      if (url.includes("/assets?")) return Promise.resolve(json({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }));
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

  it("creates one image job from server allowlists and an eligible source Asset", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === "/api/ai-budget/status") return Promise.resolve(json({
        modelProfiles: ["STANDARD"], textTemplateKeys: ["copy.default"],
        imageModelProfiles: ["STANDARD_IMAGE"], imageTemplateKeys: ["image.background-composite-v1"],
        imageWorkflowKeys: ["background-composite-v1"],
      }));
      if (url.includes("/creative-plans")) return Promise.resolve(json({
        content: [{ creativePlanUuid: planUuid, planName: "Launch plan" }], totalElements: 1, totalPages: 1,
      }));
      if (url.includes("/assets?")) return Promise.resolve(json({
        content: [{ assetUuid: sourceAssetUuid, assetType: "IMAGE", originalFilename: "product.png" }],
        page: 0, size: 100, totalElements: 1, totalPages: 1,
      }));
      if (init?.method === "POST") return Promise.resolve(json({ generationBatchUuid: "batch" }, 201));
      return Promise.resolve(json([]));
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<CreativeFactoryTab productUuid={productUuid} productArchived={false} />);
    await waitFor(() => expect(screen.getByLabelText("Generation mode")).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText("Generation mode"), { target: { value: "IMAGE" } });
    fireEvent.click(screen.getByRole("button", { name: "Create image batch" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      `/api/products/${productUuid}/ai-generation-batches`,
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ generationType: "IMAGE", creativePlanUuid: planUuid,
          templateKey: "image.background-composite-v1", workflowKey: "background-composite-v1",
          modelProfile: "STANDARD_IMAGE", variationCount: 1, sourceAssetUuid, maskAssetUuid: null }),
      }),
    ));
  });

  it("does not expose generation controls for an archived Product", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => {
      if (url === "/api/ai-budget/status") return Promise.resolve(json({ modelProfiles: [], textTemplateKeys: [], imageModelProfiles: [], imageTemplateKeys: [], imageWorkflowKeys: [] }));
      if (url.includes("/creative-plans")) return Promise.resolve(json({ content: [], totalElements: 0, totalPages: 0 }));
      if (url.includes("/assets?")) return Promise.resolve(json({ content: [], totalElements: 0, totalPages: 0 }));
      return Promise.resolve(json([]));
    }));
    render(<CreativeFactoryTab productUuid={productUuid} productArchived />);
    expect(screen.getByText("Archived Products cannot start AI generation.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create text batch" })).not.toBeInTheDocument();
  });

  it("approves an eligible output with its current ETag", async () => {
    const outputUuid = "a54e5b68-8cd7-43ef-8ee0-95bbba6c3190";
    const fetchMock = vi.fn((url: string) => {
      if (url === "/api/ai-budget/status") return Promise.resolve(json(allowlists()));
      if (url.includes("/creative-plans")) return Promise.resolve(json({ content: [], totalElements: 0, totalPages: 0 }));
      if (url.includes("/assets?")) return Promise.resolve(json({ content: [], totalElements: 0, totalPages: 0 }));
      if (url === `/api/ai-generation-outputs/${outputUuid}/approve`) return Promise.resolve(json({ reviewStatus: "APPROVED" }));
      if (url === `/api/ai-generation-outputs/${outputUuid}`) return Promise.resolve(json(output(outputUuid), 200, { ETag: 'W/"0"' }));
      return Promise.resolve(json([{ generationBatchUuid: "batch", status: "COMPLETED", succeededJobCount: 1, failedJobCount: 0, rejectedJobCount: 0, jobs: [{ generationJobUuid: "job", generationType: "TEXT", status: "SUCCEEDED", reservedCost: 1, currency: "USD", outputUuid, version: 2 }] }]));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<CreativeFactoryTab productUuid={productUuid} productArchived={false} />);
    fireEvent.click(await screen.findByRole("button", { name: "Approve output" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/ai-generation-outputs/${outputUuid}/approve`,
      expect.objectContaining({ method: "POST", headers: expect.objectContaining({ "If-Match": 'W/"0"' }), body: "{}" })));
  });

  it("shows blockers, disables approval and submits a rejection reason", async () => {
    const outputUuid = "a54e5b68-8cd7-43ef-8ee0-95bbba6c3190";
    const fetchMock = vi.fn((url: string) => {
      if (url === "/api/ai-budget/status") return Promise.resolve(json(allowlists()));
      if (url.includes("/creative-plans")) return Promise.resolve(json({ content: [], totalElements: 0, totalPages: 0 }));
      if (url.includes("/assets?")) return Promise.resolve(json({ content: [], totalElements: 0, totalPages: 0 }));
      if (url === `/api/ai-generation-outputs/${outputUuid}/reject`) return Promise.resolve(json({ reviewStatus: "REJECTED" }));
      if (url === `/api/ai-generation-outputs/${outputUuid}`) return Promise.resolve(json({ ...output(outputUuid), reviewBlockers: ["SAFETY_FINDINGS"] }, 200, { ETag: 'W/"0"' }));
      return Promise.resolve(json([{ generationBatchUuid: "batch", status: "COMPLETED", succeededJobCount: 1, failedJobCount: 0, rejectedJobCount: 0, jobs: [{ generationJobUuid: "job", generationType: "TEXT", status: "SUCCEEDED", reservedCost: 1, currency: "USD", outputUuid, version: 2 }] }]));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<CreativeFactoryTab productUuid={productUuid} productArchived={false} />);
    expect(await screen.findByRole("button", { name: "Approve output" })).toBeDisabled();
    expect(screen.getByText("SAFETY_FINDINGS")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Rejection reason"), { target: { value: "Unsafe claim" } });
    fireEvent.click(screen.getByRole("button", { name: "Reject output" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(`/api/ai-generation-outputs/${outputUuid}/reject`,
      expect.objectContaining({ body: JSON.stringify({ reason: "Unsafe claim" }) })));
  });
});

function allowlists() {
  return { modelProfiles: ["STANDARD"], textTemplateKeys: ["copy.default"], imageModelProfiles: [], imageTemplateKeys: [], imageWorkflowKeys: [] };
}

function output(outputUuid: string) {
  return { generationOutputUuid: outputUuid, generationType: "TEXT", textContent: "Generated copy", modelLabel: "stub-text", actualCost: 1, currency: "USD", reviewStatus: "PENDING_REVIEW", reviewBlockers: [], reviewDecisions: [] };
}

function json(value: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json", ...headers } });
}
