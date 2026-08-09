import {cleanup,fireEvent,render,screen,waitFor} from "@testing-library/react";
import {afterEach,describe,expect,it,vi} from "vitest";
import {AssetsTab} from "./assets-tab";
import type {Asset} from "@/lib/assets";

vi.mock("@/components/storage-folder-panel",()=>({StorageFolderPanel:()=>null}));

const PRODUCT="d4476a19-30ed-48d9-a518-f9b111bd0911",ASSET="79be8758-1f0d-4ca5-bad6-f51aa923cdb9";
const asset:Asset={assetUuid:ASSET,productUuid:PRODUCT,creativePlanUuid:null,campaignUuid:null,assetType:"IMAGE",purpose:"Hero",storageProvider:"s3",providerFileId:"a",fileUrl:"https://cdn.example/a.jpg",mediaType:"image/jpeg",originalFilename:"a.jpg",sizeBytes:4,checksumSha256:null,providerMetadata:{region:"eu"},lifecycleStatus:"ACTIVE",archivedAt:null,createdAt:"2026-08-07T00:00:00Z",updatedAt:"2026-08-07T00:00:00Z",version:4};
const page=(content:ReadonlyArray<typeof asset>=[],overrides={})=>({content,page:0,size:10,totalElements:content.length,totalPages:content.length?1:0,sort:"updatedAt,desc",...overrides});
const json=(body:unknown,init:ResponseInit={})=>new Response(JSON.stringify(body),{status:200,...init});

describe("AssetsTab",()=>{
 afterEach(()=>{cleanup();vi.unstubAllGlobals()});

 it("renders loading, empty and archived-product read-only states",async()=>{
  vi.stubGlobal("fetch",vi.fn().mockResolvedValue(json(page())));
  render(<AssetsTab productUuid={PRODUCT} productArchived/>);
  expect(screen.getByRole("status")).toHaveTextContent("Loading assets");
  await waitFor(()=>screen.getByText("No assets found."));
  expect(screen.getByText(/read-only/)).toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"New asset"})).not.toBeInTheDocument();
 });

 it("renders initial load errors",async()=>{
  vi.stubGlobal("fetch",vi.fn().mockResolvedValue(json({message:"Backend unavailable"},{status:503})));
  render(<AssetsTab productUuid={PRODUCT} productArchived={false}/>);
  expect(await screen.findByRole("alert")).toHaveTextContent("Backend unavailable");
 });

 it("creates metadata and renders a safe external link",async()=>{
  const fetchMock=vi.fn().mockResolvedValueOnce(json(page())).mockResolvedValueOnce(json(asset,{status:201,headers:{ETag:'W/"4"'}})).mockResolvedValueOnce(json(page([asset]))).mockResolvedValueOnce(json(asset,{headers:{ETag:'W/"4"'}}));
  vi.stubGlobal("fetch",fetchMock); render(<AssetsTab productUuid={PRODUCT} productArchived={false}/>);
  await screen.findByText("No assets found.");
  fireEvent.change(screen.getByLabelText("originalFilename"),{target:{value:"a.jpg"}});
  fireEvent.change(screen.getByLabelText(/Provider metadata/),{target:{value:'{"region":"eu"}'}});
  fireEvent.click(screen.getByRole("button",{name:"Save asset"}));
  await screen.findByText("a.jpg");
  expect(screen.getByRole("link",{name:"Open file"})).toHaveAttribute("target","_blank");
  expect(screen.getByRole("link",{name:"Open file"})).toHaveAttribute("rel","noopener noreferrer");
  expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({method:"POST"}));
 });

 it("edits with the ETag that was loaded with the form",async()=>{
  const updated={...asset,purpose:"Updated",version:5};
  const fetchMock=vi.fn().mockResolvedValueOnce(json(page([asset]))).mockResolvedValueOnce(json(asset,{headers:{ETag:'W/"4"'}})).mockResolvedValueOnce(json(updated,{headers:{ETag:'W/"5"'}})).mockResolvedValueOnce(json(page([updated]))).mockResolvedValueOnce(json(updated,{headers:{ETag:'W/"5"'}}));
  vi.stubGlobal("fetch",fetchMock); render(<AssetsTab productUuid={PRODUCT} productArchived={false}/>);
  fireEvent.click(await screen.findByRole("button",{name:/a.jpg/}));
  await screen.findByRole("heading",{name:"Edit asset"});
  fireEvent.change(screen.getByLabelText("purpose"),{target:{value:"Updated"}});
  fireEvent.click(screen.getByRole("button",{name:"Save asset"}));
  await waitFor(()=>expect(fetchMock.mock.calls[2][1]).toEqual(expect.objectContaining({method:"PATCH",headers:expect.objectContaining({"If-Match":'W/"4"'})})));
 });

 it.each([
  ["Archive",asset,"DELETE",`/api/products/${PRODUCT}/assets/${ASSET}`],
  ["Restore",{...asset,lifecycleStatus:"ARCHIVED" as const,version:7},"POST",`/api/products/${PRODUCT}/assets/${ASSET}/restore`]
 ])("supports %s with the displayed version",async(label,item,method,url)=>{
  const fetchMock=vi.fn().mockResolvedValueOnce(json(page([item] as never))).mockResolvedValueOnce(new Response(null,{status:204})).mockResolvedValueOnce(json(page()));
  vi.stubGlobal("fetch",fetchMock); render(<AssetsTab productUuid={PRODUCT} productArchived={false}/>);
  fireEvent.click(await screen.findByRole("button",{name:label}));
  await waitFor(()=>expect(fetchMock).toHaveBeenCalledTimes(3));
  expect(fetchMock.mock.calls[1][0]).toBe(url);
  expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({method,headers:{"If-Match":`W/"${item.version}"`}}));
 });

 it("sends a stale displayed lifecycle token and surfaces 412 without fetching a fresh token",async()=>{
  const fetchMock=vi.fn().mockResolvedValueOnce(json(page([asset]))).mockResolvedValueOnce(new Response(null,{status:412}));
  vi.stubGlobal("fetch",fetchMock); render(<AssetsTab productUuid={PRODUCT} productArchived={false}/>);
  fireEvent.click(await screen.findByRole("button",{name:"Archive"}));
  expect(await screen.findByRole("alert")).toHaveTextContent("changed since it was loaded");
  expect(fetchMock).toHaveBeenCalledTimes(2);
  expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({headers:{"If-Match":'W/"4"'}}));
 });

 it("clears stale selection, form and ETag when conflict recovery reloads",async()=>{
  const fetchMock=vi.fn().mockResolvedValueOnce(json(page([asset]))).mockResolvedValueOnce(json(asset,{headers:{ETag:'W/"4"'}})).mockResolvedValueOnce(new Response(null,{status:412})).mockResolvedValueOnce(json(page([asset])));
  vi.stubGlobal("fetch",fetchMock); render(<AssetsTab productUuid={PRODUCT} productArchived={false}/>);
  fireEvent.click(await screen.findByRole("button",{name:/a.jpg/})); await screen.findByRole("heading",{name:"Edit asset"});
  fireEvent.change(screen.getByLabelText("purpose"),{target:{value:"unsaved edit"}}); fireEvent.click(screen.getByRole("button",{name:"Save asset"}));
  fireEvent.click(await screen.findByRole("button",{name:"Reload"}));
  expect(await screen.findByRole("heading",{name:"New asset metadata"})).toBeInTheDocument();
  expect(screen.getByLabelText("purpose")).toHaveValue("");
  expect(fetchMock.mock.calls[2][1]).toEqual(expect.objectContaining({headers:expect.objectContaining({"If-Match":'W/"4"'})}));
 });

 it.each([[409,"archived"],[412,"changed"],[428,"version is missing"]])("shows clear recovery for status %s",async(status,message)=>{
  const fetchMock=vi.fn().mockResolvedValueOnce(json(page())).mockResolvedValueOnce(new Response(null,{status}));
  vi.stubGlobal("fetch",fetchMock); render(<AssetsTab productUuid={PRODUCT} productArchived={false}/>);
  await screen.findByText("No assets found."); fireEvent.click(screen.getByRole("button",{name:"Save asset"}));
  expect(await screen.findByRole("alert")).toHaveTextContent(message);
  expect(screen.getByRole("button",{name:"Reload"})).toBeInTheDocument();
 });

 it("applies filters and navigates stable pagination",async()=>{
  const fetchMock=vi.fn().mockResolvedValueOnce(json(page([asset],{totalElements:11,totalPages:2}))).mockResolvedValue(json(page()));
  vi.stubGlobal("fetch",fetchMock); render(<AssetsTab productUuid={PRODUCT} productArchived={false}/>);
  await screen.findByText("1 / 2");
  fireEvent.change(screen.getByLabelText("Status"),{target:{value:"ARCHIVED"}});
  fireEvent.change(screen.getByLabelText("Type"),{target:{value:"VIDEO"}});
  fireEvent.click(screen.getByRole("button",{name:"Next"}));
  await waitFor(()=>expect(fetchMock.mock.calls.some(call=>String(call[0]).includes("status=ARCHIVED")&&String(call[0]).includes("assetType=VIDEO")&&String(call[0]).includes("page=1"))).toBe(true));
 });
});
