import { NextResponse } from "next/server";

const TIMEOUT_MS = 5_000;

export async function GET() {
  const backendUrl = process.env.BACKEND_INTERNAL_URL;
  if (!backendUrl) {
    return NextResponse.json({ status: "DOWN" }, { status: 503 });
  }

  try {
    const response = await fetch(`${backendUrl}/actuator/health`, {
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    const health = (await response.json()) as { status?: string };
    const requestId = response.headers.get("X-Request-ID");
    const headers = requestId ? { "X-Request-ID": requestId } : undefined;
    const isUp = response.ok && health.status === "UP";

    return NextResponse.json(
      { status: isUp ? "UP" : "DOWN" },
      { status: isUp ? 200 : 503, headers },
    );
  } catch {
    return NextResponse.json({ status: "DOWN" }, { status: 503 });
  }
}
