export type SheetValidationError = { field: string; code: string; message: string };

export type SheetImportRow = {
  importRowUuid: string;
  rowNumber: number;
  plannedAction: "CREATE" | "UPDATE" | "INVALID";
  matchStrategy: "NONE" | "PRODUCT_UUID" | "PRODUCT_ID";
  source: { productUuid?: string | null; productId?: string | null; sku?: string | null; productName?: string | null };
  validationErrors: SheetValidationError[];
  executionStatus: "PENDING" | "SUCCEEDED" | "FAILED" | "SKIPPED";
  resultProductUuid: string | null;
  resultProductId: string | null;
  executionErrorCode: string | null;
  executionErrorMessage: string | null;
};

export type SheetImport = {
  importJobUuid: string;
  spreadsheetId: string;
  sheetName: string;
  sourceRange: string;
  status: "PREVIEWED" | "EXECUTING" | "COMPLETED" | "COMPLETED_WITH_ERRORS" | "FAILED";
  totalRows: number;
  validRows: number;
  invalidRows: number;
  createdCount: number;
  updatedCount: number;
  failedCount: number;
  failureCode: string | null;
  failureMessage: string | null;
  version: number;
  rows: SheetImportRow[];
};

export type ProductStorageFolder = {
  storageFolderUuid: string;
  productUuid: string;
  storageProvider: "GOOGLE_DRIVE";
  rootFolderId: string;
  sharedDriveId: string | null;
  productFolderId: string;
  version: number;
  subfolders: Record<"ORIGINAL" | "IMAGES" | "VIDEOS" | "DOCUMENTS" | "CAMPAIGNS" | "ARCHIVE", string>;
};
