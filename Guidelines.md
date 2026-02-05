# ACScore Development Guidelines (Final)

## Project Overview
이 프로젝트는 **Kotlin Multiplatform(KMP)** 기반의 **악보(PDF) 앱** 개발이다.

- Android를 먼저 개발한 후 iOS를 추가한다.
- 초기 목표는 **MVP v1** 완성이다.

---

## MVP v1 Scope
- PDF Import
- Library 목록 표시
- 검색 (제목 / 초성)
- PDF Viewer
    - 단일 파일 열기 기준
    - 다중 파일 / 탭 / 세트 연계는 T2에서 확장

---

## Module & Architecture Rules
- `shared` 모듈
    - 도메인 모델
    - UseCase
    - Repository **interface**
    - 비즈니스 규칙(검색/정렬/섹션 등)
    - ❌ 플랫폼 의존 코드 금지
        - Android / iOS API
        - Context, Uri, File, PdfRenderer 등

- `composeApp` (androidMain)
    - 파일 피커(SAF)
    - PDF 파일 복사 및 경로 관리
    - 로컬 DB 실제 구현
    - PDF 렌더링 (PdfRenderer)

- iOS 추가 시
    - shared 구조 유지
    - DB / 파일 접근은 iosMain에서 구현

---

## Persistence / Local DB
- **Library(파일 리스트)** / **Setlist(세트리스트)** 는 로컬 DB로 관리한다.
- DB 접근은 `shared` 에서 **Repository interface** 로만 정의한다.
- 실제 DB 구현은 각 플랫폼에서 담당한다.

### 허용 구현체
- Android
    - SQLite(Room 등)
    - Realm
- iOS
    - CoreData
    - Realm

### 저장 원칙
- PDF 파일 자체는 DB에 저장하지 않는다.
- PDF 파일은 앱 내부 저장소에 저장한다.
    - 경로: `filesDir/scores/`
- DB에는 메타데이터만 저장한다.
    - id
    - 파일명
    - 제목
    - 생성일
    - 세트리스트 관계 등

### MVP 단계 정책
- 초기 MVP 단계에서는
    - 스키마 변경
    - 마이그레이션 복잡도
      를 최소화한다.
- 필요 시 **앱 데이터 초기화 또는 간단 마이그레이션을 허용**한다.

---

## Platform-Specific Rules
- Android 전용
    - 파일 피커 (SAF)
    - PDF 렌더링: `PdfRenderer`
- iOS 전용
    - PDFKit 등 네이티브 API 사용 가능

---

## Coding Rules
- 코드 제안은 **파일 단위**로 제공한다.
- 코드 제안 순서:
    1. Data
    2. Domain
    3. UI
- 앱이 **크래시 나지 않도록 예외 처리 우선**
    - 파일 접근
    - PDF 렌더링
    - DB IO
    - 리소스 close 처리

---

## Naming
- 기본 패키지명: 'com.kandc.acscore'

---

## Future Extension (명시적 허용)
- 세트리스트 고도화
- 다중 PDF 탭(Viewer)
- Google Drive / Dropbox 등 외부 디렉토리 연동
- Android / iOS UI 차별화

(단, MVP v1 범위에는 포함하지 않는다)

---

# 🔒 Change Contract (변경 계약 규칙)

이 프로젝트는 구조 안정성을 최우선으로 한다.  
아래 계약 규칙은 기능 추가 시에도 반드시 유지한다.

## 절대 변경 금지 규칙
- shared 모듈에 플랫폼 의존 코드 추가 금지
- Repository interface는 shared에만 정의
- 플랫폼 구현은 androidMain / iosMain 에만 위치
- 내부 저장 경로 규칙은 SPEC.md 정의를 따른다
- ScoreId / SetlistId UUID 정책은 변경하지 않는다
- Viewer 세션 복원 책임은 ViewerSessionStore 단일 책임으로 유지

## 구조 변경 필요 시
- TASK 단위로 먼저 정의
- 변경 범위 파일 목록을 먼저 확정
- UI → Domain → Data 역방향 변경 금지
- 반드시 Data → Domain → UI 순으로 수정

---

# 🧩 Work Unit Rule (작업 단위 규칙)

단편 수정으로 인한 구조 붕괴를 방지하기 위해  
모든 작업은 아래 단위 중 하나로만 수행한다.

허용되는 작업 단위:

1. Feature 단위
   예: Setlist 공유 기능 전체

2. Flow 단위
   예: Import → 저장 → metadata 반영 → 목록 갱신

3. File-set 단위
   예: ViewerSessionStore + SnapshotJson + RestoreLogic

금지:

- 한 파일만 수정 요청 (버그 핫픽스 제외)
- View만 수정 요청
- Model만 수정 요청

항상 관련 레이어 전체를 함께 본다.