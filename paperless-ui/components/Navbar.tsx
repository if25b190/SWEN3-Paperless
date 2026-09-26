"use client";

import React, { useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/context";
import { useTranslation } from "@/lib/i18n/context";
import {
  FileText,
  Users,
  Tag,
  Search,
  LogOut,
  User as UserIcon,
  Globe,
  ChevronDown,
} from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Avatar, AvatarFallback } from "./ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "./ui/dropdown-menu";
import { cn } from "@/lib/utils";

export function Navbar() {
  const { user, logout } = useAuth();
  const { t, locale, setLocale } = useTranslation();
  const pathname = usePathname();
  const router = useRouter();
  const [quickQuery, setQuickQuery] = useState("");

  const handleQuickSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (quickQuery.trim()) {
      router.push(`/search?query=${encodeURIComponent(quickQuery.trim())}`);
      setQuickQuery("");
    }
  };

  const navLinks = [
    { href: "/dashboard", label: t.nav.documents, icon: FileText },
    { href: "/teams", label: t.nav.teams, icon: Users },
    { href: "/document-types", label: t.nav.documentTypes, icon: Tag },
  ];

  return (
    <header className="sticky top-0 z-40 w-full border-b border-slate-200 bg-white/95 backdrop-blur-xs shadow-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
        {/* Brand & Left Navigation */}
        <div className="flex items-center gap-8">
          <Link
            href={user ? "/dashboard" : "/login"}
            className="flex items-center gap-2.5 font-bold text-xl text-blue-600 tracking-tight"
          >
            <div className="h-9 w-9 rounded-lg bg-blue-600 text-white flex items-center justify-center shadow-xs">
              <FileText className="h-5 w-5" />
            </div>
            <span>{t.common.appName}</span>
          </Link>

          {user && (
            <nav className="hidden md:flex items-center gap-1">
              {navLinks.map((link) => {
                const Icon = link.icon;
                const isActive =
                  pathname === link.href ||
                  (link.href !== "/dashboard" && pathname.startsWith(link.href));
                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    className={cn(
                      "flex items-center gap-2 px-3 py-1.5 rounded-md text-sm font-medium transition-colors",
                      isActive
                        ? "bg-blue-50 text-blue-700"
                        : "text-slate-600 hover:text-slate-900 hover:bg-slate-100"
                    )}
                  >
                    <Icon className="h-4 w-4" />
                    {link.label}
                  </Link>
                );
              })}
            </nav>
          )}
        </div>

        {/* Center: Quick Search Bar (when logged in) */}
        {user && (
          <form
            onSubmit={handleQuickSearch}
            className="hidden sm:flex flex-1 max-w-md items-center relative"
          >
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
            <Input
              type="search"
              placeholder={t.search.searchPlaceholder}
              value={quickQuery}
              onChange={(e) => setQuickQuery(e.target.value)}
              className="pl-9 h-9 bg-slate-50 border-slate-200 focus:bg-white text-sm"
            />
          </form>
        )}

        {/* Right Section: Language Selector & User Menu */}
        <div className="flex items-center gap-3">
          {/* Search icon link for mobile */}
          {user && (
            <Button
              variant="ghost"
              size="icon"
              className="sm:hidden text-slate-600"
              onClick={() => router.push("/search")}
            >
              <Search className="h-5 w-5" />
            </Button>
          )}

          {/* Language Selector */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="outline"
                size="sm"
                className="h-8 gap-1.5 px-2.5 font-semibold text-xs border-slate-200 text-slate-700 hover:bg-slate-100"
              >
                <Globe className="h-3.5 w-3.5 text-slate-500" />
                <span>{locale.toUpperCase()}</span>
                <ChevronDown className="h-3 w-3 opacity-60" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-32">
              <DropdownMenuItem
                onClick={() => setLocale("en")}
                className={cn(locale === "en" && "font-bold text-blue-600")}
              >
                English (EN)
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => setLocale("de")}
                className={cn(locale === "de" && "font-bold text-blue-600")}
              >
                Deutsch (DE)
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          {/* User Menu or Login link */}
          {user ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  className="flex items-center gap-2 p-1.5 rounded-full hover:bg-slate-100"
                >
                  <Avatar className="h-8 w-8 border border-slate-200">
                    <AvatarFallback className="text-xs font-semibold bg-blue-100 text-blue-700">
                      {user.username.slice(0, 2).toUpperCase()}
                    </AvatarFallback>
                  </Avatar>
                  <span className="hidden sm:inline-block text-sm font-medium text-slate-700 max-w-[120px] truncate">
                    {user.username}
                  </span>
                  <ChevronDown className="h-3.5 w-3.5 text-slate-400" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-52">
                <div className="px-3 py-2 border-b border-slate-100">
                  <p className="text-xs text-slate-500">{t.profile.userInfo}</p>
                  <p className="text-sm font-semibold text-slate-900 truncate">
                    {user.username}
                  </p>
                </div>
                <DropdownMenuItem
                  onClick={() => router.push("/profile")}
                  className="gap-2 cursor-pointer"
                >
                  <UserIcon className="h-4 w-4 text-slate-500" />
                  <span>{t.nav.profile}</span>
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => router.push("/teams")}
                  className="gap-2 cursor-pointer"
                >
                  <Users className="h-4 w-4 text-slate-500" />
                  <span>{t.nav.teams}</span>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onClick={logout}
                  className="gap-2 text-red-600 focus:text-red-700 focus:bg-red-50 cursor-pointer"
                >
                  <LogOut className="h-4 w-4" />
                  <span>{t.nav.logout}</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => router.push("/login")}
              >
                {t.nav.login}
              </Button>
              <Button
                size="sm"
                onClick={() => router.push("/login?tab=register")}
              >
                {t.nav.register}
              </Button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
