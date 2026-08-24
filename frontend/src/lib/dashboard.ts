import { NextRequest, NextResponse } from "next/server";

const SUMMARY = /^\/api\/dashboard$/;
const SECTION = /^\/api\/dashboard\/(todos|products|reviews|campaigns|platform-campaigns|anomalies)$/;
const SAFE_REQUEST_ID = /^[A-Za-z0-9._:-]{1,128}$/;
const RESPONSE_LIMIT = 1024 * 1024;
const PAGE_KEYS = new Set(["page", "size"]);

export async function forwardDashboard(request: NextRequest, backendPath: string) {
  if (process.env.PLATFORM_STAGE5_ENABLED !== "true") {
    return error("DASHBOARD_DISABLED", "Dashboard is unavailable", 404);
  }
  if (request.method !== "GET" || request.nextUrl.hash || (!SUMMARY.test(backendPath) && !SECTION.test(backendPath))) {
    return error("DASHBOARD_REQUEST_INVALID", "Dashboard request is invalid", 400);
  }
  if (SUMMARY.test(backendPath) && request.nextUrl.search) {
    return error("DASHBOARD_REQUEST_INVALID", "Dashboard request is invalid", 400);
  }
  if (SECTION.test(backendPath)) {
    const keys = [...request.nextUrl.searchParams.keys()];
    if (keys.some((key) => !PAGE_KEYS.has(key) || request.nextUrl.searchParams.getAll(key).length !== 1)) {
      return error("DASHBOARD_REQUEST_INVALID", "Dashboard request is invalid", 400);
    }
  }
  const origin = backendOrigin();
  if (!origin) return error("BACKEND_UNAVAILABLE", "Backend is unavailable", 503);
  const headers = new Headers();
  const requestId = request.headers.get("X-Request-ID");
  if (requestId && SAFE_REQUEST_ID.test(requestId)) headers.set("X-Request-ID", requestId);
  try {
    const timeout = AbortSignal.timeout(10_000);
    const signal = AbortSignal.any([request.signal, timeout]);
    const upstream = new URL(backendPath, origin);
    for (const key of PAGE_KEYS) {
      const value = request.nextUrl.searchParams.get(key);
      if (value) upstream.searchParams.set(key, value);
    }
    const response = await fetch(upstream, { method: "GET", headers, cache: "no-store", redirect: "manual", signal });
    if (response.status >= 300 && response.status < 400) return error("BACKEND_UNAVAILABLE", "Backend is unavailable", 502);
    const contentType = response.headers.get("content-type")?.split(";", 1)[0].trim().toLowerCase();
    if (contentType !== "application/json") return error("BACKEND_UNAVAILABLE", "Backend is unavailable", 502);
    let bytes: Uint8Array<ArrayBuffer>;
    try {
      bytes = await readBounded(response.body, RESPONSE_LIMIT);
    } catch {
      return error("BACKEND_RESPONSE_TOO_LARGE", "Backend response is too large", 502);
    }
    if (bytes.byteLength === 0 || !safeJson(bytes)) return error("BACKEND_UNAVAILABLE", "Backend is unavailable", 502);
    const exposed = new Headers();
    for (const name of ["Content-Type", "X-Request-ID", "Cache-Control"]) {
      const value = response.headers.get(name);
      if (value) exposed.set(name, value);
    }
    return new NextResponse(bytes.buffer, { status: response.status, headers: exposed });
  } catch (cause) {
    const timedOut = cause instanceof DOMException && cause.name === "TimeoutError";
    return error(timedOut ? "BACKEND_TIMEOUT" : "BACKEND_UNAVAILABLE",
        timedOut ? "Backend request timed out" : "Backend is unavailable", timedOut ? 504 : 503);
  }
}

async function readBounded(stream: ReadableStream<Uint8Array> | null, limit: number): Promise<Uint8Array<ArrayBuffer>> {
  if (!stream) return new Uint8Array(0);
  const reader = stream.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!value) continue;
      size += value.byteLength;
      if (size > limit) {
        await reader.cancel();
        throw new Error("limit");
      }
      chunks.push(value);
    }
    const result = new Uint8Array(new ArrayBuffer(size));
    let offset = 0;
    for (const chunk of chunks) {
      result.set(chunk, offset);
      offset += chunk.byteLength;
    }
    return result;
  } finally {
    reader.releaseLock();
  }
}

function safeJson(bytes: Uint8Array) {
  try {
    return isAllowlisted(JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)));
  } catch {
    return false;
  }
}

function isAllowlisted(value: unknown): boolean {
  if (Array.isArray(value)) return value.every(isAllowlisted);
  if (value && typeof value === "object") {
    return Object.entries(value).every(([key, item]) => SAFE_KEYS.has(key) && isAllowlisted(item));
  }
  return true;
}

const SAFE_KEYS = new Set([
  "generatedAt", "todos", "products", "reviews", "campaigns", "platformCampaigns", "anomalies", "kpis",
  "available", "items", "truncated", "totalElements", "kind", "subjectUuid", "productUuid", "href", "title",
  "summary", "occurredAt", "productName", "lifecycleStatus", "readinessStatus", "finalScore", "blockerCount",
  "generationOutputUuid", "generationType", "reviewStatus", "version", "approvalBlocked", "campaignUuid",
  "campaignName", "startDate", "endDate", "platform", "platformCampaignUuid", "desiredState", "observedState",
  "windowStart", "windowEnd", "timezone", "currency", "attributionClickDays", "attributionViewDays",
  "eligibleCampaignCount", "presentCampaignCount", "incomplete", "impressions", "reach", "clicks", "conversions",
  "spend", "revenue", "ctr", "cpc", "cpm", "cpa", "cvr", "roas", "content", "page", "size", "totalPages",
  "code", "message", "requestId", "timestamp", "path", "fieldErrors", "field",
]);

function backendOrigin() {
  const value = process.env.BACKEND_INTERNAL_URL;
  if (!value) return null;
  try {
    const url = new URL(value);
    if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
      return null;
    }
    url.pathname = url.pathname.replace(/\/*$/, "/");
    return url;
  } catch {
    return null;
  }
}

function error(code: string, message: string, status: number) {
  return NextResponse.json({ code, message }, { status });
}
