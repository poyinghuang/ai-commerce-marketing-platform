import type { Product } from "@/lib/products";
import type { Knowledge } from "@/lib/knowledge";
import type { CreativePlan } from "@/lib/creative-plans";
import type { Campaign, CampaignProduct } from "@/lib/campaigns";
import type { Asset } from "@/lib/assets";

export type AggregateCampaign = Campaign & { association: CampaignProduct };

export type ProductAggregate = {
  product: Product;
  knowledge: Knowledge[];
  creativePlans: CreativePlan[];
  campaigns: AggregateCampaign[];
  assets: Asset[];
};
