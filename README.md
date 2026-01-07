# 프로젝트 명칭: 대용량 통신 요금 명세서 및 알림 발송 시스템

---

## 1. 기술 스택 (Tech Stack)

| 분류 | 기술 스택 |
| :--- | :--- |
| **Language** | Java 17 |
| **Framework** | Spring Boot 3 |
| **Database** | MySQL 8 |
| **ORM/Mapper** | MyBatis |
| **Migration** | Flyway |
| **Build Tool** | Gradle |
| **Frontend** | Thymeleaf |

---

## 2. 협업 규칙 (Collaboration Rules)

### 개발 환경
- **Version Control:** GitHub
- **Communication:** Slack, Discord
- **IDE:** STS, IntelliJ

### 브랜치 전략
> 브랜치는 기능별로 생성하며, 작업 완료 후 PR(Pull Request)을 통해 머지합니다.

#### 브랜치 네이밍 규칙
- **형식:** type/{issueId}-{short-desc}
- **규칙:** 소문자 사용, 단어 사이 하이픈(-) 연결
- **issueId:** GitHub Issue 식별자 (없을 경우 BE-YYYYMMDD-01 형식 허용)

| Type | 설명 | 예시 |
| :--- | :--- | :--- |
| **feat** | 새로운 기능 추가 | feat/12-admin-login |
| **fix** | 버그 수정 | fix/33-null-pointer |
| **docs** | 문서 수정 | docs/04-update-readme |
| **chore** | 설정 및 의존성 수정 | chore/05-add-flyway |

#### 브랜치 생성 및 머지 흐름
- 모든 작업 브랜치는 dev 브랜치에서 분기합니다.
- **분기:** git checkout -b feat/... origin/dev
- **PR 대상:** 모든 PR은 항상 dev 브랜치를 타겟으로 생성합니다.

---

### 커밋 메시지 컨벤션 (Commit Convention)

#### 메시지 형식
```
<type>(<scope>): <subject>
```

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

*‘첫 버전’ 파일명은 예외적으로 **V1__init.sql**로 지정합니다.*
```
V[타임스탬프10자리 숫자]__[설명].sql # yymmddhhmm

# 25년11월25일 16시23분에 생성한 파일
ex) V2511251623__brewery_update_column_start_time.sql

# 25년11월25일 17시20분에 생성한 파일
ex) V2511251720__brewery_add_constraint_start_time.sql
```