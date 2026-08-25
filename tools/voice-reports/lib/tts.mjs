export class TtsAdapter {
  async synthesize(_text, _options) {
    throw new Error("TtsAdapter.synthesize must be implemented");
  }
}

export class NoopTtsAdapter extends TtsAdapter {
  provider = "noop";

  async synthesize(_text, _options = {}) {
    return {
      skipped: true,
      reason: "tts_not_configured",
      provider: "noop",
    };
  }
}

export class FileTtsAdapter extends TtsAdapter {
  provider = "file";

  constructor(fs, path, cwd) {
    super();
    this.fs = fs;
    this.path = path;
    this.cwd = cwd;
  }

  async synthesize(text, options = {}) {
    const outDir = this.path.join(this.cwd, options.outDir ?? "artifacts/voice-reports");
    await this.fs.mkdir(outDir, { recursive: true });
    const fileName = options.fileName ?? "summary.spoken.txt";
    const outPath = this.path.join(outDir, fileName);
    await this.fs.writeFile(outPath, text, "utf8");
    return {
      skipped: false,
      provider: "file",
      path: outPath,
    };
  }
}

export function createTtsAdapter({ provider, fs, path, cwd }) {
  const name = (provider ?? process.env.VOICE_TTS_PROVIDER ?? "noop").toLowerCase();
  if (name === "file") {
    return new FileTtsAdapter(fs, path, cwd);
  }
  if (name === "noop") {
    return new NoopTtsAdapter();
  }
  return {
    provider: name,
    async synthesize() {
      return {
        skipped: true,
        reason: "tts_provider_not_wired",
        provider: name,
      };
    },
  };
}
