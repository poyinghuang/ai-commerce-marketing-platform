"use client";

import type { CampaignInput } from "@/lib/campaigns";
import { campaignFields, emptyCampaign } from "@/lib/campaigns";

const labels: Record<keyof CampaignInput, string> = {
  campaignName: "活動名稱", activityType: "活動類型", startDate: "開始日期", endDate: "結束日期",
  objective: "目標", platform: "平台", budgetDaily: "每日預算", budgetTotal: "總預算",
  currency: "幣別", promotion: "促銷內容", landingPage: "Landing Page",
};

export function CampaignForm({ value = emptyCampaign, disabled = false, submitLabel, onChange, onSubmit }: {
  value?: CampaignInput; disabled?: boolean; submitLabel: string;
  onChange: (value: CampaignInput) => void; onSubmit: () => void;
}) {
  return <form className="product-form" onSubmit={(event) => { event.preventDefault(); onSubmit(); }}>
    <div className="form-grid">
      {campaignFields.map((field) => {
        const textArea = ["objective", "promotion"].includes(field);
        const type = ["startDate", "endDate"].includes(field) ? "date" : ["budgetDaily", "budgetTotal"].includes(field) ? "number" : "text";
        return <label key={field} className={textArea ? "span-two" : ""}>{labels[field]}
          {textArea
            ? <textarea maxLength={2000} disabled={disabled} value={value[field]} onChange={(event) => onChange({ ...value, [field]: event.target.value })} />
            : <input required={field === "campaignName"} type={type} min={type === "number" ? "0" : undefined} step={type === "number" ? "0.0001" : undefined} maxLength={field === "campaignName" ? 256 : field === "landingPage" ? 2048 : 64} disabled={disabled} value={value[field]} onChange={(event) => onChange({ ...value, [field]: event.target.value })} />}
        </label>;
      })}
    </div>
    <button className="primary-button" disabled={disabled}>{submitLabel}</button>
  </form>;
}
