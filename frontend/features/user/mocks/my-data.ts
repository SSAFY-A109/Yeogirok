import type { SocialProvider } from "@/features/auth/model";

export const mockProfile: { name: string; provider: SocialProvider } = { name: "지윤", provider: "kakao" };

export const mockRecords = [
  { id: "gangneung", title: "강릉 우정여행", description: "11월 8일 – 9일 3명", draft: true },
  { id: "busan", title: "부산 영도 1박 2일", description: "10월 3일 – 4일 장소 5곳", draft: false },
];
