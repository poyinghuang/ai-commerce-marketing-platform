export type AiJob = {
  generationJobUuid: string;
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
  textContent: string;
  modelLabel: string;
  inputUnits: number;
  outputUnits: number;
  actualCost: number;
  currency: string;
  safetyFindings: string[];
  reviewStatus: string;
  version: number;
};

export type AiBudgetStatus = {
  currency: string;
  maximumJobCost: number;
  maximumBatchCost: number;
  maximumDailyCost: number;
  modelProfiles: string[];
  textTemplateKeys: string[];
};
