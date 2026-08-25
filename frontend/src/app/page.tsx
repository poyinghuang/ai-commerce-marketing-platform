import { BackendHealth } from "@/components/backend-health";
import Link from "next/link";

export const dynamic = "force-dynamic";

export default function Home() {
  const dashboard = process.env.PLATFORM_STAGE5_ENABLED === "true";
  return (
    <main>
      <section className="panel home-panel">
        <p className="eyebrow">Stage 02 · Product Knowledge Center</p>
        <h1>AI Commerce Marketing Platform</h1>
        <p className="summary">
          專案基礎與 Product Master Vertical Slice 已就緒，可建立、搜尋、編輯、封存與還原商品。
        </p>
        <div className="home-actions">
          {dashboard && <Link className="primary-button link-button" href="/dashboard">Dashboard</Link>}
          <Link className="primary-button link-button" href="/products">Product Center</Link>
          <Link className="secondary-button link-button" href="/connectors/google-sheets">Google Sheets import</Link>
        </div>
        <BackendHealth />
      </section>
    </main>
  );
}
