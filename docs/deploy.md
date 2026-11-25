# 배포 기록 (네이버 클라우드)

- 시작한 날: 2026-11-24
- CAMPINE을 네이버 클라우드 서버에 올리는 과정을 **무엇을 · 어떤 순서로 · 왜 했는지** 적는다. 단계를 끝낼 때마다 이어서 쓴다
- 전체 작업 기록의 정본은 `docs/progress.md`
- **비밀번호, `.pem` 키 내용, API 키, 공용 DB 접속 주소는 이 문서에 적지 않는다.** 따라 할 때 필요한 서버 공인 IP, 서버 DB의 이름과 계정 이름은 적는다 (저장소를 공개한다면 다시 정한다)

## 처음 보는 팀원은 여기부터

**1 ~ 14단계를 순서대로 하면 웹사이트가 인터넷에 공개된다.** 15(앱) · 16(벡터 DB)은 웹사이트를 여는 데 필요하지 않다.

### 먼저 고르기

| 하고 싶은 일 | 볼 곳 |
|---|---|
| 새 서버를 처음부터 만들기 | 1 → 14를 순서대로. **13-4 · 13-5 · 13-6은 건너뛴다** (다시 올릴 때 쓰는 방법) |
| 지금 있는 서버의 화면 고치기 | 13-4만. 서버를 새로 만들지 않는다 |
| 지금 있는 서버의 백엔드 고치기 | 13-6만. 서버를 새로 만들지 않는다 |

- 지금 있는 서버에 접속하려면 서버 담당자에게 **ACG 22번에 내 IP 추가**를 부탁한다 (4단계)
- 서버를 새로 만들면 **요금이 나온다.** 만들기 전에 팀에 알리고, 다 쓰면 서버 · 스토리지 · 공인 IP를 반납한다

### 시작 전에 준비할 것

| 준비할 것 | 어디서 | 언제 쓰나 |
|---|---|---|
| 네이버 클라우드 계정 (결제 수단 등록) | 네이버 클라우드 | 1단계 |
| 저장소 권한 (`LX-team3/safecamping`) | 팀 | 11-0 |
| Tailscale 초대, 공용 DB 읽기 계정 | 공용 DB 주인. **복사해도 되는지 허락도 받는다** (회원 · 가족 위치 · 아이 건강 카드가 들어 있다) | 10-2 |
| 카카오 지도 키(JavaScript) · REST 키, 카카오 콘솔 권한 | 카카오 콘솔을 관리하는 팀원 | 11-0, 12-1, 13-3, 14-5 |
| 기상청 · 재난문자 · 한강홍수통제소 키 | 팀 | 12-1 (없어도 서버는 켜지지만 그 기능은 안 되거나 더미로 나온다) |
| Firebase 서비스 계정 키 파일 | 팀 | 12-2 (없으면 푸시만 안 된다) |

키와 비밀번호는 git, 공개 채팅, 이 문서에 **올리지 않는다.**

### 내 값으로 바꿔 쓸 것

명령에 적힌 값은 우리 서버의 것이다. 새 서버를 만든다면 **자기 값으로 바꿔서** 실행한다.

| 문서에 적힌 값 | 바꿀 값 |
|---|---|
| `49.50.137.194` | 내 서버의 공인 IP (3단계) |
| `campine.duckdns.org` | 내가 만든 도메인 (14-1). `campine`은 이미 쓰고 있어 다시 만들 수 없다 |
| `C:\Users\LX` | 내 PC의 사용자 폴더. PowerShell에서 `$env:USERPROFILE`을 치면 나온다 |
| `C:\mylx\safecamping` | 내 PC에서 프로젝트를 받은 폴더 |
| sha256(지문) 값 | 내가 만든 파일의 값. 다시 만들면 값이 달라지는 것이 정상이다 |
| 날짜, 개수, 커밋 번호 | 2026-11-24에 확인한 기록. 똑같지 않아도 된다 |

### 명령을 어디에 치나

| 코드 위의 표시 | 치는 곳 | 화면 앞부분 모양 |
|---|---|---|
| `powershell` · "작업 PC" | 내 PC의 PowerShell | `PS C:\...>` |
| `bash` · "서버" | `ssh`로 접속한 서버 창 | `root@campine-server:~#` |
| `sql` | 서버 창에서 연 psql (10-4의 비교용 SQL만 pgAdmin) | `postgres=#` |

- 문서의 **한 일 · 결과**는 우리가 한 기록이고, **따라 하기**와 명령이 직접 할 일이다
- 오류가 나면 다음으로 넘어가지 말고 아래 **겪은 문제와 해결**을 먼저 본다

### `nano`로 파일을 만들고 저장하는 법

12 · 13단계에서 쓴다. `nano 파일경로` → 내용 붙여 넣기(마우스 오른쪽 클릭) → `Ctrl+O` → `Enter`(저장) → `Ctrl+X`(나가기)

## 지금 어디까지 왔나

| 순서 | 할 일 | 상태 |
|---|---|---|
| 1 | VPC 만들기 (우리 전용 내부망) | 완료 |
| 2 | Subnet 만들기 (내부망 안의 구역) | 완료 |
| 3 | 서버 만들기 | 완료 |
| 4 | ACG(방화벽) 설정 | 완료 (22번이 내 IP만인지는 콘솔에서 확인 남음) |
| 5 | 서버에 접속하기 (SSH) | 완료 |
| 6 | 서버 업데이트, 시간대 설정 | 완료 |
| 7 | Java · Nginx 설치 | 완료 |
| 8 | PostgreSQL · PostGIS 설치 | 완료 |
| 9 | 브라우저로 접속 확인 | 완료 |
| 10 | DB 만들고 데이터 옮기기 | 완료 |
| 11 | 프론트 · 백엔드 빌드해서 올리기 | 완료 |
| 12 | 환경변수 설정, 백엔드 자동 실행 | 완료 |
| 13 | Nginx에 우리 서비스 연결 | 연결 완료, 지도 나옴. **로그인 · 저장 확인이 다음 할 일** |
| 14 | 도메인과 HTTPS | HTTPS 적용 완료. **카카오 새 주소 등록, 브라우저 확인 남음** |
| 15 | 앱(APK) 만들기 | 빌드 · 폰 설치 완료. **폰에서 기능 확인 남음** |
| 16 | RAG용 벡터 DB(pgvector) | **순서만 정리함. 서버에서 실행한 결과를 아직 받지 못함** |

### 이제 할 일 (순서대로)

남은 일은 **여기에만** 적고, 끝나면 체크한다.

**1. 배포 주소에서 기능 확인** (시험용 계정)
- [ ] 로그인 · 저장 (CORS, 11-3)
- [ ] 재난지도 · 메타버스가 화면에 그려지는지
- [ ] 사진 올리기

**2. 외부 API**
- [ ] 행안부 재난문자: 공유플랫폼에 서버 IP 등록 (등록한 IP에서만 불림, 안 하면 더미로 대신). 하루 한도 99회인데 서버 하나가 최대 72회라, 팀 PC와 같은 키를 쓰면 넘을 수 있음
- [ ] 카카오 REST 키: 콘솔에 허용 IP를 정해 두었다면 서버 IP 추가
- [ ] 카카오 길찾기 · 기상청 · 재난문자 · 수위 키 동작 확인

**3. 자동 실행**
- [ ] 서버를 다시 켜도 백엔드가 자동으로 켜지는지

**4. 도메인과 HTTPS (14단계)**
- 도메인은 **DuckDNS**(무료)를 쓰기로 정함 (2026-11-24)
- [x] DuckDNS에서 이름을 만들고 서버 IP에 연결: `campine.duckdns.org` → `49.50.137.194` (2026-11-24, 작업자가 `nslookup`으로 확인)
- [x] Nginx에 도메인 적기 → certbot으로 HTTPS 인증서 받기 (14-2 · 14-3)
- [ ] 자동 갱신 시험 `certbot renew --dry-run`
- [ ] 카카오에 새 주소 `https://campine.duckdns.org` 등록
- [ ] 로그인 · 저장 · 현재 위치 다시 확인

**5. 앱**
- [x] 이름을 `campine`으로 바꾼 화면(`campine-dist-5.tgz`)을 서버에 올리기 (2026-11-24, 사이트 첫 화면이 새 빌드와 같은 것을 밖에서 확인)
- [x] 실제 폰에 APK 설치 (2026-11-24, 작업자가 확인)
- [x] `client/capacitor.config.json`의 `http://localhost:5173`을 배포 주소로 바꿔 앱을 다시 만들기 (15단계)
- [ ] 바꾼 앱 설정 · 이름 · 문서를 브랜치 → PR로 올리기
- [ ] 실제 폰에서 로그인 · 저장 · 지도 · 현재 위치 · 푸시 확인

**6. RAG용 벡터 DB (16단계)**
- [ ] CSV 보내기 → pgvector 설치 → DB · 테이블 만들기 → 데이터 넣기 (`COPY 120`)
- [ ] 개수와 벡터 검색 확인 (16-5)
- [ ] 작업한 팀원의 `01_table.sql`과 테이블 구조가 같은지 확인
- [ ] 메모가 남은 조각 35개(특히 응급처치)를 RAG에 쓰기 전에 다시 확인
- [ ] RAG 백엔드 코드 만들기 → OpenAI 키를 서버 환경변수에 넣기 → 백엔드 다시 올리기 (13-6)

**7. 보안 정리**
- [ ] ACG에서 22가 **내 IP만** 허용하는지 확인 (4단계)
- [ ] root 비밀번호를 바꿨는지 확인 (5단계)
- [ ] `.pem` 키를 옮기고 백업 (3단계. 다운로드 폴더에 있는 것까지 확인)
- [ ] `npm` 라이브러리 취약점 안내 살펴보기 (11-8)
- [ ] 배포가 끝나면 복사본 `campine.dump` 삭제 (작업 PC, 서버 `/root`)
- [ ] 배포가 끝나면 올릴 때 쓴 파일 삭제: 서버의 `/root/campine-server*.jar` · `/root/campine-dist*.tgz` · `/root/safety_doc_chunk.csv` · `/tmp/campine.list`, 작업 PC의 `C:\mylx\campine-build`. **실행 중인 `/opt/campine/server.jar`와 `/var/www/campine`은 지우지 않는다**

**8. 팀과 정할 일**
- [ ] 공용 DB 주인에게 복사 허락을 받았는지 확인 (회원 · 가족 위치 · 아이 건강 카드 포함)
- [ ] 팀 합의: `metaverse/CLAUDE.md`에는 Firebase Hosting + Cloud Run + Cloud SQL로 적혀 있음
- [ ] 프로젝트가 끝나면 서버 · 스토리지 · 공인 IP 반납 (그대로 두면 계속 과금될 수 있음)

확인이 끝난 일: 3389 닫힘(4단계), DB 크기(10-4), `.jar`에 비밀 설정 파일 없음(11-8), 안전점수 23곳(12-4), 카카오 배포 주소 등록(13-3)

## 배포란

내 컴퓨터에서만 되던 프로그램을 **인터넷에 연결된 서버에 올려서 누구나 쓰게 하는 일**이다.

`localhost`는 "지금 이 컴퓨터 자신"이라는 뜻이라, 친구에게 주소를 보내도 열리지 않는다. 그래서 24시간 켜져 있고 공인 IP가 있는 서버가 필요하다.

## 완성됐을 때 모습

```
사용자 브라우저
     │  http / https  (80 · 443 포트)
     ▼
┌─ campine-server (네이버 클라우드) ───────────────────┐
│  Nginx ── 화면 파일(dist) 보내 주기                  │
│    └─ /api 요청 → Spring Boot (8080)                 │
│                        └→ PostgreSQL + PostGIS (5432)│
└──────────────────────────────────────────────────────┘
```

- 서버 한 대에 모두 올린다. **이유:** 구조가 단순해서 처음 배포하기 쉽고 비용이 적다
- 밖에서는 Nginx만 만날 수 있다. Spring Boot와 DB는 서버 안에서만 쓴다. **이유:** 밖에 열린 곳이 적을수록 안전하다

## 1. VPC 만들기

**한 일**: 이름 `campine-vpc`, IP 주소 범위 `10.0.0.0/16`

**쉽게 말하면**: 클라우드 안에 우리만 쓰는 **동네**를 하나 만든 것이다.

**따라 하기** (메뉴 이름은 2026-11-25에 네이버 클라우드 공식 가이드에서 확인. 화면이 바뀌면 이름이 조금 다를 수 있다)
1. 콘솔 **Menu → All Services → Networking → VPC → VPC Management → VPC 생성**
2. VPC 이름 `campine-vpc`, IP 주소 범위 `10.0.0.0/16`, 유형은 `NORMAL` → **생성**
3. 목록에서 상태가 **운영중**이 될 때까지 기다린다

**왜 이 값인가**
- `10.x.x.x`는 내부에서만 쓰기로 정해진 사설 주소다. 내 PC IP나 홈페이지 주소를 넣는 칸이 아니다
- `/16`은 앞 두 자리(`10.0`)를 고정한다는 뜻이고, 주소가 65,536개라 넉넉하다
- 나중에 바꾸기 어려우므로 처음부터 넉넉하게 잡는다

## 2. Subnet 만들기

**한 일**: 이름 `campine-subnet`, IP 주소 범위 `10.0.1.0/24`, Zone `KR-1`, **Public**

**쉽게 말하면**: 동네 안에 서버를 놓을 **골목**을 하나 만든 것이다.

**따라 하기** (공식 가이드 기준)
1. 콘솔 **VPC → Subnet Management → Subnet 생성**
2. Subnet 이름 `campine-subnet`, VPC `campine-vpc`, IP 주소 범위 `10.0.1.0/24`, 가용 Zone `KR-1`
3. Network ACL은 기본값, **Internet Gateway 전용 여부는 `Y (Public)`**
4. 용도는 일반 서버용을 고른다. Load Balancer · NAT Gateway 같은 전용 용도는 고르지 않는다 (화면에 적힌 정확한 이름은 확인 못 함)
5. **생성** → 상태가 **운영중**이 될 때까지 기다린다

**왜 이 값인가**
- Subnet은 VPC 범위 안에 있어야 한다. `10.0`으로 시작하므로 VPC 안에 든다
- `/24`는 앞 세 자리(`10.0.1`)를 고정한다는 뜻이고, 주소가 256개다. 일부는 네이버 클라우드가 쓴다
- **Public으로 한 이유:** 그래야 서버에 공인 IP를 붙여 인터넷에서 접속할 수 있다

## 3. 서버 만들기

**한 일**

| 항목 | 고른 값 | 왜 |
|---|---|---|
| 서버 이름 | `campine-server` | |
| OS | Ubuntu 24.04 | 서버에서 많이 쓰고 자료가 많다 |
| 스펙 | s2-g3a (2vCPU, 메모리 8GB) | Spring Boot · DB · Nginx를 한 서버에서 돌릴 여유 |
| 스토리지 | 30GB | 시연용. DB 크기는 10-4 |
| 요금제 | 시간 요금제 | 짧게 쓰거나 중간에 바꾸기 좋다 |
| Network Interface | eth0, `campine-subnet`, IP 자동할당 | |
| 공인 IP | 새로 할당: `49.50.137.194` | 사용자가 접속할 주소. 월 4,032원(생성 화면 안내) |
| 반납 보호 | 설정 | DB도 있는 서버라 실수로 지우는 것을 막는다 |
| 인증키 | `campine-key` (`.pem`) | 관리자 비밀번호를 확인할 때 필요하다 |

**따라 하기** (공식 가이드의 **신규 콘솔** 기준)
1. 콘솔 **Menu → All Services → Compute → Server → 서버 생성** → **신규 콘솔 화면** 선택
2. 서버 이미지: Ubuntu 24.04 (KVM) → **다음**
3. 서버 설정: `campine-vpc`, `campine-subnet`, 스펙 `s2-g3a`(2vCPU, 8GB), 시간 요금제, 서버 1대, 이름 `campine-server`
4. 같은 화면의 네트워크 인터페이스: eth0의 IP를 비워 두고 **추가** (비우면 자동 할당)
5. 같은 화면의 공인 IP: 새로 할당. 반납 보호: 설정 → **다음**
6. 스토리지: 30GB → **다음**
7. 인증키: **새로운 인증키 생성** → 이름 `campine-key` → **인증키 생성 및 저장**. `.pem` 파일이 내려받아진다 (아래 주의 참고) → **다음**
8. 네트워크 접근 설정: `campine-vpc-default-acg` → **다음** → 최종 확인에서 **서버 생성**
9. 상태가 **운영중**이 되면 **공인 IP를 적어 둔다.** 생성에 몇 분에서 몇십 분 걸린다

**3D인데 왜 GPU가 필요 없나**: three.js 메타버스와 Cesium 재난지도는 **사용자 기기가 그린다.** 서버는 파일만 보내 준다.

> 스펙은 시연 규모를 기준으로 한 추정이다. 부하 테스트는 하지 않았다.

**`.pem` 키 주의**
- 다운로드 폴더에서 `C:\Users\LX\.ssh\`로 옮기고 백업한다. **이유:** 다시 받을 수 없고, 다운로드 폴더는 정리하다 지우기 쉽다
- 프로젝트 폴더에 넣지 않는다. 지금 `.gitignore`는 `.pem`을 막지 않는다

## 4. ACG(방화벽) 설정

**한 일**: `campine-vpc-default-acg`의 Inbound(들어오는 길)에 80 · 443을 추가하고 3389를 닫음

**쉽게 말하면**: 서버의 **문마다 경비원**을 세운 것이다. 포트는 문 번호다.

| 포트 | 용도 | 누구에게 여나 | 왜 |
|---|---|---|---|
| 22 | SSH (관리자 접속) | 내 IP만 | 전체에 열면 누구나 비밀번호를 맞혀 볼 수 있다 |
| 80 | HTTP | 전체 | 사용자가 접속하는 문 |
| 443 | HTTPS | 전체 | 나중에 HTTPS를 붙일 문 |
| 8080 | Spring Boot | 닫음 | Nginx가 서버 안에서 전달한다 |
| 5432 | PostgreSQL | 닫음 | DB는 밖에서 바로 접속하지 못하게 한다 |
| 3389 | 윈도우 원격 접속 | 닫음 | 우리 서버는 Ubuntu라 쓰지 않는다 |

**따라 하기**
1. 콘솔 **Server → ACG** → `campine-vpc-default-acg` 체크 → **ACG 설정 → Inbound**
2. 80 · 443 추가: 프로토콜 TCP, 접근 소스 `0.0.0.0/0`
3. 22번: 접근 소스에 **myIp**를 넣은 줄을 **먼저 추가**하고, `0.0.0.0/0` 줄이 있으면 삭제
   - 순서를 바꾸면 나도 접속이 막힌다. 막혀도 콘솔에서 다시 추가하면 풀린다
   - 장소가 바뀌어 IP가 달라지면 myIp를 다시 추가한다
4. 3389 줄 삭제
5. 맨 아래 **적용** (누르지 않으면 저장되지 않는다)

**팀 PC에서 시험한 결과 (2026-11-24)**

| 포트 | 결과 | 뜻 |
|---|---|---|
| 22 | 연결됨 | 정상. 내 IP만 허용했는지는 이 시험으로 알 수 없어 콘솔에서 확인 필요 |
| 80 | 연결됨 | 정상 |
| 443 | 연결 거부 | ACG는 열림. HTTPS를 아직 설정하지 않아서 정상 |
| 8080 · 5432 | 시간 초과 | 밖에서 막혀 있음. 정상 |
| 3389 | 처음 연결 거부 → 규칙 삭제 뒤 **시간 초과** | 처음에는 열려 있었고 지금은 막힘. 정상 |

> **시간 초과와 연결 거부의 차이**: 시간 초과는 대개 방화벽이 요청을 버린 것이고, 연결 거부는 대개 요청이 서버까지 갔지만 그 문에서 기다리는 프로그램이 없는 것이다.

## 5. 서버에 접속하기 (SSH)

**한 일**
1. 콘솔에서 서버 체크 → **서버 관리 및 설정 변경 → 관리자 비밀번호 확인** → `.pem` 올리기 → **비밀번호 확인**
2. 내 PC의 PowerShell에서 접속. 처음이면 `Are you sure you want to continue connecting`이라고 묻는다 → `yes`

```powershell
ssh root@49.50.137.194
```

**쉽게 말하면**: SSH는 멀리 있는 서버의 **명령창을 내 PC에서 안전하게 여는 방법**이다.

**알아둘 것**
- 비밀번호는 입력해도 화면에 보이지 않는다 (정상)
- `.pem`은 비밀번호를 확인할 때 쓰고, 접속할 때는 그 비밀번호를 넣는다
- 접속한 뒤 `passwd`로 비밀번호를 바꾼다

## 6. 서버 업데이트, 시간대 설정

```bash
timedatectl set-timezone Asia/Seoul
apt update
apt upgrade -y
reboot
```

| 명령 | 왜 |
|---|---|
| `timedatectl ...` | 일정 · 날씨 시간이 9시간 어긋나지 않게 한국 시간으로 맞춘다 |
| `apt update` | 설치할 수 있는 프로그램 **목록**을 새로 받는다 |
| `apt upgrade` | 설치된 프로그램을 최신으로 올린다 (보안 수정 포함) |
| `reboot` | 업데이트를 완전히 적용한다 |

- `reboot`을 하면 접속이 끊긴다 (정상). 1 ~ 2분 뒤 `ssh`로 다시 접속한다
- `apt upgrade` 중에 파일을 바꿀지 물으면 `N`(현재 상태 유지). `/etc/issue`에서 이 질문이 나왔다 (겪은 문제)
- 다시 접속한 뒤 `date`를 쳐서 한국 시간(`KST`)이 나오면 된다

## 7. Java · Nginx 설치

```bash
apt install -y openjdk-17-jre-headless nginx unzip
```

| 프로그램 | 하는 일 | 왜 |
|---|---|---|
| Java 17 | Spring Boot(`.jar`) 실행 | `server/pom.xml`이 Java 17 기준 |
| Nginx | 사용자에게 화면을 보내는 웹 서버 | 아래 설명 |
| unzip | `.zip` 압축 풀기 | 올린 파일을 풀려고 설치했지만, 실제로는 `tar`로 풀었다 (11-9) |

**왜 Vite가 아니라 Nginx인가**
- Vite 개발 서버(`npm run dev`)는 **개발할 때** 쓴다. 코드를 고치면 바로 보여 주지만, 공개 운영용이 아니다
- Nginx는 **사용자에게 서비스할 때** 쓴다. 완성된 파일을 빠르게 보내고 압축 · HTTPS를 맡는다
- **빌드는 Vite, 전달은 Nginx**로 역할을 나눈다
- `client/vite.config.js`의 `/api` proxy는 개발 서버에서만 된다. 그래서 13단계에서 Nginx에 같은 설정을 한다

## 8. PostgreSQL · PostGIS 설치

```bash
apt install -y postgresql-common ca-certificates
/usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y
apt install -y postgresql-18 postgresql-18-postgis-3
```

**왜 18인가**: Ubuntu 24.04 기본은 16이지만, 작업 PC에 18이 있다. 18에서 뽑은 데이터를 낮은 버전에 넣으면 오류가 날 수 있어 맞췄다. 둘째 줄은 PostgreSQL 공식 저장소를 추가하는 명령이다.

**PostGIS란**: PostgreSQL에 지도 기능(위치, 거리 계산 등)을 더해 주는 확장 프로그램이다.

## 9. 설치 확인

```bash
java -version
nginx -v
sudo -u postgres psql -c "SELECT version();"
systemctl is-active nginx postgresql
```

| 확인한 것 | 결과 | 확인 방법 |
|---|---|---|
| Java 17 · PostgreSQL 18 설치 | 완료 | 서버 작업자가 확인 |
| `active` 두 번 (nginx, postgresql) | 완료 | 서버 작업자가 확인 |
| `http://49.50.137.194` | 200 응답, 당시 **Welcome to nginx!** (nginx 1.24.0). 13단계 뒤에는 CAMPINE 화면이 나온다 | 팀 PC에서 직접 접속해 확인 |

아직 HTTPS를 설정하지 않아 `https`로는 열리지 않는다.

## 10. DB 만들고 데이터 옮기기

**쉽게 말하면**: 네이버 서버에 빈 창고(DB)를 만들고, 공용 DB의 **복사본**을 채워 넣은 것이다.

```
공용 DB ──(Tailscale)──→ 작업 PC (campine.dump) ──(scp)──→ campine-server의 DB
```

**왜 공용 DB에 바로 연결하지 않았나**
- 공용 DB 주소는 `100.81.x.x`로, Tailscale(등록된 기기끼리만 통하는 사설망) 주소로 보인다. 네이버 서버에는 Tailscale이 없어 닿지 않을 것으로 판단했다 (서버에서 직접 시험하지는 않음)
- 서버에 Tailscale을 설치하면 연결할 수 있지만, 인터넷에 공개된 서버를 팀 사설망에 넣게 되어 위험이 커진다
- 공용 DB가 꺼져도 배포 사이트는 멈추지 않는다

> **주의**: 두 DB는 자동으로 맞춰지지 않는다. 배포 사이트의 새 데이터는 네이버 DB에만 저장된다. 발표 직전에 최신 데이터가 필요하면 다시 복사한다.

### 10-1. 빈 DB와 전용 계정 만들기

```sql
-- 서버에서 sudo -u postgres psql 로 들어가 실행. 끝나면 \q 로 나온다
CREATE ROLE campine_app LOGIN;
\password campine_app
CREATE DATABASE campine OWNER campine_app TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8';
\c campine
CREATE SCHEMA extensions;
CREATE EXTENSION postgis SCHEMA extensions;
GRANT USAGE ON SCHEMA extensions TO campine_app;
ALTER DATABASE campine SET search_path = public, extensions;
```

`\password campine_app`에서 **새로 정한 비밀번호**를 두 번 넣는다. 12-1에서 다시 쓰므로 안전한 곳에 적어 둔다. 영문과 숫자로만 길게 만든다. **이유:** 따옴표나 `\` 같은 특수문자는 12-1의 설정 파일에서 다르게 읽힐 수 있다.

| 한 일 | 왜 |
|---|---|
| 전용 계정 `campine_app` | 최고 관리자(`postgres`)로 서비스를 돌리면, 비밀번호가 샜을 때 DB 전체가 위험하다 |
| `ENCODING 'UTF8'` | 한글이 깨지지 않게 한다 |
| PostGIS를 `extensions` 스키마에 설치 | 서버 코드(`ChabakMapper`)가 `extensions.ST_...`로 부른다. 다른 곳에 설치하면 차박 지도가 안 된다 |
| `search_path` 설정 | 테이블과 지도 기능을 찾을 위치를 정한다 |

### 10-2. 복사본 만들기

**따라 하기** (작업 PC, 공용 DB는 읽기만 한다)
1. Tailscale을 켜고 pgAdmin에서 공용 DB에 접속한다
2. 서비스 DB 우클릭 → **Backup...**
3. General: Filename `C:\Users\LX\Downloads\campine.dump`, Format **Custom**
4. Data Options: Do not save의 Owner · Privileges 켜기
5. Objects: `public` · `raw` · `chabak`만 체크
6. **Backup**을 누르고 완료를 확인한다. **Restore는 누르지 않는다** (공용 DB에 데이터를 넣는 기능)

> 시작하기 전에 공용 DB 주인의 허락을 받는다. `campine.dump`에는 개인정보가 들어 있으니 **공유하거나 git에 넣지 않고,** 배포가 끝나면 지운다.

- **덤프(dump)란**: DB를 파일로 복사한 것
- **왜 Custom인가**: 넣을 때 목록을 골라낼 수 있다. Plain(`.sql`)과 달리 `pg_restore`로 넣는다
- **왜 세 스키마인가**: `public`(서비스 · 회원 데이터), `raw`(공공데이터 원본), `chabak`(차박)을 서버 코드가 읽는다

### 10-3. 서버에 올려서 넣기

```powershell
# 작업 PC의 PowerShell (서버 창 아님)
scp "C:\Users\LX\Downloads\campine.dump" root@49.50.137.194:/root/
```

```bash
# 서버
chmod 600 /root/campine.dump
pg_restore -l /root/campine.dump | grep -v ' SCHEMA - public ' > /tmp/campine.list
sudo -u postgres pg_restore -d campine --no-owner --no-acl --role=campine_app -1 -L /tmp/campine.list < /root/campine.dump
```

| 명령 | 왜 |
|---|---|
| `chmod 600` | 개인정보가 든 파일을 root만 읽게 한다 |
| `grep -v ...` | 이미 있는 `public` 스키마를 또 만들다 나는 오류를 막는다 |
| `--role=campine_app` | 테이블 주인을 우리 서비스 계정으로 만든다 |
| `-1` | 전부 성공하거나 전부 취소한다. 반쯤 들어간 상태를 막는다 |

### 10-4. 확인

복사는 "오류가 없었다"로 끝내지 않고 **개수가 같은지** 비교한다.

**① 서버 DB**: 서비스 계정(`campine_app`)으로 읽히는지도 함께 본다.
```bash
sudo -u postgres psql -d campine -c "SET ROLE campine_app" -c "SELECT (SELECT count(*) FROM camp) camp, (SELECT count(*) FROM facility) facility, (SELECT count(*) FROM raw.camps) raw_camps, (SELECT count(*) FROM chabak.campsite) chabak;"
```

**② 복사본 파일**: 작업 PC에서 파일의 목차를 보고, 위 네 테이블의 줄 수를 세었다. 개인정보가 있는 테이블의 데이터는 읽지 않았다.
- 스키마는 `public` · `raw` · `chabak`만 있음 (테이블 28 · 6 · 11개)
- 공용 DB도 서버도 PostgreSQL 18 계열이라 버전이 맞음

| 테이블 | 서버 DB (작업자가 확인) | 복사본 파일 (줄 수 대조) |
|---|---|---|
| `camp` | 23 | 23 |
| `facility` | 249 | 249 |
| `raw.camps` | 23 | 23 |
| `chabak.campsite` | 23 | 23 |

네 테이블의 개수가 같다. 나머지 테이블은 세지 않았다. 복사본 파일은 압축 상태로 약 25.8MB라 스토리지 30GB로 충분해 보인다.

**따라 하는 사람은 이렇게 비교한다**: ①을 서버에서 실행하고, 같은 때 pgAdmin에서 공용 DB에 아래를 실행해 네 숫자가 같은지 본다 (**읽기만 하는** 명령). 데이터가 늘었다면 위 표와 숫자가 달라도 된다.

```sql
SELECT (SELECT count(*) FROM public.camp) camp, (SELECT count(*) FROM public.facility) facility, (SELECT count(*) FROM raw.camps) raw_camps, (SELECT count(*) FROM chabak.campsite) chabak;
```

## 11. 프론트 · 백엔드 빌드해서 올리기

먼저 준비물과 문제가 될 만한 곳을 확인하고(11-1 ~ 11-4), 빌드해서(11-5 ~ 11-8) 서버에 올렸다(11-9).

**쉽게 말하면**: 빌드는 우리가 쓴 코드를 서버에 올릴 수 있는 **완성품으로 포장하는 일**이다.

| 만드는 것 | 명령 | 결과 |
|---|---|---|
| 프론트 (Vue) | `client`에서 `npm run build` | `client/dist` 폴더 |
| 백엔드 (Spring Boot) | **11-7의 방법** (프로젝트의 `server` 폴더에서 바로 `package` 하지 않는다. 비밀 설정 파일이 들어간다) | `.jar` 파일 |

**왜 서버가 아니라 작업 PC에서 하나**: 빌드는 메모리를 많이 쓴다. 완성품만 올리면 서버에 Node.js나 소스 코드를 둘 필요도 없다.

### 11-0. 작업 PC 준비 (처음 한 번)

1. 설치: Git, Node.js **24.12 이상**(22를 쓴다면 22.18 이상), JDK **17**. Maven은 프로젝트의 `mvnw.cmd`가 알아서 받는다

```powershell
git --version
node -v
java -version
```

2. 코드 받기. 이미 받아 두었다면 `clone`은 건너뛴다

```powershell
New-Item -ItemType Directory -Force C:\mylx     # 있으면 그대로 쓴다
cd C:\mylx
git clone https://github.com/LX-team3/safecamping.git
cd C:\mylx\safecamping
git switch main
git pull
git status -sb
```

`git status -sb`에 `## main...origin/main` **한 줄만** 나와야 한다. 고친 파일이 있으면 프론트 빌드에 함께 들어간다. 커밋하지 않은 내 작업이 있다면 지우지 말고 다른 브랜치에 먼저 커밋한다.

3. `client` 폴더에 `.env` 파일을 만들고 한 줄을 적는다

```
VITE_KAKAO_MAP_KEY=팀에서_받은_JavaScript_키
```

4. 백엔드 테스트(11-6)에 쓸 로컬 설정 파일 `application-local.properties`를 팀에서 받아 `server/src/main/resources`에 둔다. 테스트 하나(`ServerApplicationTests`)가 서버 전체를 켜 보기 때문이다

3 · 4의 파일은 git에 올라가지 않는다. 아래 명령에 두 파일의 경로가 **모두** 나오면 정상이다.

```powershell
cd C:\mylx\safecamping
git check-ignore client/.env server/src/main/resources/application-local.properties
```

### 11-1. 어떻게 확인했나

| 확인한 것 | 방법 | 결과 |
|---|---|---|
| Node 버전 | `node -v` | v24.18.0. `client/package.json`의 조건에 맞음 |
| 설정 파일 | **파일 이름만** 봄 (내용은 열지 않음) | `server`에 `application-local.properties`, `client`에 `.env`가 있음 |
| git 제외 여부 | `git check-ignore -v 파일` | 둘 다 git에 올라가지 않음 |
| 접속 주소 제한 | `SecurityConfig.java`의 CORS 설정 | `http://localhost:5173`만 허용 |
| 카카오맵 키 | `kakaoMap.js` | 빌드할 때 필요함 (11-5) |

### 11-2. 찾은 문제 ① `.jar`에 비밀 정보가 들어갈 수 있다

- **왜 문제인가**: `application-local.properties`는 git에는 안 올라가지만 `resources` 폴더에 있다. 빌드하면 이 폴더의 파일이 `.jar`에 함께 포장된다. `.jar`는 압축 파일이라 누구나 풀어 볼 수 있다
- **비유하면**: 택배를 포장하면서 집 열쇠를 상자에 같이 넣는 것과 같다
- **해결 방법**: 이 파일을 뺀 `.jar`를 만들고, 필요한 값은 12단계에서 **서버 환경변수**로 넣는다

**환경변수란**: 코드 밖, 서버에 따로 적어 두는 설정값이다. 코드가 공개돼도 비밀번호는 드러나지 않고, 개발용과 배포용 값을 다르게 쓸 수 있다.

서버에 넣은 환경변수 목록은 12-1에 있다.

> `application.properties`에는 DB 비밀번호 항목이 환경변수가 아니라 직접 적힌 값으로 되어 있다. 진짜 비밀번호인지는 열어 보지 않아 확인하지 못했다. 진짜라면 DB 주인과 비밀번호 변경을 상의한다.

### 11-3. 찾은 문제 ② 로그인 · 저장이 403으로 막힐 수 있다

- **왜 문제인가**: 서버는 허용한 주소에서 온 요청만 받는다. 이 규칙이 **CORS**이고, 지금은 개발 주소 `http://localhost:5173`만 허용한다
- **비유하면**: 손님 명단에 개발용 주소만 적혀 있는 것과 같다
- **해결 방법**: 화면과 API를 **같은 주소**로 서비스하고, Nginx가 원래 접속 주소를 Spring Boot에 알려 주게 한다. 주소가 같으면 이 명단을 확인할 필요가 없다
- 코드 수정 없이 **조회(GET)는 되는 것을 확인했다** (13-2). 로그인과 저장은 아직 확인하지 못했다
- HTTPS를 붙이면 `http`와 `https`가 다른 주소로 취급될 수 있어, 14단계에서도 다시 확인한다

### 11-4. 빌드 전에 할 일
1. 카카오 콘솔에 배포 주소 등록: 완료 (13-3). 콘솔 등록은 빌드한 뒤에 해도 된다
2. `campine-key.pem` 옮기고 백업: 아직 (3단계 `.pem` 키 주의)

### 11-5. 프론트 빌드 (2026-11-24, `main` 16ded4a)

**왜 `main`인가**: 팀이 합친 최신 코드를 올리기 위해서다. 빌드 전에 GitHub의 `main`과 같은 커밋인지 비교했다.

```powershell
cd C:\mylx\safecamping\client
npm ci
npm run build
```

| 순서 | 하는 일 | 왜 |
|---|---|---|
| 1 | `npm ci`: `package-lock.json`에 적힌 정확한 버전으로 라이브러리 설치 | 팀원마다 버전이 달라 결과가 바뀌는 것을 막는다 |
| 2 | Cesium 파일을 `public/cesium`에 복사 (자동) | 3D 지도가 이 파일들을 주소로 직접 불러온다 |
| 3 | `.vue` 파일을 HTML · JS · CSS로 변환 | 브라우저는 `.vue`를 읽지 못한다 |
| 4 | 파일을 묶고 줄이기 | 받는 용량이 작아진다 |
| 5 | 카카오맵 키(`client/.env`의 `VITE_KAKAO_MAP_KEY`)를 JS에 넣기 | 화면에서 지도를 불러올 때 필요하다. **키를 바꾸면 다시 빌드해야 한다** |
| 6 | `client/dist`에 저장 | 이 폴더가 완성품이다 |

**결과**: 성공. 변환 3.94초, `dist` 27MB, 파일 538개

- "500kB보다 큰 파일이 있다"는 안내가 나왔다. 재난지도(Cesium) 3.6MB와 메타버스(three.js) 0.65MB 때문이고, **오류가 아니다.** 압축하면 각각 약 0.94MB · 0.17MB라 13단계에서 Nginx 압축을 켠다

> 카카오맵 JavaScript 키는 원래 브라우저에 공개되는 키다. 그래서 카카오 콘솔에 등록한 주소에서만 쓰도록 제한한다.

### 11-6. 백엔드 테스트

```powershell
cd C:\mylx\safecamping\server
.\mvnw.cmd test
```

**왜 포장 전에 하나**: 고장 난 채로 서버에 올리는 것을 막는다.

**결과**: 216개 모두 통과 (실패 0, 오류 0, 건너뜀 0), `BUILD SUCCESS`

### 11-7. 백엔드 포장 (`.jar`)

`.jar`는 우리 코드, 라이브러리, 웹 서버를 하나로 묶은 파일이다. 서버에는 Java만 있으면 실행된다.

```powershell
# 1) git에 커밋된 파일만 다른 폴더로 꺼낸다 → 비밀 파일이 들어갈 수 없다
cd C:\mylx\safecamping
New-Item -ItemType Directory C:\mylx\campine-build     # 이미 있다고 나오면 예전 것이 있는 것이다. 다른 폴더 이름을 쓴다
git archive -o C:\mylx\campine-build\server.tar HEAD server
tar -xf C:\mylx\campine-build\server.tar -C C:\mylx\campine-build

# 2) 포장
cd C:\mylx\campine-build\server
.\mvnw.cmd package -DskipTests
```

- **왜 다른 폴더에서 하나**: 원래 폴더에는 `application-local.properties`가 있어 `.jar`에 함께 들어간다 (11-2)
- **왜 테스트를 건너뛰나**: 같은 코드로 11-6에서 이미 통과했다. 이 복사본에는 DB 설정이 없어 테스트할 수도 없다. **11-6이 실패했다면 건너뛰면 안 된다**

**결과**: 성공, 약 95MB

### 11-8. 결과 검사

| 검사 | 방법 | 결과 |
|---|---|---|
| 비밀 설정 파일 | `.jar` 안의 파일 이름 목록 | `application-local.properties` **없음** |
| Java 버전 | 클래스 파일의 버전 번호 | 61 = Java 17. 서버의 Java 17에서 실행 가능 |
| 개발 주소 | `dist`의 JS · HTML에서 `localhost:5173` · `8080` 찾기 | 없음 |
| 원본 코드 노출 | `.map` 파일 개수 | 0개 |
| 카카오맵 키 | 빌드 결과에 들어갔는지 **개수만** 셈 | 들어감. 지도가 나오는 것은 13-3에서 확인 |
| 화면 파일 | 작업 PC에서 잠깐 열어 보기 | 첫 화면 · `/web` · 재난지도 데이터 · 메타버스 지형 · Cesium 파일 모두 200 |

- `.jar`에는 git에 있는 `application.properties`가 들어 있다. 11-2에 적은 DB 비밀번호 항목도 함께 들어 있으니 `.jar`를 공유하지 않는다
- 빌드할 때 확인하지 못한 `.jar` 실행은 12-4, API 연결은 13-2, 지도 표시는 13-3에서 확인했다
- `npm ci`가 라이브러리 취약점 안내를 냈다. 버전이 바뀌면 고장 날 수 있어 자동 수정(`npm audit fix`)은 하지 않았고, 따로 살펴봐야 한다

**올릴 파일 만들기** (작업 PC)

```powershell
# 1) 화면: dist 폴더를 파일 하나로 묶기
tar -czf C:\mylx\campine-build\campine-dist.tgz -C C:\mylx\safecamping\client dist

# 2) 백엔드: 포장한 .jar 를 알아보기 쉬운 이름으로 복사
Copy-Item C:\mylx\campine-build\server\target\server-0.0.1-SNAPSHOT.jar C:\mylx\campine-build\campine-server.jar

# 3) 검사: application-local.properties 가 나오면 올리지 않는다 (application.properties 는 나와도 된다)
tar -tf C:\mylx\campine-build\campine-server.jar | Select-String "application"

# 4) 지문 구하기: 11-9에서 서버의 값과 비교한다 (대문자 · 소문자는 상관없다)
Get-FileHash C:\mylx\campine-build\campine-dist.tgz, C:\mylx\campine-build\campine-server.jar | Format-List Path, Hash
```

> 1 · 2번 명령은 13-4 · 13-6의 명령을 처음 올릴 때에 맞춰 적은 것이다. 이 순서 그대로 다시 실행해 보지는 않았다.

**서버에 올릴 파일** (`C:\mylx\campine-build`)

| 파일 | 내용 | 크기 |
|---|---|---|
| `campine-dist.tgz` | `dist` 폴더를 하나로 묶은 것 | 약 16MB |
| `campine-server.jar` | 백엔드 | 약 95MB |

파일 538개를 하나씩 올리면 느리고 빠뜨릴 수 있어 하나로 묶었다. 서버에 올린 뒤 지문을 비교하면 **깨지지 않고 도착했는지** 알 수 있다. 아래는 2026-11-24에 만든 파일의 값이고, 따라 하는 사람은 위 4번에서 나온 값과 비교한다.

```
43078ae782b83484474b25c7d0234e9461aa23dafb35d21a7d2258c30fe01242  campine-dist.tgz
159eb1e2b6da9c72218dc578cdc84b79b26affe511f9feba7eadfa86a213b8e0  campine-server.jar
```

### 11-9. 서버에 올리기 (2026-11-24)

> **새 서버에서 처음 한 번만 실행한다.** 지금 있는 서버에서 다시 실행하면 새 화면 위에 옛 화면이 덮인다. 화면을 다시 올릴 때는 13-4, 백엔드를 다시 올릴 때는 13-6을 따른다.

```bash
# 1) 서버: 전용 계정과 폴더
useradd -r -s /usr/sbin/nologin campine
mkdir -p /opt/campine /var/www/campine /var/lib/campine/uploads
chown campine:campine /var/lib/campine/uploads
```

```powershell
# 2) 작업 PC의 PowerShell: 파일 보내기
scp C:\mylx\campine-build\campine-dist.tgz C:\mylx\campine-build\campine-server.jar root@49.50.137.194:/root/
```

```bash
# 3) 서버: 깨지지 않았는지 확인한 뒤 제자리에 놓기
sha256sum /root/campine-dist.tgz /root/campine-server.jar
tar -xzf /root/campine-dist.tgz -C /var/www/campine --strip-components=1
chown -R root:root /var/www/campine
chmod -R u=rwX,go=rX /var/www/campine
install -o root -g campine -m 640 /root/campine-server.jar /opt/campine/server.jar
```

| 폴더 | 들어 있는 것 | 권한과 이유 |
|---|---|---|
| `/var/www/campine` | 화면 파일 | 모두 읽기, 고치는 것은 root만. Nginx가 읽어야 하고, 다른 계정이 바꾸면 안 된다 |
| `/opt/campine` | `server.jar` | root와 `campine` 그룹만 읽기. 안에 설정 파일이 들어 있다 (11-8) |
| `/var/lib/campine/uploads` | 후기 사진 | 주인이 `campine`. 백엔드가 사진을 저장한다 |

- **왜 전용 계정인가**: root로 백엔드를 돌리다 뚫리면 서버 전체가 위험하다. `campine`은 로그인할 수 없고 권한이 적다
- `campine`(Ubuntu 계정)과 `campine_app`(DB 계정)은 다르다

**결과** (서버 작업자가 붙여 준 출력으로 확인)
- 두 파일의 sha256이 11-8의 값과 같음 → 깨지지 않고 도착
- 화면 폴더에 `assets` `cesium` `disaster-map` `favicon.ico` `index.html` `metaverse` `web`
- `server.jar`는 `-rw-r----- root campine`, 99,394,313바이트 (작업 PC와 같음)

## 12. 환경변수 설정, 백엔드 자동 실행 (2026-11-24)

**쉽게 말하면**: `.jar`에 넣지 않은 비밀번호와 키를 서버에 따로 적고, 서버가 켜지면 백엔드도 자동으로 켜지게 했다.

### 12-1. 설정 파일 만들기

```bash
# 1) 빈 파일 만들기 (처음 한 번만. 다시 실행하면 적어 둔 내용이 지워진다)
mkdir -p /etc/campine
install -o root -g root -m 600 /dev/null /etc/campine/campine.env

# 2) 아래 표의 이름을 '이름=값'으로 적고 저장. REMEMBER_ME_KEY= 와 GOOGLE_APPLICATION_CREDENTIALS= 는 이름만 적는다
nano /etc/campine/campine.env

# 3) REMEMBER_ME_KEY 값을 무작위로 채우기 (다시 실행하면 값이 바뀌어 로그인 유지가 풀린다)
sed -i "s/^REMEMBER_ME_KEY=.*/REMEMBER_ME_KEY=$(openssl rand -hex 32)/" /etc/campine/campine.env
grep -c '^REMEMBER_ME_KEY=.' /etc/campine/campine.env   # 1이 나오면 채워진 것 (값은 보이지 않는다)
```

**왜 이름을 먼저 적나**: `sed`는 **이미 있는 줄**을 바꾸는 명령이다. 줄이 없으면 오류도 없이 아무 일도 일어나지 않는다. 12-2의 `sed`도 같다.

**2번에서 적을 모양** (`<...>`는 괄호까지 지우고 자기 값으로 바꾼다. `=` 앞뒤에 빈칸을 넣지 않는다)

```
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=127.0.0.1
SERVER_FORWARD_HEADERS_STRATEGY=native
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/campine
SPRING_DATASOURCE_USERNAME=campine_app
SPRING_DATASOURCE_PASSWORD=<10-1에서 정한 비밀번호>
REMEMBER_ME_KEY=
CAMPIN_UPLOAD_DIR=/var/lib/campine/uploads
KAKAO_REST_KEY=<팀에서 받은 키>
KMA_SERVICE_KEY=<팀에서 받은 디코딩 키>
SAFETY_DATA_KEY=<팀에서 받은 키>
HRFCO_SERVICE_KEY=<팀에서 받은 키>
GOOGLE_APPLICATION_CREDENTIALS=
```

- `SPRING_DATASOURCE_URL`은 10-1에서 만든 서버 안 DB의 일반적인 주소 형식이다. 지금 서버에 적힌 값과 똑같은지는 열어 보지 않아 확인하지 못했다
- 없는 키는 `=` 뒤를 비워 둔다. 카카오 · 기상청 · 재난문자 · 홍수통제소 · Firebase 키는 코드에 빈 기본값이 있다. **DB 세 줄은 꼭 채운다**

이 파일에 적은 이름 (**값은 이 문서에 적지 않는다**)

| 이름 | 무엇 | 왜 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | 개발용(local) 설정을 쓰지 않게 |
| `SERVER_ADDRESS` | `127.0.0.1` | 백엔드가 서버 안의 요청만 받게 |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `native` | Nginx가 알려 주는 원래 주소를 믿게 (11-3의 403 대비) |
| `SPRING_DATASOURCE_URL` · `SPRING_DATASOURCE_USERNAME` · `SPRING_DATASOURCE_PASSWORD` | 서버 DB 접속 | 공용 DB가 아니라 서버 안의 DB를 쓴다 |
| `REMEMBER_ME_KEY` | 로그인 상태 유지 | 서버에서 무작위로 만든 값. 없으면 백엔드를 다시 켤 때 풀린다 |
| `CAMPIN_UPLOAD_DIR` | `/var/lib/campine/uploads` | 후기 사진 폴더 |
| `KAKAO_REST_KEY` | 카카오 길찾기 | 지도 키(JavaScript)와 다르다 |
| `KMA_SERVICE_KEY` | 기상청 날씨 · 특보 · 강수량 | 디코딩 키 하나로 셋 다 쓴다 |
| `SAFETY_DATA_KEY` | 행안부 재난문자 | 등록한 IP에서만 불린다 |
| `HRFCO_SERVICE_KEY` | 한강홍수통제소 수위 | 없으면 재난지도에 상류 수위만 안 나온다 |
| `GOOGLE_APPLICATION_CREDENTIALS` | Firebase 키 파일의 **경로** | 키 내용이 아니라 위치를 적는다 |

- 파일 권한 `600`: root만 읽는다. systemd가 root로 읽어서 백엔드에 전달한다
- **카카오 지도 키는 넣지 않는다** (11-5)
- **안전점수는 넣을 것이 없다.** 카카오 소요시간으로 미리 계산해 DB에 저장한 값이 함께 옮겨졌다. `--score`로 켜지 않으면 다시 계산하지 않는다

### 12-2. Firebase 키 파일

```powershell
# 작업 PC
scp "$env:GOOGLE_APPLICATION_CREDENTIALS" root@49.50.137.194:/root/firebase.json
```

```bash
# 서버
install -o root -g campine -m 640 /root/firebase.json /etc/campine/firebase.json
rm /root/firebase.json
sed -i "s#^GOOGLE_APPLICATION_CREDENTIALS=.*#GOOGLE_APPLICATION_CREDENTIALS=/etc/campine/firebase.json#" /etc/campine/campine.env
```

- 작업 PC에 `GOOGLE_APPLICATION_CREDENTIALS` 환경변수가 없다면 `"$env:..."` 자리에 **키 파일의 경로**를 적는다. 키 파일이 없으면 12-2를 건너뛴다 (푸시만 안 된다)
- 올린 것은 알림을 **보낼 권한**인 서비스 계정 키다. 폰 앱이 쓰는 `google-services.json`은 올리지 않는다
- 폰 주소(토큰)는 서버 메모리에만 있다. 백엔드를 다시 켜면 앱을 완전히 껐다 켜서 다시 등록해야 한다

### 12-3. 자동 실행 (`/etc/systemd/system/campine.service`)

`nano /etc/systemd/system/campine.service`로 파일을 만들고 아래 내용을 저장한다. 그다음 `systemctl` 명령을 실행한다.

```ini
[Unit]
Description=CAMPINE Spring Boot
Wants=network-online.target
After=network-online.target postgresql.service

[Service]
User=campine
Group=campine
WorkingDirectory=/var/lib/campine
EnvironmentFile=/etc/campine/campine.env
ExecStart=/usr/bin/java -Dfile.encoding=UTF-8 -jar /opt/campine/server.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now campine
```

**systemd란**: 프로그램을 켜고 지켜보는 Ubuntu의 관리인이다. 이 파일이 지시서다. 오류로 꺼지면 10초 뒤 다시 켜고, 서버를 다시 켤 때도 자동으로 켠다.

| 하고 싶은 일 | 명령 |
|---|---|
| 끄기 / 켜기 | `systemctl stop campine` / `systemctl start campine` |
| 설정을 고친 뒤 다시 켜기 | `systemctl restart campine` |
| 로그 보기 | `journalctl -u campine -n 40 --no-pager` |

### 12-4. 확인 (서버 작업자가 붙여 준 출력)

**따라 하기** (서버에서 실행. 켜지는 데 시간이 걸려 처음에는 실패할 수 있으니 1분쯤 뒤 다시 해 본다)

```bash
systemctl is-active campine                                   # active
curl -s http://127.0.0.1:8080/api/test; echo                  # 스프링 서버 연결 성공!
curl -s http://127.0.0.1:8080/api/test/db; echo               # 현재 시각
curl -s http://127.0.0.1:8080/api/regions/sidos; echo         # 시·도 이름
journalctl -u campine --no-pager | grep -i firebase           # Firebase 연결 준비됨
```

`/api/test`는 되는데 `/api/test/db`만 실패하면 DB 세 줄(12-1)을 다시 본다. 고친 뒤에는 `systemctl restart campine`을 한다.

| 확인한 것 | 결과 |
|---|---|
| `systemctl is-active campine` | `active` |
| `/api/test` | `스프링 서버 연결 성공!` |
| `/api/test/db` | 처음 실패 → 해결 뒤 현재 시각 (16:29, 한국 시간) |
| `/api/regions/sidos` | 시·도 이름이 나옴 |
| Firebase | `Firebase 연결 준비됨` (키 파일을 읽었다는 뜻. 실제 발송은 확인 전) |
| 안전점수 | 캠핑장 23곳 중 23곳에 점수 있음 |

외부 API 키 동작과 자동 실행은 아직 확인하지 못했다 ("이제 할 일" 2 · 3번).

## 13. Nginx에 우리 서비스 연결 (2026-11-24)

**쉽게 말하면**: Nginx가 우리 화면을 보여 주고, `/api` 요청은 백엔드로 넘기게 했다.

### 13-1. 설정 (`/etc/nginx/sites-available/campine`)

`nano /etc/nginx/sites-available/campine`으로 파일을 만들고 아래 내용을 저장한다. 그다음 `bash` 명령을 실행한다. `nginx -t`에서 `successful`이 나오지 않으면 `reload`하지 않고 파일을 다시 본다.

```nginx
server {
    listen 80 default_server;
    server_name _;
    root /var/www/campine;
    server_tokens off;
    client_max_body_size 101m;

    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/css application/javascript text/javascript application/json image/svg+xml application/wasm;

    location ^~ /api/test {
        return 404;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /assets/ {
        try_files $uri =404;
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    location = /index.html {
        add_header Cache-Control "no-cache";
    }

    location / {
        try_files $uri /index.html;
    }
}
```

```bash
rm /etc/nginx/sites-enabled/default      # 기본 화면의 바로가기만 지운다
ln -s /etc/nginx/sites-available/campine /etc/nginx/sites-enabled/campine
nginx -t                                 # 문법 검사
systemctl reload nginx
```

| 부분 | 왜 |
|---|---|
| `client_max_body_size` | Nginx 기본은 1MB라 사진이 막힌다. 백엔드 한도 100MB에 맞춘다 |
| `gzip` | 큰 파일을 압축해 보낸다 |
| `/api/test` 404 | 개발용 시험 주소를 밖에서 막는다 |
| `/api/` | 백엔드로 넘기고 원래 접속 주소를 알려 준다 (11-3의 403 대비) |
| `/assets/` | 빌드마다 파일 이름이 바뀌므로 오래 저장해 두게 한다 |
| `/index.html` no-cache | 다시 배포하면 새 화면이 바로 보이게 한다 |
| `try_files $uri /index.html` | 주소에 맞는 파일이 없으면 Vue 첫 화면을 보낸다. `$uri/`는 넣지 않는다: `/web` · `/metaverse`는 화면 주소이면서 폴더 이름이라 403이 날 수 있다 |

### 13-2. 확인 (팀 PC에서 밖에서 접속)

**따라 하기** (작업 PC의 PowerShell. 14단계를 마친 뒤에는 IP로 열면 404가 정상이다)

```powershell
curl.exe -s -o NUL -w "%{http_code}\n" http://49.50.137.194/                      # 200
curl.exe -s -o NUL -w "%{http_code}\n" http://49.50.137.194/api/regions/sidos     # 200
curl.exe -s -o NUL -w "%{http_code}\n" http://49.50.137.194/api/test              # 404 (일부러 막음)
```

| 주소 | 결과 |
|---|---|
| `/` · `/web` · `/metaverse` | 200 |
| `/api/regions/sidos` · `/api/camps` | 200 |
| `/api/test` | 404 (일부러 막음) |
| 같은 주소에서 온 조회(GET) 요청 / 다른 주소에서 온 조회 요청 | 200 / 403. 11-3의 CORS 대비가 조회에서는 동작한다 |
| 8080 · 5432 | 밖에서 막혀 있음 |

로그인 · 저장, 3D 화면, 사진 올리기는 아직 확인하지 못했다 ("이제 할 일" 1번).

### 13-3. 카카오 지도

- 카카오 콘솔 → 앱 → 플랫폼 키 → JavaScript 키 → **JavaScript SDK 도메인**에 `http://49.50.137.194`와 `http://localhost:5173`이 함께 있어야 한다. **이유:** 등록한 주소에서만 지도가 열린다. 앱의 **카카오맵 → 사용 설정**도 ON이어야 한다
- 콘솔 설정만 바꾸면 다시 빌드하지 않아도 된다. 키를 바꾸면 다시 빌드해서 올린다 (11-5, 13-4)

**결과**: 사이트에 들어 있는 키로 두 주소 모두 카카오 응답 200 (팀 PC에서 확인). 브라우저에서 지도가 나오는 것은 작업자가 확인

### 13-4. 화면을 다시 빌드해서 올리는 방법

> **처음 배포하는 중이라면 13-4 · 13-5 · 13-6을 건너뛰고 14로 간다.** 이미 올린 것을 바꿀 때 쓰는 방법이다. 13-5 · 13-6의 확인 명령은 14에서 만드는 `https` 주소를 쓴다.

지도 키나 화면 코드를 바꿨을 때 이 순서로 한다. 아래는 이번에 쓴 명령이다. **먼저 서버에서 `ls /var/www`를 보고,** 이미 있는 번호를 피해 다음 번호로 바꿔서 쓴다.

```powershell
# 작업 PC: 빌드하고 하나로 묶어 보내기
cd C:\mylx\safecamping\client
npm run build
tar -czf C:\mylx\campine-build\campine-dist-3.tgz -C C:\mylx\safecamping\client dist
scp C:\mylx\campine-build\campine-dist-3.tgz root@49.50.137.194:/root/
```

```bash
# 서버: 지문 확인 → 새 폴더에 풀기 → 바꿔 끼우기
sha256sum /root/campine-dist-3.tgz
mkdir /var/www/campine-3
tar -xzf /root/campine-dist-3.tgz -C /var/www/campine-3 --strip-components=1
chown -R root:root /var/www/campine-3
chmod -R u=rwX,go=rX /var/www/campine-3
mv /var/www/campine /var/www/campine-2
mv /var/www/campine-3 /var/www/campine
```

```
91892ac716286187f5fcfeed8f909eb2c1aa4972195b02fb612dfee042094ea6  campine-dist-3.tgz
```

- **왜 새 폴더에 풀어 바꿔 끼우나**: 문제가 생기면 이전 화면 폴더를 다시 제자리에 놓아 되돌릴 수 있다 (13-5)
- 다음에는 번호를 올린다 (`-4` …). 이미 있는 폴더 이름으로 `mv`하면 그 폴더 **안으로** 들어가 엉킨다
- 권한(`chown` · `chmod`)의 이유는 11-9의 표를 본다
- Nginx와 백엔드는 다시 켜지 않아도 된다

**결과** (팀 PC에서 확인): 사이트의 첫 화면 파일이 새 빌드와 같음, 화면 주소와 API 200

### 13-5. 이전 화면으로 되돌리는 방법

새 화면이 고장 났을 때 쓴다. **아직 해 보지 않은 명령이다.** 서버에서 실행한다.

```bash
ls /var/www                                                   # campine-2(이전 화면)가 있고 campine-bad 는 없어야 한다
mv -T /var/www/campine /var/www/campine-bad && mv -T /var/www/campine-2 /var/www/campine
curl -s -o /dev/null -w "%{http_code}\n" https://campine.duckdns.org/    # 200 이면 응답하는 것
```

- **쉽게 말하면**: 고장 난 화면을 옆으로 치우고, 보관해 둔 이전 화면을 제자리에 놓는 것이다
- `&&`는 앞 명령이 성공해야 뒤를 실행한다는 뜻이다. `mv -T`는 폴더 안으로 들어가 엉키는 것을 막는다
- 4번째로 올린 뒤라면 이전 화면은 `campine-3`이다. 되돌릴 폴더 이름을 바꿔 쓴다
- `campine-2`는 지도 키를 고치기 전 화면이라 **지도가 나오지 않을 수 있다**
- 브라우저에서 화면을 직접 확인한다. 문제가 해결되면 `campine-bad`는 지워도 된다
- 이전 화면 폴더는 새 화면을 충분히 확인할 때까지 지우지 않는다

### 13-6. 백엔드를 다시 빌드해서 올리는 방법

Java 코드를 바꿨을 때 쓴다. **아직 이 순서로 올려 보지는 않았다.** 아래는 두 번째로 올릴 때의 예이고, 다음에는 번호를 `-3`으로 올린다.

```powershell
# 1) 작업 PC: 테스트. 실패하면 여기서 멈춘다
cd C:\mylx\safecamping\server
.\mvnw.cmd test

# 2) 커밋된 파일만 새 폴더로 꺼내 포장 (이유는 11-7)
cd C:\mylx\safecamping
New-Item -ItemType Directory C:\mylx\campine-build\server-2
git archive -o C:\mylx\campine-build\server-2.tar HEAD server
tar -xf C:\mylx\campine-build\server-2.tar -C C:\mylx\campine-build\server-2
cd C:\mylx\campine-build\server-2\server
.\mvnw.cmd package -DskipTests
Copy-Item target\server-0.0.1-SNAPSHOT.jar C:\mylx\campine-build\campine-server-2.jar

# 3) 검사: application-local.properties 가 나오면 올리지 않는다
tar -tf C:\mylx\campine-build\campine-server-2.jar | Select-String "application"
(Get-FileHash C:\mylx\campine-build\campine-server-2.jar).Hash

# 4) 보내기
scp C:\mylx\campine-build\campine-server-2.jar root@49.50.137.194:/root/
```

```bash
# 5) 서버: 지문 확인 (3의 값과 같아야 한다. 대문자 · 소문자는 상관없다)
sha256sum /root/campine-server-2.jar

# 6) 지금 것을 보관하고 바꾼 뒤 다시 켜기
cp -p /opt/campine/server.jar /opt/campine/server-1.jar
install -o root -g campine -m 640 /root/campine-server-2.jar /opt/campine/server.jar
systemctl restart campine

# 7) 확인: 켜지는 동안은 502 가 나올 수 있다. 잠시 뒤 200 이면 화면 쪽에서 백엔드까지 연결된 것
systemctl is-active campine
curl -s -o /dev/null -w "%{http_code}\n" https://campine.duckdns.org/api/regions/sidos
```

**문제가 생기면 이전 백엔드로 되돌리기**

```bash
journalctl -u campine -n 40 --no-pager                        # 먼저 원인을 본다
install -o root -g campine -m 640 /opt/campine/server-1.jar /opt/campine/server.jar
systemctl restart campine
```

| 알아둘 것 | 왜 |
|---|---|
| 커밋하지 않은 코드는 들어가지 않는다 | `git archive`는 커밋된 파일만 꺼낸다 |
| 테이블이 바뀌었다면 서버 DB도 따로 맞춘다 | `.jar`를 바꿔도 DB는 바뀌지 않는다. 되돌릴 때도 같다 |
| 새 환경변수가 생겼다면 `campine.env`에 먼저 적는다 | 없으면 백엔드가 켜지지 않거나 일부 기능이 안 될 수 있다 |
| 다시 켜면 폰 알림 주소가 사라진다 | 앱을 완전히 껐다 켜서 다시 등록한다 (12-2) |
| 이용자가 적은 시간에 한다 | 다시 켜지는 동안 잠깐 멈춘다 |

## 14. 도메인과 HTTPS (2026-11-24)

**쉽게 말하면**: 숫자로 된 주소 대신 이름(`campine.duckdns.org`)을 쓰고, 주고받는 내용을 암호화했다. **현재 위치**는 HTTPS에서만 쓸 수 있어서 꼭 필요하다.

### 14-1. 무료 도메인 (DuckDNS)

duckdns.org에 로그인 → 이름 `campine` 추가(새 서버라면 **다른 이름**) → current ip에 `49.50.137.194`(내 서버의 공인 IP) 저장. **token은 비밀번호와 같으므로 어디에도 적지 않는다.**

```powershell
nslookup campine.duckdns.org 8.8.8.8     # 49.50.137.194 가 나오면 연결된 것
```

**DNS란**: 도메인 이름을 IP 주소로 바꿔 주는 인터넷 전화번호부다. 서버 IP가 나오지 않으면 14-3으로 가지 않고 몇 분 뒤 다시 확인한다. **이유:** 이름이 서버로 이어져 있어야 인증서를 받을 수 있다.

### 14-2. Nginx에 도메인 적기 (서버)

```bash
cp /etc/nginx/sites-available/campine /root/campine-nginx.bak     # 되돌릴 때 쓸 복사본
sed -i "s/server_name _;/server_name campine.duckdns.org;/" /etc/nginx/sites-available/campine
grep server_name /etc/nginx/sites-available/campine
nginx -t && systemctl reload nginx
```

**왜**: certbot이 어느 설정에 HTTPS를 붙일지 도메인 이름으로 찾는다.

### 14-3. 인증서 받기 (서버)

```bash
apt update && apt install -y certbot python3-certbot-nginx
certbot --nginx -d campine.duckdns.org --redirect
certbot renew --dry-run                  # 자동 갱신 시험
```

- certbot이 이메일과 약관 동의를 물으면 이메일을 넣고 `Y`로 답한다. 소식 메일을 받을지 묻는 질문은 `N`이어도 된다
- 시작하기 전에 ACG의 80 · 443이 열려 있는지 확인한다 (4단계). 80이 막혀 있으면 인증서를 받지 못한다
- `--dry-run`은 시험만 한다. `all simulated renewals succeeded`가 나오면 자동 갱신이 준비된 것이다

- 인증서는 **2027-02-22**까지. certbot이 자동 갱신 작업을 등록했다고 안내함. 자동 갱신 시험 결과는 아직 받지 못했다
- `--redirect`: `http`로 들어와도 `https`로 보낸다
- certbot이 13-1의 Nginx 설정에 443과 인증서 줄을 더한다. 13-1은 HTTPS를 붙이기 **전** 내용이다
- 실패하면 여러 번 반복하지 않는다. 발급 횟수 제한에 걸릴 수 있다

### 14-4. 확인 (팀 PC에서 밖에서 접속)

| 확인한 것 | 결과 |
|---|---|
| 인증서 | Let's Encrypt, `campine.duckdns.org`, 검증 통과 |
| `http://campine.duckdns.org` | 301, `https`로 이동 |
| `/` · `/web` · `/metaverse` · `/api/camps` · `/api/regions/sidos` | 200 |
| `/api/test` | 404 (일부러 막음) |
| 같은 주소 / 다른 주소에서 온 조회(GET) 요청 | 200 / 403. HTTPS에서도 CORS 대비가 동작한다 |
| XSRF 쿠키 | `Secure`가 붙음. 백엔드가 HTTPS 접속임을 안다 |
| 8080 · 5432 | 밖에서 막혀 있음 |
| `http://49.50.137.194` | **404 (정상).** 이제 도메인으로만 접속한다 |

확인 못 함: 로그인 · 저장, 카카오 지도(새 주소), 현재 위치, 자동 갱신 시험

**따라 하기** (작업 PC의 PowerShell)

```powershell
curl.exe -s -o NUL -w "%{http_code}\n" http://campine.duckdns.org/                      # 301 (https로 이동)
curl.exe -s -o NUL -w "%{http_code}\n" https://campine.duckdns.org/                     # 200
curl.exe -s -o NUL -w "%{http_code}\n" https://campine.duckdns.org/api/regions/sidos    # 200
curl.exe -s -o NUL -w "%{http_code}\n" https://campine.duckdns.org/api/test             # 404 (일부러 막음)
```

### 14-5. 카카오에 새 주소 등록

우리 서버는 **아직 등록하지 못했다** ("이제 할 일" 4번).

1. 카카오 콘솔 → 앱 → 플랫폼 키 → JavaScript 키 → **JavaScript SDK 도메인**
2. `https://campine.duckdns.org`를 추가하고 저장한다. `http://localhost:5173`은 개발할 때 쓰므로 지우지 않는다
3. 사이트를 새로고침(`Ctrl+F5`)해서 지도가 나오는지 본다. 다시 빌드하지 않아도 된다

**왜**: 카카오는 등록한 주소에서만 지도를 열어 준다. `http`와 `https`, IP와 도메인은 서로 다른 주소라 13-3에서 등록한 것으로는 부족하다.

### 14-6. 마지막 확인 (브라우저)

시험용 계정으로 `https://campine.duckdns.org`에서 직접 해 본다. **모두 되면 웹 배포가 끝난 것이다.** 우리 서버의 결과는 "이제 할 일"에 적는다.

| 해 볼 것 | 되면 | 안 되면 볼 곳 |
|---|---|---|
| 주소창의 자물쇠 | HTTPS 정상 | 14-3 |
| 지도 | 카카오 주소 등록 정상 | 14-5, 13-3 |
| 로그인 → 일정 저장 → 새로고침 | CORS · DB 저장 정상 | 403이면 11-3과 12-1의 `SERVER_FORWARD_HEADERS_STRATEGY`, 그 밖에는 `journalctl -u campine -n 40 --no-pager` |
| 현재 위치 | 브라우저 위치 권한 정상 | 주소창의 위치 권한 |
| 후기 사진 올리기 | 업로드 폴더 정상 | 11-9의 `/var/lib/campine/uploads` 권한 |
| 재난지도 · 메타버스 | 3D 파일 정상 | 11-5, 13-1 |
| 서버 `reboot` 뒤 사이트가 다시 열림 (이용자가 적을 때) | 자동 실행 정상 | 12-3 |

## 15. 앱(APK) 만들기 (2026-11-24)

**쉽게 말하면**: 배포한 사이트를 여는 안드로이드 앱이다. `client/capacitor.config.json`의 주소를 `https://campine.duckdns.org`로 바꿨다. `cleartext`는 `http`를 허용하는 설정이라 뺐다.

```powershell
cd C:\mylx\safecamping\client
npx cap sync android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\android\gradlew.bat -p .\android assembleDebug
```

- 결과 파일: `client\android\app\build\outputs\apk\debug\app-debug.apk` (시연용 debug, git에 올라가지 않음)
- 앱 이름은 `campine`. 이름은 `capacitor.config.json`의 `appName`과 `client/android/app/src/main/res/values/strings.xml`의 `app_name` · `title_activity_main`을 **함께** 바꾼다. `cap sync`는 `strings.xml`을 바꾸지 않는다. `appId`는 Firebase와 연결되어 바꾸지 않는다
- `google-services.json`이 있는 PC에서 만든다. 없으면 푸시가 동작하지 않는다
- 다른 PC에서 만든 앱이 깔려 있으면 서명이 달라 설치되지 않는다. 기존 앱을 지우고 설치한다
- **화면이나 서버만 고치면 APK를 다시 만들지 않는다.** 앱 이름 · 아이콘 · 권한 · 플러그인 · 주소를 바꾸면 다시 만든다
- 개발할 때 폰에서 개발 서버를 열려면 `url`을 `http://localhost:5173`, `cleartext`를 `true`로 잠깐 되돌린다 (커밋하지 않는다)

**결과**: 빌드 성공, APK 안의 주소가 `https://campine.duckdns.org`인 것을 확인. 실제 폰 설치는 작업자가 확인. 확인 못 함: 폰에서의 기능 동작

## 16. RAG용 벡터 DB (pgvector)

> **상태**: 2026-11-25에 순서를 정리했다. 서버에서 실행한 결과는 아직 받지 못했다. CSV 확인(16-1)만 작업 PC에서 실제로 했다.

**쉽게 말하면**: AI 브리핑(RAG)이 참고할 안전 자료 조각 120개를 서버에 넣는다. **pgvector**는 PostgreSQL에 "뜻이 비슷한 글 찾기" 기능을 더해 주는 확장 프로그램이다.

- **지금 사이트에는 변화가 없다.** 아직 이 DB를 쓰는 백엔드 코드가 없다. 지금 브리핑은 정해진 문장 틀로 만든다 (`client/src/utils/campBrief.js`)
- **왜 새 DB(`campine_vector`)인가**: 서비스 DB(`campine`)와 나누어 지금 데이터를 건드리지 않는다
- **왜 도커를 안 쓰나**: 서버에 PostgreSQL 18이 이미 있어 확장만 더하면 된다
- **왜 CSV로 옮기나**: 벡터까지 들어 있어 다시 임베딩하지 않아도 된다

### 16-1. 옮길 파일 확인 (2026-11-25, 작업 PC)

파일: 작업 PC 바탕화면의 `safety_doc_chunk.csv` (git에는 없음)

| 확인한 것 | 결과 |
|---|---|
| 줄 수 | 120줄. 번호(`id`) 중복과 깨진 줄 없음 |
| 벡터 | 120개 모두 1536 크기, 빈 값 없음 |
| 임베딩 모델 | `text-embedding-3-small` (OpenAI) |
| 가짜 임베딩이 아닌지 | 진짜로 보임. 가장 가까운 조각과 주제가 겹치는 것이 120개 중 103개 |
| 검수 표시 | 120개 모두 `is_reviewed = True`. 다만 35개에 메모가 있음 ("원문 대조 필요", "최신 응급처치 지침과 대조 필요" 등) |
| 지문(sha256) | `d549b28fcb6808603a4f66c4387396e637cd7a1ffccf33c889d13ad2416a0cb7` |

- 바탕화면의 `안전규칙.csv` · `캠핑안전가이드_데이터_종합.csv`는 줄바꿈 말고는 내용이 같다. 셋 중 `safety_doc_chunk.csv`를 쓴다
- 연습용 DB(`campin_practice`)는 가짜 임베딩이라 쓰지 않는다

### 16-2. 파일 보내기

```powershell
# 작업 PC의 PowerShell (서버 창 아님)
scp C:\Users\LX\Desktop\safety_doc_chunk.csv root@49.50.137.194:/root/
```

### 16-3. pgvector 설치, DB 만들기

```bash
# 서버
sha256sum /root/safety_doc_chunk.csv      # 16-1의 지문과 같아야 한다
apt update && apt install -y postgresql-18-pgvector
sudo -u postgres psql -c "CREATE DATABASE campine_vector OWNER campine_app TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8';"
sudo -u postgres psql -d campine_vector -c "CREATE EXTENSION vector;"
```

- `postgresql-18-...`: 서버의 PostgreSQL 버전(18)에 맞춘 것이다
- DB나 확장이 `already exists`라고 나오면 이미 만든 것이니 다음으로 넘어간다

### 16-4. 테이블 만들기, 데이터 넣기

```bash
# 서버 (SQL 줄까지 통째로 붙여 넣는다)
sudo -u postgres psql -d campine_vector -v ON_ERROR_STOP=1 <<'SQL'
SET ROLE campine_app;
CREATE TABLE safety_doc_chunk (
  id              text PRIMARY KEY,
  source          text NOT NULL,
  org             text NOT NULL,
  doc_year        integer,
  page            text,
  heading         text,
  content         text NOT NULL,
  topic           text[],
  kind            text,
  companion       text,
  sleep_type      text,
  season          text,
  is_edited       boolean,
  is_reviewed     boolean,
  note            text,
  embedding       vector(1536) NOT NULL,
  embedding_model text
);
SQL
```

```bash
# 서버
sudo -u postgres psql -d campine_vector -c "COPY safety_doc_chunk FROM STDIN WITH (FORMAT csv, HEADER true, NULL 'NULL', ENCODING 'UTF8')" < /root/safety_doc_chunk.csv
```

정상이면 `CREATE TABLE`, 이어서 **`COPY 120`** 이 나온다.

| 부분 | 왜 |
|---|---|
| `SET ROLE campine_app` | 테이블 주인을 서비스 계정으로 만든다 |
| `vector(1536)` | 임베딩 크기와 같아야 한다 |
| `NULL 'NULL'` | CSV에 빈 값이 `NULL`이라는 글자로 적혀 있다. 없으면 메모 칸에 글자 `NULL`이 들어간다 |
| `COPY` | 전부 들어가거나, 오류가 나면 한 줄도 들어가지 않는다 |

- 테이블 구조는 **CSV의 칸을 보고 만든 것**이다. 작업한 팀원의 `01_table.sql`과 다를 수 있다
- 조각이 120개뿐이라 벡터 검색용 인덱스는 만들지 않는다

### 16-5. 확인

```bash
# 서버: ① 개수 (서비스 계정으로 읽히는지도 함께 본다)
sudo -u postgres psql -d campine_vector -c "SET ROLE campine_app" -c "SELECT count(*), count(embedding) FROM safety_doc_chunk;"
# ② 벡터 검색: JD-001과 뜻이 가까운 조각 3개
sudo -u postgres psql -d campine_vector -c "SELECT id, heading, round((embedding <=> (SELECT embedding FROM safety_doc_chunk WHERE id='JD-001'))::numeric, 3) AS distance FROM safety_doc_chunk ORDER BY distance LIMIT 3;"
```

| 확인 | 나와야 하는 값 (작업 PC에서 CSV로 계산) | 서버 결과 |
|---|---|---|
| ① 개수 | 120, 120 | 아직 |
| ② 첫째 줄 | `JD-001`, 거리 0 | 아직 |
| ② 둘째 줄 | "물놀이 사망: 장소는 하천·계곡…", 거리 약 0.508 | 아직 |

`<=>`는 두 벡터가 얼마나 다른지(코사인 거리) 재는 기호다. 작을수록 뜻이 비슷하다.

### 16-6. RAG 코드를 만들 때 지킬 것

- **임베딩 모델을 똑같이 쓴다** (`text-embedding-3-small`). 다른 모델로 질문을 바꾸면 엉뚱한 자료가 나온다
- OpenAI 키와 DB 비밀번호는 코드에 적지 않고 서버 환경변수로 넣는다 (12-1)
- 안전 정보이므로 **찾은 자료 안에서만 답하고 출처를 보여 준다**
- AI 호출이 실패하면 지금의 문장 틀 브리핑이 나오게 한다
- APK는 다시 만들지 않아도 된다

## 겪은 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| 업데이트 중 `/etc/issue`, `/etc/issue.net`을 바꿀지 물음 | 로그인 안내 문구 파일이 서버에서 지워져 있었음 | `N`(현재 상태 유지). 동작에는 영향 없음 |
| nginx 설치 중 `dpkg returned an error code (1)` | **정확한 원인은 기록하지 못함.** IPv6가 꺼진 서버에서 `[::]:80`을 열려다 실패한 경우를 의심했음 | 해결됨(지금 nginx 정상). 실제로 쓴 방법은 확인되면 적는다 |
| 브라우저에서 사이트가 열리지 않음 | 80 · 443이 시간 초과 → ACG 규칙 문제로 판단 | 다시 시험하니 80이 열리고 443은 ACG를 통과함 |
| `scp`에서 `100%`가 나오지 않음 | 정확한 원인은 확인하지 못함. `scp`를 서버 창에서 실행한 경우를 의심했음 | `scp`는 **내 PC의 PowerShell**(`PS C:\...`)에서 실행한다 |
| `relation "camp" does not exist` | 정확한 원인은 확인하지 못함. 복원이 안 됐거나 취소된 상태로 추정 (복사본에는 `camp`가 있었음) | 해결됨. 이후 개수가 정상으로 나옴 |
| 백엔드는 켜졌는데 DB가 필요한 API만 실패 (`잠시 후 다시 시도해 주세요.`) | 정확한 원인은 기록하지 못함. DB 비밀번호를 의심했음 | 비밀번호를 다시 맞추고 `systemctl restart campine`을 하는 방법을 안내했고, 이후 정상 |

## 용어

| 말 | 뜻 |
|---|---|
| 서버 | 24시간 켜 두고 서비스를 제공하는 컴퓨터 |
| 공인 IP | 인터넷에서 우리 서버를 찾아오는 주소 |
| 사설 IP | 내부망 안에서만 쓰는 주소 (`10.0.1.x`) |
| 포트 | 서버의 문 번호. 프로그램마다 다른 문을 쓴다 |
| ACG | 네이버 클라우드의 서버 방화벽 |
| SSH | 내 PC에서 서버의 명령창을 안전하게 여는 방법 |
| root | 서버의 최고 관리자 계정 |
| apt | Ubuntu에서 프로그램을 설치하는 명령 |
| 덤프 | DB를 파일로 복사한 것 |
| Tailscale | 등록된 기기끼리만 통하는 사설망(VPN) |
| 빌드 | 코드를 배포용 파일로 바꾸는 일 (`dist`, `.jar`) |
| sha256 | 파일의 지문. 내용이 조금만 달라도 값이 바뀌어 깨졌는지 알 수 있다 |
| 환경변수 | 코드 밖, 서버에 따로 적어 두는 설정값 |
| CORS | 어느 주소의 화면이 이 서버에 요청해도 되는지 정하는 규칙 |
| DNS | 도메인 이름을 IP 주소로 바꿔 주는 인터넷 전화번호부 |
| HTTPS | 주고받는 내용을 암호화하는 접속 방식. 인증서가 필요하다 |
| 임베딩 | 글의 뜻을 숫자 여러 개(벡터)로 바꾼 것 |
| RAG | 자료를 먼저 찾고, 찾은 내용을 바탕으로 AI가 답하게 하는 방법 |
| pgvector | PostgreSQL에서 벡터를 저장하고 비슷한 것을 찾게 해 주는 확장 프로그램 |
| 403 | "요청은 받았지만 허용하지 않는다"는 응답 |
