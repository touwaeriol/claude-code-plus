### Git 커밋 정책

**중요**: 버전 관리 작업에 터미널 명령어(git commit, git add, git push 등)를 사용하지 마십시오.
대신 jetbrains_git MCP 도구를 사용해야 합니다.

### 커밋 워크플로우

1. `GetVcsChanges()` → 변경 목록 가져오기
2. 변경 사항 분석 후 `SelectFiles` / `DeselectFiles`로 파일 선택 조정
3. `SetCommitMessage()` → 커밋 메시지 생성 및 입력
4. **반드시** `AskUserQuestion`으로 사용자 확인 요청
5. 사용자 확인 후 `CommitChanges()` 호출하여 실행

### 사용 시기

IDEA의 VCS/Git 통합과 상호작용: 변경 사항 읽기, 커밋 메시지 설정, 상태 확인.

### 파일 선택 도구

- `SelectFiles(paths, mode)` → Commit 패널에서 파일 선택 (mode: "replace" 대체 또는 "add" 추가)
- `DeselectFiles(paths)` → 파일 선택 해제
- `SelectAllFiles()` → 모든 변경 파일 선택
- `DeselectAllFiles()` → 모든 선택 해제

### 커밋 메시지 규칙 (Conventional Commits)

Conventional Commits 형식을 따릅니다:

```
<type>(<scope>): <description>

[선택적 본문]

[선택적 꼬리말]
```

**타입**:
- `feat`: 새로운 기능
- `fix`: 버그 수정
- `docs`: 문서만 변경
- `style`: 코드 스타일 변경 (포맷팅, 세미콜론 누락 등)
- `refactor`: 기능 추가나 버그 수정 없는 리팩토링
- `perf`: 성능 개선
- `test`: 테스트 추가 또는 수정
- `chore`: 빌드 프로세스, 보조 도구 변경 등
- `ci`: CI 설정 변경
- `build`: 빌드 시스템 또는 외부 종속성 변경

**예시**:
- `feat(auth): add OAuth2 login support`
- `fix(api): resolve null pointer exception in user endpoint`
- `docs: update README with installation instructions`
- `refactor(core): simplify data processing logic`

### 참고 사항

- 커밋 전 반드시 사용자 검토를 기다림
- `CommitChanges`에서 `push=true` 사용 시 커밋과 푸시를 한 번에 실행
