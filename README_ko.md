# Claude Code Plus

<p align="center">
  <img src="jetbrains-plugin/src/main/resources/META-INF/pluginIcon.svg" width="80" alt="Claude Code Plus Logo">
</p>

<p align="center">
  <strong>JetBrains IDE를 위한 고급 AI 프로그래밍 어시스턴트</strong>
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/28343-claude-code-plus">
    <img src="https://img.shields.io/jetbrains/plugin/v/26972-claude-code-plus.svg" alt="JetBrains Plugin">
  </a>
  <a href="https://github.com/touwaeriol/claude-code-plus/releases">
    <img src="https://img.shields.io/github/v/release/touwaeriol/claude-code-plus" alt="GitHub Release">
  </a>
  <a href="https://github.com/touwaeriol/claude-code-plus/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/touwaeriol/claude-code-plus" alt="License">
  </a>
</p>

<p align="center">
  <a href="README.md">English</a> |
  <a href="README_zh-CN.md">简体中文</a> |
  <a href="README_ja.md">日本語</a> |
  <a href="README_ko.md">한국어</a>
</p>

---

Claude Code Plus는 Claude AI를 개발 환경에 직접 통합하는 IntelliJ IDEA 플러그인으로, 자연어 상호작용을 통해 지능형 코드 지원을 제공합니다.

## ✨ 기능

- **AI 기반 대화** - IDE에서 직접 Claude AI와 채팅
- **스마트 컨텍스트 관리** - @ 멘션으로 파일 및 코드 스니펫 참조
- **멀티 세션 지원** - 여러 채팅 세션을 동시에 관리
- **풍부한 도구 통합** - Claude의 도구 사용 보기 및 상호작용 (파일 읽기/쓰기, bash 명령 등)
- **IDE 통합** - 클릭하여 파일 열기, diff 보기, 특정 라인으로 이동
- **다크 테마 지원** - IntelliJ 다크 테마와 완전 호환
- **내보내기 기능** - 대화 기록을 다양한 형식으로 저장

## 📸 스크린샷

### 도구 호출 데모
Read, Write, Edit 작업에 대한 상세 정보를 포함한 Claude의 도구 사용 보기.

![도구 호출 데모](docs/screenshots/tool-calls-demo.png)

### @ 멘션 파일 검색
@ 멘션 기능을 사용하여 프로젝트의 파일을 빠르게 참조.

![@ 멘션 파일 검색](docs/screenshots/at-mention-file-search.png)

### 모델 선택기
다양한 Claude 모델 간 전환 (Opus 4.5, Sonnet 4.5, Haiku 4.5).

![모델 선택기](docs/screenshots/model-selector.png)

### 권한 요청
파일 쓰기 작업을 위한 보안 인증 대화상자.

![권한 요청](docs/screenshots/permission-request.png)

### 사용자 질문 대화상자
모델 선택 및 기능 설정을 지원하는 Claude가 사용자에게 질문하는 대화형 대화상자.

![사용자 질문 대화상자](docs/screenshots/user-question-dialog.png)

## 📦 설치

### 방법 1: JetBrains Marketplace (권장)
1. JetBrains IDE 열기
2. **설정** → **플러그인** → **Marketplace**로 이동
3. "**Claude Code Plus**" 검색
4. **설치** 클릭 후 IDE 재시작

### 방법 2: GitHub Release (수동)
1. [Releases](https://github.com/touwaeriol/claude-code-plus/releases)에서 최신 `jetbrains-plugin-x.x.x.zip` 다운로드
2. IDE에서: **설정** → **플러그인** → ⚙️ → **디스크에서 플러그인 설치...**
3. 다운로드한 zip 파일 선택 후 IDE 재시작

## 🔧 요구사항

- **JetBrains IDE**: IntelliJ IDEA 2024.2 - 2025.3.x (Build 242-253)
- **Node.js**: v18 이상 ([다운로드](https://nodejs.org/)) - `node` 명령어가 PATH에서 사용 가능해야 함
- **Claude Code**: 최초 설정 필요
  - 터미널에서 실행: `npx @anthropic-ai/claude-code`
  - 안내에 따라 인증 완료
  - 자세한 설정 가이드는 [공식 문서](https://docs.anthropic.com/en/docs/claude-code/getting-started) 참조

> **참고**: 플러그인에 Claude CLI가 내장되어 있어 별도 CLI 설치가 필요 없습니다!

### API 키 사용 (대안)

Claude Code 구독 대신 자체 Anthropic API 키를 사용하려면 [cc-switch](https://github.com/farion1231/cc-switch)로 설정할 수 있습니다:

```bash
npx cc-switch
```

이 도구는 Claude Code의 다양한 인증 방법 간 전환을 도와줍니다.

## 🚀 빠른 시작

1. 위의 설치 단계에 따라 플러그인 설치
2. Claude Code가 설치되고 인증되었는지 확인
3. **Claude Code Plus** 도구 창 열기 (오른쪽 사이드바)
4. Claude와 대화 시작!

### 팁
- `@`를 사용하여 파일을 멘션하고 컨텍스트로 추가
- 도구 출력의 파일 경로를 클릭하면 편집기에서 열기
- 도구 카드(Read/Write/Edit)를 클릭하여 diff 미리보기 표시
- `ESC`를 눌러 AI 생성 중단
- 모델 선택기를 사용하여 언제든지 모델 전환 (Opus/Sonnet/Haiku)
- 키보드 단축키:
  - `Ctrl+J` - 빠른 작업
  - `Ctrl+U` - 일반 작업
  - `Enter` - 메시지 전송
  - `Shift+Enter` - 입력창 줄바꿈

## 🤝 기여하기

기여를 환영합니다! 자유롭게 Pull Request를 제출해 주세요.

## 📝 라이선스

이 프로젝트는 MIT 라이선스에 따라 라이선스가 부여됩니다 - 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 🔗 링크

- [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28343-claude-code-plus)
- [GitHub 저장소](https://github.com/touwaeriol/claude-code-plus)
- [이슈 트래커](https://github.com/touwaeriol/claude-code-plus/issues)
- [변경 로그](https://github.com/touwaeriol/claude-code-plus/releases)

---

<p align="center">
  ❤️을 담아 <a href="https://github.com/touwaeriol">touwaeriol</a>이 제작
</p>
