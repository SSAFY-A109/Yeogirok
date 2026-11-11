# DB 스키마 관리 (Flyway)

> 테이블은 Flyway 마이그레이션 파일이 만든다. Hibernate 는 엔티티와 테이블이 맞는지만 확인한다(`ddl-auto: validate`).
> 11/11 에 `ddl-auto: update` 에서 바꿨다. 서버에 처음 반영하는 순서는 `infra/README.md` 7단계에 있다.

## 1. 구성

| 항목 | 값 |
| --- | --- |
| 의존성 | `spring-boot-starter-flyway`, `flyway-database-postgresql` |
| 파일 위치 | `src/main/resources/db/migration/V{번호}__{설명}.sql` |
| local·prod | Flyway 가 마이그레이션을 적용한 뒤 Hibernate 가 `validate` 로 확인한다. 맞지 않으면 서버가 뜨지 않는다 |
| 테스트 | Flyway 를 끈다. 마이그레이션 파일은 PostgreSQL 문법이라 H2 에서 돌지 않는다 |

테스트는 전처럼 H2 에 `src/test/resources/schema.sql` 과 Hibernate(`update`)가 테이블을 만든다.

- `@ActiveProfiles("test")` 인 테스트: `application-test.yaml` 의 `spring.flyway.enabled: false`.
- 프로필을 지정하지 않은 테스트(`@DataJpaTest` 등, 기본 프로필 `local`): `src/test/resources/config/application-local.yaml` 이
  main 의 `application-local.yaml` 을 덮어 Flyway 를 끄고 `ddl-auto: update` 로 둔다.

그래서 **테스트는 마이그레이션 파일을 검증하지 않는다.** 마이그레이션은 로컬 PostgreSQL 에서 서버를 띄워 확인한다.

## 2. 스키마를 바꿀 때

1. 엔티티를 고친다.
2. 다음 번호 파일을 만든다. 예: `V2__add_room_notice.sql`. PostgreSQL 문법으로 쓴다.
3. 로컬에서 서버를 띄운다(`./gradlew bootRun`). Flyway 가 새 파일을 적용하고 `validate` 가 통과해야 뜬다.

규칙:

- **한 번 적용된 파일은 고치지 않는다.** 내용이 바뀌면 Flyway 가 `checksum mismatch` 로 시작을 거부한다. 바꿀 것은 새 번호 파일로 낸다.
- 번호는 `backend/dev` 에 머지된 마지막 번호 다음을 쓴다. 두 PR 이 같은 번호를 쓰면 나중에 머지되는 쪽이 번호를 올린다.
- 엔티티만 고치고 파일을 빼먹으면 로컬과 서버가 `Schema-validation: missing column ...` 으로 뜨지 않는다. H2 테스트는 통과하므로 PR 검사로는 잡히지 않는다.
- 데이터를 지우거나 열을 없애는 변경은 서버 데이터가 사라진다. PR 본문에 적는다.

## 3. 예전 로컬 DB 비우기

`update` 로 만든 테이블이 남아 있으면 Flyway 가 "기록 없는 테이블이 있다"며 시작을 거부한다
(`Found non-empty schema(s) "public" but no schema history table`). 한 번 비운다. 로컬 데이터는 지워진다.

```bash
docker compose -f infra/docker-compose.local.yml down -v
docker compose -f infra/docker-compose.local.yml up -d
```

## 4. V1 에 넣은 것과 넣지 않은 것

`V1__init.sql` 은 Flyway 를 들이기 전 Hibernate(`update`)가 만들던 스키마를 그대로 옮긴 것이다(11/11, backend/dev `18ea256`).
빈 PostgreSQL 16 에 `update` 로 만든 DB 와 V1 로 만든 DB 를 비교해 열·제약·인덱스가 같은 것을 확인했다. 다른 점은 아래 둘이다.

- **`user_push_tokens` 를 만든다.** 이 테이블은 `update` 로는 PostgreSQL 에 만들어지지 않았다. 엔티티의 `platform` 열거형 검사에
  PostgreSQL 이 붙이는 이름(`user_push_tokens_platform_check`)이 엔티티가 이름 붙인 검사와 겹쳐 `CREATE TABLE` 이 실패한다
  (`Place` 엔티티 주석에 적힌 것과 같은 문제). V1 은 이름 붙인 검사 하나만 두고 만든다.
- 유니크 제약 세 개의 이름이 Hibernate 가 만든 무작위 이름(`uk5hw5…`) 대신 PostgreSQL 기본 이름(`ar_tip_images_tip_id_key` 등)이다.
  코드에서 이 이름을 쓰는 곳은 없다.

**넣지 않은 것.** 엔티티 주석에 "Flyway V1 에 맡긴다"고 적힌 아래 항목은 노션 ERD DDL 에만 있고 코드로 검증한 적이 없어 V1 에서 뺐다.
백엔드에서 합의한 뒤 다음 번호 파일로 추가한다. 추가할 때는 엔티티 주석도 함께 고친다.

| 항목 | 해당 테이블 | 넣기 전에 확인할 것 |
| --- | --- | --- |
| FK | 거의 모든 테이블(`room_messages`, `footprints`, `ar_tips`, `actual_visits`, `visit_images`, `room_members`, `room_places.source_record_id`, `user_push_tokens` 등) | 지금 코드의 저장·삭제 순서가 FK 를 어기지 않는지. H2 테스트에는 FK 가 없어 잡히지 않는다 |
| ENUM 타입 | `places`, `travel_rooms`, `travel_records`, `actual_visits`, `ar_tips`, `ar_tip_reactions`, `users` | 지금은 `varchar` + CHECK 다. ENUM 타입으로 바꾸면 엔티티 매핑도 바꿔야 저장이 된다 |
| 열 기본값 | 여러 테이블의 `created_at`·`updated_at` 등 | 지금은 서비스 시계로 값을 넣는다 |
| 부분 인덱스 | `room_places`(`WHERE removed_at IS NULL`), `room_members`(`uq_room_members_creator`), `footprints` | 지금은 일반 인덱스다 |
| DEFERRABLE 유니크 | `actual_visits (record_id, visit_date, position)`, `visit_images (visit_id, position)` | 순서를 다시 매기는 코드(`TemporaryPositions`)가 이 제약을 전제로 쓰였다 |
| CHECK | `footprints.category_codes` 배열, `travel_records.reference_plan`, `travel_rooms` JSONB | |
| 인덱스 | `footprint_statistics`(`ix_footprint_statistics_date_cell`) | |
