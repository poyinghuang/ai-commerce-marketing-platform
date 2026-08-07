import { describe, expect, it } from "vitest";
import * as adjustmentRoute from "./route";

describe("Quality manual adjustment Route Handler exports", () => {
  it("is PATCH-only", () => expect(Object.keys(adjustmentRoute).sort()).toEqual(["PATCH"]));
});
