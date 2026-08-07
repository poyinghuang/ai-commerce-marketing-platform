export type CreativePlan={creativePlanUuid:string;productUuid:string;planName:string;primaryAudience:string|null;secondaryAudience:string|null;painPoint:string|null;coreBenefit:string|null;creativeAngle:string|null;emotionalDirection:string|null;brandTone:string|null;visualStyle:string|null;mainColor:string|null;characterSetting:string|null;cta:string|null;lifecycleStatus:"ACTIVE"|"ARCHIVED";archivedAt:string|null;createdAt:string;updatedAt:string;version:number};
export type CreativePlanPage={content:CreativePlan[];page:number;size:number;totalElements:number;totalPages:number;sort:{field:string;direction:"asc"|"desc"}};
export const creativePlanFields=["planName","primaryAudience","secondaryAudience","painPoint","coreBenefit","creativeAngle","emotionalDirection","brandTone","visualStyle","mainColor","characterSetting","cta"] as const;
export type CreativePlanInput=Record<(typeof creativePlanFields)[number],string>;
export const emptyCreativePlan=Object.fromEntries(creativePlanFields.map(k=>[k,""])) as CreativePlanInput;
export function toInput(plan:CreativePlan):CreativePlanInput{return Object.fromEntries(creativePlanFields.map(k=>[k,plan[k]??""])) as CreativePlanInput;}
export function payload(input:CreativePlanInput){return Object.fromEntries(creativePlanFields.map(k=>[k,input[k].trim()===""?(k==="planName"?"":null):input[k].trim()]));}
export function patch(before:CreativePlanInput,after:CreativePlanInput){return Object.fromEntries(creativePlanFields.filter(k=>before[k]!==after[k]).map(k=>[k,after[k].trim()===""?(k==="planName"?"":null):after[k].trim()]));}
