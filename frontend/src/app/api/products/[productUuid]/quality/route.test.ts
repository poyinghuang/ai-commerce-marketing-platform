import { describe, expect, it } from "vitest";
import * as qualityRoute from "./route";

describe("Quality Route Handler exports", () => {
  it("is GET-only", () => expect(Object.keys(qualityRoute).sort()).toEqual(["GET"]));
});
