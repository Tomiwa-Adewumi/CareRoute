import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CareRoute",
  description: "Compare available reported adult emergency-department waits and estimated travel, with explicit data limitations.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
