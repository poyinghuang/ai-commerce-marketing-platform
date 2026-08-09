"use client";

import { useCallback, useEffect, useState } from "react";
import type { ProductStorageFolder } from "@/lib/connectors";
import type { ApiError } from "@/lib/products";

export function StorageFolderPanel({ productUuid, productArchived }: { productUuid: string; productArchived: boolean }) {
  const [folder, setFolder] = useState<ProductStorageFolder | null>(null);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const response = await fetch(`/api/products/${productUuid}/storage-folder`, { cache: "no-store" });
      if (response.status === 404) { setFolder(null); return; }
      const body = (await response.json()) as ProductStorageFolder & ApiError;
      if (!response.ok) throw new Error(body.message ?? "Unable to load Drive folders");
      setFolder(body);
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to load Drive folders"); }
    finally { setLoading(false); }
  }, [productUuid]);

  useEffect(() => {
    const controller = new AbortController();
    fetch(`/api/products/${productUuid}/storage-folder`, { cache: "no-store", signal: controller.signal })
      .then(async response => {
        if (response.status === 404) { setFolder(null); return; }
        const body = (await response.json()) as ProductStorageFolder & ApiError;
        if (!response.ok) throw new Error(body.message ?? "Unable to load Drive folders");
        setFolder(body);
      })
      .catch((failure: unknown) => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Unable to load Drive folders"); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [productUuid]);

  async function ensure() {
    if (productArchived) return;
    setCreating(true); setError(null);
    try {
      const response = await fetch(`/api/products/${productUuid}/storage-folder`, { method: "POST" });
      const body = (await response.json()) as ProductStorageFolder & ApiError;
      if (!response.ok) throw new Error(body.message ?? "Unable to create Drive folders");
      setFolder(body);
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to create Drive folders"); }
    finally { setCreating(false); }
  }

  return <section className="storage-folder-panel" aria-labelledby="drive-folder-heading">
    <div className="card-heading"><div><h3 id="drive-folder-heading">Google Drive folders</h3>{folder && <span className="status-badge active">Connected</span>}</div>{!folder && !productArchived && <button className="secondary-button" disabled={creating || loading} onClick={() => void ensure()}>{creating ? "Creating..." : "Create folder structure"}</button>}</div>
    {productArchived && !folder && <div className="state-card warning-state">Restore the Product before creating its Drive folder structure.</div>}
    {loading ? <div className="state-card" role="status">Loading Drive folder state...</div> : error ? <div className="state-card error-state" role="alert">{error} <button className="secondary-button" onClick={() => void load()}>Retry</button></div> : folder ? <div className="folder-tree"><p><strong>Product folder</strong><code>{folder.productFolderId}</code></p><ul>{Object.entries(folder.subfolders).map(([role,id])=><li key={role}><span>{role}</span><code>{id}</code></li>)}</ul>{folder.sharedDriveId && <p className="summary">Shared Drive: <code>{folder.sharedDriveId}</code></p>}</div> : <p className="summary">No managed Drive folder exists for this Product.</p>}
  </section>;
}
