-- API-079 카테고리 목록. 표시 순서 열을 더하고 공통 카테고리 8개(TravelCategory)의 표시명을 넣는다.
-- 표시명은 프론트와 11/2 에 맞춘 Figma 카테고리 표 기준이다.
alter table travel_categories add column display_order integer;

insert into travel_categories (code, name, display_order) values
	('NATURE', '자연 힐링', 1),
	('CULTURE', '역사 문화', 2),
	('SIGHT', '랜드마크 거리', 3),
	('FOOD', '맛집', 4),
	('CAFE', '카페', 5),
	('ACTIVITY', '체험 액티비티', 6),
	('SHOPPING', '쇼핑 시장', 7),
	('EVENT', '축제 공연 행사', 8);

alter table travel_categories alter column display_order set not null;
