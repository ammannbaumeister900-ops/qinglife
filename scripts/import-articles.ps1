param(
    [Parameter(Mandatory=$true)][string]$Manifest,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskJar = Join-Path $taskRoot 'server/api/box-web/target/box-web.jar'
$taskJava = Join-Path $taskRoot '.tools/jdk8u504-b01/bin/java.exe'
if (!(Test-Path -LiteralPath $taskJar)) { throw 'Build the backend package first.' }
if (!(Test-Path -LiteralPath $taskJava)) { $taskJava = 'java' }
if (!$env:QINGLIFE_ARTICLE_IMPORT_URL) { throw 'QINGLIFE_ARTICLE_IMPORT_URL is required. Only a marked loopback article test schema is accepted.' }
$taskInput = (Resolve-Path -LiteralPath $Manifest).Path
$taskArguments = @('-Dloader.main=com.yicai.web.tools.ArticleImport','-cp',$taskJar,'org.springframework.boot.loader.PropertiesLauncher',$taskInput)
if ($Apply) { $taskArguments += '--apply' }
& $taskJava @taskArguments
if ($LASTEXITCODE -ne 0) { throw 'Article import failed or contains blocked records. Inspect result codes; do not retry with ordinary article create.' }
