param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$JarPath
)

$ErrorActionPreference = 'Stop'
$env:SPRING_PROFILES_ACTIVE = 'prod'
if ($env:SPRING_PROFILES_ACTIVE -ne 'prod') {
    throw 'Production startup requires SPRING_PROFILES_ACTIVE=prod'
}

& java -jar (Resolve-Path -LiteralPath $JarPath).Path --spring.profiles.active=prod
exit $LASTEXITCODE
