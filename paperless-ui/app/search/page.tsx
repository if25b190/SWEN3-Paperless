"use client";

import React, { useState, useEffect, useCallback, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { useTranslation } from "@/lib/i18n/context";
import { searchApi, documentsApi } from "@/lib/api/client";
import { SearchResponse, SearchResultItem } from "@/lib/types/api";
import { StatusBadge } from "@/components/StatusBadge";
import {
  Search,
  FileText,
  Download,
  Eye,
  Loader2,
  Calendar,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

function SearchContent() {
  const { t } = useTranslation();
  const searchParams = useSearchParams();
  const router = useRouter();

  const initialQuery = searchParams.get("query") || "";
  const [query, setQuery] = useState(initialQuery);
  const [fuzzy, setFuzzy] = useState(true);
  const [page, setPage] = useState(0);

  const [results, setResults] = useState<SearchResultItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);

  const executeSearch = useCallback(
    async (searchQuery: string, fuzzyEnabled: boolean, pageIndex: number) => {
      if (!searchQuery.trim()) {
        setResults([]);
        setTotalElements(0);
        setTotalPages(0);
        setHasSearched(false);
        return;
      }

      setLoading(true);
      setHasSearched(true);

      try {
        const res: SearchResponse = await searchApi.search({
          query: searchQuery.trim(),
          fuzzy: fuzzyEnabled,
          page: pageIndex,
          size: 10,
        });

        setResults(res.items || []);
        setTotalElements(res.pagination.total_elements);
        setTotalPages(res.pagination.total_pages);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    if (initialQuery) {
      setQuery(initialQuery);
      executeSearch(initialQuery, fuzzy, page);
    }
  }, [initialQuery, fuzzy, page, executeSearch]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    router.push(
      `/search?query=${encodeURIComponent(query.trim())}&fuzzy=${fuzzy}`
    );
    executeSearch(query, fuzzy, 0);
  };

  const formatDate = (isoString?: string) => {
    if (!isoString) return "-";
    try {
      return new Date(isoString).toLocaleDateString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric",
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          {t.search.title}
        </h1>
        <p className="text-sm text-slate-500">{t.search.subtitle}</p>
      </div>

      {/* Search Input Bar */}
      <Card className="border-slate-200 bg-white shadow-xs">
        <CardContent className="p-4 sm:p-6">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="flex flex-col sm:flex-row gap-3">
              <div className="relative flex-1">
                <Search className="absolute left-3.5 top-3 h-4 w-4 text-slate-400" />
                <Input
                  type="search"
                  placeholder={t.search.searchPlaceholder}
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  className="pl-10 h-10 text-base"
                />
              </div>
              <Button
                type="submit"
                disabled={loading || !query.trim()}
                className="bg-blue-600 hover:bg-blue-700 text-white h-10 px-6 font-medium gap-2"
              >
                {loading ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Search className="h-4 w-4" />
                )}
                <span>{t.common.search}</span>
              </Button>
            </div>

            {/* Fuzzy search toggle */}
            <div className="flex items-center justify-between pt-1 text-xs sm:text-sm">
              <label className="flex items-center gap-2 text-slate-700 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={fuzzy}
                  onChange={(e) => setFuzzy(e.target.checked)}
                  className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                />
                <span className="font-medium">{t.search.fuzzySearch}</span>
              </label>
              <span className="text-xs text-slate-400 hidden sm:inline-block">
                {t.search.fuzzyDescription}
              </span>
            </div>
          </form>
        </CardContent>
      </Card>

      {/* Results Section */}
      {loading ? (
        <div className="flex flex-col items-center justify-center p-12 bg-white rounded-xl border border-slate-200">
          <Loader2 className="h-8 w-8 animate-spin text-blue-600 mb-2" />
          <p className="text-sm text-slate-500">{t.common.loading}</p>
        </div>
      ) : hasSearched && results.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-12 bg-white rounded-xl border border-slate-200 text-center">
          <div className="h-12 w-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-3">
            <Search className="h-6 w-6" />
          </div>
          <h3 className="text-base font-semibold text-slate-900">
            {t.search.noResultsTitle}
          </h3>
          <p className="text-sm text-slate-500 max-w-sm mt-1">
            {t.search.noResultsSubtitle}
          </p>
        </div>
      ) : results.length > 0 ? (
        <div className="space-y-4">
          <div className="flex items-center justify-between text-xs text-slate-500 px-1">
            <span>
              {totalElements} {t.search.resultsFound}
            </span>
          </div>

          <div className="space-y-3">
            {results.map((item, idx) => (
              <Card
                key={`${item.document.id}-${idx}`}
                className="border-slate-200 bg-white hover:border-slate-300 transition-all shadow-xs"
              >
                <CardContent className="p-5 space-y-3">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <FileText className="h-4 w-4 text-blue-600 shrink-0" />
                      <Link
                        href={`/documents/${item.document.id}`}
                        className="font-semibold text-base text-slate-900 hover:text-blue-600 transition-colors"
                      >
                        {item.document.title}
                      </Link>
                      {item.document.document_type && (
                        <Badge variant="outline" className="font-normal text-xs">
                          {item.document.document_type.name}
                        </Badge>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge variant="secondary" className="text-xs font-mono">
                        {t.search.relevanceScore}: {item.score.toFixed(2)}
                      </Badge>
                      <StatusBadge status={item.document.status} showIcon={false} />
                    </div>
                  </div>

                  {/* Highlights Snippets */}
                  {item.highlights && item.highlights.length > 0 && (
                    <div className="space-y-1.5 pt-1">
                      <p className="text-xs font-medium text-slate-500">
                        {t.search.matchedSnippets}:
                      </p>
                      <div className="space-y-1">
                        {item.highlights.map((snippet, sIdx) => (
                          <div
                            key={sIdx}
                            className="p-2.5 bg-slate-50 rounded-md border border-slate-100 text-xs text-slate-700 leading-relaxed [&_em]:bg-yellow-200 [&_em]:text-yellow-900 [&_em]:font-semibold [&_em]:not-italic [&_em]:px-1 [&_em]:rounded-xs"
                            dangerouslySetInnerHTML={{ __html: snippet }}
                          />
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Document Footer Info */}
                  <div className="flex items-center justify-between pt-2 border-t border-slate-100 text-xs text-slate-500">
                    <div className="flex items-center gap-4">
                      <span className="flex items-center gap-1">
                        <Calendar className="h-3.5 w-3.5 text-slate-400" />
                        {formatDate(item.document.created_at)}
                      </span>
                      <span>{item.document.original_filename}</span>
                    </div>

                    <div className="flex items-center gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-7 text-xs gap-1 text-slate-600 hover:text-slate-900"
                        onClick={() =>
                          window.open(
                            documentsApi.getDownloadUrl(item.document.id),
                            "_blank"
                          )
                        }
                      >
                        <Download className="h-3.5 w-3.5" />
                        <span>{t.common.download}</span>
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-7 text-xs gap-1"
                        onClick={() =>
                          router.push(`/documents/${item.document.id}`)
                        }
                      >
                        <Eye className="h-3.5 w-3.5" />
                        <span>{t.common.details}</span>
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between pt-4 text-xs text-slate-500">
              <div>
                {t.common.page} {page + 1} {t.common.of} {totalPages}
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page <= 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  {t.common.paginationPrev}
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  {t.common.paginationNext}
                </Button>
              </div>
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}

export default function SearchPage() {
  return (
    <ProtectedRoute>
      <Suspense
        fallback={
          <div className="flex items-center justify-center p-12">
            <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
          </div>
        }
      >
        <SearchContent />
      </Suspense>
    </ProtectedRoute>
  );
}
