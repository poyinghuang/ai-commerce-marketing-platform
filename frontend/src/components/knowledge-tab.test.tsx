import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Knowledge, KnowledgePage } from "@/lib/knowledge";
import { KnowledgeTab } from "./knowledge-tab";

const entry: Knowledge = {
  knowledgeUuid: "5cf53b23-eabe-4b51-b565-62dbe4333721",
  productUuid: "d4476a19-30ed-48d9-a518-f9b111bd0911",
  knowledgeType: "FEATURE",
  title: "Feature title",
  content: "Feature content",
  source: null,
  lifecycleStatus: "ACTIVE",
  archivedAt: null,
  createdAt: "2026-08-07T00:00:00Z",
  updatedAt: "2026-08-07T00:00:00Z",
  version: 0,
};

function page(content: Knowledge[], totalPages = content.length === 0 ? 0 : 1): KnowledgePage {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages,
    status: "ACTIVE",
    sort: "updatedAt,desc",
  };
}

function jsonResponse(body: unknown, status = 200, headers?: HeadersInit) {
  return new Response(JSON.stringify(body), { status, headers });
}

describe("KnowledgeTab", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("renders loading then empty states", async () => {
    let resolveFetch: ((value: Response) => void) | undefined;
    vi.stubGlobal("fetch", vi.fn(() => new Promise<Response>((resolve) => { resolveFetch = resolve; })));

    render(<KnowledgeTab productUuid={entry.productUuid} productArchived={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("Loading knowledge");

    resolveFetch?.(jsonResponse(page([])));
    expect(await screen.findByText("No knowledge entries.")).toBeInTheDocument();
  });

  it("renders the Backend error state", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      jsonResponse({ code: "BACKEND_UNAVAILABLE", message: "Backend is unavailable" }, 503),
    ));

    render(<KnowledgeTab productUuid={entry.productUuid} productArchived={false} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Backend is unavailable");
  });

  it("creates and archives knowledge through same-origin endpoints", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(page([entry])))
      .mockResolvedValueOnce(jsonResponse({ ...entry, title: "New" }, 201, { ETag: 'W/"0"' }))
      .mockResolvedValueOnce(jsonResponse(page([entry])))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(page([])));
    vi.stubGlobal("fetch", fetchMock);

    render(<KnowledgeTab productUuid={entry.productUuid} productArchived={false} />);
    await screen.findByText("Feature title");
    fireEvent.click(screen.getByRole("button", { name: "Add knowledge" }));
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "New" } });
    fireEvent.change(screen.getByLabelText("Content"), { target: { value: "Body" } });
    fireEvent.click(screen.getByRole("button", { name: "Save knowledge" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    fireEvent.click(screen.getByRole("button", { name: "Archive" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(5));

    expect(fetchMock.mock.calls[1][0]).toBe(`/api/products/${entry.productUuid}/knowledge`);
    expect(fetchMock.mock.calls[3][1]).toEqual(expect.objectContaining({
      method: "DELETE",
      headers: { "If-Match": 'W/"0"' },
    }));
  });

  it("edits an existing entry with its resource ETag", async () => {
    const edited = { ...entry, title: "Edited title", version: 1 };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(page([entry])))
      .mockResolvedValueOnce(jsonResponse(edited, 200, { ETag: 'W/"1"' }))
      .mockResolvedValueOnce(jsonResponse(page([edited])));
    vi.stubGlobal("fetch", fetchMock);

    render(<KnowledgeTab productUuid={entry.productUuid} productArchived={false} />);
    await screen.findByText("Feature title");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Edited title" } });
    fireEvent.click(screen.getByRole("button", { name: "Save knowledge" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));

    expect(fetchMock.mock.calls[1][0]).toBe(
      `/api/products/${entry.productUuid}/knowledge/${entry.knowledgeUuid}`,
    );
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({
      method: "PATCH",
      headers: expect.objectContaining({ "If-Match": 'W/"0"' }),
      body: JSON.stringify({ title: "Edited title" }),
    }));
  });

  it("restores an archived entry with its resource ETag", async () => {
    const archived: Knowledge = {
      ...entry,
      lifecycleStatus: "ARCHIVED",
      archivedAt: "2026-08-07T01:00:00Z",
    };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(page([archived])))
      .mockResolvedValueOnce(jsonResponse({ ...entry, version: 1 }, 200, { ETag: 'W/"1"' }))
      .mockResolvedValueOnce(jsonResponse(page([])));
    vi.stubGlobal("fetch", fetchMock);

    render(<KnowledgeTab productUuid={entry.productUuid} productArchived={false} />);
    await screen.findByText("Feature title");
    fireEvent.click(screen.getByRole("button", { name: "Restore" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));

    expect(fetchMock.mock.calls[1]).toEqual([
      `/api/products/${entry.productUuid}/knowledge/${entry.knowledgeUuid}/restore`,
      expect.objectContaining({ method: "POST", headers: { "If-Match": 'W/"0"' } }),
    ]);
  });

  it("uses the archive response ETag when restoring in the same view", async () => {
    const archived: Knowledge = {
      ...entry,
      lifecycleStatus: "ARCHIVED",
      archivedAt: "2026-08-07T01:00:00Z",
      version: 1,
    };
    const restored = { ...entry, version: 2 };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(page([entry])))
      .mockResolvedValueOnce(new Response(null, { status: 204, headers: { ETag: 'W/"1"' } }))
      .mockResolvedValueOnce(jsonResponse(page([archived])))
      .mockResolvedValueOnce(jsonResponse(restored, 200, { ETag: 'W/"2"' }))
      .mockResolvedValueOnce(jsonResponse(page([restored])));
    vi.stubGlobal("fetch", fetchMock);

    render(<KnowledgeTab productUuid={entry.productUuid} productArchived={false} />);
    await screen.findByText("Feature title");
    fireEvent.click(screen.getByRole("button", { name: "Archive" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Restore" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Restore" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(5));

    expect(fetchMock.mock.calls[3][1]).toEqual(expect.objectContaining({
      method: "POST",
      headers: { "If-Match": 'W/"1"' },
    }));
  });

  it("requests stable pages and approved sorts", async () => {
    const second = { ...entry, knowledgeUuid: "6cf53b23-eabe-4b51-b565-62dbe4333722", title: "Second" };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(page([entry], 2)))
      .mockResolvedValueOnce(jsonResponse({ ...page([second], 2), page: 1 }))
      .mockResolvedValueOnce(jsonResponse(page([entry], 2)));
    vi.stubGlobal("fetch", fetchMock);

    render(<KnowledgeTab productUuid={entry.productUuid} productArchived={false} />);
    await screen.findByText("Feature title");
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    await screen.findByText("Second");
    expect(fetchMock.mock.calls[1][0]).toContain("page=1");

    fireEvent.change(screen.getByLabelText("Sort"), { target: { value: "title,asc" } });
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    expect(fetchMock.mock.calls[2][0]).toContain("sort=title,asc&page=0&size=20");
  });

  it("is read-only for an archived product", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(page([entry]))));
    render(<KnowledgeTab productUuid={entry.productUuid} productArchived />);
    await screen.findByText("Feature title");
    expect(screen.getByText(/remains readable/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add knowledge" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Archive" })).not.toBeInTheDocument();
  });

  it.each([409, 412, 428])("offers reload on concurrency status %s", async (status) => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(page([entry])))
      .mockResolvedValueOnce(jsonResponse({ code: "PRECONDITION_FAILED" }, status));
    vi.stubGlobal("fetch", fetchMock);
    render(<KnowledgeTab productUuid={entry.productUuid} productArchived={false} />);
    await screen.findByText("Feature title");
    fireEvent.click(screen.getByRole("button", { name: "Archive" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("changed or is no longer editable");
  });
});
