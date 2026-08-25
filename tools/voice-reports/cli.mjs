#!/usr/bin/env node
import { exec as execCallback } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { collectDailyEvidence } from "./lib/collect-daily.mjs";
import { renderDailyMarkdown } from "./lib/daily-summary.mjs";
import { extractSpokenScript } from "./lib/markdown.mjs";
import { createNotificationAdapter } from "./lib/notify.mjs";
import { formatStatusJson } from "./lib/status.mjs";
import { renderStageMarkdown } from "./lib/stage-summary.mjs";
import { createTtsAdapter } from "./lib/tts.mjs";

const exec = promisify(execCallback);

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");

function argsOf(argv) {
  const args = { _: [] };
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (token.startsWith("--")) {
      const key = token.slice(2);
      const value = argv[index + 1] && !argv[index + 1].startsWith("--") ? argv[++index] : true;
      args[key] = value;
    } else {
      args._.push(token);
    }
  }
  return args;
}

async function readJson(filePath) {
  return JSON.parse(await readFile(filePath, "utf8"));
}

function nowIso() {
  return new Date().toISOString();
}

async function generateStage(args) {
  const inputPath = path.resolve(args.input);
  const input = await readJson(inputPath);
  const markdown = renderStageMarkdown(input, args["generated-at"] ?? nowIso());
  const outPath = path.resolve(
    args.out ?? path.join(repoRoot, "docs/voice-reports/stages", `${input.stage_id}-voice-summary.md`),
  );
  await mkdir(path.dirname(outPath), { recursive: true });
  await writeFile(outPath, markdown, "utf8");
  return outPath;
}

async function generateDaily(args) {
  const inputPath = path.resolve(args.input);
  const input = await readJson(inputPath);
  const markdown = renderDailyMarkdown(input, args["generated-at"] ?? nowIso());
  const outPath = path.resolve(
    args.out ?? path.join(repoRoot, "docs/voice-reports/daily", `${input.date}.md`),
  );
  await mkdir(path.dirname(outPath), { recursive: true });
  await writeFile(outPath, markdown, "utf8");
  return outPath;
}

async function collectDaily(args) {
  const date = args.date;
  if (!date) {
    throw new Error("collect-daily requires --date YYYY-MM-DD");
  }
  const statusSnapshot = args.status ? await readJson(path.resolve(args.status)) : null;
  const evidence = await collectDailyEvidence({
    date,
    statusSnapshot,
    exec: (command) => exec(command, { cwd: repoRoot, maxBuffer: 10 * 1024 * 1024 }),
  });
  const outPath = path.resolve(args.out ?? path.join(repoRoot, "docs/voice-reports/evidence", `${date}.collected.json`));
  await mkdir(path.dirname(outPath), { recursive: true });
  await writeFile(outPath, `${JSON.stringify(evidence, null, 2)}\n`, "utf8");
  return outPath;
}

async function writeStatus(args) {
  const inputPath = path.resolve(args.input);
  const snapshot = await readJson(inputPath);
  const outPath = path.resolve(args.out ?? path.join(repoRoot, ".project/status.json"));
  await mkdir(path.dirname(outPath), { recursive: true });
  await writeFile(outPath, formatStatusJson(snapshot), "utf8");
  return outPath;
}

async function speak(args) {
  const filePath = path.resolve(args.file);
  const markdown = await readFile(filePath, "utf8");
  const script = extractSpokenScript(markdown);
  const adapter = createTtsAdapter({
    provider: args.provider,
    fs: { mkdir, writeFile },
    path,
    cwd: repoRoot,
  });
  try {
    const result = await adapter.synthesize(script, {
      outDir: "artifacts/voice-reports",
      fileName: `${path.basename(filePath, path.extname(filePath))}.spoken.txt`,
    });
    return result;
  } catch (error) {
    return {
      skipped: true,
      reason: "tts_failure",
      provider: adapter.provider ?? args.provider ?? "unknown",
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

async function notify(args) {
  const filePath = path.resolve(args.file);
  const markdown = await readFile(filePath, "utf8");
  const adapter = createNotificationAdapter({ provider: args.provider });
  try {
    return await adapter.deliver({
      title: path.basename(filePath),
      body: extractSpokenScript(markdown),
    });
  } catch (error) {
    return {
      skipped: true,
      reason: "notification_failure",
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

async function main(argv) {
  const args = argsOf(argv);
  const command = args._[0];
  if (command === "generate-stage") {
    const outPath = await generateStage(args);
    process.stdout.write(`${outPath}\n`);
    return 0;
  }
  if (command === "generate-daily") {
    const outPath = await generateDaily(args);
    process.stdout.write(`${outPath}\n`);
    return 0;
  }
  if (command === "collect-daily") {
    const outPath = await collectDaily(args);
    process.stdout.write(`${outPath}\n`);
    return 0;
  }
  if (command === "write-status") {
    const outPath = await writeStatus(args);
    process.stdout.write(`${outPath}\n`);
    return 0;
  }
  if (command === "speak") {
    const result = await speak(args);
    process.stdout.write(`${JSON.stringify(result)}\n`);
    return 0;
  }
  if (command === "notify") {
    const result = await notify(args);
    process.stdout.write(`${JSON.stringify(result)}\n`);
    return 0;
  }
  process.stderr.write(`Unknown command: ${command ?? "(missing)"}\n`);
  process.stderr.write(
    "Usage: node tools/voice-reports/cli.mjs <generate-stage|generate-daily|collect-daily|write-status|speak|notify> [options]\n",
  );
  return 1;
}

const isDirect = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isDirect) {
  main(process.argv.slice(2)).then((code) => process.exit(code));
}

export { main };
