import { expect, test } from "@playwright/test";

test("Compose-backed /platforms/meta does not auto-POST platform writes or refreshes", async ({ page }) => {
  const posts: string[] = [];
  page.on("request", (request) => {
    if (request.method() !== "POST") {
      return;
    }
    let path = request.url();
    try {
      path = new URL(request.url()).pathname;
    } catch {
      /* keep the raw URL when the browser emits a relative or opaque request */
    }
    if (path.startsWith("/api/platforms/meta/") || path.startsWith("/api/platform-entities/")) {
      posts.push(`${request.method()} ${path}`);
    }
  });

  await page.goto("/platforms/meta");
  await expect(page.getByRole("heading", { name: "Meta platform operations" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "4. Delivery and metrics" })).toBeVisible();
  await page.waitForTimeout(2000);
  expect(posts).toEqual([]);
});
