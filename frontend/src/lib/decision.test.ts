import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { forwardDecision } from "./decision";

const id = "00000000-0000-4000-8000-0000000000c1";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  delete process.env.PLATFORM_STAGE6_ENABLED;
  delete process.env.BACKEND_INTERNAL_URL;
});

describe("Decision fail-closed proxy", () => {
  it("returns a local 404 while disabled without contacting Backend", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const result = await forwardDecision(
      new NextRequest("http://local/api/decision-recommendations/generate", { method: "POST" }),
      "/api/decision-recommendations/generate");
    expect(result.status).toBe(404);
    expect(await result.json()).toEqual({ code: "DECISION_DISABLED", message: "Decision engine is unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("forwards empty generate POST without Content-Type and rejects traversal", async () => {
    process.env.PLATFORM_STAGE6_ENABLED = "true";
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const fetchMock = vi.fn().mockImplementation(() => jsonResponse('{"createdCount":0,"items":[],"warnings":[]}'));
    vi.stubGlobal("fetch", fetchMock);
    const ok = await forwardDecision(
      new NextRequest("http://local/api/decision-recommendations/generate", { method: "POST" }),
      "/api/decision-recommendations/generate");
    expect(ok.status).toBe(200);
    expect(fetchMock.mock.calls[0][0].toString()).toBe("http://backend:8080/api/decision-recommendations/generate");
    expect(fetchMock.mock.calls[0][1].method).toBe("POST");
    expect(fetchMock.mock.calls[0][1].headers.get("Content-Type")).toBeNull();
    expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
    const traversal = await forwardDecision(
      new NextRequest("http://local/api/decision-recommendations/../generate", { method: "POST" }),
      "/api/decision-recommendations/../generate");
    expect(traversal.status).toBe(400);
    const extra = await forwardDecision(
      new NextRequest(`http://local/api/decision-recommendations/${id}/extra`),
      `/api/decision-recommendations/${id}/extra`);
    expect(extra.status).toBe(400);
    const list = await forwardDecision(
      new NextRequest("http://local/api/decision-recommendations?status=PENDING"),
      "/api/decision-recommendations");
    expect(list.status).toBe(200);
    expect(fetchMock.mock.calls.at(-1)?.[0].toString())
        .toBe("http://backend:8080/api/decision-recommendations?status=PENDING");
  });

  it("closes forbidden DTO fields and oversize responses", async () => {
    process.env.PLATFORM_STAGE6_ENABLED = "true";
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const request = new NextRequest("http://local/api/decision-recommendations");
    for (const field of ["platformAccountUuid", "evidenceFingerprint", "metricSourceFingerprint", "sourceFingerprint", "secret"]) {
      vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(JSON.stringify({ [field]: "sentinel" }))));
      const result = await forwardDecision(request, "/api/decision-recommendations");
      expect(result.status, field).toBe(502);
      expect(await result.text()).not.toContain("sentinel");
    }
    let cancelled = false;
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new Uint8Array(1024 * 1024));
        controller.enqueue(new Uint8Array(1));
      },
      cancel() {
        cancelled = true;
      },
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(stream, { headers: { "content-type": "application/json" } })));
    const large = await forwardDecision(request, "/api/decision-recommendations");
    expect(large.status).toBe(502);
    expect(cancelled).toBe(true);
  });
});

function jsonResponse(body: string, status = 200) {
  return new Response(body, { status, headers: { "content-type": "application/json" } });
}
