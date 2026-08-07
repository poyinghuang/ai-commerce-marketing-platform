export type LifecycleStatus = "ACTIVE" | "ARCHIVED";

export type Campaign = {
  campaignUuid: string;
  campaignName: string;
  activityType: string | null;
  startDate: string | null;
  endDate: string | null;
  objective: string | null;
  platform: string | null;
  budgetDaily: string | null;
  budgetTotal: string | null;
  currency: string | null;
  promotion: string | null;
  landingPage: string | null;
  lifecycleStatus: LifecycleStatus;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  association?: CampaignProduct;
};

export type CampaignProduct = {
  campaignProductUuid: string;
  campaignUuid: string;
  productUuid: string;
  role: string | null;
  priority: number | null;
  budgetWeight: string | null;
  lifecycleStatus: LifecycleStatus;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: { field: string; direction: "asc" | "desc" };
};

export const campaignFields = ["campaignName", "activityType", "startDate", "endDate", "objective", "platform", "budgetDaily", "budgetTotal", "currency", "promotion", "landingPage"] as const;
export type CampaignInput = Record<(typeof campaignFields)[number], string>;
export const emptyCampaign = Object.fromEntries(campaignFields.map((field) => [field, ""])) as CampaignInput;

export function campaignToInput(campaign: Campaign): CampaignInput {
  return Object.fromEntries(campaignFields.map((field) => [field, campaign[field] ?? ""])) as CampaignInput;
}

function nullable(value: string) { return value.trim() === "" ? null : value.trim(); }
export function campaignPayload(input: CampaignInput) {
  return Object.fromEntries(campaignFields.map((field) => [field, field === "campaignName" ? input[field].trim() : nullable(input[field])]));
}
export function campaignPatch(before: CampaignInput, after: CampaignInput) {
  return Object.fromEntries(campaignFields.filter((field) => before[field] !== after[field]).map((field) => [field, field === "campaignName" ? after[field].trim() : nullable(after[field])]));
}

export type AssociationInput = { productUuid: string; role: string; priority: string; budgetWeight: string };
export const emptyAssociation: AssociationInput = { productUuid: "", role: "", priority: "", budgetWeight: "" };
export function associationPayload(input: AssociationInput) {
  return { productUuid: input.productUuid.trim(), role: nullable(input.role), priority: input.priority === "" ? null : Number(input.priority), budgetWeight: nullable(input.budgetWeight) };
}
export function associationPatch(before: CampaignProduct, input: AssociationInput) {
  const values = { role: nullable(input.role), priority: input.priority === "" ? null : Number(input.priority), budgetWeight: nullable(input.budgetWeight) };
  return Object.fromEntries(Object.entries(values).filter(([key, value]) => String(before[key as keyof CampaignProduct] ?? "") !== String(value ?? "")));
}
