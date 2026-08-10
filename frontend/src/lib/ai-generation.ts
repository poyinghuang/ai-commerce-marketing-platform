export type AiJob = {
  generationJobUuid: string;
  generationType: "TEXT" | "IMAGE";
  promptTemplateVersionUuid: string;
  providerKey: string;
  modelKey: string;
  status: string;
  modelProfile: string;
  estimatedCost: number;
  reservedCost: number;
  actualCost: number;
  currency: string;
  failureCode: string | null;
  failureMessage: string | null;
  version: number;
  outputUuid: string | null;
};

export type AiBatch = {
  generationBatchUuid: string;
  productUuid: string;
  creativePlanUuid: string;
  status: string;
  currency: string;
  requestedJobCount: number;
  succeededJobCount: number;
  failedJobCount: number;
  rejectedJobCount: number;
  version: number;
  jobs: AiJob[];
};

export type AiOutput = {
  generationOutputUuid: string;
  generationJobUuid: string;
  generationType: "TEXT" | "IMAGE";
  textContent: string | null;
  modelLabel: string;
  inputUnits: number;
  outputUnits: number;
  actualCost: number;
  currency: string;
  safetyFindings: string[];
  reviewStatus: string;
  version: number;
  sourceAssetUuid: string | null;
  maskAssetUuid: string | null;
  generatedAssetUuid: string | null;
  generationMode: string | null;
  workflowKey: string | null;
  workflowVersion: string | null;
  imageWidth: number | null;
  imageHeight: number | null;
  mediaType: string | null;
  sizeBytes: number | null;
  sourceChecksumSha256: string | null;
  maskChecksumSha256: string | null;
  outputChecksumSha256: string | null;
  protectedPixelsSha256: string | null;
  preservationAlgorithm: string | null;
  preservationStatus: "PASSED" | "BLOCKED" | null;
  preservationDetails: { changedPixelCount?: number; protectedPixelCount?: number } | null;
  reviewBlockers: string[];
  reviewDecisions: AiReviewDecision[];
};

export type AiReviewDecision = {
  reviewDecisionUuid: string;
  decision: "APPROVED" | "REJECTED";
  reason: string | null;
  reviewerType: "LOCAL_ADMIN" | "TRUSTED_ACTOR";
  reviewerId: string;
  reviewedOutputVersion: number;
  decidedAt: string;
};

export type AiBudgetStatus = {
  currency: string;
  maximumJobCost: number;
  maximumBatchCost: number;
  maximumDailyCost: number;
  modelProfiles: string[];
  textTemplateKeys: string[];
  imageModelProfiles: string[];
  imageTemplateKeys: string[];
  imageWorkflowKeys: string[];
};
