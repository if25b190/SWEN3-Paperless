import type { Metadata } from "next";
import { Poppins } from "next/font/google";
import InitColorSchemeScript from "@mui/material/InitColorSchemeScript";
import Providers from "./providers";
import "./globals.css";

const poppins = Poppins({ subsets: ["latin"], weight: ["400", "500", "600", "700", "800"], display: "swap", variable: "--font-poppins" });

export const metadata: Metadata = {
  title: "Paperless — your calm document desk",
  description: "A focused workspace for every document.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={poppins.variable}
      suppressHydrationWarning
    >
      <body>
        <InitColorSchemeScript attribute="class" defaultMode="system" modeStorageKey="paperless_theme" />
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
