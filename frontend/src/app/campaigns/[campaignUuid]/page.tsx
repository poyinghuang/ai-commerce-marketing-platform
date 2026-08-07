import { CampaignDetailView } from "@/components/campaign-detail-view";
export default async function CampaignPage({ params }: { params: Promise<{ campaignUuid: string }> }) { const { campaignUuid } = await params; return <main className="app-main"><CampaignDetailView campaignUuid={campaignUuid} /></main>; }
