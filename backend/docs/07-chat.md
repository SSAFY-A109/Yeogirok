# 여행방 채팅 결정 사항

> 여행방 채팅(API-076·077·088·095·096, WebSocket `/ws/v1`, Android 앱 푸시)을 만들면서 정한 규칙이다. 원천은 팀 Notion "API 명세"와 "ERD 설계 관련 사항·Redis"이고,
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
| 푸시 토큰 등록 | API-095 `PUT /users/me/push-tokens` | 로그인 뒤. `{token, platform:"ANDROID"}`. 같은 토큰 재등록 성공, 다른 계정 토큰이면 지금 계정으로 옮긴다 |
| 푸시 토큰 삭제 | API-096 `DELETE /users/me/push-tokens`, 또는 API-007 `pushToken` | 기기 토큰이 바뀌었을 때 096. 로그아웃은 API-007 에 `pushToken` 을 함께 보낸다. 본인 토큰만 지우고 없어도 성공 |
| 앱 푸시 | FCM HTTP v1 | 채팅 화면에 연결되지 않은 참여자에게. 아래 "앱 푸시 알림" |

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

### 앱 푸시 알림 (Android, FCM)

원천은 노션 API-077 "채팅 푸시 알림 (Android)"·API-095·096, ERD `user_push_tokens`, Redis `push:chat:{userId}:{roomId}` 다.
채팅 화면을 열지 않은 참여자(백그라운드·다른 화면·앱 꺼짐)가 새 메시지를 알 수 있게 한다.

**방식**: Expo Push 를 거치지 않고 Firebase Admin SDK 로 FCM HTTP v1 에 직접 보낸다. 앱은 `getDevicePushTokenAsync()` 로 받은
FCM 기기 토큰을 API-095 로 등록한다. Android 만 지원한다.

**구조**: 푸시 발송은 채팅 밖에서도 쓰도록 공용으로 둔다. 초대 수락·일정 확정 같은 알림도 받을 회원과 알림 모양만 정해 `PushNotifier` 로 보낸다.

| 위치 | 맡는 일 |
| --- | --- |
| `domain/push` (공용) | 기기 토큰 저장·API-095·096·탈퇴 삭제, FCM 발송기(`PushSender`), `PushNotifier.sendToUsers`(받을 회원들의 기기에 한 번에 발송: 토큰 조회 한 번, FCM 은 토큰 500개마다 한 번. 해제된 토큰 정리, 회원마다 결과 `SENT`·`NO_DEVICE`·`REJECTED`·`UNAVAILABLE`) |
| `domain/chat/push` (채팅) | 받는 사람 조회, 접속자 제외(`chat:online`), 3초 제한(`push:chat`), 알림 모양, 비동기 발송 줄 |

**언제·누구에게**

- 커밋된 새 메시지(`replayed=false`)에서만 보낸다. 저장 실패·재전송 ACK 에는 보내지 않는다.
- 방이 살아 있고, 현재 참여 중이고(`left_at IS NULL`), 계정이 활성인 회원 중 보낸 사람을 뺀, 토큰이 있는 회원.
- 그 방에 **READY 로 연결된 회원은 모든 기기에서 뺀다**. 앱은 채팅 화면을 나가거나 백그라운드로 가면 연결을 닫는다.
- 같은 수신자·같은 방은 **3초에 한 번**. 발송 직전에 Redis `push:chat:{userId}:{roomId}` 를 `SET NX EX 3` 하고, 키가 이미
  있으면 그 수신자에게는 이번 푸시를 생략한다. 같은 방 다른 수신자와 WebSocket 실시간 수신에는 적용하지 않는다.
- 제한을 통과한 수신자 모두의 등록 기기에 한 번에 보낸다(`sendEach`, 500개씩). 결과는 수신자마다 따로 보고 제한을 푼다.

**알림 모양**(노션 API-077 예시 + Android tag)

| 항목 | 값 |
| --- | --- |
| `notification.title` | 여행방 이름 |
| `notification.body` | `닉네임: 메시지 앞 40자`. 글자 수는 본문만 코드 포인트로 센다(채팅 100자 기준과 같다). 40자를 넘으면 자르고 `…` |
| `data` | `{type:"CHAT", roomId, messageId}`. 값은 모두 문자열. 알림을 누르면 앱이 그 방 채팅으로 간다 |
| `android.notification.channel_id` | `chat`(앱이 만든 채널) |
| `android.notification.tag` | `chat:{roomId}`. 같은 방 알림은 새 알림이 이전 알림을 바꿔 방마다 하나만 보인다 |
| `android.priority` | `HIGH` |

`…` 와 `tag` 는 노션 예시에 없어 여기서 정했다(노션에 반영 필요).

**접속 상태(Redis)**: 방마다 정렬 집합 `chat:online:{roomId}`. 멤버 `{userId}:{연결 ID}`, 점수는 만료 시각(ms).

- READY 때 넣고, PING 마다 만료 시각을 지금부터 90초로 늘리고, 연결이 닫히면 지운다. 서버가 여러 대여도 같은 키를 본다.
- 서버가 죽어 지우지 못한 멤버는 만료 시각이 지나면 조회에서 빠진다(최대 90초 동안 그 사람에게 푸시가 생략될 수 있다).
  키 자체에도 180초 만료를 걸어 빈 방 키가 남지 않는다.
- 노션 Redis 문서에 아직 없는 키다(노션에 반영 필요).

**비동기·실패**

- WebSocket 처리 스레드는 발송 줄에 넣고 바로 돌아온다. 발송은 전용 스레드 2개가 하고, 줄(1,000개)이 넘치면 그 메시지의 푸시만 버린다.
  실행기를 빈으로 두지 않는다(빈이 있으면 스프링 부트 기본 @Async 실행기가 물러난다).
- 푸시가 실패해도 메시지 저장·ACK·방송·이력에는 영향이 없다.
- 접속 상태·발송 제한 Redis 가 실패하면 보내는 쪽으로 간다(알림을 놓치는 것보다 중복이 낫다).
- FCM 이 그 수신자의 토큰을 모두 거절하면(잘못된 토큰·해제된 토큰) 발송 제한을 바로 풀어 다음 메시지에서 남은·새 토큰으로 다시
  보낸다. FCM 에 닿지 못했거나 FCM 쪽 장애(네트워크·시간 초과, `UNAVAILABLE`·`INTERNAL`·`QUOTA_EXCEEDED`)면 풀지 않는다.
  풀면 장애 중 메시지마다 재시도가 쌓여 발송 줄이 막히기 때문이다(노션에 없는 서버 규칙이다. 노션에 반영 필요).
- 서버가 내려갈 때는 줄에 남은 푸시를 최대 5초 기다려 마저 보내고, 그 안에 못 보낸 것은 버린다(보내던 발송은 인터럽트로
  멈추고 남은 토큰 묶음(500개)은 보내지 않는다). 종료 중 들어온 메시지의 푸시는 보내지 않는다.
- FCM 이 `UNREGISTERED`(앱 삭제 등)를 돌려준 토큰은 **보낼 당시 주인과 같을 때만** 지운다. 그사이 다른 계정으로 옮겨 간 토큰은 둔다.
  그 밖의 실패는 토큰을 두고 로그만 남긴다. 토큰·키·본문은 로그에 남기지 않는다.

**토큰 관리**

- 한 회원에 여러 기기 토큰. 토큰이 기본키라 한 기기는 한 계정에만 묶인다(노션 ERD DDL 20번).
- 같은 토큰의 첫 등록이 동시에 오면 한쪽이 기본키 충돌에 걸리고, 한 번 더 시도해 이미 들어간 행을 갱신한다.
- 등록은 회원 행을 탈퇴와 같은 잠금(`SELECT … FOR UPDATE`)으로 잡고 활성인지 본다. 탈퇴 커밋 직후 아직 유효한 액세스 토큰으로
  온 등록은 A003 이고, 등록이 먼저면 탈퇴가 기다렸다가 그 토큰까지 지운다.
- 토큰 최대 4,096자(넘으면 C003)는 노션에 없는 서버 규칙이다. FCM 토큰은 보통 200자 안팎이다.
- 로그아웃(API-007)은 요청의 `pushToken`(선택)을 세션 폐기와 함께 지운다. 앱이 API-096 을 빠뜨리거나 실패해도 로그아웃한 기기에
  미리보기가 가지 않게 하려는 것이다(PR 리뷰 뒤 프론트와 합의). 탈퇴(API-009)는 탈퇴 트랜잭션 안에서 그 회원 토큰을 모두 지운다.

**서비스 계정 키**

- 환경 변수 `FIREBASE_SERVICE_ACCOUNT_BASE64`: 서비스 계정 키 JSON 파일 전체를 Base64 한 줄로(`base64 -w0 키.json`).
  `application.yaml` 의 `yeogirok.push.fcm.service-account-base64`.
- **선택 값**이다. 비어 있으면 시작 때 경고("FCM 서비스 계정 키(FIREBASE_SERVICE_ACCOUNT_BASE64)가 없어 채팅 푸시를 보내지 않습니다.")를
  한 번 남기고 푸시만 보내지 않는다(수신자 조회·Redis 작업도 하지 않는다). 로컬·테스트는 키 없이 뜬다. 값을 잘못 넣으면(Base64 아님·서비스 계정 키 아님) 시작을 멈춘다.
- 서버는 `infra/docker-compose.yml` backend 의 선택 키(`${FIREBASE_SERVICE_ACCOUNT_BASE64:-}`)와 `.env` 로 넣는다(인프라 담당).
  기본값이 있어 `check-backend-env.sh` 점검 대상은 아니다.
- 켜지면 시작 때 "FCM 채팅 푸시를 켰습니다. projectId=…" 를 남긴다.
- SDK 9.11 의 `Message.setToken` 은 deprecated(FID 권장)지만 앱이 FCM 등록 토큰을 쓰므로 그대로 쓴다. FID 로 옮기면 앱·API-095 를 함께 바꾼다.

**운영 수치**(`yeogirok.chat.push`): 접속 기록 수명 90초, 발송 제한 3초, 미리보기 40자, 발송 스레드 2개, 대기 줄 1,000개.
FCM 연결 5초·응답 10초(`yeogirok.push.fcm`).

## 3. 하지 않은 것

- 사진 채팅, 타인 읽음 실시간 방송, 메시지별 안 읽은 인원 수(노션 "별도 결정 항목").
- 메시지 보관 기간 만료 삭제. 방이 유지되는 동안 계속 보관한다.
- 전달 시점마다 받는 사람의 권한을 DB 에서 다시 확인하는 것. 강제 종료 알림과 전송·PING 재확인으로 대신한다.
- 앱 푸시 중 미룬 것: 방별 알림 끄기, 안 읽은 개수 배지, iOS(APNs), 채팅 외 알림(초대·일정).

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

푸시 기기 토큰(`user_push_tokens`)도 배포하면 엔티티 기준으로 만들어진다. 엔티티는 노션 ERD DDL 20번을 따른다(토큰이 기본키).
FK(`ON DELETE CASCADE`)는 Flyway 때 넣고, 시각은 DB 기본값 대신 서비스 시계로 넣는다. ERD DDL:

```sql
CREATE TABLE user_push_tokens (
    token TEXT PRIMARY KEY CHECK (btrim(token) <> ''),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform TEXT NOT NULL CHECK (platform = 'ANDROID'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_user_push_tokens_user ON user_push_tokens (user_id);
```

탈퇴는 회원 행을 지우지 않아(상태만 바꾼다) `ON DELETE CASCADE` 는 쓰이지 않는다. 탈퇴 트랜잭션에서 토큰을 직접 지운다.
