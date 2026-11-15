# 드라이브 토크 (JB_drivetalk)

차 안의 네 좌석이 질문 카드 10장을 두고 A와 B 중 하나를 고르는 밸런스 게임이다. Pleos Connect 차량 화면에서 도는 안드로이드 앱이다.

## 실행 방법

### Pleos Connect 에뮬레이터에서

1. 안드로이드 스튜디오에서 `android/` 폴더를 연다. Gradle 동기화가 끝날 때까지 기다린다.
2. Build Variants에서 `app`을 `standaloneDebug`로 고른다.
3. 기기 목록에서 `Pleos Connect v2.1`을 고르고 실행(▶)을 누른다.

만들어 둔 APK를 바로 넣어도 된다. 에뮬레이터를 켠 뒤 저장소 루트에서 친다.

```sh
adb install android/apk/JB_drivetalk_release.apk
```

### 터미널에서 빌드

```sh
cd android
./gradlew :backend:test                  # 코틀린 백엔드 테스트
./gradlew :app:assembleStandaloneDebug   # app/build/outputs/apk/standalone/debug/
./gradlew :app:assembleClientDebug       # app/build/outputs/apk/client/debug/
```

### 앱 두 가지

- `standalone` 서버 없이 도는 앱. 화면과 코틀린 백엔드(`android/backend`)가 앱 안에 있다. 판은 앱 전용 저장 공간에 남아 앱을 다시 켜도 이어진다.
- `client` 화면 서버에 붙는 앱. 처음에 서버 주소를 묻는다. PC에서 `ruby bin/drive_talk_all`을 켜 두고, 에뮬레이터에서는 `http://10.0.2.2:4567`, 실제 기기에서는 `http://<PC 주소>:4567`을 적는다. 뒤로 가기를 누르면 주소를 바꿀 수 있다.

### PC 브라우저에서 (선택)

저장소 루트에서 친다. 러스트 판 API(8080)를 빌드해 띄우고, Ruby 화면 서버(4567)를 그 API에 붙인다. Ctrl+C로 둘 다 끈다.

```sh
ruby bin/drive_talk_all
# http://127.0.0.1:4567
```

주소 뒤 `?demo=1`은 브라우저 마이크 확인을 건너뛴다. API 계약은 [BACKEND.md](BACKEND.md)에 있다.

## 테스트 환경 및 필요한 설정

| 항목 | 버전 |
| --- | --- |
| OS | Windows 11 |
| 안드로이드 스튜디오 | 2026.2.2 |
| JDK | 17 이상 (안드로이드 스튜디오에 든 JBR을 쓴다) |
| 안드로이드 SDK | Platform 35, Build-Tools 35.0.0, minSdk 29 |
| Gradle / 안드로이드 Gradle 플러그인 / Kotlin | 9.9.1 (wrapper) / 9.4.1 / 2.4.21 |
| Pleos 차량 SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| Ruby / Rust | 3.4 / 1.100 (PC 브라우저로 띄울 때만) |

필요한 설정은 다음과 같다.

- `android/local.properties`에 SDK 경로를 적는다. 안드로이드 스튜디오로 열면 알아서 만든다. 저장소에는 넣지 않는다.
  ```
  sdk.dir=C:/Users/<사용자>/AppData/Local/Android/Sdk
  ```
- 에뮬레이터를 켜려면 SDK Manager → SDK Tools에서 Android Emulator hypervisor driver를 깐다. BIOS에서 가상화(VT-x 또는 AMD-V)가 켜져 있어야 한다.
- Pleos 차량 SDK는 Pleos 저장소(`https://nexus-playground.pleos.ai/repository/maven-releases/`)에서 받는다. `android/settings.gradle.kts`에 적혀 있다.
- 앱은 좌석 착석 정보를 읽으려고 `pleos.car.permission.CAR_SEATS` 권한을 쓴다. Pleos SDK가 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 권한을 더 붙인다. 차량 서비스가 없는 기기에서는 착석 정보 없이 손으로 좌석을 고르는 그대로 돈다.
- 착석 정보를 쓰는 것은 `standalone`뿐이다. 매니페스트를 두 앱이 같이 써서 `client`(`drive-talk-client.apk`)에도 `CAR_SEATS`와 `VEHICLE_BINDING` 권한이 들어가지만, `client`는 착석 정보를 읽지 않는다.

확인한 것과 아직 확인하지 못한 것은 다음과 같다.

- 확인함: 코틀린 백엔드 테스트 16개(`./gradlew :backend:test`) 통과, 두 APK 빌드.
- 아직 확인 전: Pleos Connect 에뮬레이터나 차량에서 착석 센서가 실제로 좌석 참여를 바꾸는지.

## 개발에 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격

| 항목 | 값 |
| --- | --- |
| AVD 이름 | Pleos Connect v2.1 |
| 시스템 이미지 | `pleos_car_emulator` (42dot), 빌드 `RELEASE.release_connect_v2.1.0` |
| 안드로이드 | 14 (API 34), x86_64, Automotive |
| 기기 정의 | `automotive_1024p_landscape` |
| 해상도 | 1024 × 768, 가로 |
| 밀도 | 160 dpi (mdpi) |

## APK 파일 위치

| 앱 | 파일 |
| --- | --- |
| 서버 없이 도는 앱 (standalone) | [`android/apk/JB_drivetalk_release.apk`](android/apk/JB_drivetalk_release.apk) |
| 화면 서버에 붙는 앱 (client) | [`android/apk/drive-talk-client.apk`](android/apk/drive-talk-client.apk) |

GitHub에서는 <https://github.com/URI13271/hyundai/tree/connect-api/android/apk> 에서 받는다.

두 APK 모두 디버그 열쇠로 서명했다. 소스를 고치면 다시 빌드해 이 폴더의 파일을 바꾼다.
