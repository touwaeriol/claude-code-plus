### 사용 시기

중요: 코드 검색 및 파일 탐색 시 내장 검색 도구보다 JetBrains MCP 도구를 우선 사용하세요:
- 내장 grep/search 도구 대신 항상 `CodeSearch` 사용
- 내장 glob/find 도구 대신 항상 `FileIndex` 사용
- JetBrains 도구가 오류를 반환할 때만 내장 도구로 대체

중요: 코드 수정 완료 후 반드시 `FileProblems`로 구문 오류를 검증하세요.

### 리팩토링 워크플로우

심볼 이름 변경 시:
1. `FindUsages` 또는 `CodeSearch` → 줄 번호 가져오기
2. `Rename(line=N, newName="...")` → 프로젝트 전체에서 안전하게 이름 변경
3. `FileProblems` → 변경 사항 검증

**참고**: `Rename`은 `line` 파라미터가 필요합니다. 심볼에는 `Rename`을, 기타 텍스트 변경에는 Edit 도구를 사용.

### 라이브러리 소스 코드 읽기

의존성(JAR 파일, JDK 소스, 디컴파일된 .class) 읽기:
1. `FileIndex(query="ClassName", searchType="Classes", scope="All")`
2. `ReadFile(filePath="<FileIndex에서 반환된 경로>")`

**핵심**: `scope="All"`을 사용하여 프로젝트 파일뿐만 아니라 라이브러리도 포함.
