import { ContractMismatchError } from "../../lib/contract";

// 가정(TBD): API-046 방문 분포도의 요청·응답 상세는 명세 미확정이다. Figma W15·W15-A(지도 발자취 1:6260)에서 역산했다.
// 지도에 보이는 영역(남서·북동 좌표)과 확대 단계로 묻고, 서버가 그 단계에 맞는 집계 단위("경포호 일대")로 묶어 준다.
// 집계 기간·공간 단위(DEC-02·03)와 표시 정밀도(DEC-07)는 서버가 정한 값을 그대로 쓴다.

export type CategoryShareDto = { categoryCode: string; categoryName: string; percent: number };

export type VisitAreaDto = {
  areaId: string;
  name: string;
  latitude: number;
  longitude: number;
  visitorCount: number;
  visitCount: number;
  categoryShares: CategoryShareDto[];
};

export type VisitStatisticsResponseDto = { periodDays: number; areas: VisitAreaDto[] };

/** 지도 조회 영역. 남서(south·west)·북동(north·east) 좌표와 카카오 지도 확대 단계(level, 작을수록 가깝다). */
export type VisitMapArea = { south: number; west: number; north: number; east: number; level: number };

export type CategoryShare = { code: string; name: string; percent: number };

/** 서버가 묶은 집계 단위 하나. 인원·방문 수와 카테고리 비율만 있고, 누가 다녀갔는지는 없다(규칙 80). */
export type VisitArea = {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  visitorCount: number;
  visitCount: number;
  categoryShares: CategoryShare[];
};

export type VisitStatistics = { periodDays: number; areas: VisitArea[] };

const MAX_LATITUDE = 90;
const MAX_LONGITUDE = 180;

function isLatitude(value: number) {
  return Number.isFinite(value) && Math.abs(value) <= MAX_LATITUDE;
}

function isLongitude(value: number) {
  return Number.isFinite(value) && Math.abs(value) <= MAX_LONGITUDE;
}

/** 조회 영역이 위경도 범위 안이고 남서가 북동보다 아래·왼쪽인지 확인한다(C015·C017 예방). */
export function isValidVisitMapArea(area: VisitMapArea): boolean {
  return (
    isLatitude(area.south) &&
    isLatitude(area.north) &&
    isLongitude(area.west) &&
    isLongitude(area.east) &&
    area.south < area.north &&
    area.west < area.east &&
    Number.isInteger(area.level) &&
    area.level > 0
  );
}

function assertNumber(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function assertText(value: unknown, field: string): string {
  if (typeof value !== "string" || value.length === 0) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function toCategoryShare(dto: CategoryShareDto): CategoryShare {
  return {
    code: assertText(dto.categoryCode, "areas.categoryShares.categoryCode"),
    name: assertText(dto.categoryName, "areas.categoryShares.categoryName"),
    percent: assertNumber(dto.percent, "areas.categoryShares.percent"),
  };
}

function toVisitArea(dto: VisitAreaDto): VisitArea {
  return {
    id: assertText(dto.areaId, "areas.areaId"),
    name: assertText(dto.name, "areas.name"),
    latitude: assertNumber(dto.latitude, "areas.latitude"),
    longitude: assertNumber(dto.longitude, "areas.longitude"),
    visitorCount: assertNumber(dto.visitorCount, "areas.visitorCount"),
    visitCount: assertNumber(dto.visitCount, "areas.visitCount"),
    categoryShares: dto.categoryShares.map(toCategoryShare),
  };
}

export function toVisitStatistics(dto: VisitStatisticsResponseDto): VisitStatistics {
  return {
    periodDays: assertNumber(dto.periodDays, "periodDays"),
    areas: dto.areas.map(toVisitArea),
  };
}
