# 评价体系（/evaluation 与 /evaluation/records）数据链验证
#
# 断言的是页面真正依赖的形状，不是「接口返回 200」：
#   · 指标页签读 /api/evaluation/metrics —— 少一个字段卡片就会显示 undefined
#   · 能力边界页签读 /api/predict/ddi/drugs —— DDI 的硬边界就是这份白名单
#   · 记录页读 /api/results 与 /api/tasks
#   · 候选评估页的「外部预测」读 /api/design/candidates/{id}/predictions
#
# 最后一段还锁住三条纪律：同一预测结果重复挂接必须幂等、algoType 非法必须 400、
# 别人的候选必须 404（不是 403，避免探测）。
$ErrorActionPreference = 'Stop'
$GW = 'http://localhost:8080'
$pass = 0; $fail = 0

function Check([string]$name, [bool]$ok, [string]$detail) {
  if ($ok) { $script:pass++; Write-Output ("PASS  {0}  {1}" -f $name, $detail) }
  else { $script:fail++; Write-Output ("FAIL  {0}  {1}" -f $name, $detail) }
}

function Invoke-Json([string]$method, [string]$path, $obj, [string]$token) {
  $headers = @{}
  if ($token) { $headers['Authorization'] = "Bearer $token" }
  $req = @{ Uri = "$GW$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
  if ($null -ne $obj) {
    $req['ContentType'] = 'application/json; charset=utf-8'
    $req['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 8 -Compress))
  }
  try {
    $r = Invoke-WebRequest @req
    return @{ Status = [int]$r.StatusCode; Body = ([System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray()) | ConvertFrom-Json) }
  } catch {
    $resp = $_.Exception.Response
    if ($null -eq $resp) { throw }
    $sr = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
    return @{ Status = [int]$resp.StatusCode; Body = ($sr.ReadToEnd() | ConvertFrom-Json) }
  }
}

$login = Invoke-Json 'POST' '/api/auth/login' @{ loginType = 'guest' } $null
$token = $login.Body.data.accessToken
Check 'auth.guest' ($login.Status -eq 200 -and $token) "userId=$($login.Body.data.user.id)"

# ---------------------------------------------------------------- 指标目录
$m = Invoke-Json 'GET' '/api/evaluation/metrics' $null $token
$metrics = @($m.Body.data)
Check 'metrics.status' ($m.Status -eq 200) "count=$($metrics.Count)"
Check 'metrics.count' ($metrics.Count -ge 20) "count=$($metrics.Count)"

$dt = $metrics | Where-Object { $_.metricCode -eq 'DTI_CONFIDENCE' } | Select-Object -First 1
Check 'metrics.fields' ([bool]$dt.metricName -and [bool]$dt.category -and [bool]$dt.better) `
  "name=$($dt.metricName) cat=$($dt.category) better=$($dt.better) weight=$($dt.weight)"
Check 'metrics.weight' ([decimal]$dt.weight -eq 0.3) "DTI_CONFIDENCE weight=$($dt.weight)"

# applies_to 是 JSON 数组文本，服务端必须解析成数组；解析失败会退化成空数组
$ab = $metrics | Where-Object { $_.metricCode -eq 'TM_STABILITY' } | Select-Object -First 1
Check 'metrics.appliesTo.parsed' (@($ab.appliesTo).Count -ge 1) "TM_STABILITY appliesTo=$((@($ab.appliesTo)) -join ',')"
$wt = $metrics | Where-Object { $_.metricCode -eq 'MOL_WT' } | Select-Object -First 1
Check 'metrics.appliesTo.emptyMeansAll' (@($wt.appliesTo).Count -eq 0) "MOL_WT appliesTo count=$(@($wt.appliesTo).Count)"

# 权重之和不要求为 1（综合分按实算指标的权重归一化），但必须 > 0 否则体系空转
$wsum = ($metrics | Measure-Object -Property weight -Sum).Sum
Check 'metrics.weightSum' ([decimal]$wsum -gt 0) "sum=$wsum"

# ---------------------------------------------------------------- 能力边界
$ddi = Invoke-Json 'GET' '/api/predict/ddi/drugs' $null $token
$drugs = @($ddi.Body.data.drugs)
Check 'boundary.ddiWhitelist' ($ddi.Status -eq 200 -and $drugs.Count -gt 0) "drugs=$($drugs.Count)"
Check 'boundary.ddiDrug.fields' ([bool]$drugs[0].drugId) "drugId=$($drugs[0].drugId) name=$($drugs[0].drugName)"

# ---------------------------------------------------------------- 记录页数据
$res = Invoke-Json 'GET' '/api/results?page=1&pageSize=10' $null $token
Check 'records.results' ($res.Status -eq 200 -and $null -ne $res.Body.data.total) "total=$($res.Body.data.total)"
$tsk = Invoke-Json 'GET' '/api/tasks' $null $token
Check 'records.tasks' ($tsk.Status -eq 200 -and $null -ne $tsk.Body.data) "tasks=$(@($tsk.Body.data).Count)"

# ---------------------------------------------------------------- 候选 × 预测回写
$mod = Invoke-Json 'GET' '/api/design/library?category=MODALITY&page=1&pageSize=100' $null $token
$small = @($mod.Body.data.list) | Where-Object { $_.name -eq '小分子' } | Select-Object -First 1
if (-not $small) { $small = @($mod.Body.data.list)[0] }
$tl = Invoke-Json 'GET' '/api/design/targets?page=1&pageSize=20' $null $token
$target = @($tl.Body.data.list)[0]
$proj = Invoke-Json 'POST' '/api/design/projects' @{
  name = '评价体系打通验证'
  modalityId = $small.id
  moaCode = 'INHIBITOR'
  minCandidates = 2
  quotaGpuHours = 24
  quotaWetLab = 4
  targets = @(@{ targetId = $target.id; role = 'WILD_TYPE'; isPrimary = $true })
  constraintRules = @(@{
    ruleType = 'NUMERIC_RANGE'; severity = 'HARD'
    params = @{ field = 'MolWt'; min = 150; max = 600 }
    weight = 10; humanReadable = 'MW 150-600'
  })
} $token
$projId = $proj.Body.data.id

$null = Invoke-Json 'POST' "/api/design/projects/$projId/metrics:init-from-modality" @{} $token
$pv = Invoke-Json 'POST' "/api/design/projects/$projId/baseline:preview" @{} $token
$fz = Invoke-Json 'POST' "/api/design/projects/$projId/baseline:freeze" @{ expectedContentHash = $pv.Body.data.contentHash } $token
$rd = Invoke-Json 'POST' "/api/design/projects/$projId/rounds" @{ strategyNote = 'evaluation' } $token
$cand = Invoke-Json 'POST' "/api/design/projects/$projId/candidates" @{ smiles = 'CC(=O)Oc1ccccc1C(=O)O'; moleculeName = 'aspirin' } $token
$candId = $cand.Body.data.candidateId
Check 'link.candidateReady' ([bool]$candId) `
  "projectId=$projId freeze=$($fz.Status) round=$($rd.Status) candidate=$($cand.Status) msg=$($cand.Body.message)"
if (-not $candId) {
  Write-Output ("RESULT  pass={0} fail={1}" -f $pass, ($fail + 1))
  exit 1
}

$attachBody = @{
  algoType = 'DTI'
  targetId = $target.uniprotId
  targetName = $target.name
  ligandSmiles = 'CC(=O)Oc1ccccc1C(=O)O'
  bindingAffinity = -7.42
  confidenceScore = 0.8612
  confidenceLevel = 'high'
  predictResultId = "probe-$projId-$candId"
  interactions = @(@{ type = 'hydrogen_bond'; residueName = 'MET'; residueNumber = 793; distance = 2.9 })
  datasetInfo = @{ name = 'DTI 预测结果'; source = 'predict' }
}
$a1 = Invoke-Json 'POST' "/api/design/candidates/$candId/predictions" $attachBody $token
Check 'link.attach' ($a1.Status -eq 200 -and [bool]$a1.Body.data.id) "id=$($a1.Body.data.id) cid=$($a1.Body.data.candidateId)"

# 幂等：同一 predictResultId 再挂一次不该产生第二条
$a2 = Invoke-Json 'POST' "/api/design/candidates/$candId/predictions" $attachBody $token
Check 'link.attachIdempotent' ($a2.Status -eq 200 -and $a2.Body.data.id -eq $a1.Body.data.id) `
  "first=$($a1.Body.data.id) second=$($a2.Body.data.id)"

$lst = Invoke-Json 'GET' "/api/design/candidates/$candId/predictions" $null $token
$rows = @()
if ($null -ne $lst.Body.data) { $rows = @($lst.Body.data) }
Check 'link.list' ($lst.Status -eq 200 -and $rows.Count -eq 1) "status=$($lst.Status) rows=$($rows.Count)"
Check 'link.jsonRoundTrip' (@($rows[0].interactions).Count -eq 1 -and [bool]$rows[0].datasetInfo.name) `
  "interactions=$(@($rows[0].interactions).Count) dataset=$($rows[0].datasetInfo.name)"
Check 'link.numericRoundTrip' ([decimal]$rows[0].bindingAffinity -eq -7.42 -and [decimal]$rows[0].confidenceScore -eq 0.8612) `
  "affinity=$($rows[0].bindingAffinity) confidence=$($rows[0].confidenceScore)"

$bad = Invoke-Json 'POST' "/api/design/candidates/$candId/predictions" @{ algoType = 'XYZ' } $token
Check 'link.badAlgoType' ($bad.Status -eq 400 -and $bad.Body.errorCode -eq 'VALIDATION_FAILED') `
  "status=$($bad.Status) code=$($bad.Body.errorCode)"

# 别人的候选：404 而不是 403 —— 403 等于告诉对方「这个 id 存在」
$foreign = Invoke-Json 'GET' '/api/design/candidates/1/predictions' $null $token
Check 'link.foreignCandidate404' ($foreign.Status -eq 404 -and $foreign.Body.errorCode -eq 'CANDIDATE_NOT_FOUND') `
  "status=$($foreign.Status) code=$($foreign.Body.errorCode)"

$del = Invoke-Json 'DELETE' "/api/design/candidates/$candId/predictions/$($a1.Body.data.id)" $null $token
$after = Invoke-Json 'GET' "/api/design/candidates/$candId/predictions" $null $token
$afterRows = @()
if ($null -ne $after.Body.data) { $afterRows = @($after.Body.data) }
Check 'link.detach' ($del.Status -eq 200 -and $afterRows.Count -eq 0) `
  "status=$($del.Status) remaining=$($afterRows.Count)"

Write-Output ("RESULT  pass={0} fail={1}" -f $pass, $fail)
if ($fail -gt 0) { exit 1 }
