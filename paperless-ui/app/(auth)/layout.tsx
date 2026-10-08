"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { FileText } from "lucide-react";
import { LanguageSwitcher } from "@/components/language-switcher";
import { cn } from "@/lib/utils";
import { useAuth } from "@/components/providers/auth-provider";
import { useI18n } from "@/lib/i18n/use-i18n";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  const { t } = useI18n();
  const { ready, user } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (ready && user) router.replace("/");
  }, [ready, user, router]);

  const tabs = [
    { href: "/login", label: t("auth.sign_in") },
    { href: "/register", label: t("auth.register") },
  ];

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center px-4 py-10">
      <div className="absolute top-4 right-4">
        <LanguageSwitcher />
      </div>
      <div className="w-full max-w-md">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <div className="flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-md">
            <FileText className="size-6" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{t("app.name")}</h1>
            <p className="text-sm text-muted-foreground">{t("app.tagline")}</p>
          </div>
        </div>
        <div className="rounded-xl border bg-card p-6 shadow-sm">
          <div className="mb-6 grid grid-cols-2 gap-1 rounded-lg bg-muted p-1" role="tablist">
            {tabs.map((tab) => (
              <Link
                key={tab.href}
                href={tab.href}
                role="tab"
                aria-selected={pathname === tab.href}
                className={cn(
                  "rounded-md px-3 py-1.5 text-center text-sm font-medium text-muted-foreground transition-all",
                  pathname === tab.href && "bg-card text-foreground shadow-sm",
                )}
              >
                {tab.label}
              </Link>
            ))}
          </div>
          {children}
        </div>
      </div>
    </div>
  );
}
