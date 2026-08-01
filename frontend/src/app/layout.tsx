import type { Metadata } from "next";
import "./styles.css";

export const metadata: Metadata = {
  title: "AI Commerce Marketing Platform",
  description: "Project foundation status",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-Hant">
      <body>{children}</body>
    </html>
  );
}
