# 24시간 상시 수집 루프 (기숙사 PC). b1 -> b2 -> b3 -> b4 무한 반복.
# (docker-compose-prod.yml의 batch 루프와 동일한 순서·의미)
#
# 실행: PowerShell 창에서  .\run_loop.ps1  (이 창을 열어둔 채로 두면 계속 돎)
#   - 창을 닫거나 PC가 절전에 들어가면 멈춤 → 전원 옵션 "절전 안 함" 권장
#   - 선행: DB_PASSWORD가 사용자 환경변수로 설정돼 있어야 함
#       [Environment]::SetEnvironmentVariable("DB_PASSWORD", "본인비번", "User")  (한 번만, 새 창부터 적용)
#   - 잡 하나 실패해도 루프는 계속 (커서 group_cursors로 이어받기라 안전)

Set-Location "$PSScriptRoot\.."   # backend/batch 로 이동 (python -m jobs.* 가 여기 기준)

while ($true) {
    Write-Host "=== loop $(Get-Date -Format 'HH:mm:ss') ===" -ForegroundColor Cyan
    python -m jobs.b1_charts
    python -m jobs.b2_queue_consumer
    python -m jobs.b3_rec_cache
    python -m jobs.b4_fan_collector
}
