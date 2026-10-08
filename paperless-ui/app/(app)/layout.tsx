"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { FileText, FolderTree, LogOut, Search, User as UserIcon, Users } from "lucide-react";
import { toast } from "sonner";
import { FullPageSpinner } from "@/components/common";
import { LanguageSwitcher } from "@/components/language-switcher";
import { useAuth } from "@/components/providers/auth-provider";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { useI18n } from "@/lib/i18n/use-i18n";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  { href: "/", key: "nav.documents", icon: FileText },
  { href: "/search", key: "nav.search", icon: Search },
  { href: "/teams", key: "nav.teams", icon: Users },
  { href: "/document-types", key: "nav.document_types", icon: FolderTree },
] as const;

function isActive(pathname: string, href: string) {
  if (href === "/") return pathname === "/" || pathname.startsWith("/documents");
  return pathname === href || pathname.startsWith(`${href}/`);
}

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { t } = useI18n();
  const { ready, user, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [query, setQuery] = useState("");

  useEffect(() => {
    if (ready && !user) router.replace("/login");
  }, [ready, user, router]);

  if (!ready || !user) {
    return <FullPageSpinner label={t("auth.restoring")} />;
  }

  function onSearch(event: React.FormEvent) {
    event.preventDefault();
    const value = query.trim();
    if (!value) return;
    router.push(`/search?q=${encodeURIComponent(value)}`);
  }

  function onLogout() {
    logout();
    toast.success(t("messages.logged_out"));
    router.replace("/login");
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b bg-card/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6">
          <Link href="/" className="flex items-center gap-2 font-semibold">
            <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
              <FileText className="size-4" />
            </span>
            <span className="hidden sm:inline">{t("app.name")}</span>
          </Link>

          <nav className="ml-2 hidden items-center gap-1 md:flex">
            {NAV_ITEMS.map(({ href, key, icon: Icon }) => (
              <Link
                key={href}
                href={href}
                className={cn(
                  "flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground",
                  isActive(pathname, href) && "bg-accent text-accent-foreground",
                )}
              >
                <Icon className="size-4" />
                {t(key)}
              </Link>
            ))}
          </nav>

          <form onSubmit={onSearch} className="relative ml-auto w-full max-w-xs" role="search">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t("nav.search_placeholder")}
              aria-label={t("nav.search")}
              className="h-9 pl-9"
            />
          </form>

          <LanguageSwitcher />

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" aria-label={user.username} className="rounded-full">
                <Avatar className="size-8">
                  <AvatarFallback>{user.username.slice(0, 2)}</AvatarFallback>
                </Avatar>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuLabel>
                {t("nav.signed_in_as")}
                <span className="block text-sm font-medium text-foreground">{user.username}</span>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onSelect={() => router.push("/profile")}>
                <UserIcon />
                {t("nav.profile")}
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={onLogout}>
                <LogOut />
                {t("nav.logout")}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        <nav className="flex gap-1 overflow-x-auto border-t px-3 py-2 md:hidden">
          {NAV_ITEMS.map(({ href, key, icon: Icon }) => (
            <Link
              key={href}
              href={href}
              className={cn(
                "flex shrink-0 items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium text-muted-foreground",
                isActive(pathname, href) && "bg-accent text-accent-foreground",
              )}
            >
              <Icon className="size-4" />
              {t(key)}
            </Link>
          ))}
        </nav>
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6">{children}</main>
    </div>
  );
}
