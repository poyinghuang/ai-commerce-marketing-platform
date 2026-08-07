import { describe, expect, it } from "vitest";
import * as aggregateRoute from "./route";

describe("Aggregate Route Handler exports", () => {
  it("is GET-only", () => {
    expect(Object.keys(aggregateRoute).sort()).toEqual(["GET"]);
  });
});
