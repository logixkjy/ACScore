# ACScore SPEC (MVP v1)

## Goal
Android(KMP)에서 PDF 악보를 가져와 저장/목록/검색하고 뷰어로 연다.

## MVP v1
- PDF Import (Files에서 선택)
- 앱 내부 저장소에 복사 (중복 파일명 처리)
- Library 목록 표시 + 검색(제목)
- 삭제(파일 + 메타)
- (뷰어는 v1.1 또는 T2)

## Architecture
- shared: 모델/유스케이스/레포 인터페이스 (플랫폼 의존 금지)
- composeApp(androidMain): 파일피커, 파일 복사/경로, repository actual 구현

## Storage
- PDF: filesDir/scores/
- metadata: filesDir/scores/metadata.json (kotlinx.serialization)