import { delay, http } from "msw";

import type { VisitAreaDto, VisitMapArea, VisitStatisticsResponseDto } from "../../../features/statistics/model";
import { isValidVisitMapArea } from "../../../features/statistics/model";
import { findCategory } from "../fixtures/common";
import { VISIT_PERIOD_DAYS, visitAreasFixture } from "../fixtures/statistics";
import { fail, MOCK_DELAY_MS, ok } from "./respond";

const PERCENT = 100;

function readArea(params: URLSearchParams): VisitMapArea | null {
  const area = {
    south: Number(params.get("swLat")),
    west: Number(params.get("swLng")),
    north: Number(params.get("neLat")),
    east: Number(params.get("neLng")),
    level: Number(params.get("level")),
  };

  const hasAllParams = ["swLat", "swLng", "neLat", "neLng", "level"].every((name) => {
    return params.has(name);
  });

  return hasAllParams && isValidVisitMapArea(area) ? area : null;
}

function isInside(area: VisitMapArea, visitArea: VisitAreaDto) {
  return visitArea.latitude >= area.south
    && visitArea.latitude <= area.north
    && visitArea.longitude >= area.west
    && visitArea.longitude <= area.east;
}

/** 카테고리를 고르면 그 카테고리 비율만큼의 인원·방문 수로 줄인다. 서버 집계를 흉내 낸 목 전용 계산이다. */
function forCategory(visitArea: VisitAreaDto, categoryCode: string): VisitAreaDto | null {
  const share = visitArea.categoryShares.find((categoryShare) => {
    return categoryShare.categoryCode === categoryCode;
  });

  if (!share) {
    return null;
  }

  return {
    ...visitArea,
    visitorCount: Math.max(1, Math.round((visitArea.visitorCount * share.percent) / PERCENT)),
    visitCount: Math.max(1, Math.round((visitArea.visitCount * share.percent) / PERCENT)),
  };
}

export const statisticsHandlers = [
  http.get("*/api/v1/visit-statistics", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const params = new URL(request.url).searchParams;
    const area = readArea(params);
    const categoryCode = params.get("category");

    if (!area) {
      return fail(400, "C017", "지도 조회 영역의 형식이나 범위가 올바르지 않습니다.");
    }

    if (categoryCode !== null && findCategory(categoryCode) === null) {
      return fail(400, "C003", "지원하지 않는 카테고리입니다.");
    }

    const areas = visitAreasFixture.filter((visitArea) => {
      return isInside(area, visitArea);
    }).flatMap((visitArea) => {
      const filtered = categoryCode === null ? visitArea : forCategory(visitArea, categoryCode);
      return filtered ? [filtered] : [];
    });

    const response: VisitStatisticsResponseDto = { periodDays: VISIT_PERIOD_DAYS, areas };
    return ok(response);
  }),
];
