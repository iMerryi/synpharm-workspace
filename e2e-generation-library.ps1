# 阶段 C 端到端验证：从数据资产库选种子（不给 SMILES 也能生成）
#
# 覆盖的是「非科班用户路径」：前端只传 libraryItemId，服务端按可见性解析结构。
#   ① 分子库条目当种子 → 真 RDKit 粗筛 → 候选落库 → 候选带库里的名字
#   ② 二甲双胍（MW 129）被基线 MW 150~600 硬约束真淘汰
#   ③ 靶点条目不能当种子（400 LIBRARY_ENTRY_NOT_SEEDABLE）
#   ④ 不存在的库条目 → 404（不泄露存在性）
#   ⑤ 私有库条目对别人不可见（借 id 也拿不到结构）
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
    $txt = $sr.ReadToEnd()
    $parsed = $null
    try { $parsed = $txt | ConvertFrom-Json } catch { }
    return @{ Status = [int]$resp.StatusCode; Body = $parsed; Raw = $txt }
  }
}

# ---------------------------------------------------------------- 等待网关就绪
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
  try {
    $probe = Invoke-WebRequest -Uri "$GW/actuator/health" -UseBasicParsing -TimeoutSec 3
    if ($probe.StatusCode -eq 200) { $ready = $true; break }
  } catch { }
  Start-Sleep -Seconds 4
}
if (-not $ready) { Write-Output 'GATEWAY NOT READY'; exit 2 }

# ---------------------------------------------------------------- 登录
$login = Invoke-Json 'POST' '/api/auth/login' @{ loginType = 'guest' } $null
$token = $login.Body.data.accessToken
Check 'auth.guest' ($login.Status -eq 200 -and $token) "userId=$($login.Body.data.user_id)$($login.Body.data.user.id)"

$stamp = Get-Date -Format 'HHmmss'

# ---------------------------------------------------------------- 1) 库数据就位
$dsAll = Invoke-Json 'GET' '/api/library/datasets' $null $token
Check 'library.datasets' ($dsAll.Status -eq 200 -and @($dsAll.Body.data).Count -ge 3) "datasets=$(@($dsAll.Body.data).Count)"

$molDs = @($dsAll.Body.data) | Where-Object { $_.code -eq 'PUBLIC_MOLECULE' } | Select-Object -First 1
$targetDs = @($dsAll.Body.data) | Where-Object { $_.code -eq 'PUBLIC_TARGET' } | Select-Object -First 1
Check 'library.publicMolecule' ($null -ne $molDs.id -and $molDs.itemCount -ge 3) "datasetId=$($molDs.id) items=$($molDs.itemCount)"

$molItems = @((Invoke-Json 'GET' "/api/library/items?datasetId=$($molDs.id)&page=1&pageSize=100" $null $token).Body.data.list)
Check 'library.molecule.items' ($molItems.Count -ge 3) "items=$($molItems.Count)"

# 挑三条：两条正常分子 + 二甲双胍（MW 129，应被硬约束淘汰）
$aspirin = $molItems | Where-Object { $_.title -eq '阿司匹林' } | Select-Object -First 1
$ibuprofen = $molItems | Where-Object { $_.title -eq '布洛芬' } | Select-Object -First 1
$metformin = $molItems | Where-Object { $_.title -eq '二甲双胍' } | Select-Object -First 1
Check 'library.pick.aspirin' ($null -ne $aspirin.id) "itemId=$($aspirin.id) mw=$($aspirin.payload.mw)"
Check 'library.pick.ibuprofen' ($null -ne $ibuprofen.id) "itemId=$($ibuprofen.id) mw=$($ibuprofen.payload.mw)"
Check 'library.pick.metformin' ($null -ne $metformin.id) "itemId=$($metformin.id) mw=$($metformin.payload.mw) (应 < 150)"

# ---------------------------------------------------------------- 1.1 分面：逐步收窄
$f0 = Invoke-Json 'GET' "/api/library/facets?datasetId=$($molDs.id)&itemType=MOLECULE" $null $token
Check 'facet.base' ($f0.Status -eq 200 -and $f0.Body.data.total -eq $molDs.itemCount) "total=$($f0.Body.data.total) itemCount=$($molDs.itemCount)"
$f0Tags = @($f0.Body.data.tags)
$srcTag = $f0Tags | Where-Object { $_.code -eq 'SOURCE_CHEMBL' } | Select-Object -First 1
Check 'facet.base.source' ($srcTag.usageCount -eq $molDs.itemCount) "SOURCE_CHEMBL=$($srcTag.usageCount) (应等于全集)"

$f1 = Invoke-Json 'GET' "/api/library/facets?datasetId=$($molDs.id)&itemType=MOLECULE&tags=DISEASE_ONCOLOGY" $null $token
$f1Tags = @($f1.Body.data.tags)
$oncologyTotal = $f1.Body.data.total
$oncologyCardio = ($f1Tags | Where-Object { $_.code -eq 'DISEASE_CARDIO' } | Select-Object -First 1).usageCount
$oncologySrc = ($f1Tags | Where-Object { $_.code -eq 'SOURCE_CHEMBL' } | Select-Object -First 1).usageCount
Check 'facet.narrowed.total' ($oncologyTotal -gt 0 -and $oncologyTotal -le $molDs.itemCount) "肿瘤=$oncologyTotal / 全集=$($molDs.itemCount)"
# 关键语义：疾病组的分面不扣自己组 → 心血管仍可点；来源组要跟着收窄
Check 'facet.ownGroup.kept' ($oncologyCardio -gt 0) "选了肿瘤后，同组的心血管计数=$oncologyCardio (应 > 0，允许改选)"
Check 'facet.otherGroup.narrowed' ($oncologySrc -eq $oncologyTotal) "选了肿瘤后，SOURCE_CHEMBL=$oncologySrc (应 = 肿瘤总数 $oncologyTotal)"

# 跨组 AND：肿瘤 ∩ 已上市，总数不应超过单条件
$f2 = Invoke-Json 'GET' "/api/library/facets?datasetId=$($molDs.id)&itemType=MOLECULE&tags=DISEASE_ONCOLOGY,PHASE_APPROVED" $null $token
Check 'facet.crossGroup.and' ($f2.Body.data.total -le $oncologyTotal) "肿瘤∩已上市=$($f2.Body.data.total) ≤ 肿瘤=$oncologyTotal"

# 靶点库的分面要包含家族组
$tf = Invoke-Json 'GET' "/api/library/facets?datasetId=$($targetDs.id)&itemType=TARGET" $null $token
$tGroups = @($tf.Body.data.tags) | Where-Object { $_.usageCount -gt 0 } | ForEach-Object { $_.tagGroup } | Sort-Object -Unique
Check 'facet.target.groups' (($tGroups -contains 'FAMILY') -and ($tGroups -contains 'DISEASE')) "groups=$(($tGroups) -join ',')"

# 分面必须要求数据集，否则等价于全库扫描
$noDs = Invoke-Json 'GET' '/api/library/facets?itemType=MOLECULE' $null $token
Check 'facet.requiresDataset' ($noDs.Status -eq 404) "HTTP $($noDs.Status)"

# ---------------------------------------------------------------- 2) 冻基线 + 开轮
$tl = Invoke-Json 'GET' '/api/design/targets?page=1&pageSize=20' $null $token
$target = @($tl.Body.data.list) | Where-Object { $_.name -eq 'EGFR' } | Select-Object -First 1
Check 'target.egfr' ($null -ne $target.id) "targetId=$($target.id)"

$createBody = @{
  name = "生成-库选种子 $stamp"; modalityId = 1; moaCode = 'INHIBITOR'
  minCandidates = 3; quotaGpuHours = 48; quotaWetLab = 8
  targets = @(@{ targetId = $target.id; role = 'WILD_TYPE'; isPrimary = $true })
  constraintRules = @(@{
    ruleType = 'NUMERIC_RANGE'; severity = 'HARD'
    params = @{ field = 'MolWt'; min = 150; max = 600 }
    weight = 10; humanReadable = 'MW 150~600'
  })
}
$created = Invoke-Json 'POST' '/api/design/projects' $createBody $token
$projectId = $created.Body.data.id
Check 'setup.project' ($null -ne $projectId) "projectId=$projectId HTTP $($created.Status) $($created.Raw)"

$null = Invoke-Json 'POST' "/api/design/projects/$projectId/metrics:init-from-modality" $null $token
$pv = Invoke-Json 'POST' "/api/design/projects/$projectId/baseline:preview" $null $token
$fz = Invoke-Json 'POST' "/api/design/projects/$projectId/baseline:freeze" @{ expectedContentHash = $pv.Body.data.contentHash } $token
Check 'setup.freeze' ($fz.Status -eq 200 -and $fz.Body.data.status -eq 'ACTIVE') "HTTP $($fz.Status) $($fz.Raw)"
$rd = Invoke-Json 'POST' "/api/design/projects/$projectId/rounds" @{ strategyNote = 'lib-seed' } $token
Check 'setup.round' ($rd.Status -eq 201) "HTTP $($rd.Status) roundId=$($rd.Body.data.id)"

# ---------------------------------------------------------------- 3) 只传 libraryItemId 提交
$seeds = @(
  @{ ref = "LIB-$($aspirin.id)"; libraryItemId = $aspirin.id; name = $aspirin.title },
  @{ ref = "LIB-$($ibuprofen.id)"; libraryItemId = $ibuprofen.id; name = $ibuprofen.title },
  @{ ref = "LIB-$($metformin.id)"; libraryItemId = $metformin.id; name = $metformin.title }
)
$gen = Invoke-Json 'POST' "/api/design/projects/$projectId/generations" @{
  seeds = $seeds; seed = 42; idempotencyKey = "lib-seed-$stamp"
} $token
Check 'gen.submit' ($gen.Status -eq 202) "HTTP $($gen.Status) $($gen.Raw)"

$run = $gen.Body.data
Check 'gen.status' ($run.status -in @('SUCCEEDED', 'PARTIAL')) "status=$($run.status) final=$($run.finalCount) err=$($run.errorMsg)"
Check 'gen.strategy' ($run.strategy -eq 'SEED_LIBRARY') "strategy=$($run.strategy)"
# 种子数等于勾选数：库解析没丢条目，也没多出条目
Check 'gen.funnel.generated' ($run.funnel.generated -eq 3) "generated=$($run.funnel.generated) (应 = 3)"
# 二甲双胍应被 MW 约束真淘汰
Check 'gen.metformin.filtered' ($run.funnel.afterPreFilter -lt 3) "afterPreFilter=$($run.funnel.afterPreFilter) (应 < 3)"

$logs = @((Invoke-Json 'GET' "/api/design/generations/$($run.id)/candidates?stage=S6_PRE_FILTER&outcome=FILTERED" $null $token).Body.data)
$codes = @()
foreach ($row in $logs) {
  if ($row.filterReason) { foreach ($r in ($row.filterReason | ConvertFrom-Json)) { $codes += $r.code } }
}
Check 'gen.filter.reason' (($codes -join ',') -match 'HARD_CONSTRAINT_VIOLATION') "codes=$(($codes | Sort-Object -Unique) -join ',')"

# 闸门与归因：3 个种子筛掉 1 个 → 候选 2 < 基线要求 3
#   · gate 必须明确是 BELOW_MIN_CANDIDATES，而不是笼统的 PASS_WITH_WARNING
#   · errorMsg 不能再拿「有效簇数」说事 —— 候选只有 2 个时簇数必然 < 3，那是同义反复
Check 'gen.gate.belowMin' ($run.diagnosis.gate -eq 'BELOW_MIN_CANDIDATES') "gate=$($run.diagnosis.gate) final=$($run.finalCount)"
Check 'gen.readyForEdit.warning' ($run.diagnosis.readyForEdit -eq $true) "readyForEdit=$($run.diagnosis.readyForEdit)（允许进入，但结论是未达标）"
Check 'gen.errmsg.noClusterNoise' (-not ($run.errorMsg -match '有效簇数')) "errorMsg=$($run.errorMsg)"

# 同一批库条目再提交一次：不能 409「该分子已存在」，而要留一个可复盘的批次
$again = Invoke-Json 'POST' "/api/design/projects/$projectId/generations" @{
  seeds = $seeds; seed = 99; idempotencyKey = "lib-seed-again-$stamp"
} $token
Check 'gen.repeat.noConflict' ($again.Status -eq 202) "HTTP $($again.Status) code=$($again.Body.code)"
Check 'gen.repeat.gate' ($again.Body.data.diagnosis.gate -in @('ALL_DUPLICATE', 'EMPTY')) "gate=$($again.Body.data.diagnosis.gate) final=$($again.Body.data.finalCount)"
Check 'gen.repeat.finalZero' ($again.Body.data.finalCount -eq 0) "final=$($again.Body.data.finalCount)（重复分子不该再次入库）"
$merged = Invoke-Json 'GET' "/api/design/generations/$($again.Body.data.id)/candidates?stage=S5_DEDUP&outcome=MERGED" $null $token
Check 'gen.repeat.mergedLog' (@($merged.Body.data).Count -ge 1) "MERGED=$(@($merged.Body.data).Count)（每条重复都要留下追溯日志）"

# ---------------------------------------------------------------- 4) 候选带上库里的名字
$proj = Invoke-Json 'GET' "/api/design/projects/$projectId" $null $token
Check 'gen.candidates.persisted' ($proj.Body.data.candidateStats.total -ge 1) "total=$($proj.Body.data.candidateStats.total)"

# 从批次产物里取一个候选 id，再看版本树里的名字
$out = Invoke-Json 'GET' "/api/design/generations/$($run.id)/candidates?stage=S7_QUOTA&outcome=KEPT" $null $token
$kept = @($out.Body.data)
$candidateId = @($kept | Where-Object { $_.candidateId } | Select-Object -First 1)[0].candidateId
Check 'gen.candidate.id' ($null -ne $candidateId) "candidateId=$candidateId kept=$($kept.Count)"

if ($candidateId) {
  $tree = Invoke-Json 'GET' "/api/design/candidates/$candidateId/tree" $null $token
  $names = @(@($tree.Body.data.nodes) | ForEach-Object { $_.moleculeName } | Where-Object { $_ })
  Check 'gen.candidate.name' ($names.Count -ge 1) "names=$($names -join '/')"
  Check 'gen.candidate.nameInLibrary' ((($names -join '/') -match '阿司匹林|布洛芬')) "期望是库里的中文名，实际=$($names -join '/')"
}

# ---------------------------------------------------------------- 5) 靶点条目不能当种子
$tItems = @((Invoke-Json 'GET' "/api/library/items?datasetId=$($targetDs.id)&page=1&pageSize=5" $null $token).Body.data.list)
$targetItemId = @($tItems)[0].id
$bad = Invoke-Json 'POST' "/api/design/projects/$projectId/generations" @{
  seeds = @(@{ ref = 'LIB-T'; libraryItemId = $targetItemId }); idempotencyKey = "bad-target-$stamp"
} $token
Check 'gen.target.notSeedable' ($bad.Status -eq 400 -and $bad.Body.code -eq 40079) "HTTP $($bad.Status) code=$($bad.Body.code) msg=$($bad.Body.message)"

# ---------------------------------------------------------------- 6) 不存在的库条目 → 404
$ghost = Invoke-Json 'POST' "/api/design/projects/$projectId/generations" @{
  seeds = @(@{ ref = 'GHOST'; libraryItemId = 99999999 }); idempotencyKey = "ghost-$stamp"
} $token
Check 'gen.ghost.notFound' ($ghost.Status -eq 404) "HTTP $($ghost.Status) code=$($ghost.Body.code)"

# ---------------------------------------------------------------- 7) 抗体序列库当种子（重链/轻链分支）
$seqDs = @($dsAll.Body.data) | Where-Object { $_.code -eq 'PUBLIC_SEQUENCE' } | Select-Object -First 1
$seqItems = @((Invoke-Json 'GET' "/api/library/items?datasetId=$($seqDs.id)&page=1&pageSize=5" $null $token).Body.data.list)
Check 'library.sequence.items' ($seqItems.Count -ge 1) "items=$($seqItems.Count) first=$(@($seqItems)[0].title)"

$seqBody = @{
  name = "生成-序列库选 $stamp"; modalityId = 2; moaCode = 'INHIBITOR'
  minCandidates = 3; quotaGpuHours = 48; quotaWetLab = 8
  targets = @(@{ targetId = $target.id; role = 'WILD_TYPE'; isPrimary = $true })
  constraintRules = @(@{
    ruleType = 'NUMERIC_RANGE'; severity = 'HARD'
    params = @{ field = 'MolWt'; min = 150; max = 600 }; weight = 10; humanReadable = 'MW 150~600'
  })
}
$seqProj = Invoke-Json 'POST' '/api/design/projects' $seqBody $token
$seqPid = $seqProj.Body.data.id
$null = Invoke-Json 'POST' "/api/design/projects/$seqPid/metrics:init-from-modality" $null $token
$pv2 = Invoke-Json 'POST' "/api/design/projects/$seqPid/baseline:preview" $null $token
$null = Invoke-Json 'POST' "/api/design/projects/$seqPid/baseline:freeze" @{ expectedContentHash = $pv2.Body.data.contentHash } $token
$null = Invoke-Json 'POST' "/api/design/projects/$seqPid/rounds" @{ strategyNote = 'seq-lib' } $token

$seqGen = Invoke-Json 'POST' "/api/design/projects/$seqPid/generations" @{
  seeds = @(@{ ref = "LIB-$(@($seqItems)[0].id)"; libraryItemId = @($seqItems)[0].id }); seed = 7
} $token
Check 'gen.seq.submit' ($seqGen.Status -eq 202) "HTTP $($seqGen.Status) $($seqGen.Raw)"
Check 'gen.seq.strategy' ($seqGen.Body.data.strategy -eq 'SEQUENCE_MUTATION') "strategy=$($seqGen.Body.data.strategy)"
Check 'gen.seq.produced' ($seqGen.Body.data.finalCount -ge 1) "final=$($seqGen.Body.data.finalCount) err=$($seqGen.Body.data.errorMsg)"

Write-Output ''
Write-Output ("RESULT  pass={0} fail={1}" -f $pass, $fail)
if ($fail -gt 0) { exit 1 }
