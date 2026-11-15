# 드라이브 토크

차 안 밸런스 게임 화면. Ruby 표준 라이브러리만 쓴다. 덱·음성인식·기록의 주인은 [BACKEND.md](BACKEND.md).

실행은 이 저장소를 받은 폴더에서 한다. 다른 폴더에서 `ruby bin/drive_talk`을 치면 Ruby가 스크립트를 열지 못하고 `No such file or directory -- bin/drive_talk (LoadError)`가 난다.

```sh
git clone https://github.com/URI13271/hyundai.git
cd hyundai
ruby bin/drive_talk
# http://127.0.0.1:4567
```

이미 받아 둔 폴더면 그 폴더에서 `git pull`을 한 뒤 같은 `ruby` 명령을 쓴다. 폴더를 옮기지 않으려면 파일 경로를 직접 준다.

```sh
ruby /path/to/hyundai/bin/drive_talk
```

`PORT`로 포트를 바꾼다. 주소 뒤 `?demo=1`은 브라우저 마이크 확인을 건너뛴다.

화면 위 라이트·다크·가로·세로는 이 브라우저에만 남는다.

## 차량 화면에 앱으로

화면은 설치할 수 있는 웹앱이다. 크롬 계열 브라우저로 주소를 열고 주소창의 설치 버튼을 누르면 아이콘이 생기고, 그 아이콘으로 열면 주소창과 틀 없이 화면을 다 쓴다. 설정 줄은 숨고 테마는 차량의 주야간을 따른다.

버튼을 눌러도 페이지를 새로 열지 않는다. 다음 화면만 받아서 갈아 끼운다. 화면 서버가 잠깐 꺼지면 “화면 서버에 연결할 수 없어요”를 보여 주고 5초마다 다시 붙는다.

설치와 서비스 워커는 `127.0.0.1`이나 HTTPS 주소에서만 된다. 헤드유닛이 다른 기기의 서버를 `http://192.168…`로 열면 화면은 뜨지만 설치는 안 된다.

## 안드로이드 앱

`android/`는 안드로이드 스튜디오 프로젝트다. 안드로이드 스튜디오에서 이 폴더를 열고 실행 버튼을 누른다. 빌드 변형(Build Variants)에서 `standaloneDebug`와 `clientDebug`를 고른다.

- `standalone` 서버 없이 도는 앱. 화면과 코틀린 백엔드가 앱 안에 있다. 권한은 없다.
- `client` 화면 서버에 붙는 앱. 처음에 서버 주소를 묻는다. `ruby bin/drive_talk_all`이 떠 있는 기기의 주소를 `http://192.168.0.10:4567`처럼 적는다. 뒤로 가기를 누르면 주소를 바꿀 수 있다.

터미널에서는 `android` 폴더에서 한다. JDK 17 이상과 안드로이드 SDK가 있어야 한다.

```sh
cd android
./gradlew :backend:test                  # 코틀린 백엔드 테스트
./gradlew :app:assembleStandaloneDebug   # app/build/outputs/apk/standalone/debug/
./gradlew :app:assembleClientDebug       # app/build/outputs/apk/client/debug/
```

만들어 둔 APK는 `android/apk/`에 있다. 소스를 고치면 다시 만들어 이 폴더의 파일을 바꾼다. 둘 다 디버그 열쇠로 서명한다. 기기에는 `adb install android/apk/drive-talk-standalone.apk`로 넣는다.

### 코틀린 백엔드

`android/backend`는 러스트 `backend`를 코틀린으로 옮긴 것이다. 덱을 섞고, 좌석과 차례와 선택을 맡고, 판을 파일로 남긴다. [BACKEND.md](BACKEND.md)의 API와 주소, 응답, 거절(409, 400, 422)이 같고, 판 파일의 모양도 같다.

앱 안에서는 HTTP를 쓰지 않는다. 화면(`android/app/src/standalone/assets/game.js`)이 `DriveTalkBackend.call("POST", "/v1/sessions/…", body)`로 코틀린을 바로 부른다. 덱은 빌드할 때 `backend/deck.json`을 앱에 넣고, 판은 앱 전용 저장 공간의 `data/sessions`에 남는다. 앱이나 헤드유닛을 다시 켜도 하던 판으로 돌아온다.

기기 없이 돌려 보려면 코틀린 백엔드를 HTTP로 띄운다. 러스트 API와 같은 자리(8080)라 화면 서버가 그대로 붙는다.

```sh
cd android
./gradlew :backend:devServer
# 다른 터미널, 저장소 루트
DRIVE_TALK_API=http://127.0.0.1:8080 ruby bin/drive_talk
```

러스트 `backend`와 Ruby 화면 서버는 그대로다. PC에서 웹으로 띄울 때는 전처럼 그쪽을 쓴다.

## API 서버

덱·판·기록은 `backend`다. 화면 서버는 `DRIVE_TALK_API`가 있으면 이 API를 부르고, 없으면 목 세션이다.

```sh
cd backend
cargo run --offline
# http://127.0.0.1:8080
```

`PORT`로 포트를 바꾼다. `BIND` 기본값은 `127.0.0.1`이다. 덱은 `DRIVE_TALK_DECK`(기본 `deck.json`). 파일이 없으면 바이너리에 넣은 같은 10장이다. 진행 중인 판은 `DRIVE_TALK_DATA`(기본 `data`)의 `sessions`에 남고, 프로세스를 다시 켜도 그 판은 그대로다.

## 같이 띄우기

한 번에 띄우려면 저장소 루트에서 아래 하나만 친다. API를 빌드해 띄우고, 화면을 그 API에 붙인다. Ctrl+C로 둘 다 끈다.

```sh
ruby bin/drive_talk_all
# 화면 http://127.0.0.1:4567, API http://127.0.0.1:8080
```

화면 포트는 `PORT`, API 포트는 `API_PORT`다. 그 포트에 API가 이미 떠 있으면 새로 띄우지 않고 그것을 쓴다. 화면이 어디에 붙었는지는 시작할 때 둘째 줄에 찍힌다.

따로 띄우려면 프로세스 둘, 터미널도 둘이다. 포트를 따로 쓴다. 한 셸의 `PORT`를 같이 쓰면 둘 다 그 포트에 붙으려다 하나가 죽는다.

```sh
# 터미널 1
cd backend
cargo run --offline
# http://127.0.0.1:8080

# 터미널 2, 저장소 루트. 화면.
DRIVE_TALK_API=http://127.0.0.1:8080 ruby bin/drive_talk
# http://127.0.0.1:4567
```

PowerShell은 `$env:DRIVE_TALK_API = "http://127.0.0.1:8080"`을 먼저 치고 `ruby bin/drive_talk`을 친다.

브라우저는 4567만 연다. 8080은 판 API다. `DRIVE_TALK_API`를 주면 덱, 좌석, 선택, 기록은 API에 남는다. 판 id는 브라우저의 `sid` 쿠키라, 화면 서버를 다시 켜도 하던 판으로 돌아온다. API가 꺼져 있으면 화면은 목 세션으로 넘어가지 않고 “판을 불러오지 못했어요”를 보여 준다.

`DRIVE_TALK_API`를 주지 않으면 전처럼 쿠키 목 세션이고, 게임 기록은 API로 가지 않는다.

