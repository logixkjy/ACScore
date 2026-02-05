# ACScore SPEC (MVP v1)

## Goal
Android(KMP)에서 PDF 악보를 가져와 저장/목록/검색하고 뷰어로 연다.

---

## MVP v1
- PDF Import (Files 앱에서 선택)
    - Import 중 로딩 표시로 사용자 대기 상태 명확화
- 앱 내부 저장소에 PDF 복사
    - 중복 파일명 자동 처리
- Library 목록 표시
    - 제목 기반 검색 지원
        - 완성형 검색 (예: "찬송")
        - 초성 검색 (예: "ㅊㅅ")
        - 공백 분리 검색 지원 (예: "ㅈㄴㅇ ㅊ")
    - 섹션 헤더 기반 정렬
        - 정렬 순서: 숫자 → 영어 → 한글
        - 섹션 인덱스 바 및 섹션 점프 UX 제공
- 삭제
    - PDF 파일 + 메타데이터 동시 삭제
- (PDF Viewer는 v1.1 또는 T2에서 구현)

---

## Viewer (T2 방향성)
- PDF Viewer는 단일 파일뿐 아니라 여러 PDF를 동시에 열 수 있는 구조를 고려한다.
- 여러 문서를 동시에 열 경우, 상단 탭(브라우저 형태)으로 구분한다.
- 탭은 닫기 전까지 유지되며 활성 탭 전환이 가능하다.
- Android에서는 PdfRenderer를 사용한다.
    - ParcelFileDescriptor / PdfRenderer close 등 리소스 안전 처리를 우선한다.

---

## Architecture
- shared
    - 도메인 모델
    - 유스케이스
    - Repository 인터페이스
    - 검색/정렬/섹션 그룹핑 규칙 포함
    - 플랫폼 의존 코드 포함 금지
- composeApp (androidMain)
    - 파일 피커(SAF)
    - 파일 복사 및 내부 저장소 경로 관리
    - metadata.json 관리
    - PdfRenderer 기반 Viewer 구현

---

## Storage
- PDF 파일
    - 경로: `filesDir/scores/`
- 메타데이터
    - 경로: `filesDir/scores/metadata.json`
    - 포맷: `kotlinx.serialization`
    - 내부 식별자는 UUID 기반으로 관리

---

# 🧱 Architectural Invariants (불변 아키텍처 규칙)

아래 규칙은 MVP 이후에도 유지한다.

## 모듈 규칙
- shared = 순수 비즈니스 로직 + 인터페이스
- composeApp(androidMain) = 파일 접근 / PDF 렌더링 / picker
- iosMain = 동일 인터페이스 기반 구현

## 데이터 흐름
UI → Domain → Repository → Platform 구현

역방향 참조 금지.

## ID 정책
- 모든 Score / Setlist 는 UUID 기반
- 파일명 ≠ 식별자
- 경로 변경 가능 / ID 불변

## 저장 규칙
내부 저장소 기준:

files/
scores/
metadata/
setlists/

외부 Uri 직접 참조 금지.

---

# 🔧 Extension Rule (확장 시 규칙)

새 기능 추가 시:

- 기존 인터페이스 먼저 검토
- 새 Repository 필요 여부 판단
- shared에 interface → platform에 구현 순서

shared에 구현을 먼저 추가하지 않는다.