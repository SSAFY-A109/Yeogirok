// 코스 탐색 mock 데이터. API 명세(EXP-03~05) 확정 전까지 화면이 읽는 임시 원천이다.
// 명세가 나오면 features/explore/api/explore.api.ts의 호출부만 실제 API로 바꾼다.

/**
 * 공통 카테고리 8개. Figma "카테고리 기준 (v3)"(20:3235)와 백엔드 협의(2026-11-02)의 코드·표시 이름·아이콘 키.
 * @type {{ id: string, label: string, icon: string }[]}
 */
export const categories = [
  { id: "NATURE", label: "자연 힐링", icon: "mountain" },
  { id: "CULTURE", label: "역사 문화", icon: "landmark" },
  { id: "SIGHT", label: "랜드마크 거리", icon: "map-pin" },
  { id: "FOOD", label: "맛집", icon: "utensils" },
  { id: "CAFE", label: "카페", icon: "coffee" },
  { id: "ACTIVITY", label: "체험 액티비티", icon: "bike" },
  { id: "SHOPPING", label: "쇼핑 시장", icon: "shopping-bag" },
  { id: "EVENT", label: "축제 공연 행사", icon: "ticket" },
];

/** 대분류별 인기 장소 중분류 칩(TourAPI 중분류 기준 제안). 대분류가 바뀌면 "전체"로 돌아간다. */
export const subcategories = {
  NATURE: ["전체", "자연경관(산)", "자연경관(하천 해양)", "자연생태", "자연공원", "기타자연관광", "도시공원", "웰니스관광"],
  CULTURE: ["전체", "역사유적지", "역사유물", "종교성지", "안보관광지", "공연시설", "전시시설", "전통체험", "산사체험", "근대산업유산"],
  SIGHT: ["전체", "랜드마크관광", "도시·지역문화관광", "복합관광시설"],
  FOOD: ["전체", "한식", "외국식", "간이음식", "주점"],
  CAFE: ["전체", "카페/찻집", "제과"],
  ACTIVITY: ["전체", "공예체험", "농·산·어촌 체험", "웰니스관광", "산업관광", "기타체험", "육상레저스포츠", "수상레저스포츠", "항공레저스포츠", "복합레저스포츠", "테마공원", "레저스포츠시설"],
  SHOPPING: ["전체", "백화점", "쇼핑몰", "대형마트", "면세점", "전문매장/상가", "시장", "기타쇼핑시설"],
  EVENT: ["전체", "축제", "공연", "행사"],
};

/** 메인 상단 추천 코스(가로 페이저). 일차별 대표 장소 3곳을 함께 보여 준다. */
export const featuredCourses = [
  {
    id: "course-1",
    title: "강릉 바다 따라 2박 3일",
    placeCount: 8,
    duration: "2박 3일",
    transport: "자차",
    author: "해",
    highlights: [
      { day: 1, name: "안목 카페거리", tag: "카페" },
      { day: 2, name: "초당순두부마을", tag: "맛집" },
      { day: 3, name: "경포호 산책길", tag: "자연 힐링" },
    ],
  },
  {
    id: "course-2",
    title: "강릉 카페 투어 당일치기",
    placeCount: 5,
    duration: "당일",
    transport: "대중교통",
    author: "민",
    highlights: [
      { day: 1, name: "테라로사", tag: "카페" },
      { day: 1, name: "안목해변", tag: "자연 힐링" },
      { day: 1, name: "봉봉방앗간", tag: "카페" },
    ],
  },
  {
    id: "course-3",
    title: "비 오는 날 강릉 실내 코스",
    placeCount: 6,
    duration: "1박 2일",
    transport: "자차",
    author: "지",
    highlights: [
      { day: 1, name: "오죽헌", tag: "역사 문화" },
      { day: 1, name: "참소리박물관", tag: "역사 문화" },
      { day: 2, name: "중앙시장", tag: "쇼핑 시장" },
    ],
  },
  {
    id: "course-4",
    title: "정동진 해돋이와 바다열차",
    placeCount: 4,
    duration: "1박 2일",
    transport: "기차",
    author: "하",
    highlights: [
      { day: 1, name: "정동진역", tag: "랜드마크 거리" },
      { day: 1, name: "모래시계공원", tag: "랜드마크 거리" },
      { day: 2, name: "정동진 해돋이", tag: "자연 힐링" },
    ],
  },
  {
    id: "course-5",
    title: "강릉 시장 먹방 루트",
    placeCount: 7,
    duration: "당일",
    transport: "도보",
    author: "민",
    highlights: [
      { day: 1, name: "중앙시장", tag: "쇼핑 시장" },
      { day: 1, name: "교동 맛집거리", tag: "맛집" },
      { day: 1, name: "월화거리", tag: "랜드마크 거리" },
    ],
  },
  {
    id: "course-6",
    title: "주문진 바다 드라이브",
    placeCount: 5,
    duration: "당일",
    transport: "자차",
    author: "해",
    highlights: [
      { day: 1, name: "주문진항", tag: "자연 힐링" },
      { day: 1, name: "소돌아들바위공원", tag: "자연 힐링" },
      { day: 1, name: "주문진 수산시장", tag: "쇼핑 시장" },
    ],
  },
];

/** 대분류별 최근 30일 인기 장소 순위. 값이 없는 장소(null)는 순위에 넣지 않는다. */
export const rankings = {
  NATURE: [
    { id: "place-gyeongpo-beach", name: "경포해변", area: "강릉 해변", courseCount: 198, subcategory: "자연경관(하천 해양)", lat: 37.8055, lng: 128.9087 },
    { id: "place-gyeongpo-lake", name: "경포호", area: "강릉 호수", courseCount: 176, subcategory: "자연경관(하천 해양)", lat: 37.7957, lng: 128.8996 },
    { id: "place-sacheon-beach", name: "사천해변", area: "강릉 해변", courseCount: 141, subcategory: "자연경관(하천 해양)", lat: 37.8396, lng: 128.8771 },
    { id: "place-jumunjin-port", name: "주문진항", area: "강릉 항구", courseCount: 97, subcategory: "기타자연관광", lat: 37.8932, lng: 128.8271 },
    { id: "place-gangmun-beach", name: "강문해변", area: "강릉 해변", courseCount: 83, subcategory: "자연경관(하천 해양)", lat: 37.7955, lng: 128.9185 },
  ],
  CULTURE: [
    { id: "place-ojukheon", name: "오죽헌", area: "강릉 고택", courseCount: 120, subcategory: "역사유적지", lat: 37.779, lng: 128.8795 },
    { id: "place-chamsori", name: "참소리박물관", area: "강릉 박물관", courseCount: 88, subcategory: "전시시설", lat: 37.7958, lng: 128.9174 },
    { id: "place-seonggyojang", name: "선교장", area: "강릉 고택", courseCount: 64, subcategory: "역사유적지", lat: 37.786, lng: 128.884 },
  ],
  SIGHT: [
    { id: "place-anmok", name: "안목 커피거리", area: "강릉 거리", courseCount: 210, subcategory: "도시·지역문화관광", lat: 37.7728, lng: 128.9476 },
    { id: "place-jeongdongjin", name: "정동진역", area: "강릉 역", courseCount: 101, subcategory: "랜드마크관광", lat: 37.6916, lng: 129.0343 },
  ],
  FOOD: [
    { id: "place-chodang", name: "초당순두부마을", area: "강릉 맛집", courseCount: 189, subcategory: "한식", lat: 37.7918, lng: 128.9135 },
    { id: "place-jungang-market-food", name: "중앙시장 먹거리", area: "강릉 시장", courseCount: 132, subcategory: "간이음식", lat: 37.7547, lng: 128.8972 },
  ],
  CAFE: [
    { id: "place-terarosa", name: "테라로사 본점", area: "강릉 카페", courseCount: 155, subcategory: "카페/찻집", lat: 37.7437, lng: 128.9068 },
    { id: "place-bongbong", name: "봉봉방앗간", area: "강릉 카페", courseCount: 70, subcategory: "제과", lat: 37.7536, lng: 128.8987 },
  ],
  ACTIVITY: [
    { id: "place-sea-train", name: "정동진 바다열차", area: "강릉 체험", courseCount: 92, subcategory: "기타체험", lat: 37.6916, lng: 129.0343 },
  ],
  SHOPPING: [
    { id: "place-jungang-market", name: "중앙시장", area: "강릉 시장", courseCount: 145, subcategory: "시장", lat: 37.7547, lng: 128.8972 },
  ],
  EVENT: [],
};

/** 코스 검색 대상. 검색어는 제목·장소명·지역으로, 조건은 province·duration·transport로 거른다(DEC-20). */
export const courses = [
  {
    id: "course-1",
    title: "강릉 바다 따라 2박 3일",
    region: "강릉",
    province: "강원",
    placeCount: 8,
    duration: "2박 3일",
    transport: "자차",
    forkCount: 312,
    summary: "안목해변 → 초당순두부마을 → 경포호 외 5곳",
    description: "바다를 따라 걷고, 순두부와 커피로 쉬어 가는 코스예요. 둘째 날 아침은 초당에서 시작해요.",
    travelers: ["지", "민", "하"],
    places: [
      { id: "p1-1", day: 1, name: "안목해변 카페거리", category: "CAFE", note: "창가 자리에서 바다가 보여요", lat: 37.7728, lng: 128.9476 },
      { id: "p1-2", day: 1, name: "강문해변", category: "NATURE", note: "해 질 무렵이 가장 예뻐요", lat: 37.7955, lng: 128.9185 },
      { id: "p1-3", day: 1, name: "경포호 산책길", category: "NATURE", note: "호수 한 바퀴 40분", lat: 37.7979, lng: 128.9022 },
      { id: "p1-4", day: 2, name: "초당순두부마을", category: "FOOD", note: "아침 일찍 가면 덜 기다려요", lat: 37.7918, lng: 128.9135 },
      { id: "p1-5", day: 2, name: "오죽헌", category: "CULTURE", note: "오전에 한산해요", lat: 37.779, lng: 128.8795 },
      { id: "p1-6", day: 3, name: "참소리박물관", category: "CULTURE", note: "비 오는 날에도 좋아요", lat: 37.7958, lng: 128.9174 },
    ],
  },
  {
    id: "course-2",
    title: "강릉 카페 투어 당일치기",
    region: "강릉",
    province: "강원",
    placeCount: 5,
    duration: "당일",
    transport: "대중교통",
    forkCount: 176,
    summary: "테라로사 → 안목해변 → 봉봉방앗간 외 2곳",
    description: "버스로 이동해도 무리 없는 카페 위주 코스예요.",
    travelers: ["민", "하"],
    places: [
      { id: "p2-1", day: 1, name: "테라로사 본점", category: "CAFE", note: "오전에는 자리가 넉넉해요", lat: 37.7437, lng: 128.9068 },
      { id: "p2-2", day: 1, name: "안목해변", category: "NATURE", note: "커피 들고 산책하기 좋아요", lat: 37.7728, lng: 128.9476 },
      { id: "p2-3", day: 1, name: "봉봉방앗간", category: "CAFE", note: "한과 디저트가 유명해요", lat: 37.7536, lng: 128.8987 },
    ],
  },
  {
    id: "course-3",
    title: "비 오는 날 강릉 실내 코스",
    region: "강릉",
    province: "강원",
    placeCount: 6,
    duration: "1박 2일",
    transport: "자차",
    forkCount: 94,
    summary: "오죽헌 → 참소리박물관 → 중앙시장 외 3곳",
    description: "비가 와도 걱정 없는 박물관과 시장 중심 코스예요.",
    travelers: ["지"],
    places: [
      { id: "p3-1", day: 1, name: "오죽헌", category: "CULTURE", note: "우산 쓰고 걷기 좋아요", lat: 37.779, lng: 128.8795 },
      { id: "p3-2", day: 1, name: "참소리박물관", category: "CULTURE", note: "두 시간은 잡아 두세요", lat: 37.7958, lng: 128.9174 },
      { id: "p3-3", day: 2, name: "중앙시장", category: "SHOPPING", note: "점심은 여기서 해결해요", lat: 37.7547, lng: 128.8972 },
    ],
  },
  {
    id: "course-4",
    title: "정동진 해돋이와 바다열차",
    region: "정동진",
    province: "강원",
    placeCount: 4,
    duration: "1박 2일",
    transport: "기차",
    forkCount: 61,
    summary: "정동진역 → 모래시계공원 → 썬크루즈 외 1곳",
    description: "일출 시간에 맞춰 움직이는 기차 여행 코스예요.",
    travelers: ["하", "해"],
    places: [
      { id: "p4-1", day: 1, name: "정동진역", category: "SIGHT", note: "역 앞 바다가 바로 보여요", lat: 37.6916, lng: 129.0343 },
      { id: "p4-2", day: 1, name: "모래시계공원", category: "SIGHT", note: "일몰 무렵이 좋아요", lat: 37.6914, lng: 129.0356 },
    ],
  },
  {
    id: "course-5",
    title: "강릉 시장 먹방 루트",
    region: "강릉",
    province: "강원",
    placeCount: 7,
    duration: "당일",
    transport: "도보",
    forkCount: 58,
    summary: "중앙시장 → 교동 → 월화거리 외 4곳",
    description: "시장 골목을 걸어서 도는 먹거리 중심 코스예요.",
    travelers: ["민"],
    places: [
      { id: "p5-1", day: 1, name: "중앙시장", category: "SHOPPING", note: "닭강정은 줄이 길어요", lat: 37.7547, lng: 128.8972 },
      { id: "p5-2", day: 1, name: "교동 맛집거리", category: "FOOD", note: "점심 시간대는 대기가 있어요", lat: 37.7611, lng: 128.8946 },
      { id: "p5-3", day: 1, name: "월화거리", category: "SIGHT", note: "야시장이 열려요", lat: 37.7512, lng: 128.8949 },
    ],
  },
  {
    id: "course-6",
    title: "주문진 바다 드라이브",
    region: "주문진",
    province: "강원",
    placeCount: 5,
    duration: "당일",
    transport: "자차",
    forkCount: 47,
    summary: "주문진항 → 소돌아들바위공원 → 주문진 수산시장 외 2곳",
    description: "해안도로를 따라 달리며 바다 풍경을 즐기는 당일 코스예요.",
    travelers: ["해"],
    places: [
      { id: "p6-1", day: 1, name: "주문진항", category: "NATURE", note: "아침 경매 시간에 가면 활기차요", lat: 37.8932, lng: 128.8271 },
      { id: "p6-2", day: 1, name: "소돌아들바위공원", category: "NATURE", note: "기암괴석 사이로 산책로가 있어요", lat: 37.8986, lng: 128.8294 },
      { id: "p6-3", day: 1, name: "주문진 수산시장", category: "SHOPPING", note: "회를 포장해 가기 좋아요", lat: 37.8935, lng: 128.8284 },
    ],
  },
  {
    id: "course-7",
    title: "제주 동쪽 오름과 바다 2박 3일",
    region: "제주",
    province: "제주",
    placeCount: 7,
    duration: "2박 3일",
    transport: "자차",
    forkCount: 205,
    summary: "성산일출봉 → 다랑쉬오름 → 월정리해변 외 4곳",
    description: "동쪽 해안도로를 따라 오름과 바다를 번갈아 들르는 코스예요.",
    travelers: ["지", "해"],
    places: [
      { id: "p7-1", day: 1, name: "성산일출봉", category: "NATURE", note: "일출을 보려면 새벽에 올라요", lat: 33.4588, lng: 126.9425 },
      { id: "p7-2", day: 2, name: "다랑쉬오름", category: "NATURE", note: "정상까지 30분 정도 걸려요", lat: 33.4779, lng: 126.8222 },
      { id: "p7-3", day: 3, name: "월정리해변", category: "NATURE", note: "해변 앞 카페가 많아요", lat: 33.5562, lng: 126.7958 },
    ],
  },
  {
    id: "course-8",
    title: "경주 역사 산책 당일치기",
    region: "경주",
    province: "경상",
    placeCount: 5,
    duration: "당일",
    transport: "대중교통",
    forkCount: 133,
    summary: "불국사 → 대릉원 → 첨성대 외 2곳",
    description: "시내버스로 이어지는 유적지 중심 코스예요.",
    travelers: ["민"],
    places: [
      { id: "p8-1", day: 1, name: "불국사", category: "CULTURE", note: "오전 일찍 가면 한산해요", lat: 35.7901, lng: 129.3321 },
      { id: "p8-2", day: 1, name: "대릉원", category: "CULTURE", note: "천마총 내부도 들어가 볼 수 있어요", lat: 35.8383, lng: 129.2113 },
      { id: "p8-3", day: 1, name: "첨성대", category: "CULTURE", note: "밤 조명이 예뻐요", lat: 35.8347, lng: 129.2190 },
    ],
  },
];

/** 장소를 담을 수 있는 내 여행방. 여행방 도메인 API가 나오면 features/trip으로 옮긴다. */
export const rooms = [
  { id: "room-1", name: "강릉 우정여행", dates: "11.08 – 11.09", memberCount: 4, placeCount: 5 },
  { id: "room-2", name: "제주 가을 여행", dates: "날짜 미정", memberCount: 2, placeCount: 0 },
];
