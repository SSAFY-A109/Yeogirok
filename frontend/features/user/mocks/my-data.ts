import type { ImageSource } from "expo-image";

export const TIP_CATEGORIES = ["포토", "맛집", "카페", "꿀팁", "TMI"] as const;

export type MyTip = {
  id: string;
  category: typeof TIP_CATEGORIES[number];
  registeredAt: string;
  body: string;
  photo: number | ImageSource;
};

export type MyFootprint = {
  id: string;
  place: string;
  address: string;
  lat: number;
  lng: number;
  visitedAt: string;
  visitedOn: string;
  categories: string[];
  body: string;
};

export const mockProfile = { name: "지윤", provider: "카카오" };

export const mockTips: MyTip[] = [
  { id: "photo", category: "포토", registeredAt: "2026.11.04", body: "해 질 무렵 바다 쪽으로 찍으면 노을이 예뻐요. 산책로 끝 벤치 주변은 비교적 한적해요.", photo: require("@/assets/images/my/tip-photo.png") },
  { id: "food", category: "맛집", registeredAt: "2026.11.03", body: "점심시간보다 조금 일찍 가면 기다리지 않고 먹을 수 있어요.", photo: require("@/assets/images/my/tip-food.png") },
  { id: "cafe", category: "카페", registeredAt: "2026.11.01", body: "창가 자리는 오전이 한적해요. 바다 보며 커피 한 잔 하기 좋아요.", photo: require("@/assets/images/my/tip-cafe.png") },
  { id: "advice", category: "꿀팁", registeredAt: "2026.10.28", body: "입구 옆 보관함에 짐을 맡기면 가볍게 산책할 수 있어요.", photo: require("@/assets/images/my/tip-advice.png") },
];

export const mockFootprints: MyFootprint[] = [
  { id: "ojukheon", lat: 37.7794, lng: 128.8784, place: "오죽헌 부근", address: "강릉시 율곡로", visitedOn: "2026-11-09", visitedAt: "11월 9일 (월) 10:40", categories: ["역사 문화"], body: "" },
  { id: "anmok", lat: 37.7717, lng: 128.9474, place: "안목해변 부근", address: "강릉시 창해로", visitedOn: "2026-11-08", visitedAt: "11월 8일 (일) 14:20", categories: ["카페", "자연 힐링"], body: "민서랑 커피 들고 방파제 끝까지 걸었다.\n바람이 세서 모자 날아갈 뻔. 다음엔 노을 시간에 오기." },
  { id: "gyeongpo", lat: 37.7957, lng: 128.8996, place: "경포호 부근", address: "강릉시 경포로", visitedOn: "2026-11-08", visitedAt: "11월 8일 (일) 11:30", categories: ["자연 힐링"], body: "아침 산책, 물안개가 살짝 있었다." },
  { id: "songjeong", lat: 35.1808, lng: 129.1999, place: "송정해변 부근", address: "부산시 송정해변로", visitedOn: "2026-07-25", visitedAt: "7월 25일 (토) 16:00", categories: ["자연 힐링"], body: "파도 소리가 좋았다." },
  { id: "haedong", lat: 35.1885, lng: 129.2233, place: "해동용궁사 부근", address: "부산시 용궁길", visitedOn: "2026-07-25", visitedAt: "7월 25일 (토) 10:30", categories: ["역사 문화"], body: "아침 일찍 도착해서 한적했다." },
  { id: "dalmaji", lat: 35.1576, lng: 129.1815, place: "달맞이길 부근", address: "부산시 달맞이길", visitedOn: "2026-07-24", visitedAt: "7월 24일 (금) 15:20", categories: ["카페"], body: "창가에서 쉬어 간 곳." },
  { id: "huinnyeoul", lat: 35.0788, lng: 129.0442, place: "흰여울문화마을 부근", address: "부산시 흰여울길", visitedOn: "2026-06-14", visitedAt: "6월 14일 (일) 14:00", categories: ["역사 문화"], body: "골목길 산책." },
  { id: "taejongdae", lat: 35.0513, lng: 129.0877, place: "태종대 부근", address: "부산시 전망로", visitedOn: "2026-06-14", visitedAt: "6월 14일 (일) 10:00", categories: ["자연 힐링"], body: "바람이 시원했다." },
  { id: "jeonpo", lat: 35.1563, lng: 129.0636, place: "전포카페거리 부근", address: "부산시 전포대로", visitedOn: "2026-06-13", visitedAt: "6월 13일 (토) 13:00", categories: ["카페"], body: "" },
  { id: "gwangalli", lat: 35.1532, lng: 129.1186, place: "광안리해변 부근", address: "부산시 광안해변로", visitedOn: "2026-05-17", visitedAt: "5월 17일 (일) 18:00", categories: ["자연 힐링"], body: "해 질 때 다시 오고 싶다." },
  { id: "gamcheon", lat: 35.0975, lng: 129.0106, place: "감천문화마을 부근", address: "부산시 감내2로", visitedOn: "2026-05-17", visitedAt: "5월 17일 (일) 11:00", categories: ["역사 문화"], body: "알록달록한 골목을 걸었다." },
  { id: "nampo", lat: 35.0995, lng: 129.0326, place: "남포동 부근", address: "부산시 광복로", visitedOn: "2026-05-16", visitedAt: "5월 16일 (토) 15:00", categories: ["카페"], body: "여행 중 잠깐 쉬어 가기." },
];

export const mockPeriodStart = { recent: "2026-08-10", month: "2026-10-10", all: "0000-01-01" };

export const mockRecords = [
  { id: "gangneung", title: "강릉 우정여행", description: "11월 8일 – 9일 3명", draft: true },
  { id: "busan", title: "부산 영도 1박 2일", description: "10월 3일 – 4일 장소 5곳", draft: false },
];

export const footprintPeriodOptions = [
  { value: "recent", label: "최근 3개월" },
  { value: "month", label: "최근 1개월" },
  { value: "all", label: "전체 기간" },
];

export const footprintCategoryOptions = [
  { value: "all", label: "카테고리 전체" },
  { value: "역사 문화", label: "역사 문화" },
  { value: "카페", label: "카페" },
  { value: "자연 힐링", label: "자연 힐링" },
];
