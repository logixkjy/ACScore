---

# 🤖 AI 작업 요청 템플릿

각 TASK 수행 시 아래 포맷으로 작업 요청한다.

## Goal
이 작업의 최종 목표

## Scope
수정/생성 대상 모듈 또는 Feature

## Target Files (예상)
수정될 가능성이 있는 파일 목록

## DoD (Definition of Done)
완료 판정 기준

## Constraints
지켜야 할 계약 규칙
- shared 플랫폼 코드 금지
- Repository interface 유지
- ID 정책 유지
- 저장 경로 규칙 유지

## Validation Checklist
- 크래시 없음
- 예외 처리 포함
- 빈 데이터 처리
- 파일 누락 처리
- 복원 시나리오 검증
 
- [v] T1 PDF Import → 내부저장 → 목록 표시(재실행 유지)
    - [v] T1-1 Import 중 로딩 표시 (파일 앱 왕복 시 검은 화면 방지)
    - [v] T1-2 Import 구조는 단일/다중 선택 확장 가능하게 설계

- [v] T2 PDF Viewer (PdfRenderer)
    - [v] T2-1 단일 PDF 열기
    - [v] T2-2 여러 PDF 동시 열기 (세트리스트/다중 선택)
    - [v] T2-3 상단 탭 UI (브라우저 탭 형태, 닫기 전까지 유지)
    - [v] T2-4 PdfRenderer / FileDescriptor close 안전 처리

- [v] T3 삭제 (파일 + 메타)

- [x] T4 검색 / 정렬
    - [x] T4-P1 인덱스 UX 미세 조정 (옵션)
    - [x] T4-P2 stickyHeader 적용 검토 (옵션)

- [v] T5 세트리스트 v1
    - [v] T5-1 세트리스트 생성
    - [v] T5-2 곡 추가/삭제
    - [v] T5-3 순서 변경
    - [v] T5-4 세트리스트에서 여러 PDF 한 번에 열기 (T2 연계)
  
- [ ] T6 디테일 업 및 메타데이터 설정
    - [v] T6-1 뷰어 스와이프 및 앞/뒤, 처음페이지 이동 버튼 추가
    - [v] T6-2 라이브러리 오버레이 마자막 상태 유지 (매번 라이브러리로 다시 표시X 머지막 표시 화면으로 유지)
    - [ ] T6-3 세트리스트 공유(악보와 세트리스트 구성 인덱스, 파일명 등의 정보들) 차후 iOS 호환 가능해야 함.
    - [ ] T6-4 아이콘, 런치이미지, 앱 이름 등 메타데이터 설정
    

- [ ] T7 다음 스텝 정리
      - [ ] T7-1 외부 라이브러리 연동(드롭박스, 구글드라이브 등)
      - [ ] T7-2 노트기능 추가
      - [ ] T7-3 광고 추가 여부 판단
      - [ ] T7-4 다음 스템으로 고도화에 포함할 기능 정리 (ex. 음악용어사전 기능 추가 등)

🔒 Regression Checklist (봉인용 · 필수)

규칙
•	이 체크리스트가 있는 기능은 “완료(✅)”로 간주한다.
•	이후 수정 시, 아래 항목 중 하나라도 실패하면 작업은 실패로 간주한다.
•	새 기능 추가 / 리팩토링 전후에 반드시 1회 이상 수동 확인한다.
•	실패 시 기존 기능을 고치기 위한 Hotfix 외 변경은 금지한다.

⸻

✅ R1. Library (악보 목록 / 단일 뷰어)
•	Library에서 악보 1개 탭 시 즉시 Viewer로 진입
•	Viewer 진입 시 pick 모드가 아님
•	Viewer 닫기 후 Library 상태 정상 복귀

⸻

✅ R2. Setlist 생성 / 진입
•	새 Setlist 생성 가능
•	Setlist 리스트 → SetlistDetail 진입 가능
•	SetlistDetail에서 곡이 없을 때도 화면 정상 표시

⸻

✅ R3. Setlist 곡 추가 (Picker 핵심 경계)
•	SetlistDetail에서 + 버튼 탭
•	즉시 Library 탭으로 전환됨
•	Library가 곡 선택(pick) 모드 UI로 표시됨
•	이미 포함된 곡은 선택 불가 또는 무시됨
•	곡 선택 시
•	Setlist에 곡이 추가됨
•	pick 모드가 즉시 종료됨
•	SetlistDetail로 자동 복귀됨

⸻

✅ R4. Setlist 곡 탭 → Viewer
•	SetlistDetail에서 곡 탭 시 Viewer로 진입
•	Viewer가 정상 뷰어 모드로 열림
•	곡을 넘겨도 pick 동작이 발생하지 않음

⸻

✅ R5. Picker 상태 봉인 (중요)
•	pick 모드 상태에서 Viewer 탭으로 이동해도
•	Viewer가 pick 모드로 변하지 않음
•	pick 종료 후
•	pickingForSetlistId == null
•	pickingSelectedIds.isEmpty()
•	Viewer에서 곡 탭 시
•	pick 상태 ❌ → 열기
•	pick 상태 ⭕ → 절대 열기 불가

⸻

✅ R6. 탭 전환 안정성
•	Library ↔ Setlist ↔ Viewer 탭 반복 전환
•	이전 pick 상태가 어디에도 남아있지 않음
•	예상치 못한 자동 추가 / 자동 선택 발생 ❌

⸻

🚨 실패 처리 규칙
•	위 항목 중 단 하나라도 실패 시
•	❌ 새 기능 추가 금지
•	❌ 구조 리팩토링 금지
•	⭕ Root(MainHost) 단에서 Hotfix만 허용
•	Hotfix 후 체크리스트 전체 재검증 필수

⸻

🧠 개발 원칙 요약 (한 줄)

Picker 상태는 Root만 소유하며,
Viewer / Setlist / Library는 절대 pick 상태를 생성하거나 유지하지 않는다.

⸻
