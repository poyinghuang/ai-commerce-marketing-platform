import { Suspense } from "react";
import { CampaignListView } from "@/components/campaign-list-view";
export default function CampaignsPage() { return <main className="app-main"><Suspense fallback={<div className="state-card">載入 Campaigns…</div>}><CampaignListView /></Suspense></main>; }
