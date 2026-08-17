import { expect, test } from "@playwright/test";

test("previews and explicitly confirms paused FAKE Campaign and Ad Set",async({page})=>{
 const campaignPlan=await page.request.post("/api/campaigns",{data:{campaignName:`Stage4B ${Date.now()}`,activityType:"SALES",startDate:"2030-01-01",endDate:"2030-01-31",objective:"OUTCOME_SALES",platform:"META",budgetDaily:100,budgetTotal:300,currency:"TWD",promotion:null,landingPage:null}});
 expect(campaignPlan.status()).toBe(201);const plan=await campaignPlan.json();
 await page.goto("/platforms/meta");await expect(page.getByRole("heading",{name:"Meta platform operations"})).toBeVisible();
 await page.getByLabel("Campaign Plan UUID").fill(plan.campaignUuid);await page.getByRole("button",{name:"Preview paused Campaign"}).click();
 await expect(page.getByRole("dialog",{name:"Confirm platform mutation"})).toContainText("PAUSED");
 await page.getByRole("button",{name:"Confirm FAKE operation"}).click();await expect(page.getByText("SUCCEEDED",{exact:true})).toBeVisible();
 const campaign=await page.getByRole("definition").nth(1).textContent();expect(campaign).toBeTruthy();
 await page.getByRole("button",{name:"Preview paused Ad Set"}).click();await expect(page.getByRole("dialog",{name:"Confirm platform mutation"})).toContainText("TWD 50");
 await page.getByRole("button",{name:"Confirm FAKE operation"}).click();await expect(page.getByText("SUCCEEDED",{exact:true})).toBeVisible();
});
