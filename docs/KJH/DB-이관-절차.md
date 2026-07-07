# DB 이관 절차 (기숙사 → EC2)

기숙사 PC에서 쌓은 수집 데이터(user_favorites·game_cofavorite 등)를 배포 시점에 EC2로 옮기는 절차.
**실행은 사람이 기숙사·EC2에서 직접 한다** (두 머신은 코드에서 접근 불가).

---

## 왜 순서가 중요한가

EC2는 **배포하는 순간 배치 컨테이너가 빈 DB로 수집을 시작**한다 (`docker-compose-prod.yml`의 batch,
`restart: unless-stopped` + 루프). 그 위에 기숙사 덤프를 로드하면 mysqldump가 `DROP TABLE + CREATE TABLE`을
포함하므로 **EC2가 그동안 모은 게 덮여서 날아간다**. → 그래서 **덤프 로드가 끝날 때까지 EC2 배치를 멈춰둔다.**

기숙사가 훨씬 크므로(덤프가 EC2를 교체하는 게 맞음), "기숙사 데이터로 교체 → 그 위에서 EC2가 이어서 수집"이 목표.

---

## 1단계 — 기숙사 PC: 덤프 뜨기

쓰기 가능한 폴더(홈)에서. `--single-transaction`이라 **수집 도는 중에도 안전**(락 안 걸림, InnoDB).
`--result-file`을 쓰는 이유: PowerShell의 `>`는 UTF-16으로 저장해 SQL을 깨뜨림.

```powershell
cd ~
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe" -u root -p `
  --single-transaction --result-file=roblox_rec_dump.sql roblox_rec
```

배포 당일엔 **가장 최신 데이터로 새로 뜨는 것**을 권장(기존 백업 파일 재사용 X — 그새 더 모임).
크기 확인: `(Get-Item roblox_rec_dump.sql).Length / 1MB` → 수십~수백 MB면 정상.

---

## 2단계 — EC2로 전송

```bash
# 로컬(기숙사 or 노트북)에서
scp roblox_rec_dump.sql ec2-user@madfinder.site:~/
```

크면 먼저 압축해서 옮겨도 됨(zip/gzip). SSH 키(.pem)는 배포에 쓰던 것과 동일.

---

## 3단계 — EC2: 배치 멈추고 → 로드 → 재개

EC2에 SSH 접속 후, 레포 디렉토리(`~/26s-w1-c3-09`)에서:

```bash
cd ~/26s-w1-c3-09
C=docker-compose-prod.yml

# (1) EC2 배치 수집 멈춤 (로드 중 DB 안 건드리게)
docker compose -f $C stop batch

# (2) 덤프를 MySQL 컨테이너에 로드 (서비스명 mysql로 접속, -T=파이프용)
#     비밀번호는 EC2 루트 .env의 DB_PASSWORD
docker compose -f $C exec -T mysql mysql -u root -p"$DB_PASSWORD" roblox_rec < ~/roblox_rec_dump.sql

# (3) 배치 수집 재개 (이제 기숙사 데이터 위에서 이어서 수집)
docker compose -f $C start batch
```

> `$DB_PASSWORD`가 셸에 없으면 실제 비번을 직접 넣거나 `-p` 만 쓰고 프롬프트 입력.
> 덤프에 `DROP TABLE IF EXISTS`가 있어 EC2의 기존 테이블을 기숙사 것으로 **교체**한다(의도된 동작).

---

## 4단계 — 검증 (로드 제대로 됐는지)

```bash
docker compose -f $C exec -T mysql mysql -u root -p"$DB_PASSWORD" roblox_rec -e "
  SELECT 'user_favorites' t, COUNT(*) n FROM user_favorites
  UNION SELECT 'game_cofavorite', COUNT(*) FROM game_cofavorite
  UNION SELECT 'games', COUNT(*) FROM games;"
```

기숙사에서 뜬 시점의 행 수와 대략 일치하면 성공. (예: fav 200만+·cofav 50만+ 규모)

---

## 대안 — 첫 배포 때 아예 배치 없이 올리기

배포 자체를 처음 하는 거라면, EC2가 빈 수집을 시작조차 안 하게:

```bash
# batch 빼고 올림
docker compose -f $C up -d mysql server frontend
# → 2·3단계로 덤프 로드
# → 그다음 배치 켜기
docker compose -f $C up -d batch
```

이러면 EC2의 헛수집이 0. (자동배포 워크플로가 `up -d`로 전체를 올리므로, 이 방식을 쓰려면
첫 배포는 수동으로 하거나 배포 직후 바로 `stop batch` → 로드 → `start batch`가 더 현실적.)

---

## 주의

- 기숙사 MySQL 비번과 EC2 MySQL 비번은 **다를 수 있다**(각 머신 .env). 로드는 EC2 비번 사용.
- 로드 중 EC2 서버(API)는 켜둬도 되지만, 그 사이 웹 유저가 티어 저장 등을 하면 덮일 수 있으니
  트래픽 없는 시점에 하는 게 안전.
- 스키마는 양쪽 동일(`db-schema.sql` 기준)이라 mismatch 없음. 덤프가 스키마째 교체.
