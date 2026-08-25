export class NotificationAdapter {
  async deliver(_payload) {
    throw new Error("NotificationAdapter.deliver must be implemented");
  }
}

export class NoopNotificationAdapter extends NotificationAdapter {
  async deliver(_payload) {
    return {
      skipped: true,
      reason: "notification_not_configured",
      provider: "noop",
    };
  }
}

export function createNotificationAdapter({ provider } = {}) {
  const name = (provider ?? process.env.VOICE_NOTIFY_PROVIDER ?? "noop").toLowerCase();
  if (name === "noop") {
    return new NoopNotificationAdapter();
  }
  return {
    provider: name,
    async deliver() {
      return {
        skipped: true,
        reason: "notification_provider_not_wired",
        provider: name,
      };
    },
  };
}
