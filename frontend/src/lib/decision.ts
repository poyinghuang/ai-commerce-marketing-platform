import { NextRequest, NextResponse } from "next/server";

const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
const GENERATE = /^\/api\/decision-recommendations\/generate$/;
const LIST = /^\/api\/decision-recommendations$/;
const ITEM = new RegExp(`^/api/decision-recommendations/${UUID}$`);
const APPROVE = new RegExp(`^/api/decision-recommendations/${UUID}/approve$`);
const REJECT = new RegExp(`^/api/decision-recommendations/${UUID}/reject$`);
const SAFE_REQUEST_ID = /^[A-Za-z0-9._:-]{1,128}$/;
const RESPONSE_LIMIT = 1024 * 1024;
const LIST_KEYS = new Set(["page", "size", "status"]);
const FORBIDDEN = [
  "platformAccountUuid", "accountReference", "externalId", "canonicalPayload", "requestPayload",
  "outcomeEvidence", "safeProviderTraceId", "authorization", "cookie", "token", "credential", "secret",
  "providerBody", "providerUrl", "sourceFingerprint", "evidenceFingerprint", "metricSourceFingerprint",
];

export async function forwardDecision(request: NextRequest, backendPath: string) {
  if (process.env.PLATFORM_STAGE6_ENABLED !== "true") {
    return error("DECISION_DISABLED", "Decision engine is unavailable", 404);
  }
  if (request.nextUrl.hash || backendPath.includes("..") || (!GENERATE.test(backendPath) && !LIST.test(backendPath)
      && !ITEM.test(backendPath) && !APPROVE.test(backendPath) && !REJECT.test(backendPath))) {
    return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
  }
  const generate = GENERATE.test(backendPath);
  const list = LIST.test(backendPath);
  const item = ITEM.test(backendPath);
  const approve = APPROVE.test(backendPath);
  const reject = REJECT.test(backendPath);
  if (generate && (request.method !== "POST" || request.nextUrl.search || request.headers.get("content-type")
      || request.headers.get("if-match"))) {
    return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
  }
  if (list && request.method !== "GET") {
    return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
  }
  if (list) {
    const keys = [...request.nextUrl.searchParams.keys()];
    if (keys.some((key) => !LIST_KEYS.has(key) || request.nextUrl.searchParams.getAll(key).length !== 1)) {
      return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
    }
  }
  if ((item || approve || reject) && request.nextUrl.search) {
    return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
  }
  if (item && request.method !== "GET") {
    return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
  }
  if ((approve || reject) && request.method !== "POST") {
    return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
  }
  const origin = backendOrigin();
  if (!origin) return error("BACKEND_UNAVAILABLE", "Backend is unavailable", 503);
  const headers = new Headers();
  const requestId = request.headers.get("X-Request-ID");
  if (requestId && SAFE_REQUEST_ID.test(requestId)) headers.set("X-Request-ID", requestId);
  const ifMatch = request.headers.get("If-Match");
  if (ifMatch && (approve || reject)) headers.set("If-Match", ifMatch);
  let body: Uint8Array<ArrayBuffer> | undefined;
  if (request.method === "POST" && !generate) {
    const contentType = request.headers.get("content-type")?.split(";", 1)[0].trim().toLowerCase();
    if (reject && contentType !== "application/json") {
      return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
    }
    if (approve && contentType && contentType !== "application/json") {
      return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
    }
    if (contentType === "application/json") headers.set("Content-Type", "application/json");
    try {
      body = await readBounded(request.body, 16 * 1024);
    } catch {
      return error("PAYLOAD_TOO_LARGE", "Request body is too large", 413);
    }
    if (approve && new TextDecoder().decode(body).trim() && new TextDecoder().decode(body).trim() !== "{}") {
      return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
    }
    if (body.byteLength === 0) body = undefined;
  }
  if (generate) {
    try {
      const bytes = await readBounded(request.body, 16 * 1024);
      if (new TextDecoder().decode(bytes).trim()) {
        return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
      }
    } catch {
      return error("DECISION_REQUEST_INVALID", "Decision request is invalid", 400);
    }
  }
  try {
    const timeout = AbortSignal.timeout(10_000);
    const signal = AbortSignal.any([request.signal, timeout]);
    const upstream = new URL(backendPath, origin);
    if (list) {
      for (const key of LIST_KEYS) {
        const value = request.nextUrl.searchParams.get(key);
        if (value) upstream.searchParams.set(key, value);
      }
    }
    const response = await fetch(upstream, {
      method: request.method, headers, body, cache: "no-store", redirect: "manual", signal,
    });
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
    for (const name of ["Content-Type", "X-Request-ID", "Cache-Control", "ETag"]) {
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
    return Object.entries(value).every(([key, item]) => SAFE_KEYS.has(key) && !FORBIDDEN.includes(key) && isAllowlisted(item));
  }
  return true;
}

const SAFE_KEYS = new Set([
  "generatedAt", "windowStart", "windowEnd", "timezone", "currency", "consideredCampaignCount", "createdCount",
  "updatedCount", "replayedCount", "skippedIncompleteCount", "items", "truncated", "warnings", "content", "page",
  "size", "totalElements", "totalPages", "recommendationUuid", "platformCampaignUuid", "campaignUuid", "campaignName",
  "recommendationType", "status", "attributionClickDays", "attributionViewDays", "desiredState", "reasonSummary",
  "riskSummary", "evidence", "href", "productUuid", "version", "createdAt", "updatedAt", "impressions", "reach",
  "clicks", "conversions", "spend", "revenue", "ctr", "cpc", "cpm", "cpa", "cvr", "roas", "decision",
  "recommendationDecisionUuid", "reason", "decidedAt", "code", "message", "requestId", "timestamp", "path",
  "fieldErrors", "field",
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
