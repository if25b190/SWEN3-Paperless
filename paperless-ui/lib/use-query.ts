"use client";

import { useCallback, useEffect, useRef, useState } from "react";

interface Result<T> {
  key: string;
  data?: T;
  error?: unknown;
}

/**
 * Minimal data-fetching hook.
 * - `key === null` disables the request.
 * - Previous data is kept while a new key or reload is loading.
 */
export function useQuery<T>(key: string | null, fetcher: () => Promise<T>) {
  const [tick, setTick] = useState(0);
  const [result, setResult] = useState<Result<T> | null>(null);
  const fetcherRef = useRef(fetcher);

  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  const requestKey = key === null ? null : `${key}#${tick}`;

  useEffect(() => {
    if (requestKey === null) return;
    let cancelled = false;
    fetcherRef.current().then(
      (data) => {
        if (!cancelled) setResult({ key: requestKey, data });
      },
      (error: unknown) => {
        if (!cancelled) setResult({ key: requestKey, error });
      },
    );
    return () => {
      cancelled = true;
    };
  }, [requestKey]);

  const reload = useCallback(() => setTick((value) => value + 1), []);

  const loading = requestKey !== null && result?.key !== requestKey;
  const current = key === null ? null : result;

  return {
    data: current?.data,
    error: current && current.key === requestKey ? current.error : undefined,
    loading,
    reload,
  };
}
