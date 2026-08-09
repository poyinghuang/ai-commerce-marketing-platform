import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { GoogleSheetsImportView } from "./google-sheets-import-view";

const job = {importJobUuid:"79be8758-1f0d-4ca5-bad6-f51aa923cdb9",spreadsheetId:"sheet",sheetName:"Products",sourceRange:"Products!A1:M1001",status:"PREVIEWED",totalRows:2,validRows:1,invalidRows:1,createdCount:0,updatedCount:0,failedCount:0,failureCode:null,failureMessage:null,version:0,rows:[{importRowUuid:"11111111-1111-4111-8111-111111111111",rowNumber:2,plannedAction:"CREATE",matchStrategy:"NONE",source:{productName:"Valid"},validationErrors:[],executionStatus:"PENDING",resultProductUuid:null,resultProductId:null,executionErrorCode:null,executionErrorMessage:null},{importRowUuid:"22222222-2222-4222-8222-222222222222",rowNumber:3,plannedAction:"INVALID",matchStrategy:"NONE",source:{productName:""},validationErrors:[{field:"productName",code:"REQUIRED",message:"must not be blank"}],executionStatus:"SKIPPED",resultProductUuid:null,resultProductId:null,executionErrorCode:null,executionErrorMessage:null}]};
const json=(body:unknown,init:ResponseInit={})=>new Response(JSON.stringify(body),{status:200,...init});

describe("GoogleSheetsImportView",()=>{
  afterEach(()=>{cleanup();vi.unstubAllGlobals();});
  it("previews rows and executes with the preview ETag",async()=>{
    const completed={...job,status:"COMPLETED",createdCount:1,version:1,rows:[{...job.rows[0],executionStatus:"SUCCEEDED",resultProductId:"PROD-00000001"},job.rows[1]]};
    const fetchMock=vi.fn().mockResolvedValueOnce(json(job,{status:201,headers:{ETag:'W/"0"'}})).mockResolvedValueOnce(json(completed,{headers:{ETag:'W/"1"'}}));
    vi.stubGlobal("fetch",fetchMock); render(<GoogleSheetsImportView/>);
    fireEvent.change(screen.getByLabelText("Spreadsheet ID"),{target:{value:"sheet"}}); fireEvent.click(screen.getByRole("button",{name:"Preview import"}));
    expect(await screen.findByText("must not be blank",{exact:false})).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:"Execute valid rows"}));
    await waitFor(()=>expect(screen.getByText("PROD-00000001",{exact:false})).toBeInTheDocument());
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({method:"POST",headers:{"If-Match":'W/"0"'}}));
  });
  it("shows stale preview recovery and reloads persisted state",async()=>{
    const fetchMock=vi.fn().mockResolvedValueOnce(json(job,{status:201,headers:{ETag:'W/"0"'}})).mockResolvedValueOnce(json({code:"PRECONDITION_FAILED"},{status:412})).mockResolvedValueOnce(json({...job,version:1},{headers:{ETag:'W/"1"'}}));
    vi.stubGlobal("fetch",fetchMock); render(<GoogleSheetsImportView/>); fireEvent.change(screen.getByLabelText("Spreadsheet ID"),{target:{value:"sheet"}}); fireEvent.click(screen.getByRole("button",{name:"Preview import"}));
    fireEvent.click(await screen.findByRole("button",{name:"Execute valid rows"})); expect(await screen.findByRole("alert")).toHaveTextContent("preview changed");
    fireEvent.click(screen.getByRole("button",{name:"Reload import"})); await waitFor(()=>expect(fetchMock).toHaveBeenCalledTimes(3));
  });
  it("maps provider errors to actionable copy",async()=>{
    vi.stubGlobal("fetch",vi.fn().mockResolvedValue(json({code:"GOOGLE_PERMISSION_DENIED",message:"denied"},{status:403})));
    render(<GoogleSheetsImportView/>); fireEvent.change(screen.getByLabelText("Spreadsheet ID"),{target:{value:"sheet"}}); fireEvent.click(screen.getByRole("button",{name:"Preview import"}));
    expect(await screen.findByRole("alert")).toHaveTextContent("cannot read this Sheet");
  });
});
