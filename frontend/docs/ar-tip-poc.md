# AR 팁 PoC

## 2026-11-06 · 카메라 실제 AR 3D 거리 기준 확정·Host/Resolve 성공 확인

사용자 확인으로 카메라의 5m는 복원된 앵커 원점과 현재 AR 카메라 사이의 3D 거리다. GPS 거리로 Resolve 시작·카메라 핀·선택 목록을 제한하지 않는다. 서버에서 받은 주변 후보의 공간 복원을 시도한 뒤, 같은 AR 세션의 XYZ 거리로 기본/주변 핀 모두 5m 이하만 그린다. 위아래 높이 차이도 포함하며 복원 전에는 핀을 그리지 않는다. 미니맵은 계속 GPS 50m·가까운 순 최대 10개, GPS 10m 이동 재조회다. 상세·반응은 기존 API에 매번 새 GPS 증거를 보내 서버 현장 조건을 검증한다. 공개 조회 권한을 AR 거리로 대체하지 않는다.

사용자 재등록 후 기기 로그에서 17:20:02 `HOST_SUCCESS durationMs=7230`, 17:21:14 `RESOLVE_SUCCESS durationMs=9205`와 `VISIBILITY reason=VISIBLE rendered=1`을 확인했다. 이후 `GPS_OUTSIDE_5M`으로 핀이 숨겨지고, 10m 재조회 전달 중 메인 프로세스에서 `BadParcelableException`이 발생했다. GPS 표시 제한을 실제 AR 거리로 교체하고, 프로세스 간 ResultReceiver를 Parcel로 framework 프록시로 바꿔 익명 클래스 이름을 전달하지 않도록 수정했다.

GPS 목록 갱신 시 동일 tipId·Cloud Anchor ID의 복원된 앵커와 진행 중 작업을 유지하고 제거/교체된 항목만 정리한다. 같은 앵커를 매번 버려 다시 복원하는 현상을 방지한다. 거리 검사에는 수평 3m+수직 4m=5m, 수직 5m 경계·초과, 잘못된 XYZ 입력을 추가했다. Parcelable 부트 클래스 로더 복원/콜백 전달은 `native/ar/tests/ArResultReceiverCheck.java`와 `scripts/check-ar-ipc.ps1`로 검사한다. `:app:assembleDebug` 후 `./scripts/check-ar-ipc.ps1 -Run -Device <serial>`로 실행하며 Android SDK·JDK·기존 debug keystore만 사용한다. 실제 기기의 Android Instrumentation을 실행하므로 앱 프로세스가 재시작된다. 새 외부 테스트 의존성은 없다.

검증: 확장한 Kotlin 3D 거리 검사·API/AR 회귀·타입·토큰 검사(199개 파일)·APK 빌드 통과. Galaxy Note10+의 실제 Android 검사에서 `AR ResultReceiver boot-loader Bundle round trip passed`, `INSTRUMENTATION_CODE: -1`을 확인했다. 새 APK 설치 후 AR 화면 진입을 확인했다. 앞서 확보한 Host/Resolve 성공은 GPS 필터 버전의 로그이며, 새 AR 3D 기준으로 실제 5m 경계를 이동하는 검증과 원래 지점과의 정렬 정확도는 별도다. 전체 린트는 기존 자동 생성 파일 끝줄 오류 1건·index.ts 경고 3건이며 규칙을 변경하지 않았다. Pencil 미대조.

최종 빌드 추가 실기기 검증: 17:36:36 `HOST_SUCCESS durationMs=5934`, 17:37:43 `RESOLVE_SUCCESS durationMs=48137`, `VISIBLE rendered=1`과 실제 AR 거리 약 1.77m를 확인했다. 복원 진행 중 갱신 4회·복원 후 갱신 2회가 `PUBLIC_TIPS_REFRESH_SUCCEEDED`로 왕복했고 메인/AR 프로세스가 유지됐다. 복원 후 갱신에도 추가 Resolve 시작 없이 약 1.73m 핀이 다시 표시됐다. GPS 유효 측정이 없을 때도 복원된 AR 거리로 IN_RANGE가 나온 것을 확인했다. 원래 바닥 지점과 정확히 일치하는지는 사용자 육안 확인 대기이며 실제 5m 경계 이동 시험은 별도다.

현장 확인 완료: 사용자가 최종 빌드의 핀을 보고 “원래 지점에 보여요”라고 답했다(2026-11-06). 이번 8층 현장에서는 실제 Keyless Host/Resolve·원래 지점 표시·갱신 왕복 성공을 확인했다. 이는 사용자 육안 확인이며 센티미터 단위 정렬 오차 측정이나 모든 환경의 복원 보장을 뜻하지 않는다. 실제 5m 경계 이동은 미실행이며 3D 거리 경계는 실행 검사로 확인했다.

## 2026-11-06 · 8층 현장 미표시 조사·AR 프로세스 충돌 수정

추가 실기기 로그에서 16:58:37 `HOST_SUCCESS durationMs=6140`을 확인했다. 이후 `RESOLVE_STARTED`는 있었지만 성공 로그는 확인되지 않았다. 복원 중 `CAMERA_INSUFFICIENT_FEATURES` 상태가 있었고, 여러 AR 프로세스에서 `GeoArActivity.refreshPublicTips` → `MainApplication.reactHost` → `SoLoader.init() not yet called`로 종료된 스택을 확인했다. 앞선 Host 실패 기록과 시간대를 구분하며 실제 공간 복원 완료로 보고하지 않는다.

10m 갱신 추가 시 별도 `:geospatial` 프로세스에서 메인 React 런타임을 참조한 오류였다. 활동의 정적 참조와 React 접근을 제거하고 Android `ResultReceiver`/Binder로 위치 갱신 요청·결과를 양방향 전달한다. React 이벤트는 메인 프로세스의 기존 모듈에서만 발행하며 이전 화면의 요청은 기존 식별자로 무시한다. 격리 AR 화면에 React 의존성을 추가하지 못하도록 기존 AR 검사에 경계 검사를 추가했다.

GPS 유효성·5m 제외 상태를 좌표·앵커 ID 없이 구분해 로그에 남기고, GPS가 5m 밖으로 판정하면 그 이유를 화면에 표시한다. 수정 APK 설치 후 같은 AR 프로세스의 실행을 확인했고 관찰 구간에 기존 충돌은 재현되지 않았다. 실제 10m 이동 갱신 왕복과 새 팁의 Resolve 성공은 추가 현장 확인이 필요하다. 현재 실기기의 보고된 GPS 정확도는 약 6~15m였으며 실제 오차나 팁과의 거리로 단정하지 않는다. 카메라 표시 기준을 실제 AR 3D 거리로 바꿀지는 사용자 확인 대기다.

개발용 팁 API 목은 메모리 저장이므로 앱 프로세스 재시작·재설치 시 등록 목록이 초기화된다. 실제 Google 공간 저장과 목 API 목록의 수명은 다르다. 재설치 전에 이 제약을 안내했고 재등록 후 확인을 요청했다. Kotlin 컴파일·APK 빌드·타입·API/AR 회귀·변경 파일 린트·토큰 검사 통과. 전체 린트는 기존 자동 생성 파일 끝줄 오류 1건·index.ts 경고 3건이다. 새 모듈·config plugin 변경은 없다. Pencil 미대조.

## 2026-11-06 · 서버 계약과 화면 연결

추가 요구사항(대화 2026-11-06): **카메라에는 현재 GPS에서 5m 이내 팁만 표시하고, 우측 상단 미니맵에는 50m 이내 팁의 위치를 표시한다.** 기존 50m 방향 핀의 카메라 표시는 이 요구사항으로 대체한다. 주변 조회는 기존 API-047의 50m·가까운 순 최대 10개를 유지한다. 마지막 성공 조회 위치에서 현재 GPS가 직선거리 10m 이상 이동하면 새 위치 증거로 POST 조회하고, 카메라를 재시작하지 않고 지도 마커·앵커 목록을 교체한다. GPS는 기존 전경 구독을 재사용하며 시각·정확도가 유효한 측정만 사용한다.

조회 사이에도 현재 GPS로 5m/50m 표시 범위를 검사한다. 실패 시 기존 목록의 유효 범위만 표시하고 5초 후 재시도하며, 성공한 조회에서만 기준 위치를 갱신한다. 같은 AR 화면의 갱신 요청은 한 번씩 처리하고 화면 이탈·앱 백그라운드 시 위치 구독을 해제하며 이전 요청 결과를 적용하지 않는다. 상세·반응은 계속 API 호출마다 현재 위치를 검증한다. 거리 경계는 `native/ar/tests/PublicTipDistanceCheck.kt`, 갱신 전달·이전 세션 무시·화면 종료 후 결과 무시는 `scripts/check-tip-api.cjs`에서 확인한다.

검증: 거리 경계 Kotlin 실행 검사·팁 API/갱신 이벤트 회귀·기존 AR 회귀·TypeScript·변경 파일 린트·토큰 검사·Android 개발 APK 빌드 통과. Galaxy Note10+에 설치해 카메라·GPS 미니맵·주변 팁 0개 안내를 확인했다. 팁이 있는 현장의 5m 경계 표시와 실제 10m 이동 재조회는 실기기 미확인이다. 전체 ESLint는 기존 미추적 `expo-env.d.ts` 끝줄 오류 1건과 `index.ts` 경고 3건이 남았다. 새 의존성·config plugin 변경은 없다. Pencil 미대조.

대화에서 제공된 팁 API 10개와 업로드 API 2개의 요청·응답 모델, Query/Mutation 훅, MSW 핸들러를 추가했다. 상세는 `docs/api-guide.md`의 AR 팁 계약 반영 기록을 따른다. `pnpm tip:check`로 사진 없는 게시·줄바꿈 정규화·50m 탐색/5m 상호작용·반응 최종 상태·수정 작업/재시도·버전 충돌·삭제·S3 PUT/READY 흐름을 검사한다.

현재 AR 화면은 서버 API 계층을 사용한다. 개발 빌드에서는 MSW가 응답하며 실제 배포 서버의 구현 완료를 뜻하지 않는다. ARCore Keyless Host/Resolve는 실제 네이티브 SDK를 사용하므로 MSW 통과와 구분해 실기기에서 확인한다. 이후 날짜의 내용은 이전 로컬 PoC 구현 이력이다.

하단 AR 팁 탭은 이전 등록 작업을 자동 복원하지 않고 현재 위치로 주변 팁을 조회해 카메라를 바로 연다. 등록 작업의 게시 완료(`PUBLISHED` + `operationStatus=SUCCEEDED`, 또는 재시도 API의 완료 응답)가 확인되면 카메라로 자동 복귀한다. 작성 취소·등록 상태의 “나중에”도 카메라로 돌아가며, 남은 작업은 내 팁에서 확인한다. Galaxy Note10+에서 하단 탭 → AR 카메라 직접 진입을 확인했다. 등록 완료 후 자동 복귀의 실제 Host 포함 전체 흐름은 아래 Host 실패로 미확인이다.

실기기 확인(2026-11-06, Galaxy Note10+): 개발 APK 설치·AR 진입·현재 GPS 표시·표면 배치·`HOST_STARTED auth=KEYLESS ttlDays=365`를 확인했다. 현재 검증 세션에서는 `ERROR_HOSTING_DATASET_PROCESSING_FAILED`가 반복되어 Host 성공을 확인하지 못했고, 새 공개 팁의 Resolve 단계는 미검증이다. 과거 프로세스의 Resolve 성공 로그를 이번 결과로 사용하지 않았다. 데이터 처리 실패를 인증·네트워크 오류로 단정하지 않도록 특징·다시 배치 안내를 분리했다. 실제 연동 완료로 보고하지 않는다.

같은 기기에서 내 팁 API 목록·상세를 열고 사진 없는 팁의 종류를 수정했다. MSW 기준으로 `사진 · 미첨부 (정상)`·작업 진행 후 `팁 수정을 완료했어요`·각 필수 단계 완료를 확인했다. 실제 백엔드 또는 S3 운영 환경 검증은 아니다. Pencil 노드는 없어서 미대조이며 기존 DESIGN.md 토큰과 UI 패턴을 사용했다.

## 2026-11-05 · 서비스 화면·AR 흐름 전환 (S15P31A109-122)

마커 선택 상세는 `features/tip/components/tip-detail-sheet.tsx`에서 공통 `components/ui/bottom-sheet.tsx`의 `BottomSheet`로 표시한다. 핸들 드래그·확장·스크림·뒤로가기는 공통 구현을 그대로 사용한다. 종류·작성 시각·거리·본문·사진 확대·기기 내 반응 조회/변경을 기존 네이티브 저장소 브리지로 연결한다. AR 전용 프로세스와 React Native를 분리한 구조를 유지하기 위해 외부에 공개되지 않는 투명 상세 호스트를 사용하며, 상세를 닫을 때는 기존 AR 세션의 앵커를 유지한다. 상세가 열려 있는 동안 카메라는 일시 정지한다. Kotlin에서 별도 Dialog 시트를 그리던 구현은 제거했다.

사용자가 이번 범위를 **화면·AR 흐름 우선 구현**으로 정했다. 아래의 기존 PoC 설명은 이전 동작의 기록이다. 현재 `/ar-tip`은 다음 흐름을 사용한다.

1. 하단 AR 팁 탭 → Android 네이티브 카메라 탐색. 최초 이용 시 카메라·위치 용도와 Google 공간 정보 전송을 안내하고, 이후 OS 권한을 요청한다. React Native의 별도 PoC 시작 화면·로컬 본문 목록은 표시하지 않는다.
2. 카메라 위 미니맵·저장한 팁 마커 → 마커 선택 시 종류 배지·본문·사진·기기 내 반응 상세. 서버 주변 팁·작성자·반응 집계는 연결하지 않았다. 새 팁은 '임시 저장'으로 구분한다.
3. + → 별도 배치 화면. 하단 탭·미니맵을 숨기고 조준점·안내·'여기에 놓기'를 표시한다. +만으로 공간을 저장하지 않으며, '여기에 놓기'를 눌렀을 때의 유효한 중앙 표면에 고정한다. 추적·표면 감지가 유효하지 않으면 확정 버튼이 비활성이다.
4. 공간 저장 성공 → 작성 화면. 종류(포토·맛집·카페·꿀팁·TMI) 1개와 본문은 필수이며 본문은 최대 120자다. 빈 본문이나 공백만 입력한 경우 저장할 수 없다. 사진 1장은 선택이며 기존 시스템 선택기/촬영·JPEG 재인코딩으로 위치 EXIF를 제거한다. '다시 놓기' 시 입력을 유지한다.
5. '팁 임시 저장' → 이 기기에만 저장 후 AR 탐색으로 복귀한다. 공간 배치만 마친 빈 마커는 자동으로 저장하지 않는다. 저장 실패 시 사진·본문·종류·공간 참조를 유지하고 같은 입력으로 재시도한다. 공간 저장 실패는 배치 화면에서 재시도·다시 놓기를 제공한다.

공개 게시·사진 업로드·다른 이용자의 현장 팁·서버 반응·서버 게시 단계별 재시도는 API 계약 확인 후 연결할 범위다. 이번 변경은 공개 게시 성공을 표시하지 않는다. 기존 PoC 팁은 이전의 본문·사진·TTL을 유지한 채 읽는다.

- 디자인: 사용자 지정 Figma `20:4436`의 W17 `20:4437`, W17-A `20:4799`, W18 `20:4467`, W19 `20:4499`, W19-1 `20:4545` 컨텍스트·스크린샷 참고. 원본 로컬 에셋과 기존 토큰 사용. Pencil 대응 노드 없음으로 미대조.
- 확인: `node scripts/check-ar-local-save.cjs`는 기존 호환성에 더해 배치의 자동 저장 생략, 종류·본문 필수, 사진 선택, 빈 본문·공백 및 120자 경계, 임시 저장 재조회, 저장 실패·재시도를 검증한다. 이전에 본문 없이 저장한 팁도 계속 읽을 수 있다.
- 네이티브 소스 변경으로 Android 개발 빌드 재생성·재설치가 필요하다. 실기기 카메라·공간 복원·화면 시각 비교는 미확인이다.

2026-11-02 기준. Android 개발 빌드 전용이며 인수인계 저장소의 `poc/arcore-basic` / `9b1cf83` Kotlin 코드를 통합했다. 출처는 `native/ar/SOURCE.md`에 있다.

## 사용 흐름

1. AR 팁 탭 → 권한 용도 안내 → AR 카메라 탐색.
2. 우측 상단 WebView 미니맵에 현재 위치·카메라 방향·기기에 저장한 팁의 탐색 좌표를 표시한다.
3. 화면 중앙 조준점으로 바닥/벽을 비춘다. 0.2~5m 안의 평면이 인식되고 카메라가 추적 중이면 +가 활성화된다. +를 누른 순간의 중앙 표면에 핀을 고정하고 즉시 Cloud 저장을 요청한다. GOOD 품질 1초 유지나 별도 위치 저장 버튼은 요구하지 않는다.
4. Cloud Anchor Host 성공 후 임시 마커를 기기에 자동 저장하고 RN 화면으로 돌아온다. 실패하면 같은 위치에서 ‘저장 다시 시도’하거나 ‘다시 배치’할 수 있다. 실제 성공 응답 전에는 저장 완료로 처리하지 않는다.
5. ‘AR로 다시 확인’ → 새 AR 세션에서 저장한 앵커를 복원해 같은 위치인지 확인한다. 본문·지도 위치 입력은 복원 테스트에 필요하지 않다. 앱을 종료·재실행해도 임시 마커가 목록에 남으며 목록의 ‘AR로 보기’ 또는 ‘AR로 탐색하기’로 다시 복원한다.
6. 본문 입력·선택적으로 사진 촬영/첨부 → 이 기기에 팁 등록. 배치 시 실제 방문 위치를 확보하지 못한 경우 지도에서 탐색 위치를 선택한다. 같은 앵커의 임시 마커를 갱신하므로 중복 항목을 만들지 않는다.
7. 목록의 AR로 보기 → 선택한 Cloud Anchor Resolve → 복원한 핀을 누르면 본문 상세.

Cloud Anchor는 공간 배치 정보다. 실제 방문 위치는 DEVICE_GPS와 측정 정확도·측정 시각을 함께 보관한다. 사용자가 지도에서 선택한 탐색 위치는 이 측정값과 구분하며 팁 자체의 정밀한 GPS 좌표로 취급하지 않는다. 선택한 팁과 나머지 유효한 팁을 함께 복원한다. ARCore의 동시 작업/해결된 앵커 한도에 맞춰 기본 팁 1개와 추가 최대 39개를 처리하며, 로컬 저장 한도 100개 전체의 동시 표시를 보장하지 않는다.

임시 마커의 앵커 ID·배치 정보·등록 시각은 기존 Android SharedPreferences에 동기적으로 저장한다. 본문을 입력하지 않았다면 ‘위치 복원 테스트용 임시 마커’로 표시한다. 단순 세션 XYZ만으로 다음 실행의 위치를 재현하지 않으며, 재실행 시 저장한 Cloud Anchor ID를 Resolve한다. 로컬 데이터는 앱 데이터 삭제·앱 제거 시 사라지고, Cloud 복원은 네트워크 및 기록별 예상 수명에 제한된다. 새 앵커는 Keyless 인증으로 365일 TTL을 요청하며 기존 1일 앵커의 만료일은 유지한다. 저장한 위치의 실제 복원 정밀도는 현장에서 확인해야 한다.

`node scripts/check-ar-local-save.cjs`는 네이티브 브리지 목으로 자동 저장·새 어댑터에서 재조회/재열기·기존 본문 보호·취소·만료·저장 실패/재시도, 사진 선택/촬영/취소, 파일 URI, 본문 필수/2,000자, 위치 검증을 검사한다. Android 실제 디스크 영속성과 AR 복원 정밀도 검증은 실기기에서 별도로 한다.

탐색 화면과 별도 배치 진입 모두 중앙 조준점과 +를 사용한다. +는 AR 세션을 다시 열지 않고 누를 당시 표시된 Hit를 앵커로 고정한다. 이후 카메라 이동·화면 터치·드래그로 위치가 바뀌지 않으며, 위치를 바꾸려면 ‘다시 배치’를 누른다. 추적 중단·표면 미감지 시 +가 비활성화되고, 500ms를 넘긴 감지 결과와 화면 이탈 전의 요청은 사용하지 않는다.

제품용 즉시 저장은 `ON_PLACEMENT` 정책으로 기록한다. 관찰한 특징 품질은 그대로 저장하고 측정하지 못했다면 `NOT_MEASURED`로 남긴다. 다각도 인식을 앱의 선행 조건으로 강제하지 않으므로 특징이 부족한 환경에서는 저장·향후 복원이 실패하거나 정확도가 낮아질 수 있다. 원래 실험 화면의 GOOD/진단 품질 정책과 기존 기록 호환성은 유지한다. ARCore 저장 API의 비동기 처리와 품질에 따른 복원 차이는 [공식 Session 문서](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#hostCloudAnchorAsync(com.google.ar.core.Anchor,int,java.util.function.BiConsumer))를 참고한다.

`native/ar/tests/CenterPlacementCheck.kt`는 감지 결과 유효 시간, 배치 거리 경계, 즉시 저장 허용·기존 품질 정책·기록 검증을 검사한다.

```powershell
kotlinc native/ar/android/java/com/yeogirokarpoc/geo/ArExperimentPolicy.kt native/ar/tests/CenterPlacementCheck.kt -include-runtime -d .expo/ar-center-check.jar
java -jar .expo/ar-center-check.jar
```

## 마커 원근감

2026-11-05: 종류 태그도 화면 위 Canvas 좌표 대신 마커와 동일한 앵커·방향·모델/뷰/투영 행렬로 그린다. 따라서 휴대폰 회전·이동·거리 변화 시 태그와 마커의 상대 위치가 유지된다. 기본 팁과 추가로 복원한 주변 팁 모두 같은 렌더러를 사용한다. 태그 글자·여백·배경 배율은 `TIP_TAG_SCALE=2.5`이며, 공간상 높이는 15cm, 마커 위 간격은 2.5cm다. 선택 시에는 마커와 함께 1.4배 확대된다. `PinPerspectiveCheck.kt`에 태그 크기·카메라 회전·거리별 투영 회귀 검증을 포함했다.

마커는 카메라를 향하는 높이 48cm의 핀으로 표시한다(초기 16cm에서 가로·세로 각각 3배 확대). 핀의 아래 끝은 표면 앵커에 고정하고, ARCore 카메라의 투영 행렬과 전방 깊이로 화면상의 너비·높이를 계산한다. 같은 카메라 조건에서 깊이가 두 배이면 화면 크기는 절반이다. 작성·복원 화면에 같은 계산을 적용하며, 터치 영역도 표시 크기를 따르되 멀리서는 최소 48dp를 확보한다.

`native/ar/tests/PinPerspectiveCheck.kt`의 독립 실행 검사는 거리·시야각·화면 방향별 크기, 뒤쪽/클리핑/잘못된 투영값 숨김, 크기에 따른 터치·드래그 판정을 확인한다. Kotlin CLI가 있다면 frontend 폴더에서 실행한다.

```powershell
kotlinc native/ar/android/java/com/yeogirokarpoc/geo/PinProjection.kt native/ar/android/java/com/yeogirokarpoc/geo/PinTapPolicy.kt native/ar/tests/PinPerspectiveCheck.kt -include-runtime -d .expo/ar-perspective-check.jar
java -jar .expo/ar-perspective-check.jar
```

## 벽 뒤 마커 가림

Cloud AR 화면은 지원 기기에서 ARCore Depth 이미지로 표면 깊이와 마커 앵커의 카메라 전방 깊이를 비교한다. 2026-11-05부터 실제 3D 마커의 투영 경계 안에서 본체 상·중부 9곳을 분산 샘플링한다. 발끝 주변의 바닥 깊이만으로 전체 마커를 숨기지 않는다. 샘플 과반이 마커보다 앞에 있는 상태가 400ms 지속될 때만 마커·태그·터치 대상을 함께 숨긴다. 가림이 풀리거나 깊이 데이터가 없으면 바로 표시하며, 250ms를 넘는 측정 공백·화면 이탈은 이전 연속 판정을 이어 쓰지 않는다. 기본 팁과 주변 팁별로 독립 판정한다. 깊이 이미지는 프레임당 한 번 얻고 사용 후 바로 닫는다.

벽/바닥에 붙인 마커가 깊이 오차로 사라지지 않도록 15cm 또는 거리의 5% 중 큰 값을 여유로 둔다. 유효한 깊이 범위는 0.2~8m이며, 미지원·아직 깊이 없음·범위 밖은 기존 표시로 돌아간다. 카메라 이동은 깊이 추정에 도움이 되지만 등록 전 별도 스캔 단계는 추가하지 않는다. 반사/투명 표면이나 어두운 환경에서는 가림이 불완전할 수 있고, 마커의 일부만 가리는 픽셀 단위 렌더링은 제공하지 않는다.

근거: [ARCore Depth 개발 가이드](https://developers.google.com/ar/develop/java/depth/developer-guide). `native/ar/tests/DepthOcclusionCheck.kt`는 앞/뒤 표면, 같은 표면 오차, 깊이 누락·경계, unsigned 16비트·행/픽셀 stride를 검사한다. 실제 벽 뒤 표시 여부는 현장 확인이 필요하다.

마커 표시에는 바닥 평면을 다시 찾는 조건을 쓰지 않는다. 카메라와 저장 앵커가 추적 중이어야 하며, 발끝이 화면 밖이어도 본체 투영 경계가 화면에 걸치면 표시한다. 종료된 주 앵커는 저장한 Cloud Anchor 참조로 다시 복원한다. 추적이 PAUSED인 동안에는 위치를 임의로 대신 그리지 않고 주변 벽·사물을 비추도록 안내한다.

미표시 진단은 `YeogirokCloud`의 `VISIBILITY` 로그와 상단 안내에서 구분한다. 로그의 `CAMERA_INSUFFICIENT_FEATURES`·`CAMERA_INSUFFICIENT_LIGHT`는 카메라 추적 문제, `ANCHOR_PAUSED`·`RESTORING`은 공간 재인식/복원, `OCCLUDED`는 가림 판정, `OUTSIDE_VIEW`는 본체가 화면 밖인 상태다. 개별 id·좌표·본문은 기록하지 않는다. `PinPerspectiveCheck.kt`에 발끝이 화면 밖인 경계 사례, `DepthOcclusionCheck.kt`에 본체 샘플·일시적 깊이 노이즈·가림 해제·측정 공백·앵커별 독립 판정 검증을 추가했다. Depth 검증을 별도로 컴파일할 때는 `PinProjection.kt`도 함께 포함한다.

## 환경 설정

`.env.local`에 `EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY`를 설정한다. 키 값을 커밋하거나 채팅에 붙이지 않는다. ARCore에는 API 키를 사용하지 않는다.

- 지도: 카카오 JavaScript 키의 JavaScript SDK 도메인에 `https://localhost` 등록. Kakao 네이티브 지도 SDK는 사용하지 않는다.
- AR: Google Cloud에서 ARCore API를 활성화하고 앱 패키지 `com.ssafya109.yeogirok`와 해당 APK 서명 SHA-1의 Android OAuth 클라이언트를 등록한다. 앱은 Keyless 인증을 사용한다. 빌드의 `arcoreAuthMode=KEYLESS`는 앱 설정을 나타내며 실제 OAuth 승인 성공을 뜻하지 않는다.
- 기존 config plugin이 prebuild 때 ARCore API 키 metadata와 생성된 `local.properties`의 해당 키를 제거하고 `play-services-auth:22.0.0`과 인증용 ProGuard 규칙을 포함한다. 사용자 `.env.local`은 자동으로 수정하지 않으며, 남아 있는 `ARCORE_API_KEY`는 전달하지 않는다. 지도 키 전달은 유지한다.
- **이전 API 키 빌드에서 전환하려면 prebuild와 APK 재빌드·재설치가 필요하다. Metro 새로고침만으로 네이티브 인증 방식은 바뀌지 않는다.**

```powershell
pnpm expo prebuild --platform android --no-install
pnpm android
```

생성된 `android/`를 직접 편집하지 않는다. `plugins/with-ar-tip.cjs`가 권한·Activity·Gradle·RN 패키지·별도 AR 프로세스 초기화 제외를 유지한다.

Keyless 설정은 [Android ARCore 인증 가이드](https://developers.google.com/ar/develop/authorization?platform=android)를 따른다. 인증 라이브러리 버전은 [Google Play services 공식 목록](https://developers.google.com/android/guides/setup)에서 확인했다. Google Cloud의 OAuth 등록은 이 코드만으로 만들거나 확인할 수 없으며, 실제 Host/Resolve 성공 응답으로 확인해야 한다. 인증 실패 응답은 네트워크 실패와 구분해 Google Cloud 앱 등록 확인을 안내한다.

새 호스팅과 `CloudRecord` 생성은 같은 `ArExperimentPolicy.CLOUD_TTL_DAYS=365`를 사용한다. Kotlin·TypeScript는 1~365일 정수 TTL과 `hostedAt + TTL`에 일치하는 예상 만료일만 허용한다. 기존 1일 기록도 수명·본문·사진을 바꾸지 않고 읽는다. 이미 저장된 앵커는 새 요청 TTL만 바꿔 자동 연장되지 않는다. 서버에서 만료일을 조회하는 기능은 없으므로 로컬 시각으로 계산한 보수적인 예상값이다.

Windows에서는 같은 플러그인이 CMake 작업 경로를 `.expo/cxx/`로 줄이고 오브젝트 경로 길이를 240자로 제한한다. pnpm의 긴 네이티브 경로가 Ninja 빌드를 막는 문제를 피하기 위한 설정이다.

Android SDK에 CMake `3.31.6`이 설치되어 있으면 동봉된 Ninja `1.12.1`을 사용한다. CMake `3.22.1`의 Ninja `1.10.2`에서는 긴 pnpm 소스 경로를 처리하지 못하는 문제가 확인됐다.

## 무선 디버깅

페어링 후 기본 무선 디버깅 화면의 연결 포트로 접속한다. 페어링 포트와 연결 포트는 다르다.

```powershell
adb connect <기기 IP>:<연결 포트>
adb reverse tcp:8081 tcp:8081
node --dns-result-order=ipv4first node_modules/expo/bin/cli start --dev-client --localhost --port 8081
```

마지막 명령은 Metro가 IPv6 `::1`에만 바인딩되는 환경에서도 ADB의 IPv4 루프백 경로를 사용할 수 있게 한다.

## 범위와 검증

- 기기 내 본문·사진·반응 저장과 Cloud Anchor 연결을 검증하는 PoC다. 서버 공개 등록·다른 사용자 공유·사진 업로드·iOS는 별도 범위다.
- 새 앵커 TTL은 365일 요청, 기존 기록은 저장 당시 수명 유지다. 본문 2,000자, 로컬 100개는 PoC 제약이다. 서비스 정책을 확정한 것이 아니다.
- 팁 작성은 중앙 표면에 고정하는 배치를 사용한다. 진단용 실험 화면의 터치/드래그 배치와 추적/권한 안내·실패/타임아웃·카메라/위치 구독 해제는 유지한다.
- Figma W17 `2:4439`, W17-A `4:2630`, W18 `2:4493`, W19 `2:4525`, W19-1 `2:4561`을 대조했다. 기존 UI와 시맨틱 토큰 사용. Pencil 화면 시안은 없어 미대조다.
- 실제 Cloud Host/Resolve 결과와 기기 간 복원 정밀도는 실기기 로그로 별도 확인해야 한다.

## Figma 화면과 PoC 정책

[디자인 원본](https://www.figma.com/design/gzYo8vG6h2LrKGOa22UCFP/Untitled?node-id=2-4438)의 카메라 탐색·선택 마커/상세 시트·중앙 배치·작성·실패 화면을 구현했다. 지도와 카메라는 실제 데이터를 사용한다. 핀·조준점·아이콘은 원본 에셋이며, 색상은 프로젝트 시맨틱 토큰에서 생성한다. 하단 탭 순서는 기존 앱 내비게이션을 따른다.

- 사용자 선택으로 사진 선택·본문 필수·최대 2,000자를 유지한다. 사진은 한 장이며 시스템 카메라/사진 선택기를 사용한다. 별도 자르기/필터 편집기는 이번 PoC에 없다.
- 사진은 기기 내부 JPEG로 재인코딩해 EXIF 위치 정보를 제외한다. 촬영 임시 파일은 좁은 FileProvider cache 경로로만 공유하며, 취소/가져오기 후 정리한다.
- 좋아요·싫어요는 이번 PoC에서만 기기당 한 종류 선택, 같은 버튼으로 취소, 다른 반응으로 변경, 내 팁 테스트를 허용한다. 별도 로컬 저장소를 사용하며 다른 사용자의 집계 숫자를 만들어 표시하지 않는다.
- 실제 공간 저장 성공 후 임시 마커를 먼저 보존한다. 본문 저장 실패 시 내용/사진/공간 참조를 유지하고 재시도한다. 서버의 등록 중/공개/실패/삭제 상태 API를 구현한 것은 아니다.
- Noto Sans KR을 RN과 네이티브에 적용한다. NativeWind의 rem 기준을 16으로 지정해 기존 디자인 간격과 맞춘다.
- 공간 저장은 + 직후 비동기로 시작한다. 앱에서 다각도 품질 대기를 강제하지 않지만 ARCore 처리 시간과 복원 품질의 제약은 남는다.

신고·내 팁 수정/삭제/재배치 관리, 서비스 반응 정책, 현장 조회 반경/오차/실내외 기준, 서버 공개 트랜잭션과 다른 기기의 공간 복원은 미구현/미정이다. 이 PoC의 로컬 저장 결과를 전체 공개 성공으로 표시하지 않는다.
