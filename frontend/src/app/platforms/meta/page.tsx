import { notFound } from "next/navigation";
import { PlatformMetaManager } from "@/components/platform-meta-manager";

export const dynamic = "force-dynamic";

export default function MetaPlatformPage(){if(process.env.PLATFORM_STAGE4B_ENABLED!=="true")notFound();return <PlatformMetaManager stage4c={process.env.PLATFORM_STAGE4C_ENABLED==="true"}/>;}
