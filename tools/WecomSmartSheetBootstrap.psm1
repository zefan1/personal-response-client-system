Set-StrictMode -Version Latest

$script:RequiredEnvironmentVariables = @(
  'WECOM_CORP_ID',
  'WECOM_APP_SECRET',
  'WECOM_SMARTSHEET_DOC_ID',
  'WECOM_SMARTSHEET_SHEET_ID',
  'WECOM_SMARTSHEET_VIEW_ID',
  'WECOM_SMARTSHEET_SOURCE_TABLE',
  'WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE'
)

function Test-WecomSmartSheetPackageRequired {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)]
    [string]$ArtifactPath,
    [Parameter(Mandatory)]
    [string[]]$InputPaths
  )

  if (-not (Test-Path -LiteralPath $ArtifactPath -PathType Leaf)) {
    return $true
  }
  $artifactTime = (Get-Item -LiteralPath $ArtifactPath).LastWriteTimeUtc
  foreach ($inputPath in $InputPaths) {
    if (-not (Test-Path -LiteralPath $inputPath)) {
      return $true
    }
    $input = Get-Item -LiteralPath $inputPath
    if (-not $input.PSIsContainer) {
      if ($input.LastWriteTimeUtc -gt $artifactTime) {
        return $true
      }
      continue
    }
    if (Get-ChildItem -LiteralPath $inputPath -Recurse -File | Where-Object { $_.LastWriteTimeUtc -gt $artifactTime } | Select-Object -First 1) {
      return $true
    }
  }
  return $false
}

function Get-WecomSmartSheetSecretFromClipboard {
  [CmdletBinding()]
  param(
    [scriptblock]$ReadClipboard = { Get-Clipboard -Raw },
    [scriptblock]$ClearClipboard = {
      param($replacement)
      Set-Clipboard -Value $replacement
    }
  )

  try {
    $secret = ([string](& $ReadClipboard)).Trim()
    if ([string]::IsNullOrWhiteSpace($secret)) {
      throw 'The clipboard does not contain an app secret.'
    }
    return $secret
  } finally {
    & $ClearClipboard '[cleared]' | Out-Null
  }
}

function Resolve-WecomSmartSheetLink {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)]
    [string]$Link
  )

  $uri = $null
  if (-not [Uri]::TryCreate($Link.Trim(), [UriKind]::Absolute, [ref]$uri)) {
    throw 'The Smart Sheet link must be a complete URL.'
  }

  $parameters = @{}
  foreach ($part in @($uri.Query.TrimStart('?'), $uri.Fragment.TrimStart('#'))) {
    foreach ($pair in $part.Split('&', [System.StringSplitOptions]::RemoveEmptyEntries)) {
      $pieces = $pair.Split('=', 2)
      $name = [Uri]::UnescapeDataString($pieces[0].Replace('+', ' ')).Trim().ToLowerInvariant()
      if ($pieces.Count -gt 1 -and -not [string]::IsNullOrWhiteSpace($name)) {
        $parameters[$name] = [Uri]::UnescapeDataString($pieces[1].Replace('+', ' ')).Trim()
      }
    }
  }

  $documentId = $parameters['docid']
  if ([string]::IsNullOrWhiteSpace($documentId)) {
    $documentId = $parameters['doc_id']
  }
  if ([string]::IsNullOrWhiteSpace($documentId) -and $uri.Host.Equals('doc.weixin.qq.com', [System.StringComparison]::OrdinalIgnoreCase)) {
    $pathParts = $uri.AbsolutePath.Trim('/').Split('/', [System.StringSplitOptions]::RemoveEmptyEntries)
    for ($index = 0; $index -lt ($pathParts.Count - 1); $index++) {
      if ($pathParts[$index].Equals('sheet', [System.StringComparison]::OrdinalIgnoreCase) -or
          $pathParts[$index].Equals('smartsheet', [System.StringComparison]::OrdinalIgnoreCase)) {
        $documentId = [Uri]::UnescapeDataString($pathParts[$index + 1])
        break
      }
    }
  }

  $sheetId = $parameters['sheet_id']
  if ([string]::IsNullOrWhiteSpace($sheetId)) {
    $sheetId = $parameters['sheetid']
  }
  if ([string]::IsNullOrWhiteSpace($sheetId) -and $uri.Host.Equals('doc.weixin.qq.com', [System.StringComparison]::OrdinalIgnoreCase)) {
    $sheetId = $parameters['tab']
  }
  $viewId = $parameters['view_id']
  if ([string]::IsNullOrWhiteSpace($viewId)) {
    $viewId = $parameters['viewid']
  }

  return [pscustomobject]@{
    DocumentId = $documentId
    SheetId = $sheetId
    ViewId = $viewId
  }
}

function Get-WecomSmartSheetConfigurationStatus {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)]
    [string]$Path
  )

  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    return [pscustomobject]@{
      Configured = $false
      Missing = $script:RequiredEnvironmentVariables
    }
  }

  try {
    $stored = Import-Clixml -LiteralPath $Path -ErrorAction Stop
  } catch {
    throw "Cannot read encrypted WeCom Smart Sheet configuration at '$Path'."
  }

  $missing = @(
    foreach ($name in $script:RequiredEnvironmentVariables) {
      $property = $stored.Values.PSObject.Properties[$name]
      if ($null -eq $property -or $property.Value -isnot [System.Security.SecureString]) {
        $name
      }
    }
  )

  return [pscustomobject]@{
    Configured = ($missing.Count -eq 0)
    Missing = $missing
  }
}

function Save-WecomSmartSheetConfiguration {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)]
    [hashtable]$Settings,
    [Parameter(Mandatory)]
    [string]$Path
  )

  $missing = @(
    foreach ($name in $script:RequiredEnvironmentVariables) {
      if (-not $Settings.ContainsKey($name) -or [string]::IsNullOrWhiteSpace([string]$Settings[$name])) {
        $name
      }
    }
  )
  if ($missing.Count -gt 0) {
    throw "Missing required values: $($missing -join ', ')"
  }

  $directory = Split-Path -Parent $Path
  if (-not [string]::IsNullOrWhiteSpace($directory) -and -not (Test-Path -LiteralPath $directory -PathType Container)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
  }

  $values = [ordered]@{}
  foreach ($name in $script:RequiredEnvironmentVariables) {
    $values[$name] = ConvertTo-SecureString ([string]$Settings[$name]) -AsPlainText -Force
  }

  [pscustomobject]@{
    Version = 1
    Values = [pscustomobject]$values
  } | Export-Clixml -LiteralPath $Path -Force

  return Get-WecomSmartSheetConfigurationStatus -Path $Path
}

function Initialize-WecomSmartSheetConfiguration {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)]
    [string]$Path,
    [Parameter(Mandatory)]
    [scriptblock]$ReadValue,
    [string]$SmartSheetLink,
    [scriptblock]$DiscoverFields
  )

  $settings = @{}
  if (-not [string]::IsNullOrWhiteSpace($SmartSheetLink)) {
    $resolved = Resolve-WecomSmartSheetLink -Link $SmartSheetLink
    $settings['WECOM_SMARTSHEET_DOC_ID'] = $resolved.DocumentId
    $settings['WECOM_SMARTSHEET_SHEET_ID'] = $resolved.SheetId
    $settings['WECOM_SMARTSHEET_VIEW_ID'] = $resolved.ViewId
    $settings['WECOM_SMARTSHEET_SOURCE_TABLE'] = $resolved.SheetId
  }
  foreach ($name in $script:RequiredEnvironmentVariables) {
    if ([string]::IsNullOrWhiteSpace([string]$settings[$name])) {
      if ($name -eq 'WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE' -and $null -ne $DiscoverFields) {
        & $DiscoverFields $settings
      }
      $settings[$name] = & $ReadValue $name ($name -eq 'WECOM_APP_SECRET')
    }
  }
  return Save-WecomSmartSheetConfiguration -Settings $settings -Path $Path
}

function Invoke-WithWecomSmartSheetEnvironment {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)]
    [string]$Path,
    [Parameter(Mandatory)]
    [scriptblock]$Command
  )

  $status = Get-WecomSmartSheetConfigurationStatus -Path $Path
  if (-not $status.Configured) {
    throw "Encrypted WeCom Smart Sheet configuration is incomplete: $($status.Missing -join ', ')"
  }

  $stored = Import-Clixml -LiteralPath $Path -ErrorAction Stop
  $previous = @{}
  foreach ($name in $script:RequiredEnvironmentVariables) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
  }

  try {
    foreach ($name in $script:RequiredEnvironmentVariables) {
      $secureValue = $stored.Values.PSObject.Properties[$name].Value
      $plainValue = [System.Net.NetworkCredential]::new('', $secureValue).Password
      Set-Item -Path "Env:$name" -Value $plainValue
    }
    & $Command
  } finally {
    foreach ($name in $script:RequiredEnvironmentVariables) {
      if ($null -eq $previous[$name]) {
        Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
      } else {
        Set-Item -Path "Env:$name" -Value $previous[$name]
      }
    }
  }
}

Export-ModuleMember -Function Test-WecomSmartSheetPackageRequired, Get-WecomSmartSheetSecretFromClipboard, Resolve-WecomSmartSheetLink, Get-WecomSmartSheetConfigurationStatus, Save-WecomSmartSheetConfiguration, Initialize-WecomSmartSheetConfiguration, Invoke-WithWecomSmartSheetEnvironment
