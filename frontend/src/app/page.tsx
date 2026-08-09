import { BackendHealth } from "@/components/backend-health";
import Link from "next/link";

export default function Home() {
  return (
    <main>
      <section className="panel home-panel">
        <p className="eyebrow">Stage 02 · Product Knowledge Center</p>
        <h1>AI Commerce Marketing Platform</h1>
        <p className="summary">
          專案基礎與 Product Master Vertical Slice 已就緒，可建立、搜尋、編輯、封存與還原商品。
        </p>
        <div className="home-actions"><Link className="primary-button link-button" href="/products">Product Center</Link><Link className="secondary-button link-button" href="/connectors/google-sheets">Google Sheets import</Link></div>
        <BackendHealth />
      </section>
    </main>
  );
}
