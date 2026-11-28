# AI 브리핑 · Spring AI로 Claude 연결 (1 ~ 3단계)

> 작성 2026-11-28 · 브랜치 `lyh-ragtest`
> AI 안전 브리핑(RAG)을 만들기 위해 **서버가 Claude에게 질문하고 답을 받을 수 있게 준비한 과정**을 정리한 문서.
> 아직 브리핑 기능 자체는 없다. 이 단계는 "연결이 되는지" 확인하는 단계다.

---

## 한눈에 보기

```
[우리 서버] ──(전화기: Spring AI)──(열쇠: API 키)──→ [Claude]
                                                      │
[로그]  ←──────────── 답 · 걸린 시간 · 토큰 수 ←────────┘
```

| 단계 | 하는 일 | 비유 | 파일 | 상태 |
|---|---|---|---|---|
| 1 | Spring AI 넣기 | 전화기 주문 | `server/pom.xml` | ✅ 완료 |
| 2 | 설정 적기 | 누구에게 걸지 메모 + 열쇠 | `application.properties`, `application-local.properties` | ⚠️ 키만 넣음, 모델 설정 남음 |
| 3 | 시험 통화 | Claude에게 질문 하나 | `brief/AiPingRunner.java` | ✅ 동작 확인 (Haiku 4.5로) |

---

## 0. 시작 전에 정한 것

### 왜 Spring AI인가
- 서버가 Spring Boot라서, Spring 방식(설정 파일 + 자동 준비)으로 Claude를 쓸 수 있다
- Spring AI 2.x의 Anthropic 부품은 **안에서 Anthropic 공식 Java SDK를 쓴다** (공식 문서: "uses the official Anthropic Java SDK to connect to Claude") → 새 모델 기능이 그대로 따라온다

### 왜 버전 2.0.3인가
- 우리 서버: **Spring Boot 4.0.8 + Java 17**
- Spring AI 1.x는 Boot 3용이라 맞지 않는다
- 공식 문서(시작하기 페이지)에 이렇게 적혀 있다:
  > "Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x."
- 최신은 2.1.0(2026-11 기준)이지만, 2.1.0이 Boot 4.0.x를 지원하는지는 적혀 있지 않다 → **지원이 명시된 2.0.x의 최신인 2.0.3**을 골랐다

### 일부러 넣지 않은 부품
| 부품 | 넣지 않은 이유 |
|---|---|
| `spring-ai-starter-vector-store-pgvector` | 자기 테이블 모양(id, content, metadata JSON, embedding)을 기대하고 기본 DB에 붙는다. 우리 벡터 테이블(`safety_doc_chunk`, 칸: `topic[]` · `companion` 등)과 맞지 않음 → 검색은 SQL을 직접 쓴다 (4 ~ 6단계) |
| `spring-ai-starter-model-openai` | 대화 모델이 둘이 되어 설정이 꼬인다. 질문 임베딩(OpenAI)은 한 번 부르는 일이라 `RestClient`로 짧게 짠다 (5단계) |

### 공식 문서에서 확인한 사실 (Spring AI Anthropic)
| 항목 | 내용 | 우리에게 주는 뜻 |
|---|---|---|
| 기본 모델 | `claude-haiku-4-5` | **모델 이름을 꼭 직접 적어야 한다** |
| 기본 최대 길이 | `max-tokens` 4096 | 생각하는 분량까지 들어가서 넉넉히 준다 |
| `temperature` | "Deprecated and ignored with a WARN log" — 보내지 않음 | Opus 5.5는 이 값을 받으면 400 오류인데, Spring AI가 안 보내서 문제없음 |
| 키 | 설정이 없으면 SDK가 환경변수 `ANTHROPIC_API_KEY`를 찾음 | 배포 서버는 환경변수로 넣으면 된다 |
| 시간 제한 · 재시도 | `timeout` 60초, `max-retries` 2 | 기본값 그대로 |
| 생각(thinking) · effort | 설정 파일로는 못 정하고 자바 옵션(`AnthropicChatOptions`)으로만 | 3단계에서는 정하지 않음 (아래 3-5) |
| 키 없이 서버가 켜지는지 | **문서에 없음** | 직접 확인해야 함 (확인 못 함) |

---

## 1단계. Spring AI 넣기 (`server/pom.xml`) — ✅ 완료

`pom.xml`은 "이 서버를 만들 때 필요한 부품 목록"이다. 두 가지를 넣었다.

### 1-1. 버전표(BOM) — `<properties>` 블록 바로 아래에 새로 추가
```xml
	<!-- Spring AI 부품들의 버전을 한곳에서 맞춘다.
	     2.0.x 를 쓰는 이유: 공식 문서에 "2.0.x 는 Spring Boot 4.0.x · 4.1.x 지원" (우리 서버는 4.0.8) -->
	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.ai</groupId>
				<artifactId>spring-ai-bom</artifactId>
				<version>2.0.3</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>
```
- **BOM = 서로 맞는 버전끼리 묶은 세트표.** Spring AI는 부품이 여러 개라 버전이 서로 맞아야 한다
- 여기서 한 번 정하면 아래 부품에는 버전을 적지 않는다
- 이 프로젝트 pom에는 원래 `<dependencyManagement>`가 없어서 새로 만들었다

### 1-2. 실제 부품 — `<dependencies>` 안, firebase 아래
```xml
		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-starter-model-anthropic</artifactId>
		</dependency>
```
- Claude와 대화하는 부품. "starter"라서 넣기만 하면 서버가 켜질 때 Spring이 알아서 준비해 준다 (`ChatClient.Builder` 등)

### 1-3. 확인 결과 (2026-11-28)
| 확인 | 명령 (`server/`에서) | 결과 |
|---|---|---|
| 부품이 들어왔나 | `.\mvnw.cmd dependency:tree "-Dincludes=org.springframework.ai,com.anthropic"` | ✅ `spring-ai-starter-model-anthropic:2.0.3`, 그 안에 `com.anthropic:anthropic-java-core:2.75.0` (공식 SDK) |
| 컴파일 · 기존 테스트 | `.\mvnw.cmd test` | ✅ **222개 모두 통과** (실패 0, 오류 0) |
| 서버가 켜지나 | (위 테스트 중 `ServerApplicationTests`) | ✅ 서버 전체를 켜는 테스트 통과 → Spring AI 2.0.3이 Boot 4.0.8에서 켜짐 |

- 테스트 로그 중간의 안전점수 `ERROR` 한 줄("카카오 길찾기를 부를 수 없어 멈춥니다")은 기존 테스트가 실패 상황을 일부러 만든 것이라 정상이다

---

## 2단계. 설정 적기 — ⚠️ 키만 넣음, 모델 설정 남음

### 2-1. 나누는 기준: 비밀은 따로, 나머지는 공유
| 종류 | 어디에 | git | 예 |
|---|---|---|---|
| **비밀** (새면 안 됨) | 작업 PC: `application-local.properties` / 배포 서버: 환경변수 | 안 올라감 | API 키 |
| **모두 같아야 하는 설정** | `application.properties` | 올라감 (팀이 공유) | 모델 이름, 최대 길이 |

- `application-local.properties`는 `.gitignore`에 들어 있다 (확인함: `.gitignore:4`)
- `application.properties`에 `spring.profiles.active=local`이 있어서, 서버가 켜질 때 local 파일도 함께 읽는다. **같은 설정 이름이면 local 값이 이긴다**

### 2-2. `application.properties` (git에 올라감) — 맨 아래에 추가 **(아직 안 넣음)**
```properties
# --- AI 브리핑 (Claude, Spring AI) ---
# 키 값은 여기 쓰지 않는다. 작업 PC는 application-local.properties, 서버는 환경변수 ANTHROPIC_API_KEY 에서 읽는다
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:}
# 꼭 적는다: 안 적으면 Spring AI 기본값(claude-haiku-4-5)이 쓰인다
spring.ai.anthropic.chat.model=claude-opus-5-5
# 생각하는 분량도 이 한도에 들어가서, 기본 4096 이면 답이 잘릴 수 있다
spring.ai.anthropic.chat.max-tokens=8000
```

| 줄 | 뜻 |
|---|---|
| `api-key=${ANTHROPIC_API_KEY:}` | "환경변수 `ANTHROPIC_API_KEY`에서 키를 가져와라, 없으면 빈 값." 키를 넣을 **자리만** 만든다. 콜론 뒤를 비워 두면 환경변수가 없어도 설정을 읽다가 멈추지 않는다 |
| `chat.model=claude-opus-5-5` | 어느 Claude에게 물을지. 설정 이름에 **`.chat.`이 꼭 들어간다** (`spring.ai.anthropic.model`은 틀린 이름) |
| `chat.max-tokens=8000` | 답의 최대 길이. Claude가 속으로 생각하는 분량도 들어가서 넉넉히. 3줄 브리핑에는 충분 |

**적지 않는 것**
- `temperature` — Spring AI가 무시하고, Opus 5.5는 이 값을 받으면 오류
- 생각(thinking) · effort — Opus 5.5 기본값(알아서 생각, effort MEDIUM)으로 충분. 바꾸면 9단계에서 코드로

### 2-3. `application-local.properties` (git에 안 올라감) — 키 한 줄만 ✅
```properties
spring.ai.anthropic.api-key=sk-ant-api03-...
```
- **따옴표 없이, 줄 끝 공백 없이.** properties 파일은 따옴표와 끝 공백까지 키 값으로 읽는다
- **모델 이름은 여기 적지 않는다.** local이 공유 설정을 덮어써서, 내 PC와 배포 서버가 다른 모델로 돌아도 알아채기 어렵다
- 프로젝트 규칙상 Claude(AI 도우미)는 이 파일을 열거나 고치지 않는다 → 사람이 직접 넣는다

### 2-4. 두 파일이 합쳐지는 방식
```
서버가 켜질 때
  ① application.properties        api-key = (빈 값)   model = claude-opus-5-5   max-tokens = 8000
  ② application-local.properties  api-key = sk-ant-…
                                       ↓ 같은 이름이면 ②가 이김
  최종 (내 PC)                     api-key = sk-ant-…  model = claude-opus-5-5   max-tokens = 8000

배포 서버에는 ②가 없음 → 환경변수 ANTHROPIC_API_KEY 가 ①의 빈 자리를 채움
```

### 2-5. 왜 local에만 적으면 안 되나
| 어디서 실행 | local 파일 | local에만 모델을 적었을 때 |
|---|---|---|
| 내 PC | 있음 | 내가 적은 모델 |
| 팀원 PC | 없음 | 기본값 Haiku 4.5, 최대 길이 4096 |
| 배포 서버 | 없음 | 기본값 Haiku 4.5, 최대 길이 4096 |

→ 내 PC에서 시험한 결과와 발표 때 서버 결과가 **다른 모델**로 만들어진다. 오류가 안 나서 알아채기 어렵다.

### 2-6. 모델 고르기
| 모델 | 100만 토큰당 (입력 / 출력) | 특징 |
|---|---|---|
| `claude-opus-5-5` | $4 / $20 | 기본 추천. 여러 조건을 묶어 판단하는 일에 가장 강함 |
| `claude-haiku-5-5` | $0.10 / $0.50 | 현재 Haiku. 빠르고 쌈 |
| `claude-haiku-4-5` (지금 쓰이는 기본값) | $1 / $5 | 이전 세대. Haiku 5.5보다 비싸고 오래됨 → 고를 이유가 없음 |

- 브리핑 한 번이 수천 토큰 수준이라 시연 비용은 어느 모델이든 크지 않다
- 정하기 어려우면 Opus 5.5로 시작하고, 평가 때 Haiku 5.5와 같은 시나리오로 비교 → 그 비교표도 발표 자료가 된다

---

## 3단계. Claude 시험 통화 (`brief/AiPingRunner.java`) — ✅ 동작 확인

### 3-1. 왜 이런 방식인가
- 기존 안전점수 계산(`--score`)과 같은 방식: **서버를 특정 인자(`--ai-ping`)로 켤 때만 한 번** 부른다
- API 주소로 열면 아무나 불러서 돈이 나갈 수 있고, 평소 실행·테스트마다 부르면 돈이 샌다
- 확인이 끝나면 지우는 **임시 파일**이다. 9단계에서 진짜 브리핑 코드가 같은 `ChatClient`를 쓴다

### 3-2. 코드 (`server/src/main/java/com/campin/brief/AiPingRunner.java`)
```java
package com.campin.brief;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// 임시: Claude 연결 확인용 (AI 브리핑 3단계). 확인이 끝나면 이 파일을 지운다
// 서버를 --ai-ping 인자로 켤 때만 한 번 부른다 → 평소 실행이나 테스트에서는 돈이 나가지 않는다
@Slf4j
@Component
public class AiPingRunner implements ApplicationRunner {

    private final ChatClient chatClient;

    // ChatClient.Builder 는 Spring AI 가 서버를 켤 때 만들어 준다 (②③의 모델 · 키 설정이 들어 있음)
    public AiPingRunner(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("ai-ping")) return; // 인자가 없으면 아무것도 안 함

        long start = System.currentTimeMillis();
        ChatResponse res = chatClient.prompt()
                .system("너는 캠핑 안전 안내자다. 한 문장으로 쉽게 답한다.")
                .user("캠핑 전날 꼭 확인할 것 하나만 알려줘.")
                .call()
                .chatResponse();
        long ms = System.currentTimeMillis() - start;

        // 실제로 어떤 모델이 답했는지, 시간 · 토큰(비용)을 남긴다. 키는 로그에 남기지 않는다
        Usage usage = res.getMetadata().getUsage();
        log.info("AI 확인: 모델={}, 걸린 시간={}ms, 입력 토큰={}, 출력 토큰={}",
                res.getMetadata().getModel(), ms, usage.getPromptTokens(), usage.getCompletionTokens());
        log.info("AI 답: {}", res.getResult().getOutput().getText());
    }
}
```

### 3-3. 코드 읽기
| 코드 | 쉬운 말 |
|---|---|
| `@Component` + `ApplicationRunner` | 서버가 다 켜진 뒤 한 번 실행되는 일 |
| `args.containsOption("ai-ping")` | `--ai-ping` 인자가 있을 때만 진행 |
| `ChatClient.Builder` → `ChatClient` | Claude에게 말을 거는 창구. Spring AI가 설정(모델 · 키)을 넣어 만들어 줌 |
| `.system("...")` | Claude에게 주는 **역할과 규칙** ("너는 캠핑 안전 안내자다") |
| `.user("...")` | 실제 **질문** |
| `.call().chatResponse()` | 보내고 답을 통째로 받음 (답 글 + 정보) |
| `getMetadata().getModel()` | 실제로 **어떤 모델이 답했는지** → 설정이 먹었는지 확인 |
| `getMetadata().getUsage()` | 입력 · 출력 토큰 수 = **비용** |
| `getResult().getOutput().getText()` | 답 글 |

### 3-4. 실행
```powershell
# server/ 에서
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--ai-ping"
```
로그를 확인한 뒤 `Ctrl+C`로 서버를 끈다.

### 3-5. 실제 결과 (2026-11-28 14:05)
```
AI 확인: 모델=claude-haiku-4-5-20251001, 걸린 시간=1805ms, 입력 토큰=68, 출력 토큰=57
AI 답: 날씨 예보를 확인해서 기온, 강수 확률, 바람을 미리 파악하고 그에 맞게 짐을 챙기세요.
```
| 항목 | 값 | 뜻 |
|---|---|---|
| 모델 | `claude-haiku-4-5-20251001` | **기본값이 쓰임** → 2-2의 모델 설정이 아직 안 들어갔다 |
| 걸린 시간 | 1.8초 | 짧은 질문 기준. Opus 5.5는 더 걸릴 수 있다 |
| 토큰 | 입력 68 · 출력 57 | 이번 한 번의 비용은 아주 적다 |
| 답 | 한 문장 | system 규칙("한 문장으로")을 잘 따름 |

**결론**: 키 · Spring AI · Claude까지 **연결은 동작한다.** 2-2를 넣고 다시 실행해 `모델=claude-opus-5-5…`가 나오면 1 ~ 3단계가 모두 끝난다.

### 3-6. 로그 보는 법
| 보이는 것 | 뜻 · 할 일 |
|---|---|
| `모델=claude-opus-5-5…` | 정상 |
| `모델=claude-haiku-4-5…` | 2-2 모델 줄이 없거나 설정 이름 오타 (`.chat.` 빠짐 등) |
| 401 `invalid x-api-key` | 키 문제 → 4-1 |
| 400 | 옵션 문제 (생각 끄기 · 생각량 지정을 넣었는지) |
| 400 `credit balance is too low` | 키는 맞지만 계정에 크레딧이 없음 → 콘솔 Billing |
| 403 | 그 키의 워크스페이스에 권한이 없음 |
| 서버가 안 켜짐 (Spring AI 오류) | 버전 문제 → 오류 메시지 확인 |

### 3-7. 생각(thinking) · effort를 정하지 않은 이유 (Opus 5.5 주의)
| 옵션 (`AnthropicChatOptions`) | Opus 5.5에서 |
|---|---|
| `.thinkingDisabled()` (생각 끄기) | **400 오류** → 쓰지 않음 |
| `.thinkingEnabled(10000L)` (생각량 직접 지정) | **400 오류** → 쓰지 않음 |
| `.thinkingAdaptive()` 또는 아무것도 안 정함 | 정상. Opus 5.5는 기본이 "알아서 생각" |
| `.effort(OutputConfig.Effort.LOW)` | 문서에 있음 (LOW · MEDIUM · HIGH · MAX). 3줄 브리핑이 느리면 9단계에서 LOW로 |

- Spring AI 문서 예제에 `thinkingEnabled(...)`가 나오지만, 그대로 따라 쓰면 Opus 5.5에서 오류가 난다

---

## 4. 겪은 문제와 해결

### 4-1. 401 `invalid x-api-key`
```
com.anthropic.errors.UnauthorizedException: 401: {"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}, ...}
```
**뜻**: 키가 Anthropic까지 전달은 됐는데 받아 주지 않음. 코드 · Spring AI 문제가 아니라 **키 값 문제**.

| # | 확인할 것 | 맞는 모습 |
|---|---|---|
| 1 | 키 종류 | Anthropic 콘솔(console.anthropic.com → Settings → API Keys)에서 만든 `sk-ant-api03-…`. **OpenAI 키(`sk-proj-…`), Claude.ai 구독, Claude Code 로그인은 API 키가 아님** |
| 2 | 적는 모양 | 따옴표 없이, 줄 끝 공백 없이 한 줄 |
| 3 | 잘린 키 | 콘솔은 키를 만들 때 한 번만 보여 줌. 덜 복사됐으면 새로 만듦 |
| 4 | 설정 이름 | `spring.ai.anthropic.api-key` |
| 5 | 파일 위치 · 저장 | `server/src/main/resources/application-local.properties`, 저장했는지. 못 읽으면 빈 값이 가서 같은 오류가 날 수 있음 |
| 6 | 키 상태 | 콘솔에서 비활성 · 삭제되지 않았는지 |

**키를 보지 않고 원인 좁히기** (필요할 때만 `AiPingRunner`에 잠깐 추가, 확인 후 지움)
```java
import org.springframework.beans.factory.annotation.Value;

    // 임시: 키가 어떤 모양으로 읽혔는지만 본다 (값은 찍지 않는다)
    @Value("${spring.ai.anthropic.api-key:}")
    private String apiKey;

    // run() 안, long start = … 바로 위
    log.info("키 확인: 비었나={}, 길이={}, sk-ant-api 로 시작={}, 앞뒤 공백={}, 따옴표={}",
            apiKey.isEmpty(), apiKey.length(), apiKey.startsWith("sk-ant-api"),
            !apiKey.equals(apiKey.strip()), apiKey.contains("\""));
```
| 로그 | 할 일 |
|---|---|
| `비었나=true` | local 파일을 못 읽음 → 위치 · 저장 · 설정 이름 |
| `sk-ant-api 로 시작=false` | 다른 종류의 키 → 콘솔에서 API 키 새로 만들기 |
| `앞뒤 공백=true` / `따옴표=true` | 공백 · 따옴표 지우기 |
| 전부 정상인데 401 | 잘렸거나 비활성 → 새 키 |

**해결**: 콘솔에서 새 키를 만들어 바꿈 → 시험 통화 성공 (3-5)

### 4-2. 키를 놓쳤을 때 — 새로 만들면 된다
1. console.anthropic.com → **Settings → API Keys** → **Create Key** (이름 예: `campine-local`)
2. 나온 키를 **바로** 복사해 `application-local.properties`에 붙여 넣기
3. 예전 키는 **삭제 · 비활성화** (어디 남아 있을지 모르는 키를 없앰)
- 키는 비밀번호 관리자 · 개인 메모에 같이 보관. 메신저 · 팀 채팅방에는 올리지 않는다
- 용도별로 따로: `campine-local`(작업 PC), `campine-server`(배포 서버) → 하나가 새도 그것만 지움

### 4-3. 키를 `application.properties`에 직접 넣었던 일
- 한때 `application.properties`의 `api-key=` 뒤에 키 값이 직접 들어가 있었다 (커밋 전에 발견, 지금은 되돌려짐)
- 이 파일은 **git이 관리**한다 → 커밋 · 푸시하면 GitHub에 키가 공개된다. 공개 저장소의 키는 자동으로 수집되어 남이 쓰고, 요금은 우리 계정에 나온다
- 루트 `CLAUDE.md` 규칙 위반: "비밀번호, API 키 등을 코드나 `application.properties`에 직접 작성 (환경변수 사용)"
- **해결**: 키는 `application-local.properties`(git 제외)로. 혹시 어딘가에 올렸다면 콘솔에서 그 키를 폐기하고 새로 받는다

### 4-4. 모델이 Haiku 4.5로 나온 일
- 원인: `application.properties`에 Spring AI 설정이 없어 **Spring AI 기본값**이 쓰임
- 해결: 2-2 블록을 넣는다 (local 파일이 아니라 공유 파일에)

---

## 5. 확인 못 한 것

| 항목 | 이유 · 언제 확인 |
|---|---|
| Opus 5.5로 시험 통화 | 2-2를 넣은 뒤 다시 실행 |
| **키가 없을 때 서버가 켜지는지** | 이 PC는 local 파일에 키가 있어 테스트가 그 키로 켜짐. 키 없는 팀원 PC · 배포 서버에서 문제가 될 수 있음 → 배포 전 확인. 안 켜지면 우리 설정(예: `campin.brief.ai.enabled`)으로 AI 부분만 끄게 한다 |
| Opus 5.5에서 걸리는 시간 · 비용 | 시험 통화 후 로그로 |
| JSON으로 답 받는 방법 | 문서상 `outputSchema`(JSON 모양 직접 적기, `additionalProperties: false` 필요) 또는 `ChatClient`의 `.entity(클래스)` → 9단계에서 확인 |
| `effort` 옵션에 Opus 5.5의 모든 단계가 있는지 | 문서에는 LOW · MEDIUM · HIGH · MAX만 나옴 |

---

## 6. 1 ~ 3단계 마무리 체크

- [x] `pom.xml`에 Spring AI 2.0.3 (BOM + Anthropic 스타터)
- [x] 의존성 확인, 테스트 222개 통과, 서버 켜짐
- [x] `application-local.properties`에 키
- [x] 시험 통화 성공 (키 · 연결 정상)
- [ ] `application.properties`에 2-2 블록 (모델 · 최대 길이 · 키 자리)
- [ ] 다시 시험 통화 → `모델=claude-opus-5-5…` 확인
- [ ] `AiPingRunner.java` 지우기 (확인용)
- [ ] 커밋 전 확인: `application-local.properties`가 커밋 목록에 없는지 (git 제외 파일이라 안 나오는 게 정상), `application.properties`에 키 값이 없는지
- [ ] `server/CLAUDE.md` "상세 의존성 목록"에 Spring AI 한 줄 추가 (예: `spring-ai-starter-model-anthropic` 2.0.3 — AI 브리핑, `brief/`)

---

## 7. 다음: 4단계부터

| 단계 | 하는 일 | 필요한 것 |
|---|---|---|
| 4 | 벡터 DB(`campine_vector`) 연결 — `brief/SafetyDocRepository` 안에서만 접속 (서비스 DB는 건드리지 않음). **✅ 완료** (2026-11-28, 작업 PC → SSH 터널 → 배포 서버 DB: 120개, JD-001 근처 MW-002 0.508) | 배포 서버의 `campine_vector` + SSH 터널, 환경변수 `VECTOR_DB_URL` · `VECTOR_DB_USERNAME` · `VECTOR_DB_PASSWORD`. 확인: `--vector-ping` |
| 5 | 질문 임베딩 — OpenAI `text-embedding-3-small` (조각과 같은 모델). `brief/EmbeddingClient` + `SafetyDocRepository.findSimilar`. **✅ 완료** (2026-11-28, 실제 호출 확인) | **OpenAI 키** — 환경변수 이름 `OPENAI_API_KEY` (`application-local.properties`에). 확인: `--vector-ping` |
| 6 | 검색 — 꼬리표로 거르기 + 뺄 목록 제외 + 거리 순 상위 5개. `brief/SafetyDocFilter` + `SafetyDocRepository.search`. **✅ 완료** (2026-11-28). 계절은 반대 계절만 빼고, 뺄 목록은 12개 (`docs/progress.md` 참고) | 확인: `--vector-ping` ③ |
| 7 | 상황 모으기 — `brief/BriefContextService` → `BriefContext`(상황 + 검색 질문 + `SafetyDocFilter`). 진단은 화면이 요청에 담아 보냄(`BriefSurvey`). **✅ 완료** (2026-11-28) | 확인: `--vector-ping` ④ |
| 8 | 프롬프트 — `brief/BriefPrompt` (system 규칙은 이 파일의 `SYSTEM` 이 정본, user = `<situation>` + `<documents>`). AI 는 대비 행동 최대 3줄만, 날씨 줄은 코드. **✅ 완료** (2026-11-28) | 확인: `--vector-ping` ⑤ (Claude 는 안 부름) |
| 9 | Claude 부르기 — `brief/BriefAiClient`(`ChatModel`) → `BriefAnswer`. **✅ 동작 확인** (2026-11-28) — 다만 답한 모델이 Haiku 4.5 (모델 설정 확인 필요) | 확인: `--vector-ping --ai-brief` |
| 10 | 검사 · 대체 — `brief/BriefCheck`(근거 · 숫자 · 보증하는 말 · 형식, 어기면 버림) + `BriefService.make`(전체를 잇고 실패하면 `BriefResult.fallback`). **✅ 완료** (2026-11-28, 실제 1회 3줄 통과) | 확인: `--vector-ping --ai-brief` |
| 11 | API — `POST /api/member/camp-plans/{planId}/brief` (`brief/BriefController`), 본문 `{ survey }`, 끝난 캠핑 400, 저장 안 함. **✅ 완료** (2026-11-28, 테스트로 확인 · 실제 서버 호출은 확인 못 함) | 로그인 필요 |
| 12 | 화면 — 앱 내 캠핑 카드 초인종 브리핑: 날씨 줄(코드) + 대비 행동(AI, 진단 줄 자리) + 근거 기관 · 연도 + 준비물 줄(코드). `ai: false` · 실패면 지금 3줄. **✅ 코드 완료** (2026-11-28, `npm run build` 통과 · 실제 화면은 확인 못 함) | `client/src/utils/campBrief.js`, `views/mobile/MobileMyCampingView.vue` |
| 13 ~ 14 | 테스트 · 배포 | 서버 환경변수 `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, 벡터 DB 정보 |

이 단계에서 만든 `ChatClient`가 9단계에서 그대로 쓰인다. 그때 `.system()`에는 안전 규칙(찾은 자료 안에서만, 수치 만들지 않기, 근거 붙이기)을, `.user()`에는 상황과 찾아온 안전자료를 넣는다.
