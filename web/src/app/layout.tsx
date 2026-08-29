import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "Convo Nexus — stories, feed and chat",
  description:
    "Convo Nexus by Rakibul Islam: a liquid-glass social messenger with stories, a live feed, private groups, dynamic themes and progressive offline loading.",
};

export const viewport: Viewport = {
  themeColor: "#07081a",
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body className="antialiased">{children}</body>
    </html>
  );
}
