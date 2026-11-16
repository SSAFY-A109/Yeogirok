-- V5: 코스에서 가져온 후보(room_places, API-030)의 원본 기록 참조에 FK 를 건다.
-- V1 에서 뺀 FK 중 하나다(backend/docs/08-flyway.md 4절). 엔티티는 domain/room/entity/RoomPlace.
--
-- 원본 기록이 지워져도 가져간 후보는 남기고 출처(source_record_id)만 비운다(ON DELETE SET NULL). 지금 코드에는 기록을 실제로
-- 지우는 곳이 없지만(여행방은 삭제 표시만 한다), 탈퇴 정리 등에서 지우게 되어도 기록 삭제가 막히지 않게 한다.
-- 출처가 비면 후보 목록의 sourceRecord 는 null 이고 메인 가져간 방 수에서도 빠진다.

-- FK 를 걸기 전에, 이미 없는 기록을 가리키는 값이 있으면 비운다(API-030 은 기록을 확인한 뒤에만 채워 보통은 없다).
update room_places rp
   set source_record_id = null
 where rp.source_record_id is not null
   and not exists (select 1 from travel_records tr where tr.id = rp.source_record_id);

alter table room_places
    add constraint fk_room_places_source_record
        foreign key (source_record_id) references travel_records (id) on delete set null;
