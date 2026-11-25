import type { FootprintDetailDto } from "../../../features/footprint/model";

export type MockFootprint = FootprintDetailDto & { ownerId: number };

export function mockDateTime(now = Date.now()) {
  return new Date(now + 9 * 3_600_000).toISOString().slice(0, 19).replace("T", " ") + "+09";
}

export function createFootprintFixtures(): MockFootprint[] {
  const daysAgo = (days: number) => {
    return mockDateTime(Date.now() - days * 86_400_000);
  };

  const first: MockFootprint = {
    id: 1, ownerId: 1, version: 1, latitude: 37.7717, longitude: 128.9474, locationName: "안목해변 부근", address: "강릉시 창해로",
    body: "방파제 끝까지 걸었어요.", categoryCodes: ["CAFE", "NATURE"], visitedAt: daysAgo(2), createdAt: daysAgo(2),
  };

  return [
    first,
    { ...first, id: 2, latitude: 37.7957, longitude: 128.8996, locationName: "경포호 부근", address: "강릉시 경포로", body: null, categoryCodes: ["NATURE"], visitedAt: daysAgo(5), createdAt: daysAgo(5) },
    { ...first, id: 3, latitude: 35.1885, longitude: 129.2233, locationName: null, address: null, body: "아침 산책", categoryCodes: ["CULTURE"], visitedAt: daysAgo(35), createdAt: daysAgo(35) },
    { ...first, id: 4, latitude: 35.1532, longitude: 129.1186, locationName: "광안리해변 부근", address: "부산시 광안해변로", body: null, categoryCodes: ["NATURE"], visitedAt: daysAgo(120), createdAt: daysAgo(120) },
    { ...first, id: 99, ownerId: 2, body: null },
  ];
}
