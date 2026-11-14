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

Ruby로 이 화면을 읽는 교재는 [docs/study/README.md](docs/study/README.md)다. 문법, 요청이 HTML이 되는 길, 다음 앱에 남길 파일 역할이 있다.
