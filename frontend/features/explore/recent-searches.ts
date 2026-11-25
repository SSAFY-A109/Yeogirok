// 최근 검색은 검색어와 조건을 함께 기억한다. 보관 위치(기기 저장·서버)는 정해지지 않아 앱 실행 중에만 유지한다(TBD).
import { useSyncExternalStore } from "react";

import type { CourseFilters } from "./model";

export type RecentSearch = { id: string; query: string; filters: CourseFilters };

const MAX_RECENT = 10;

let recentSearches: RecentSearch[] = [];
const listeners = new Set<() => void>();

function keyOf(query: string, filters: CourseFilters): string {
  return [query, filters.region, filters.duration, filters.transport].join("|");
}

function emit(next: RecentSearch[]) {
  recentSearches = next;

  listeners.forEach((listener) => {
    listener();
  });
}

function subscribe(listener: () => void) {
  listeners.add(listener);

  return () => {
    listeners.delete(listener);
  };
}

function snapshot() {
  return recentSearches;
}

export function useRecentSearches(): RecentSearch[] {
  return useSyncExternalStore(subscribe, snapshot, snapshot);
}

/** 같은 검색어·조건이 있으면 맨 앞으로 올린다. */
export function addRecentSearch(query: string, filters: CourseFilters) {
  const id = keyOf(query, filters);

  emit([{ id, query, filters }, ...recentSearches.filter((item) => {
    return item.id !== id;
  })].slice(0, MAX_RECENT));
}

export function removeRecentSearch(id: string) {
  emit(recentSearches.filter((item) => {
    return item.id !== id;
  }));
}

export function clearRecentSearches() {
  emit([]);
}
