export function extractSpokenScript(markdown) {
  const withoutFrontMatter = String(markdown).replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, "");
  const match = withoutFrontMatter.match(/## 朗讀稿\r?\n\r?\n([\s\S]*?)(?:\r?\n## |\s*$)/);
  if (!match) {
    throw new Error("Markdown is missing a 朗讀稿 section");
  }
  return match[1].trim();
}
