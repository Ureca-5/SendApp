# 대용량 통신 요금 명세서 및 알림 발송 시스템

---
# Diagram
## 배치 시퀀스 다이어그램
<img width="7820" height="5240" alt="Image" src="https://github.com/user-attachments/assets/838185f3-6cbf-456d-8800-14247901d706" />

---
# 프로젝트 소개

## 프로젝트 개요
LG U+ 유레카 백엔드 3기 종합프로젝트 (4인 팀) — 유저 100만 · 청구 이력 500만 건 규모의 대용량 정산 및 발송 시스템입니다.

Spring Batch 기반 청크 단위 처리와 Redis Stream을 활용한 비동기 파이프라인으로, 대용량 청구서 정산부터 이메일/SMS 발송까지 전체 흐름을 자동화합니다.

## 주요 기능

- 매월 정산 대상 회원 자동 선정 및 대용량 배치 정산 (Spring Batch 청크 처리)
- 청구서 헤더/상세 정보 생성 및 DB 저장
- Redis Stream 기반 비동기 적재로 정산·발송 파이프라인 분리
- 이메일/SMS 청구서 자동 발송, 채널별 발송 이력 관리
- 발송 실패 시 재시도 및 SMS 채널 자동 전환
- 야간 금지 시간대(21시~09시) 발송 제어 및 예약 발송
- 유실 데이터 자동 탐지 및 복구
- 백오피스 대시보드를 통한 정산/발송 현황 모니터링
  
## 팀 구성 및 담당
- **김준하(팀장):** 정산&배치 시스템
- **우수정:** 발송 시스템
- **조승혁:** 적재 시스템
- **정현서:** 보안&백오피스 시스템

## 시스템 아키텍처
<img width="891" height="302" alt="SendApp_아키텍처" src="https://github.com/user-attachments/assets/072b8834-407d-4786-b1ab-a30d19ee6230" />

## ERD
<img width="1658" height="729" alt="SendApp_ERD" src="https://github.com/user-attachments/assets/6b245b81-9ed0-4d10-b5ba-a425f6c880ef" />

---
# Info

## 1. 기술 스택 (Tech Stack)

| 분류 | 기술 스택             |
| :--- |:------------------|
| **Language** | Java 17           |
| **Framework** | Spring Boot 3.5.9 |
| **Database** | MySQL 8           |
| **ORM/Mapper** | MyBatis           |
| **Migration** | Flyway            |
| **Build Tool** | Gradle            |
| **Frontend** | Thymeleaf         |

---

## 2. 협업 규칙 (Collaboration Rules)

### 개발 환경
- **Version Control:** GitHub
- **Communication:** Slack, Discord
- **IDE:** STS, IntelliJ

*제공되는 .env 파일을 src/main/resources 경로에 배치시켜주세요. 해당 파일은 절대 Github에 올리지 말아주세요.*

### 브랜치 전략
> 브랜치는 기능별(이슈 1개)로 생성하며, 작업 완료 후 PR(Pull Request)을 통해 머지합니다.

#### 브랜치 네이밍 규칙
- **형식:** type/{issueId}-{short-desc}
- **규칙:** 소문자 사용, 단어 사이 하이픈(-) 연결
- **issueId:** GitHub Issue 식별자 (없을 경우 YYYYMMDD-01 형식 허용)

| Type | 설명 | 예시 |
| :--- | :--- | :--- |
| **feat** | 새로운 기능 추가 | feat/12-admin-login |
| **fix** | 버그 수정 | fix/33-null-pointer |
| **docs** | 문서 수정 | docs/04-update-readme |
| **chore** | 설정 및 의존성 수정 | chore/05-add-flyway |

#### 브랜치 생성 및 머지 흐름
- 모든 작업 브랜치는 **dev** 브랜치에서 분기합니다.
- **분기:** git checkout -b feat/... origin/dev
- **PR 대상:** 모든 PR은 항상 dev 브랜치를 타겟으로 생성합니다.

---

### 커밋 메시지 컨벤션 (Commit Convention)

#### 메시지 형식
`<type>(<scope>): <subject>`

#### Type 목록
- feat: 기능 추가
- fix: 버그 수정
- refactor: 리팩터링
- docs: 문서 수정
- test: 테스트 코드 추가/수정
- chore: 설정, 빌드, 의존성 등 기타 작업
- perf: 성능 개선

#### Scope 및 Subject 규칙
- **Scope 예시:** auth, user, admin, notice, order, config, db, ci
- **Subject:** 현재형 작성, 마침표(.) 금지, 50자 이내 작성

> **커밋 예시**
> - feat(admin): 관리자 로그인 API 추가
> - fix(order): 주문 조회 시 NPE 수정
> - chore(db): flyway V2601101602__example.sql 추가

---

### 개발 규칙
- **커밋 크기:** 커밋 1개는 설명 가능한 단일 변경 단위로 작성합니다.
- **Flyway 관리:** DB 스키마 충돌 방지를 위해 Flyway 마이그레이션 파일 추가는 별도 커밋으로 분리를 권장합니다.
- [Flyway 가이드(Notion)](https://www.notion.so/Flyway-2e1b40db26dd808a9633e76420c94b07)

**Flyway .sql 파일명 규칙**

*'첫 버전' 파일명은 예외적으로 **V1__init.sql**로 지정합니다.*

`V[타임스탬프10자리 숫자]__[설명].sql` # yymmddhhmm

25년11월25일 16시23분에 생성한 파일 → `V2511251623__brewery_update_column_start_time.sql`

25년11월25일 17시20분에 생성한 파일 → `V2511251720__brewery_add_constraint_start_time.sql`
