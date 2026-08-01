import { BackendHealth } from "@/components/backend-health";

export default function Home() {
  return (
    <main>
      <section className="panel">
        <p className="eyebrow">Stage 01 · Project Foundation</p>
        <h1>AI Commerce Marketing Platform</h1>
        <p className="summary">
          Backend、Frontend 與 PostgreSQL 基礎環境已就緒。業務功能將依 Stage 文件逐步實作。
        </p>
        <BackendHealth />
      </section>
    </main>
  );
}
