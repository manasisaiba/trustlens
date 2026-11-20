import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TrustLens — AI Customer Experience Doctor",
  description: "AI customer personas test your website in a real browser and show you exactly what to fix.",
  icons: { icon: "/icon.svg" },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className="min-h-screen">{children}</body>
    </html>
  );
}
