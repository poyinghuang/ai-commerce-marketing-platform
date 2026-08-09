import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { StorageFolderPanel } from "./storage-folder-panel";

const productUuid = "d4476a19-30ed-48d9-a518-f9b111bd0911";
const folder = { storageFolderUuid:"79be8758-1f0d-4ca5-bad6-f51aa923cdb9", productUuid, storageProvider:"GOOGLE_DRIVE", rootFolderId:"root", sharedDriveId:null, productFolderId:"product-folder", version:0, subfolders:{ORIGINAL:"original",IMAGES:"images",VIDEOS:"videos",DOCUMENTS:"documents",CAMPAIGNS:"campaigns",ARCHIVE:"archive"} };
const json = (body: unknown, init: ResponseInit = {}) => new Response(JSON.stringify(body), { status:200, ...init });

describe("StorageFolderPanel",()=>{
  afterEach(()=>{cleanup();vi.unstubAllGlobals();});
  it("creates an absent folder and renders all six roles",async()=>{
    const fetchMock=vi.fn().mockResolvedValueOnce(json({code:"STORAGE_FOLDER_NOT_FOUND"},{status:404})).mockResolvedValueOnce(json(folder,{status:201}));
    vi.stubGlobal("fetch",fetchMock); render(<StorageFolderPanel productUuid={productUuid} productArchived={false}/>);
    fireEvent.click(await screen.findByRole("button",{name:"Create folder structure"}));
    expect(await screen.findByText("product-folder")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(6);
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({method:"POST"}));
  });
  it("keeps an archived Product read-only",async()=>{
    vi.stubGlobal("fetch",vi.fn().mockResolvedValue(json({code:"STORAGE_FOLDER_NOT_FOUND"},{status:404})));
    render(<StorageFolderPanel productUuid={productUuid} productArchived/>);
    expect(await screen.findByText(/Restore the Product/)).toBeInTheDocument();
    expect(screen.queryByRole("button",{name:"Create folder structure"})).not.toBeInTheDocument();
  });
  it("offers recovery after a load failure",async()=>{
    const fetchMock=vi.fn().mockResolvedValueOnce(json({message:"Backend unavailable"},{status:503})).mockResolvedValueOnce(json(folder));
    vi.stubGlobal("fetch",fetchMock); render(<StorageFolderPanel productUuid={productUuid} productArchived={false}/>);
    fireEvent.click(await screen.findByRole("button",{name:"Retry"}));
    await waitFor(()=>expect(screen.getByText("product-folder")).toBeInTheDocument());
  });
});
