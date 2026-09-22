param(
    [Parameter(Mandatory=$true)][string]$JarPath,
    [string]$ServerDirectory = (Join-Path $PSScriptRoot '../server')
)
$ErrorActionPreference='Stop'
if (-not $env:QINGLIFE_MIGRATION_URL -or -not $env:QINGLIFE_MIGRATION_USER) {
    throw 'Set QINGLIFE_MIGRATION_URL, QINGLIFE_MIGRATION_USER and QINGLIFE_MIGRATION_PASSWORD in this process.'
}
$taskJar=(Resolve-Path -LiteralPath $JarPath).Path
$taskServer=(Resolve-Path -LiteralPath $ServerDirectory).Path
& java '-Dloader.main=com.yicai.web.tools.DatabaseMigration' -cp $taskJar org.springframework.boot.loader.PropertiesLauncher $taskServer
exit $LASTEXITCODE
