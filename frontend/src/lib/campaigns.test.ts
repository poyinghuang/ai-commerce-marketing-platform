import { describe, expect, it } from "vitest";
import { associationPatch, campaignPatch, campaignPayload, type Campaign, type CampaignInput, type CampaignProduct } from "./campaigns";

const input: CampaignInput = { campaignName: " Launch ", activityType: "", startDate: "2026-08-01", endDate: "", objective: " Sales ", platform: "", budgetDaily: "10.0000", budgetTotal: "", currency: "TWD", promotion: "", landingPage: "" };

describe("campaign payload helpers", () => {
  it("trims create fields and maps blank optional values to null", () => {
    expect(campaignPayload(input)).toMatchObject({ campaignName: "Launch", activityType: null, objective: "Sales", budgetDaily: "10.0000", endDate: null });
  });
  it("keeps absent merge-patch fields out of the payload", () => {
    const changed = { ...input, campaignName: "Launch", objective: "" };
    expect(campaignPatch(input, changed)).toEqual({ campaignName: "Launch", objective: null });
  });
  it("only emits actual association changes and never changes identity", () => {
    const before = { campaignUuid: "c", productUuid: "p", role: "HERO", priority: 1, budgetWeight: "50.00" } as CampaignProduct;
    expect(associationPatch(before, { productUuid: "other", role: "HERO", priority: "2", budgetWeight: "50.00" })).toEqual({ priority: 2 });
  });
  it("does not expose mutable system fields through CampaignInput", () => {
    expect(Object.keys(campaignPayload(input))).not.toEqual(expect.arrayContaining(["campaignUuid", "version", "lifecycleStatus"] satisfies Array<keyof Campaign>));
  });
});
