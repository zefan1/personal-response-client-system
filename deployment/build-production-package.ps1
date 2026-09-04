param(
  [string]$Output = (Join-Path (Get-Location) 'private-domain-assistant-production.zip')
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$stage = Join-Path ([System.IO.Path]::GetTempPath()) ('pda-release-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage | Out-Null
try {
  Push-Location $repo
  $tracked = git -c core.quotepath=false ls-files
  foreach ($relative in $tracked) {
    if ($relative -match '^(uploads/|target/|desktop/node_modules/|desktop/dist/|desktop/release/|.*\.log$|.*\.env$)') { continue }
    $source = Join-Path $repo $relative
    $destination = Join-Path $stage $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination
  }
  # Include production source files added before they are committed. This keeps
  # a verified hotfix from silently disappearing from the deployment bundle.
  $productionAdditions = @(
    'src/main/java/com/privateflow/modules/api/config/CorsOriginPolicy.java',
    'src/main/resources/db/migration/V119__auxiliary_smart_sheet_document_urls.sql',
    'src/main/resources/db/migration/V120__align_pending_tag_evidence_example.sql'
  )
  foreach ($relative in $productionAdditions) {
    $source = Join-Path $repo $relative
    if (-not (Test-Path -LiteralPath $source)) { throw "Missing production file: $relative" }
    $destination = Join-Path $stage $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination -Force
  }
  $deploymentSource = Join-Path $repo 'deployment'
  Copy-Item -LiteralPath $deploymentSource -Destination (Join-Path $stage 'deployment') -Recurse -Force
  if (Test-Path $Output) { Remove-Item -LiteralPath $Output -Force }
  # Compress-Archive writes Windows path separators into ZIP entries. Build the
  # archive explicitly so Linux unzip receives portable forward-slash paths.
  Add-Type -AssemblyName System.IO.Compression
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $outputStream = [System.IO.File]::Open($Output, [System.IO.FileMode]::Create)
  $archive = [System.IO.Compression.ZipArchive]::new(
    $outputStream,
    [System.IO.Compression.ZipArchiveMode]::Create,
    $false
  )
  try {
    Get-ChildItem -LiteralPath $stage -Recurse -File | ForEach-Object {
      $entryName = $_.FullName.Substring($stage.Length).TrimStart('\', '/').Replace('\', '/')
      $entry = $archive.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
      $inputStream = [System.IO.File]::OpenRead($_.FullName)
      $entryStream = $entry.Open()
      try { $inputStream.CopyTo($entryStream) } finally {
        $entryStream.Dispose()
        $inputStream.Dispose()
      }
    }
  } finally {
    $archive.Dispose()
    $outputStream.Dispose()
  }
  Write-Output "production_package=$Output"
} finally {
  Pop-Location
  Remove-Item -LiteralPath $stage -Recurse -Force
}
