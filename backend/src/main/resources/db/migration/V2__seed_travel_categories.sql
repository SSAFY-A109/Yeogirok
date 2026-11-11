-- API-079 카테고리 목록. 공통 카테고리 8개(TravelCategory)의 표시명을 넣는다. 열은 ERD 대로 code·name 만 둔다.
-- 표시명은 프론트와 11/2 에 맞춘 Figma 카테고리 표 기준이다. 표시 순서는 TravelCategory 선언 순서를 따른다.
-- 이미 손으로 넣은 행이 있어도 서버가 뜨도록 같은 코드는 표시명만 덮어쓴다.
insert into travel_categories (code, name) values
	('NATURE', '자연 힐링'),
	('CULTURE', '역사 문화'),
	('SIGHT', '랜드마크 거리'),
	('FOOD', '맛집'),
	('CAFE', '카페'),
	('ACTIVITY', '체험 액티비티'),
	('SHOPPING', '쇼핑 시장'),
	('EVENT', '축제 공연 행사')
on conflict (code) do update set name = excluded.name;
