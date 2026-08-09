"use client";

import Link from "next/link";
import { useState } from "react";
import type { ApiError } from "@/lib/products";
import type { SheetImport } from "@/lib/connectors";

const errorCopy: Record<string, string> = {
  CONNECTOR_NOT_CONFIGURED: "Google Sheets connector is not configured on the server.",
  GOOGLE_PERMISSION_DENIED: "The configured Google identity cannot read this Sheet.",
  GOOGLE_RATE_LIMITED: "Google Sheets rate limit reached. Wait and retry.",
  GOOGLE_PROVIDER_UNAVAILABLE: "Google Sheets is temporarily unavailable.",
  INVALID_SHEET_HEADER: "The Sheet headers do not match the Product import template.",
  SHEET_EMPTY: "The selected Sheet range contains no Product rows.",
  SHEET_ROW_LIMIT_EXCEEDED: "The import exceeds the 1,000-row limit.",
};

export function GoogleSheetsImportView() {
  const [source, setSource] = useState({ spreadsheetId: "", sheetName: "Products", range: "Products!A1:M1001" });
  const [job, setJob] = useState<SheetImport | null>(null);
  const [etag, setEtag] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<number | null>(null);

  async function readResponse(response: Response) {
    const body = (await response.json()) as SheetImport & ApiError;
    if (!response.ok) throw Object.assign(new Error(errorCopy[body.code ?? ""] ?? body.message ?? "Connector request failed"), { status: response.status });
    setJob(body);
    setEtag(response.headers.get("ETag") ?? `W/"${body.version}"`);
  }

  async function preview() {
    setBusy(true); setError(null); setConflict(null);
    try {
      const response = await fetch("/api/connectors/google-sheets/imports/preview", {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(source),
      });
      await readResponse(response);
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Preview failed"); }
    finally { setBusy(false); }
  }

  async function execute() {
    if (!job || !etag) { setConflict(428); return; }
    setBusy(true); setError(null); setConflict(null);
    try {
      const response = await fetch(`/api/connectors/google-sheets/imports/${job.importJobUuid}/execute`, { method: "POST", headers: { "If-Match": etag } });
      if ([409, 412, 428].includes(response.status)) { setConflict(response.status); return; }
      await readResponse(response);
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Execute failed"); }
    finally { setBusy(false); }
  }

  async function reload() {
    if (!job) return;
    setBusy(true); setError(null); setConflict(null);
    try { await readResponse(await fetch(`/api/connectors/google-sheets/imports/${job.importJobUuid}`, { cache: "no-store" })); }
    catch (failure) { setError(failure instanceof Error ? failure.message : "Reload failed"); }
    finally { setBusy(false); }
  }

  const executable = job?.status === "PREVIEWED" && job.validRows > 0;
  return <div className="page-stack connector-page">
    <header className="page-header"><div><p className="eyebrow">Stage 02 · Connector</p><h1>Google Sheets Product import</h1><p className="summary">Preview an immutable snapshot, review every row, then explicitly execute valid creates and updates.</p></div><Link href="/products">Products</Link></header>
    <section className="content-card connector-source"><div className="card-heading"><div><h2>Source</h2><a href="/api/connectors/google-sheets/template">Download CSV template</a></div></div>
      <form className="form-grid" onSubmit={event => { event.preventDefault(); void preview(); }}>
        <label>Spreadsheet ID<input required maxLength={256} value={source.spreadsheetId} onChange={event => setSource({...source, spreadsheetId:event.target.value})}/></label>
        <label>Sheet name<input required maxLength={128} value={source.sheetName} onChange={event => setSource({...source, sheetName:event.target.value})}/></label>
        <label className="span-two">A1 range<input maxLength={256} value={source.range} onChange={event => setSource({...source, range:event.target.value})}/></label>
        <button className="primary-button" disabled={busy}>{busy ? "Working..." : "Preview import"}</button>
      </form>
    </section>
    {error && <div className="state-card error-state" role="alert">{error}</div>}
    {conflict && <div className="state-card warning-state" role="alert"><strong>{conflict === 412 ? "This preview changed." : conflict === 428 ? "The preview version is missing." : "This import cannot execute in its current state."}</strong><p>Reload the persisted import before retrying.</p><button className="secondary-button" onClick={() => void reload()}>Reload import</button></div>}
    {job && <section className="content-card connector-results">
      <div className="card-heading"><div><h2>Preview results</h2><span className={`status-badge connector-${job.status.toLowerCase()}`}>{job.status}</span></div><button className="primary-button" disabled={busy || !executable} onClick={() => void execute()}>{busy ? "Working..." : "Execute valid rows"}</button></div>
      <div className="connector-counts"><span>Total<strong>{job.totalRows}</strong></span><span>Valid<strong>{job.validRows}</strong></span><span>Invalid<strong>{job.invalidRows}</strong></span><span>Created<strong>{job.createdCount}</strong></span><span>Updated<strong>{job.updatedCount}</strong></span><span>Failed<strong>{job.failedCount}</strong></span></div>
      {job.failureMessage && <div className="state-card error-state" role="alert">{job.failureMessage}</div>}
      <div className="table-shell"><table><thead><tr><th>Row</th><th>Product</th><th>Plan</th><th>Validation / execution</th></tr></thead><tbody>{job.rows.map(row => <tr key={row.importRowUuid}><td>{row.rowNumber}</td><td>{row.source.productName || row.source.productId || row.source.productUuid || "—"}<small>{row.source.sku || "No SKU"}</small></td><td><strong>{row.plannedAction}</strong><small>{row.matchStrategy}</small></td><td>{row.validationErrors.length ? <ul className="compact-errors">{row.validationErrors.map((item,index)=><li key={`${item.field}-${index}`}>{item.field}: {item.message}</li>)}</ul> : row.executionErrorMessage ? <span className="error-copy">{row.executionErrorMessage}</span> : <span>{row.executionStatus}{row.resultProductId ? ` · ${row.resultProductId}` : ""}</span>}</td></tr>)}</tbody></table></div>
    </section>}
  </div>;
}
