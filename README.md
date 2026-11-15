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

## API 서버

덱·판·기록은 `backend`다. 화면 서버는 `DRIVE_TALK_API`가 있으면 이 API를 부르고, 없으면 목 세션이다.

```sh
cd backend
cargo run --offline
# http://127.0.0.1:8080
```

`PORT`로 포트를 바꾼다. `BIND` 기본값은 `127.0.0.1`이다. 덱은 `DRIVE_TALK_DECK`(기본 `deck.json`). 파일이 없으면 바이너리에 넣은 같은 10장이다. 진행 중인 판은 `DRIVE_TALK_DATA`(기본 `data`)의 `sessions`에 남고, 프로세스를 다시 켜도 그 판은 그대로다.

## 같이 띄우기

화면과 API는 프로세스 둘이다. 터미널도 둘이다. 포트를 따로 쓴다. 한 셸의 `PORT`를 같이 쓰면 둘 다 그 포트에 붙으려다 하나가 죽는다.

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

