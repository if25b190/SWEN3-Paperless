import type { Metadata } from "next";
import { Poppins } from "next/font/google";
import "./globals.css";
import { I18nProvider } from "@/lib/i18n/context";
import { AuthProvider } from "@/lib/auth/context";
import { TooltipProvider } from "@/components/ui/tooltip";
import { Toaster } from "sonner";

const poppins = Poppins({
  weight: ["300", "400", "500", "600", "700"],
  subsets: ["latin"],
  variable: "--font-poppins",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Paperless - Document Management System",
  description: "Secure Document Management with OCR and AI Summaries",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className={`${poppins.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col font-sans bg-slate-50 text-slate-900">
        <I18nProvider>
          <AuthProvider>
            <TooltipProvider>
              {children}
              <Toaster richColors position="top-right" />
            </TooltipProvider>
          </AuthProvider>
        </I18nProvider>
      </body>
    </html>
  );
}
