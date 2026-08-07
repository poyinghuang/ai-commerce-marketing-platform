export type ReadinessStatus = "DRAFT" | "NEEDS_REVIEW" | "READY";

export type QualityBlocker = {
  code: string;
  field: string | null;
  message: string;
};

export type ProductQuality = {
  productUuid: string;
  productMasterScore: number;
  productKnowledgeScore: number;
  creativePlanScore: number;
  assetMetadataScore: number;
  campaignReadinessScore: number;
  systemScore: number;
  aiSuggestedScore: number | null;
  manualAdjustment: number;
  manualAdjustmentReason: string | null;
  manualAdjustedBy: string | null;
  manualAdjustedAt: string | null;
  finalScore: number;
  blockers: QualityBlocker[];
  readinessStatus: ReadinessStatus;
  statusReason: string;
  calculatedAt: string;
  version: number;
};

export const qualityComponents = [
  ["Product Master", "productMasterScore", 35],
  ["Product Knowledge", "productKnowledgeScore", 25],
  ["Creative Plan", "creativePlanScore", 25],
  ["Asset Metadata", "assetMetadataScore", 10],
  ["Campaign Readiness", "campaignReadinessScore", 5],
] as const;
