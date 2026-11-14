# 드라이브 토크

차 안 밸런스 게임 화면. Ruby 표준 라이브러리만 쓴다. 덱·음성인식·기록의 주인은 [BACKEND.md](BACKEND.md).

```sh
ruby bin/drive_talk
# http://127.0.0.1:4567
```

`PORT`로 포트를 바꾼다. 주소 뒤 `?demo=1`은 브라우저 마이크 확인을 건너뛴다.

화면 위 라이트·다크·가로·세로는 이 브라우저에만 남는다.

Ruby로 이 화면을 읽는 교재는 [docs/study/README.md](docs/study/README.md)다. 문법, 요청이 HTML이 되는 길, 다음 앱에 남길 파일 역할이 있다.
