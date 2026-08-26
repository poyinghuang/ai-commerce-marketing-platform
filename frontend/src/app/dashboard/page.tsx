import { notFound } from "next/navigation";
import { DashboardWorkbench } from "@/components/dashboard-workbench";

export const dynamic = "force-dynamic";

export default function DashboardPage() {
  if (process.env.PLATFORM_STAGE5_ENABLED !== "true") notFound();
  return <DashboardWorkbench stage6Enabled={process.env.PLATFORM_STAGE6_ENABLED === "true"} />;
}
