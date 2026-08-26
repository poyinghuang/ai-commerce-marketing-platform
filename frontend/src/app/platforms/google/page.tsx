import { notFound } from "next/navigation";
import { PlatformMetaManager } from "@/components/platform-meta-manager";

export const dynamic = "force-dynamic";

export default function GooglePlatformPage(){
  if(process.env.PLATFORM_STAGE7_GOOGLE_ENABLED!=="true")notFound();
  return <PlatformMetaManager title="Google platform operations" apiBase="/api/platforms/google" operationsBase="/api/platforms/google/operations" stage4c={process.env.PLATFORM_STAGE4C_ENABLED==="true"}/>;
}
