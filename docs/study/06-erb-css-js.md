# ERB, CSS, JS

화면의 글은 ERB, 모양은 CSS, 시간 지연만 JS다. 셋을 한 파일에 섞지 않는다.

## ERB 태그

`views/screen.erb`는 HTML 사이에 Ruby를 끼운 파일이다. `View#erb`가 읽고, `erb.result(binding)`이 실행한다.

`binding`은 그 `View` 객체의 변수와 메서드가 보이는 범위다. 그래서 템플릿에서 `@game`도, `pill`도, `seat_class(seat)`도 부를 수 있다. 다른 객체의 메서드는 부르지 못한다. `Game`에 화면 문장 메서드를 더 만들고 템플릿에서 바로 부르면 `NoMethodError`다. 화면 문구는 `View`에 둔다.

태그는 세 가지다.

| 태그 | 하는 일 |
| --- | --- |
| `<%- ... -%>` | 실행만 한다. 출력하지 않는다. `-`는 그 줄의 공백을 지운다 |
| `<%= ... %>` | 실행하고 결과를 HTML에 넣는다 |
| `<%# ... %>` | 주석. 이 파일에는 거의 없다 |

`trim_mode: "-"` 때문에 `<%- when :home -%>` 같은 줄이 HTML에 빈 줄로 남지 않는다.

`case`도 태그 안에 있다.

```erb
<%- case @game.screen -%>
<%- when :home -%>
  ...
<%- when :read, :answer -%>
  ...
<%- end -%>
```

`when`을 여러 태그로 나눠 써도 Ruby의 `case`다. `:read`와 `:answer`는 질문을 공유하고, 안의 `if @game.screen == :answer`로 버튼만 가른다.

## 출력은 자동으로 막지 않는다

표준 라이브러리 `ERB`의 `<%=`는 HTML을 탈출하지 않는다. `<`가 그대로 나간다.

지금 질문에 사용자가 친 문장이 없어서 당장은 안전하다. 카드 문장은 `deck.rb`의 리터럴이다. 나중에 외부 문장을 `<%=`로 넣으면 태그가 실행될 수 있다. 그때는 `CGI.escapeHTML(text)`로 감싼다.

class 이름을 만들 때도 값을 제한한다. `theme`는 세 단어만 통과한 뒤 `body_class`로 나간다. 쿼리를 그대로 class에 붙이지 않는다.

## 뷰 메서드는 문장과 클래스만

`pill`은 두 칸 배열을 돌려준다. 템플릿이 이렇게 받는다.

```erb
<%- kind, text = pill -%>
<span class="pill is-<%= kind %>"><%= text %></span>
```

배열의 첫 값이 `kind`, 둘째가 `text`다. 다중 대입이다.

`seat_class`와 `seat_state`는 같은 순서로 조건을 본다. 미참여, 말하는 중, A, B, 차례, 낭독 대기, 참여. 순서가 다르면 말이 다른 색과 만난다. 하나를 고치면 다른 하나도 같은 순서로 고친다.

`hot?`는 `count.to_i.positive?`다. 0명 알약은 회색, 1명부터 색이 생긴다.

## CSS가 맡는 것

`app.css`가 기본이다. 색은 `:root`의 변수다. `--a`는 주황, `--b`는 파랑, `--ink`는 글자, `--bg`는 배경이다. 컴포넌트는 이 변수만 쓴다. 다크에서 주황 자체를 바꾸지 않는다.

기기 프레임은 `.device`다. 너비는 세 값의 최솟값이다. 화면 너비, 1080px, 그리고 높이에서 역산한 1700/1184 비율. 피그마 가로 프레임이 그 비율이다.

```css
width: min(calc(100vw - 24px), 1080px, calc((100dvh - 56px) * 1.435));
```

`adaptive.css`는 두 가지를 덮는다.

- `body.portrait`와 좁은 화면은 좌우 배치를 위아래로 바꾼다.
- `body.dark`와 `prefers-color-scheme: dark`는 배경과 글자만 바꾼다.

테마 링크는 `/?theme=dark` 형태다. `View#pref`가 현재 화면 주소에 쿼리만 붙인다. 서버가 쿠키로 저장하고 같은 화면을 다시 그린다. 선택은 그 브라우저에만 남는다.

폰트는 `public/fonts/PretendardVariable.woff2`다. 네트워크 없이 한글이 같은 모양으로 나오게 저장소에 넣었다. 약 2MB다.

## JS가 맡는 것

`public/app.js`는 33줄이다. 앱 상태가 없다.

`post`는 폼을 만들어 제출한다. `fetch`가 아니다. 그래서 서버의 303을 브라우저가 그대로 따라간다.

세 가지 경우만 쓴다.

1. `[data-auto]`가 있고 모달이 없으면 `data-delay` 밀리초 뒤 그 주소로 POST한다. 스플래시는 `/enter`에 1500, 섞기는 `/ready`에 1400, 낭독은 `/ready`에 2000이다.
2. `[data-settle]`이 있으면 900ms 뒤 `POST /settle`이다. 목소리 확인 잠금을 푸는 두 번째 그리기다.
3. `[data-need-mic]`을 누르고 `data-demo`가 `"1"`이 아니면 `getUserMedia({ audio: true })`를 부른다. 허용되면 트랙을 바로 끄고 `POST /start`다. 거절되면 `POST /mic`다. 음성 인식은 하지 않는다. 허용 여부만 본다.

`?demo=1`이면 3번을 건너뛴다. 헤드리스 브라우저나 마이크가 없는 환경에서 화면을 보려고 넣었다.

스크립트를 지워도 폼 버튼은 동작한다. 스플래시와 섞기, 낭독의 자동 이동만 멈춘다.

## 글과 모양을 고칠 때

문장만 바꿀 때는 `screen.erb`나 `view.rb`의 문자열만 고친다. 새로고침이다.

색만 바꿀 때는 CSS 변수만 고친다. 버튼 색을 컴포넌트 안에 직접 쓰지 않는다. 다크가 같이 깨진다.

자동 이동 시간만 바꿀 때는 `data-delay`다. 서버의 `sleep`으로 옮기지 않는다. 서버가 자면 그 판의 잠금이 다른 요청까지 붙잡는다. `Store`의 Mutex가 렌더 동안 잡혀 있기 때문이다.
