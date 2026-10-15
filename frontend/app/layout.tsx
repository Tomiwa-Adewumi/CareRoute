import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CareRoute",
  description: "Explainable emergency department recommendations based on travel time, wait time, and data freshness.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
