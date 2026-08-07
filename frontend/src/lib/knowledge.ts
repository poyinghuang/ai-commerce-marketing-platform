export type KnowledgeType = "FEATURE" | "BENEFIT" | "AUDIENCE" | "PAIN_POINT" | "FAQ" | "PROOF" | "OTHER";
export type Knowledge = {
  knowledgeUuid: string; productUuid: string; knowledgeType: KnowledgeType; title: string; content: string;
  source: string | null; lifecycleStatus: "ACTIVE" | "ARCHIVED"; archivedAt: string | null;
  createdAt: string; updatedAt: string; version: number;
};
export type KnowledgePage = { content: Knowledge[]; page: number; size: number; totalElements: number; totalPages: number; status: string; sort: string };
export type KnowledgeInput = { knowledgeType: KnowledgeType; title: string; content: string; source: string };
export const EMPTY_KNOWLEDGE: KnowledgeInput = { knowledgeType: "FEATURE", title: "", content: "", source: "" };
export function knowledgeInput(value: Knowledge): KnowledgeInput { return { knowledgeType: value.knowledgeType, title: value.title, content: value.content, source: value.source ?? "" }; }
export function knowledgePatch(before: KnowledgeInput, after: KnowledgeInput) { return Object.fromEntries(Object.entries(after).filter(([k,v]) => v !== before[k as keyof KnowledgeInput]).map(([k,v]) => [k, typeof v === "string" && k === "source" && v.trim() === "" ? null : typeof v === "string" ? v.trim() : v])); }
