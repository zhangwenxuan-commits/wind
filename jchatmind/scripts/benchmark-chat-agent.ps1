param(
    [Parameter(Mandatory = $true)]
    [string]$AgentId,
    [string]$BaseUrl = "http://localhost:8080",
    [int]$MeasuredRounds = 10,
    [int]$WarmupRounds = 2,
    [int]$PollIntervalMs = 500,
    [int]$TimeoutSeconds = 120,
    [bool]$ReuseSession = $true,
    [string]$Label = "cache-benchmark",
    [switch]$DumpMetrics
)

$ErrorActionPreference = "Stop"

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null
    )

    $uri = $BaseUrl.TrimEnd("/") + $Path
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10
        return Invoke-RestMethod -Method $Method -Uri $uri -ContentType "application/json" -Body $json
    }

    return Invoke-RestMethod -Method $Method -Uri $uri
}

function New-ChatSession {
    param([string]$AgentIdValue, [string]$Title)

    $response = Invoke-Api -Method "Post" -Path "/api/chat-sessions" -Body @{
        agentId = $AgentIdValue
        title   = $Title
    }

    if ($response.code -ne 200) {
        throw "Create chat session failed: $($response | ConvertTo-Json -Depth 10)"
    }

    return $response.data.chatSessionId
}

function Get-ChatMessages {
    param([string]$SessionId)

    $response = Invoke-Api -Method "Get" -Path "/api/chat-messages/session/$SessionId"
    if ($response.code -ne 200) {
        throw "Get chat messages failed: $($response | ConvertTo-Json -Depth 10)"
    }

    return @($response.data.chatMessages)
}

function Send-UserMessage {
    param(
        [string]$AgentIdValue,
        [string]$SessionId,
        [string]$Content
    )

    $response = Invoke-Api -Method "Post" -Path "/api/chat-messages" -Body @{
        agentId  = $AgentIdValue
        sessionId = $SessionId
        role     = "user"
        content  = $Content
    }

    if ($response.code -ne 200) {
        throw "Create chat message failed: $($response | ConvertTo-Json -Depth 10)"
    }
}

function Wait-ForAgentCompletion {
    param(
        [string]$SessionId,
        [int]$BaselineCount
    )

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $stablePolls = 0
    $lastCount = -1
    $sawAgentOutput = $false

    while ($stopwatch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        $messages = Get-ChatMessages -SessionId $SessionId
        $currentCount = @($messages).Count
        $newMessages = @($messages | Select-Object -Skip $BaselineCount)
        $agentMessages = @($newMessages | Where-Object { $_.role -in @("assistant", "tool") })

        if ($agentMessages.Count -gt 0) {
            $sawAgentOutput = $true
        }

        if ($sawAgentOutput -and $currentCount -eq $lastCount) {
            $stablePolls++
        }
        else {
            $stablePolls = 0
        }

        if ($sawAgentOutput -and $stablePolls -ge 2) {
            return [pscustomobject]@{
                TurnElapsedMs = $stopwatch.ElapsedMilliseconds
                TotalMessages = $currentCount
                AgentMessages = $agentMessages.Count
                LastRole      = if ($currentCount -gt 0) { $messages[-1].role } else { $null }
            }
        }

        $lastCount = $currentCount
        Start-Sleep -Milliseconds $PollIntervalMs
    }

    throw "Timed out waiting for agent completion. sessionId=$SessionId"
}

function Get-Percentile {
    param(
        [double[]]$Values,
        [double]$Percentile
    )

    if ($null -eq $Values -or $Values.Count -eq 0) {
        return $null
    }

    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
    if ($index -lt 0) {
        $index = 0
    }

    return [Math]::Round($sorted[$index], 2)
}

function Show-MetricSnapshot {
    param([string[]]$Names)

    foreach ($name in $Names) {
        try {
            $metric = Invoke-Api -Method "Get" -Path "/actuator/metrics/$([System.Uri]::EscapeDataString($name))"
            $measurements = @($metric.measurements | ForEach-Object {
                "{0}={1}" -f $_.statistic, $_.value
            })
            Write-Host ("[metric] {0} -> {1}" -f $name, ($measurements -join ", "))
        }
        catch {
            Write-Warning "Metric not available: $name"
        }
    }
}

function Show-TaggedMetric {
    param(
        [string]$Name,
        [hashtable]$Tags
    )

    try {
        $query = @()
        foreach ($entry in $Tags.GetEnumerator()) {
            $query += "tag=$([System.Uri]::EscapeDataString('{0}:{1}' -f $entry.Key, $entry.Value))"
        }
        $path = "/actuator/metrics/$([System.Uri]::EscapeDataString($Name))"
        if ($query.Count -gt 0) {
            $path += "?" + ($query -join "&")
        }

        $metric = Invoke-Api -Method "Get" -Path $path
        $measurements = @($metric.measurements | ForEach-Object {
            "{0}={1}" -f $_.statistic, $_.value
        })
        $tagText = @($Tags.GetEnumerator() | ForEach-Object { "{0}={1}" -f $_.Key, $_.Value }) -join ", "
        Write-Host ("[metric] {0} [{1}] -> {2}" -f $Name, $tagText, ($measurements -join ", "))
    }
    catch {
        $tagText = @($Tags.GetEnumerator() | ForEach-Object { "{0}={1}" -f $_.Key, $_.Value }) -join ", "
        Write-Warning "Metric not available: $Name [$tagText]"
    }
}

function Show-LlmTokenMetrics {
    try {
        $baseMetric = Invoke-Api -Method "Get" -Path "/actuator/metrics/agent.llm.tokens"
        $modelTag = @($baseMetric.availableTags | Where-Object { $_.tag -eq "model" } | Select-Object -First 1)
        $models = if ($modelTag.Count -gt 0) { @($modelTag[0].values) } else { @("unknown") }

        foreach ($model in $models) {
            Show-TaggedMetric -Name "agent.llm.tokens" -Tags @{ stage = "primary"; type = "prompt"; model = $model }
            Show-TaggedMetric -Name "agent.llm.tokens" -Tags @{ stage = "primary"; type = "completion"; model = $model }
            Show-TaggedMetric -Name "agent.llm.tokens" -Tags @{ stage = "primary"; type = "total"; model = $model }
            Show-TaggedMetric -Name "agent.llm.tokens" -Tags @{ stage = "compression"; type = "prompt"; model = $model }
            Show-TaggedMetric -Name "agent.llm.tokens" -Tags @{ stage = "compression"; type = "completion"; model = $model }
            Show-TaggedMetric -Name "agent.llm.tokens" -Tags @{ stage = "compression"; type = "total"; model = $model }
        }
    }
    catch {
        Write-Warning "Metric not available: agent.llm.tokens"
    }
}

$totalRounds = $WarmupRounds + $MeasuredRounds
$measuredResults = New-Object System.Collections.Generic.List[object]

if ($ReuseSession) {
    $sharedSessionId = New-ChatSession -AgentIdValue $AgentId -Title "$Label-shared-session"
    Write-Host ("[setup] shared session created: {0}" -f $sharedSessionId)
}

for ($round = 1; $round -le $totalRounds; $round++) {
    $phase = if ($round -le $WarmupRounds) { "warmup" } else { "measured" }
    $sessionId = if ($ReuseSession) {
        $sharedSessionId
    }
    else {
        New-ChatSession -AgentIdValue $AgentId -Title "$Label-round-$round"
    }

    $baselineCount = @(Get-ChatMessages -SessionId $sessionId).Count
    $content = "[${Label}] round=$round phase=$phase ts=$([DateTimeOffset]::UtcNow.ToString('O'))"

    $httpStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    Send-UserMessage -AgentIdValue $AgentId -SessionId $sessionId -Content $content
    $httpStopwatch.Stop()

    $turnResult = Wait-ForAgentCompletion -SessionId $sessionId -BaselineCount $baselineCount

    $result = [pscustomobject]@{
        Round         = $round
        Phase         = $phase
        SessionId     = $sessionId
        HttpPostMs    = $httpStopwatch.ElapsedMilliseconds
        TurnElapsedMs = $turnResult.TurnElapsedMs
        AgentMessages = $turnResult.AgentMessages
        LastRole      = $turnResult.LastRole
    }

    Write-Host ("[round {0}] phase={1} session={2} http={3}ms turn={4}ms agentMessages={5} lastRole={6}" -f
        $result.Round,
        $result.Phase,
        $result.SessionId,
        $result.HttpPostMs,
        $result.TurnElapsedMs,
        $result.AgentMessages,
        $result.LastRole
    )

    if ($phase -eq "measured") {
        $measuredResults.Add($result) | Out-Null
    }
}

$turnValues = @($measuredResults | ForEach-Object { [double]$_.TurnElapsedMs })
$httpValues = @($measuredResults | ForEach-Object { [double]$_.HttpPostMs })

$summary = [pscustomobject]@{
    label          = $Label
    agent_id       = $AgentId
    measured_rounds = $MeasuredRounds
    reuse_session  = $ReuseSession
    avg_turn_ms    = [Math]::Round(($turnValues | Measure-Object -Average).Average, 2)
    p50_turn_ms    = Get-Percentile -Values $turnValues -Percentile 50
    p95_turn_ms    = Get-Percentile -Values $turnValues -Percentile 95
    p99_turn_ms    = Get-Percentile -Values $turnValues -Percentile 99
    avg_http_ms    = [Math]::Round(($httpValues | Measure-Object -Average).Average, 2)
    p95_http_ms    = Get-Percentile -Values $httpValues -Percentile 95
}

Write-Host ""
Write-Host "=== Benchmark Summary ==="
$summary | Format-List

if ($DumpMetrics) {
    Write-Host ""
    Write-Host "=== Actuator Metric Snapshot ==="
    Show-MetricSnapshot -Names @(
        "agent.turn.total",
        "agent.runtime.create",
        "agent.memory.load",
        "agent.memory.db.load",
        "agent.llm.think",
        "agent.tool.execute",
        "agent.tool.process",
        "agent.cache.requests",
        "agent.context.message.count",
        "agent.context.char.count",
        "agent.tool.payload.chars"
    )
    Show-LlmTokenMetrics
}
