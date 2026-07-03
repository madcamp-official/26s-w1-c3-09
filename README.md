# 26s-w1-c3-09

## 공통과제 I : 웹 기반 프로젝트 (2인 1팀)

**목적:** 공통 과제를 함께 수행하며 웹 개발의 전체 흐름을 빠르게 익히고 협업에 적응하기

**결과물:** 기획부터 배포까지 완료된 웹 서비스와 관련 문서 일체

---

## 팀원

| 이름 | GitHub | 역할 |
|---|---|---|
| 박민수 | miinspp |  |
|김재훈| superloser030 |  |

---

## 기획안

> 프로젝트 주제, 목적, 핵심 기능, 예상 사용자, 팀원별 역할 등 정리

- **주제:** 로블록스 게임 추천
- **목적:** 유저의 선호를 바탕으로 손쉽게 다양한 게임을 추천
- **핵심 기능:** 게임 목록 파이프라이닝 및 추천
- **예상 사용자:** 로블록스에 가입된 유저

---

## 기능 명세서

> 구현할 기능을 사용자 관점에서 정리하고, 필수 기능과 선택 기능을 구분

### 필수 기능

- [유저 닉네임 조회],
- [즐겨찾기 목록 표시],
- [게임 티어표 작성],
- [티어표 기반 추천 알고리즘 구성],
- [추천 목록 활성화]
- [게임 상세 정보 표시]

### 선택 기능

- [대화형으로 게임 추천]
- [게임 상세 페이지 속 또 다른 추천 목록 활성화]

---

## IA 및 화면 설계서

> 서비스의 전체 페이지 구조와 페이지 간 이동 흐름; 각 페이지의 주요 UI 구성, 입력 요소, 버튼, 사용자 행동 흐름 등을 간단한 와이어프레임 형태로 정리
https://www.notion.so/392b0d7737b2803699c7f4e3c678de72?source=copy_link

- 페이지 간 이동 흐름
<table border="0" align="center" cellspacing="0" cellpadding="0">
  <tr align="center" valign="middle">
    <td>
      <img src="docs/flow1.png" height="230" width="auto"><br>
      <span style="font-weight: bold; display: inline-block; margin-top: 10px;">닉네임으로 조회</span>
    </td>
    <td style="font-size: 24px; padding: 0 15px; font-weight: bold; color: #888;">➔</td>
    <td>
      <img src="docs/flow2.png" height="230" width="auto"><br>
      <span style="font-weight: bold; display: inline-block; margin-top: 10px;">티어표 작성하기</span>
    </td>
    <td style="font-size: 24px; padding: 0 15px; font-weight: bold; color: #888;">➔</td>
    <td>
      <img src="docs/flow3.png" height="230" width="auto"><br>
      <span style="font-weight: bold; display: inline-block; margin-top: 10px;">맞춤 게임 추천</span>
    </td>
    <td style="font-size: 24px; padding: 0 15px; font-weight: bold; color: #888;">➔</td>
    <td>
      <img src="docs/flow4.png" height="230" width="auto"><br>
      <span style="font-weight: bold; display: inline-block; margin-top: 10px;">게임 설명 & 영상</span>
    </td>
  </tr>
</table>

- 전체 페이지 구조
<table border="0" align="center">
  <tr align="center">
    <td><img src="docs/page1.png" height="230" width="auto"></td>
    <td><img src="docs/page2.png" height="230" width="auto"></td>
    <td><img src="docs/page3.png" height="230" width="auto"></td>
    <td><img src="docs/page4_1.png" height="230" width="auto"></td>
    <td><img src="docs/page4_2.png" height="230" width="auto"></td>
  </tr>
  <tr align="center" style="font-weight: bold;">
    <td>첫번째 페이지</td>
    <td>두번째 페이지</td>
    <td>세번째 페이지</td>
    <td>네번째 페이지 (1)</td>
    <td>네번째 페이지 (2)</td>
  </tr>
</table>

<!-- Figma 링크 또는 이미지 첨부 -->

---

## DB 스키마

> 필요한 테이블, 주요 필드, 데이터 타입, 테이블 간 관계를 정리

<!-- ERD 이미지 또는 테이블 정의 -->

---

## API 문서

> API 주소, 요청 방식, 요청값, 응답값, 에러 상황을 정리

| Method | Endpoint | 설명 | 요청 | 응답 |
|---|---|---|---|---|
|  |  |  |  |  |

---

## 배포 결과물

> 접속 가능한 링크, 실행 방법, 주요 구현 내용

- **서비스 URL:**
- **실행 방법:**

```bash
# 실행 방법 작성
```

---

## 회고 문서

> 개발 과정에서의 어려움, 해결 방법, 역할 분담, 다음에 개선할 점 (KPT 방법론 참고)

### Keep

### Problem

### Try

---

## 참고 자료

- [SDD(스펙 주도 개발) 이해하기](https://news.hada.io/topic?id=21338)
- [Software Design Document Best Practices](https://www.atlassian.com/work-management/project-management/design-document)
- [IA 정보구조도 작성 방법](https://brunch.co.kr/@nyonyo/7)
- [기획자 화면설계서 작성법](https://brunch.co.kr/@soup/10)
- [Figma 와이어프레임 가이드](https://www.figma.com/ko-kr/resource-library/what-is-wireframing/)
- [무료 Figma 와이어프레임 키트](https://www.figma.com/ko-kr/templates/wireframe-kits/)
- [ERD/DB 설계 총정리](https://inpa.tistory.com/entry/DB-%F0%9F%93%9A-%EB%8D%B0%EC%9D%B4%ED%84%B0-%EB%AA%A8%EB%8D%B8%EB%A7%81-%EA%B0%9C%EB%85%90-ERD-%EB%8B%A4%EC%9D%B4%EC%96%B4%EA%B7%B8%EB%9E%A8)
- [API 명세서 작성 가이드라인](https://velog.io/@sebinChu/BackEnd-API-%EB%AA%85%EC%84%B8%EC%84%9C-%EC%9E%91%EC%84%B1-%EA%B0%80%EC%9D%B4%EB%93%9C-%EB%9D%BC%EC%9D%B8)
- [좋은 README 작성하는 방법](https://velog.io/@sabo/good-readme)
- [단기 프로젝트 회고 KPT 방법론](https://velog.io/@habwa/%EB%8B%A8%EA%B8%B0-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8-%ED%9A%8C%EA%B3%A0-KPT-%EB%B0%A9%EB%B2%95%EB%A1%A0)
