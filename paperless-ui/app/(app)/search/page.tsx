"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Search as SearchIcon, SearchX } from "lucide-react";
import { StatusBadge } from "@/components/badges";
import { EmptyState, PageHeader, Pagination, Spinner } from "@/components/common";
import { Button } from "@/components/ui/button";
import { Badge, Card, Skeleton } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { searchApi } from "@/lib/api/services";
import { useI18n } from "@/lib/i18n/use-i18n";
import { useQuery } from "@/lib/use-query";
import { cn, formatDay, sanitizeHighlight } from "@/lib/utils";

const PAGE_SIZE = 10;

function Switch({
  checked,
  onChange,
  label,
  hint,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
  label: string;
  hint: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className="flex items-center gap-3 rounded-lg px-1 py-1 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring/60"
    >
      <span
        className={cn(
          "relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors",
          checked ? "bg-primary" : "bg-input",
        )}
      >
        <span
          className={cn(
            "inline-block size-5 rounded-full bg-white shadow transition-transform",
            checked ? "translate-x-5" : "translate-x-0.5",
          )}
        />
      </span>
      <span className="grid">
        <span className="text-sm font-medium">{label}</span>
        <span className="text-xs text-muted-foreground">{hint}</span>
      </span>
    </button>
  );
}

function SearchResults({ query, fuzzy }: { query: string; fuzzy: boolean }) {
  const { t, locale } = useI18n();
  const [page, setPage] = useState(0);
  const { data, loading } = useQuery(query ? `search:${query}:${fuzzy}:${page}` : null, () =>
    searchApi.search({ query, fuzzy, page, size: PAGE_SIZE }),
  );

  if (!query) {
    return (
      <Card>
        <EmptyState icon={<SearchIcon className="size-6" />} title={t("search.empty_prompt")} />
      </Card>
    );
  }
  if (data === undefined && loading) {
    return (
      <div className="grid gap-3">
        {Array.from({ length: 4 }).map((_, index) => (
          <Skeleton key={index} className="h-28 w-full" />
        ))}
      </div>
    );
  }
  if (!data || data.items.length === 0) {
    return (
      <Card>
        <EmptyState
          icon={<SearchX className="size-6" />}
          title={t("search.no_results")}
          description={t("search.no_results_text")}
        />
      </Card>
    );
  }

  return (
    <div className="grid gap-4">
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        {t("search.found", { count: data.pagination.total_elements })}
        {loading && <Spinner className="size-3" />}
      </p>
      <div className="grid gap-3">
        {data.items.map(({ document, score, highlights }) => (
          <Card key={document.id} className="p-5 transition-shadow hover:shadow-md">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <Link
                  href={`/documents/${document.id}`}
                  className="block truncate text-base font-semibold hover:text-primary hover:underline"
                >
                  {document.title}
                </Link>
                <p className="text-xs text-muted-foreground">
                  {document.original_filename} &middot; {formatDay(document.created_at, locale)}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {document.document_type && (
                  <Badge variant="secondary">{document.document_type.name}</Badge>
                )}
                <StatusBadge status={document.status} />
                <Badge variant="outline">{t("search.score", { score: score.toFixed(2) })}</Badge>
              </div>
            </div>
            {highlights && highlights.length > 0 && (
              <ul className="mt-3 grid gap-1.5">
                {highlights.map((snippet, index) => (
                  <li
                    key={index}
                    className="search-snippet rounded-md bg-muted/50 px-3 py-2 text-sm leading-relaxed"
                    dangerouslySetInnerHTML={{ __html: sanitizeHighlight(snippet) }}
                  />
                ))}
              </ul>
            )}
          </Card>
        ))}
      </div>
      <Card className="p-0">
        <Pagination
          page={page}
          totalPages={data.pagination.total_pages}
          onChange={setPage}
        />
      </Card>
    </div>
  );
}

function SearchView({ query, fuzzy }: { query: string; fuzzy: boolean }) {
  const { t } = useI18n();
  const router = useRouter();
  const [draft, setDraft] = useState(query);

  function go(nextQuery: string, nextFuzzy: boolean) {
    const search = new URLSearchParams();
    if (nextQuery) search.set("q", nextQuery);
    if (!nextFuzzy) search.set("fuzzy", "false");
    const qs = search.toString();
    router.push(qs ? `/search?${qs}` : "/search");
  }

  return (
    <div className="grid gap-6">
      <PageHeader title={t("search.title")} description={t("search.subtitle")} />
      <Card className="p-4">
        <form
          onSubmit={(event) => {
            event.preventDefault();
            go(draft.trim(), fuzzy);
          }}
          className="grid gap-4"
          role="search"
        >
          <div className="flex flex-col gap-2 sm:flex-row">
            <div className="relative flex-1">
              <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                placeholder={t("search.placeholder")}
                aria-label={t("search.title")}
                className="pl-9"
                autoFocus
              />
            </div>
            <Button type="submit">{t("search.button")}</Button>
          </div>
          <Switch
            checked={fuzzy}
            onChange={(value) => go(draft.trim() || query, value)}
            label={t("search.fuzzy")}
            hint={t("search.fuzzy_hint")}
          />
        </form>
      </Card>
      <SearchResults key={`${query}:${fuzzy}`} query={query} fuzzy={fuzzy} />
    </div>
  );
}

function SearchGate() {
  const params = useSearchParams();
  const query = params.get("q")?.trim() ?? "";
  const fuzzy = params.get("fuzzy") !== "false";
  return <SearchView key={query} query={query} fuzzy={fuzzy} />;
}

export default function SearchPage() {
  return (
    <Suspense fallback={<Skeleton className="h-40 w-full" />}>
      <SearchGate />
    </Suspense>
  );
}
