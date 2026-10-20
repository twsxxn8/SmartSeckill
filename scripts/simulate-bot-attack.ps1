<#
.SYNOPSIS
    模拟机器人攻击脚本 —— 批量调用秒杀接口，验证 AI 风控拦截效果。

.DESCRIPTION
    以脚本级速度（无人类操作间隔）对秒杀接口发起高频冲击：
      1. 每个账号循环执行「领 Token → 立即秒杀」，Token→执行间隔 <100ms（触发 +25）
      2. 同一账号请求间隔 <80ms（触发 +40）
      3. 多账号共用同一出口 IP（触发 +30 IP多账号）
    预期观察到的风控升级链路：
      PASS/DELAY → VERIFY(1402) → BLOCK(1401) → 3次BLOCK后进入黑名单(100分直接拦截)
    可选 -AutoVerify：命中 1402 时自动完成人机验证（GET /risk/verify/challenge + POST /risk/verify），
    验证通过后 10 分钟内放行，用于演示 VERIFY 救济通道。

.PARAMETER ActivityId
    目标秒杀活动 ID（必须处于进行中且已预热库存）。

.PARAMETER Users
    参与攻击的测试账号（密码均为 123456）。建议使用未被拉黑过的"干净"账号，
    可观察到完整的 VERIFY→BLOCK→黑名单 升级曲线。

.PARAMETER Rounds
    每个账号发起的秒杀次数。

.PARAMETER IntervalMs
    同一账号相邻请求间隔（毫秒）。默认 0 = 全速冲击。
    若想模拟"略像人"的慢速脚本可设为 200~500（此时 +40 不触发，评分更低）。

.EXAMPLE
    .\simulate-bot-attack.ps1 -ActivityId 25
    .\simulate-bot-attack.ps1 -ActivityId 25 -Users @("wangwu","zhaoliu") -Rounds 12 -AutoVerify
#>
param(
    [long]$ActivityId = 25,
    [string[]]$Users = @("wangwu", "zhaoliu", "sunqi", "zhouba", "wujiu"),
    [string]$Password = "123456",
    [int]$Rounds = 10,
    [int]$IntervalMs = 0,
    [string]$BaseUrl = "http://localhost:8080/api",
    [switch]$AutoVerify
)

$ErrorActionPreference = "Stop"

# 结果码 → 风控含义映射
$CodeMap = @{
    200  = @{ Name = "PASS/排队"; Color = "Green" }
    1401 = @{ Name = "BLOCK拦截"; Color = "Red" }
    1402 = @{ Name = "VERIFY验证"; Color = "Yellow" }
}

function Invoke-Api {
    param([string]$Method, [string]$Url, [hashtable]$Headers, [object]$Body)
    try {
        $params = @{ Method = $Method; Uri = $Url; ContentType = "application/json" }
        if ($Headers) { $params.Headers = $Headers }
        if ($Body) { $params.Body = ($Body | ConvertTo-Json) }
        $r = Invoke-RestMethod @params
        return @{ Code = $r.code; Message = $r.message; Data = $r.data }
    } catch {
        $detail = $_.ErrorDetails.Message | ConvertFrom-Json
        return @{ Code = $detail.code; Message = $detail.message; Data = $null }
    }
}

function Write-Attempt {
    param([int]$Round, [int]$Code, [string]$Message)
    $info = $CodeMap[$Code]
    if ($info) {
        Write-Host ("    第{0,2}次: {1} ({2}) {3}" -f $Round, $Code, $info.Name, $Message) -ForegroundColor $info.Color
    } else {
        Write-Host ("    第{0,2}次: {1} {2}" -f $Round, $Code, $Message) -ForegroundColor DarkGray
    }
}

# ==================== 主流程 ====================
$globalStats = @{}

foreach ($username in $Users) {
    Write-Host "`n========== 机器人账号: $username ==========" -ForegroundColor Cyan

    # 1. 登录拿 JWT
    $login = Invoke-Api -Method Post -Url "$BaseUrl/auth/login" -Body @{ username = $username; password = $Password }
    if ($login.Code -ne 200) { Write-Host "  登录失败: $($login.Message)" -ForegroundColor DarkGray; continue }
    $headers = @{ Authorization = "Bearer $($login.Data.accessToken)" }
    $userId = $login.Data.userId

    $stats = @{ PASS = 0; VERIFY = 0; BLOCK = 0; OTHER = 0 }

    for ($i = 1; $i -le $Rounds; $i++) {
        # 2. 领防重 Token（脚本速度：领完立即执行，无人类反应时间）
        $tokenResp = Invoke-Api -Method Get -Url "$BaseUrl/seckill/token?activityId=$ActivityId" -Headers $headers
        $token = if ($tokenResp.Code -eq 200) { $tokenResp.Data.token } else { "dummy-$i" }

        # 3. 立即执行秒杀
        $exec = Invoke-Api -Method Post -Url "$BaseUrl/seckill/execute" -Headers $headers `
                           -Body @{ activityId = $ActivityId; token = $token }
        Write-Attempt -Round $i -Code $exec.Code -Message $exec.Message

        switch ($exec.Code) {
            200  { $stats.PASS++ }
            1401 { $stats.BLOCK++ }
            1402 {
                $stats.VERIFY++
                if ($AutoVerify) {
                    # 命中 VERIFY → 自动完成人机验证（演示救济通道）
                    $ch = Invoke-Api -Method Get -Url "$BaseUrl/risk/verify/challenge" -Headers $headers
                    if ($ch.Code -eq 200 -and $ch.Data.question -match '(\d+) \+ (\d+)') {
                        $answer = [int]$Matches[1] + [int]$Matches[2]
                        $v = Invoke-Api -Method Post -Url "$BaseUrl/risk/verify" -Headers $headers `
                                        -Body @{ activityId = $ActivityId; token = $ch.Data.token; answer = $answer }
                        Write-Host ("           ↳ 人机验证: {0} (code={1})" -f $v.Message, $v.Code) -ForegroundColor Magenta
                    }
                }
            }
            default { $stats.OTHER++ }
        }

        if ($IntervalMs -gt 0 -and $i -lt $Rounds) { Start-Sleep -Milliseconds $IntervalMs }
    }

    # 4. 单账号小结
    Write-Host ("  小结: PASS={0} VERIFY={1} BLOCK={2} 其他={3}" -f $stats.PASS, $stats.VERIFY, $stats.BLOCK, $stats.OTHER) -ForegroundColor White
    if ($stats.BLOCK -ge 3) {
        Write-Host "  ⚠ 该账号 24h 内已 3 次被 BLOCK，预期已进入黑名单（risk:blacklist:$userId）" -ForegroundColor Red
    }
    $globalStats[$username] = $stats
}

# ==================== 全局汇总 ====================
Write-Host "`n========== 攻击汇总 ==========" -ForegroundColor Cyan
"{0,-12} {1,6} {2,8} {3,8} {4,6}" -f "账号", "PASS", "VERIFY", "BLOCK", "其他"
foreach ($u in $globalStats.Keys) {
    $s = $globalStats[$u]
    "{0,-12} {1,6} {2,8} {3,8} {4,6}" -f $u, $s.PASS, $s.VERIFY, $s.BLOCK, $s.OTHER
}
Write-Host "`n提示: 决策明细已异步落库，可用管理员账号查询 GET /admin/risk/decisions?userId={id} 核对" -ForegroundColor DarkGray
