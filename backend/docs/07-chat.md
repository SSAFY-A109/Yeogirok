# 여행방 채팅 결정 사항

> 여행방 채팅(API-076·077·088, WebSocket `/ws/v1`)을 만들면서 정한 규칙이다. 원천은 팀 Notion "API 명세"와 "ERD 설계 관련 사항·Redis"이고,
> 명세에 없는 값은 여기 적는다. 명세와 이 문서가 다르면 명세를 먼저 고치고 이 문서를 맞춘다.

## 1. 흐름

| 단계 | 방식 | 비고 |
| --- | --- | --- |
| 연결 티켓 발급 | API-077 `POST /realtime/tickets` | 멱등키 필수, 201. 사용자·방에 묶인 1회용 티켓(UUID) |
| 연결 | WebSocket `wss://도메인/ws/v1` | 티켓·토큰을 URL 에 넣지 않는다 |
| 인증 | 첫 프레임 `AUTH` → `READY` | 티켓을 한 번 소모하고 계정·현재 참여·방 미삭제를 확인한다 |
| 보내기 | `SEND_MESSAGE` → `MESSAGE_ACK`(보낸 사람) · `MESSAGE_CREATED`(방) | DB 커밋 뒤에만 보낸다 |
| 연결 유지 | `PING` → `PONG` | PONG 에 최신 저장 ID. 권한을 다시 확인한다 |
| 이력·재연결 복구 | API-076 `GET /rooms/{roomId}/messages` | `beforeMessageId` 과거 이력, `afterMessageId`+`throughMessageId` 복구 |
| 읽은 위치 | API-088 `PATCH /rooms/{roomId}/messages/read` | 앱이 실제로 확인한 위치만. 수신·ACK·조회로는 바꾸지 않는다 |

프레임 모양·오류 코드표는 노션 API-077 "채팅 양방향 WebSocket 계약"을 따른다. STOMP 는 쓰지 않는다(Spring 기본 WebSocket + JSON 텍스트 프레임).

## 2. 결정 사항 (11/10)

### 운영 수치

`application.yaml` 의 `yeogirok.chat` 에 둔다.

| 항목 | 값 | 비고 |
| --- | --- | --- |
| 티켓 수명 | 30초 | 발급 직후 연결하는 용도 |
| AUTH 대기 | 10초 | 넘기면 ERROR `A001` 후 닫는다 |
| PING | 앱이 25초마다 | 서버는 60초 동안 아무 프레임이 없으면 닫는다 |
| 시간 초과 검사 주기 | 5초 | |
| 이력 페이지 크기 | 기본 20, 최대 50 | 공통 `PageSize`. 범위 밖 `C007` |
| 사용자당 동시 연결 수 | 제한 없음 | 연결 하나는 방 하나에만 묶인다 |
| 전송 속도 제한 | 없음 | |
| Origin 검사 | 하지 않음 | 모바일 앱은 Origin 이 일정하지 않고, 쿠키가 아니라 티켓으로 인증한다 |

Nginx `proxy_read_timeout` 은 무응답 기준(60초)보다 길게 둔다(`infra/nginx/yeogirok.conf` 의 `location /ws/`, 75초).

### 보안

- `/ws/**` 는 기본 보안 체인과 분리한 전용 체인을 쓴다. Bearer 토큰 검사를 걸지 않는다.
  같은 체인에서 permitAll 만 하면, 앱이 습관처럼 붙인 만료 토큰 헤더 때문에 핸드셰이크가 401 로 실패한다.
- 인증은 첫 `AUTH` 프레임의 티켓으로만 한다. 티켓은 Redis `ws-ticket:{uuid}`(값 `{userId}:{roomId}`)에 두고 GETDEL 로 한 번만 쓴다.
- 같은 멱등키로 티켓을 다시 요청하면 처음 발급한 티켓을 돌려준다. 이미 썼거나 만료됐으면 AUTH 가 `A003` 이고, 앱은 새 멱등키로 새 티켓을 받는다.

### 메시지 저장과 순서

- 저장은 **채팅 전용 잠금**으로 방마다 한 줄로 세운다(ID 발급 → INSERT → 커밋).
  - 운영은 PostgreSQL `pg_advisory_xact_lock(채팅 이름공간, 방 ID)` 이고, 트랜잭션이 끝나면 DB 가 푼다. 테스트(H2)는 JVM 잠금(`InMemoryChatRoomLock`)을 쓴다.
  - **여행방 행 잠금과 따로 둔다.** 일정 저장·후보 변경·참여·나가기가 쓰는 방 행 잠금을 쓰지 않아, 일정 저장 중에도 메시지 전송이 기다리지 않는다.
  - 방 안에서 ID 가 작은 메시지가 늦게 커밋되지 않으므로 재연결 복구(`afterMessageId`)가 메시지를 건너뛰지 않는다.
- 권한(참여·방 삭제·계정)은 잠금 없이 한 쿼리로 확인한다. 나가기와 거의 동시에 보낸 메시지는 나가기 직전 메시지로 저장될 수 있다. 나간 뒤 보낸 메시지는 막힌다.
- 같은 `clientMessageId`·같은 본문은 새로 저장하지 않고 원래 메시지 ACK(`replayed=true`)를 돌려준다. 다른 본문은 `C005`. 유니크 `(room_id, sender_id, client_message_id)` 로도 막는다.
- 본문은 1~100자(코드 포인트, PostgreSQL `char_length` 와 같은 기준). 빈 값·공백만·100자 초과는 `C003`. 자르거나 앞뒤 공백을 지우지 않고 그대로 저장한다.

### 실시간 전달

- **Redis Pub/Sub**. 방별 채널 `chat:room:{roomId}` 에 완성된 `MESSAGE_CREATED` 프레임을 싣는다. 서버는 그 방 연결이 있을 때만 구독한다.
  서버를 여러 대로 늘려도 같은 사용자를 같은 서버로 보내는 설정(sticky session)은 필요 없다.
- 받은 메시지는 스레드 하나로 받은 순서대로 연결별 보내기 줄에 넣고, 실제 쓰기는 연결마다 따로 한다(가상 스레드). 느린 연결 하나가 다른 연결·방을 막지 않는다.
  못 보낸 프레임이 1,000개를 넘으면 그 연결을 닫는다.
- 서로 다른 요청이 커밋 직후 발행해 생기는 전달 순서 역전은 **앱이 `message.id` 로 정렬·중복 제거**해 맞춘다. 정확히 한 번 전달을 보장하지 않으며, 빠진 메시지는 PONG 의 최신 ID 와 API-076 복구로 채운다.
- AUTH 에서 방에 묶은 뒤 권한을 한 번 더 확인하고, 그 전에 온 방 메시지는 잡아 뒀다가 READY 뒤에 보낸다(실패하면 버린다).

### 읽음

- 기준은 RDB `room_members.last_read_message_id` 다. 별도 읽음 테이블·Redis 영속 키는 없다.
- API-088 은 **방 행을 잠그지 않는다.** 본인 참여 행 하나를 조건부 UPDATE(현재 참여 중이고 기존 값보다 클 때만) 한 번으로 바꿔 GREATEST 를 지킨다.
  여러 기기에서 순서가 바뀐 요청이 와도 뒤로 가지 않고, 일정 저장·메시지 전송을 기다리지 않는다.
- 안 읽음 점(`chatReadState.hasUnreadMessages`)은 같은 방에서 읽은 위치보다 큰 **타인** 메시지가 있는지로 정한다(읽은 위치가 null 이면 타인 메시지 전체).
  API-020·022·076·088 이 같은 계산(`ChatReadStates`)을 쓴다. ID 차이를 개수로 쓰지 않는다.

### 권한 상실과 강제 종료

커밋 직후 Redis `chat:control` 로 모든 서버에 알려 맞는 연결에 ERROR 를 보내고 닫는다. 알림이 실패해도 다음 전송·PING 에서 권한을 다시 확인해 닫는다.

| 일 | 대상 | 코드 |
| --- | --- | --- |
| 나가기(API-087) | 그 사람의 그 방 연결 | `RO002` |
| 마지막 참여자 나가기(방 삭제) | 그 방의 모든 연결 | `RO001` |
| 회원 탈퇴(API-009) | 그 사람의 모든 방 연결 | `A003` |

탈퇴한 회원의 메시지는 남기고, 응답에서 `sender.id`·`clientMessageId` 를 null 로, 이름을 "탈퇴한 사용자"로 내보낸다. 탈퇴 시 참여자 정리는 탈퇴 데이터 정리 작업(`UserService` TODO)에서 한다.

### 방 삭제 뒤 메시지 정리

- 방 삭제 커밋 직후 Redis Set `chat-cleanup:rooms` 에 방 ID 를 넣는다. 유예 기간은 없다.
- 10분마다 목록의 방 메시지를 500개씩 나눠 지우고, 다 지운 뒤에만 목록에서 뺀다. 실패하면 다음 주기에 다시 한다.
- Redis 가 유실돼도 1시간마다 DB 에서 "삭제됐는데 메시지가 남은 방"을 찾아 지운다.
- 삭제되지 않은 방의 메시지는 지우지 않는다(목록에 잘못 들어가도 빼기만 한다).

## 3. 하지 않은 것

- 사진 채팅, 타인 읽음 실시간 방송, 메시지별 안 읽은 인원 수(노션 "별도 결정 항목").
- 메시지 보관 기간 만료 삭제. 방이 유지되는 동안 계속 보관한다.
- 전달 시점마다 받는 사람의 권한을 DB 에서 다시 확인하는 것. 강제 종료 알림과 전송·PING 재확인으로 대신한다.

### TODO: 앱 푸시 알림

노션에서 "별도 결정 항목"이라 이번 범위에서 뺐다. 지금은 채팅 화면을 연 사람만 실시간으로 받는다. 앱이 백그라운드에 있거나
다른 화면에 있거나 꺼져 있으면 새 메시지를 알 수 없고, 방 목록의 안 읽음 점도 목록을 다시 불러올 때만 바뀐다.

넣기 전에 팀이 정할 것:

- 푸시 방식. 앱이 Expo 라 Expo Push(FCM·APNs 대행)가 가장 간단하다. iOS 는 Apple 개발자 계정·인증서가 필요하다.
- 알림 내용. 본문 미리보기를 보여 줄지, "새 메시지가 있어요"만 보여 줄지(잠금 화면 노출·개인정보).
- 방별 알림 끄기, 짧은 시간에 온 여러 메시지를 묶어 보낼지.
- 채팅만 할지, 초대 수락·일정 확정 같은 알림도 함께 공통 모듈로 할지.
- 프론트 문서(`frontend-spec.md` DEC-13)는 채팅을 아직 "만들지 않음"으로 둔다. 범위를 함께 맞춘다.

서버 작업:

- 기기 토큰 등록·삭제 API와 저장소. 로그아웃·탈퇴 때 지운다.
- 커밋된 새 메시지(`replayed=false`)에서만, 그 방에 연결되지 않은 현재 참여자에게만 보낸다. 붙일 자리는
  `ChatWebSocketHandler` 의 MESSAGE_CREATED 발행 바로 뒤다(TODO 주석). 서버가 여러 대면 접속 상태를 Redis 에 둔다.

## 4. 운영 DB 반영

운영은 아직 `ddl-auto: update` 라 배포하면 `room_messages` 가 엔티티 기준으로 만들어진다. FK 는 다른 테이블처럼 Flyway 도입 때 넣는다. ERD DDL:

```sql
CREATE TABLE room_messages (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    client_message_id VARCHAR(100) NOT NULL,
    body TEXT NOT NULL CHECK (char_length(body) <= 100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (room_id, sender_id, client_message_id),
    FOREIGN KEY (room_id) REFERENCES travel_rooms (id),
    FOREIGN KEY (sender_id) REFERENCES users (id),
    FOREIGN KEY (room_id, sender_id) REFERENCES room_members (room_id, user_id)
);

CREATE INDEX ix_room_messages_room_id ON room_messages (room_id, id DESC);
```
