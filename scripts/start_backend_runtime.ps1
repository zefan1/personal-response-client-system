param(
  [string]$RelayPath = (Join-Path $env:LOCALAPPDATA 'PrivateDomainAssistant\wecom-relay.clixml'),
  [string]$SmartSheetPath = (Join-Path $env:LOCALAPPDATA 'PrivateDomainAssistant\wecom-smartsheet.clixml'),
  [string]$InboundCallbackRelayPath = (Join-Path $env:LOCALAPPDATA 'PrivateDomainAssistant\wecom-inbound-callback-relay.clixml'),
  [switch]$RealExternal,
  [switch]$MockExternal
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

if (-not (Test-Path -LiteralPath $RelayPath) -or -not (Test-Path -LiteralPath $SmartSheetPath) -or -not (Test-Path -LiteralPath $InboundCallbackRelayPath)) {
  throw 'Missing locally saved WeCom relay or inbound callback configuration.'
}

$relay = (Import-Clixml -LiteralPath $RelayPath).Values
$smartSheet = (Import-Clixml -LiteralPath $SmartSheetPath).Values
$inboundCallbackRelay = (Import-Clixml -LiteralPath $InboundCallbackRelayPath).Values
$names = @(
  'WECOM_CORP_ID', 'WECOM_APP_SECRET',
  'WECOM_SMARTSHEET_DOC_ID', 'WECOM_SMARTSHEET_SHEET_ID', 'WECOM_SMARTSHEET_VIEW_ID',
  'WECOM_SMARTSHEET_SOURCE_TABLE', 'WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE',
  'WECOM_TRANSPORT_MODE', 'WECOM_RELAY_BASE_URL', 'WECOM_RELAY_KEY_ID', 'WECOM_RELAY_SECRET'
)
$inboundCallbackRelayNames = @(
  'WECOM_INBOUND_RELAY_BASE_URL', 'WECOM_INBOUND_RELAY_CLIENT_ID', 'WECOM_INBOUND_RELAY_CLIENT_SECRET'
)
$allNames = @($names + $inboundCallbackRelayNames)
$previous = @{}
$previousWslEnv = $env:WSLENV

try {
  if ($RealExternal -and $MockExternal) {
    throw 'RealExternal and MockExternal cannot be used together.'
  }
  # Runtime defaults to the configured real relay. Mocking is opt-in only.
  $previous['MOCK_EXTERNALS'] = [Environment]::GetEnvironmentVariable('MOCK_EXTERNALS', 'Process')
  $env:MOCK_EXTERNALS = if ($MockExternal) { 'true' } else { 'false' }
  $allNames += 'MOCK_EXTERNALS'
  foreach ($name in $names) {
    $secureValue = if ($relay.PSObject.Properties[$name]) { $relay.$name } else { $smartSheet.$name }
    if ($secureValue -isnot [System.Security.SecureString]) {
      throw "Incomplete locally saved WeCom configuration: $name"
    }
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    $value = [System.Net.NetworkCredential]::new('', $secureValue).Password
    if ([string]::IsNullOrWhiteSpace($value)) {
      throw "Empty locally saved WeCom configuration: $name"
    }
    Set-Item -Path "Env:$name" -Value $value
  }

  foreach ($name in $inboundCallbackRelayNames) {
    $secureValue = if ($inboundCallbackRelay.PSObject.Properties[$name]) { $inboundCallbackRelay.$name } else { $null }
    if ($secureValue -isnot [System.Security.SecureString]) {
      throw "Incomplete locally saved inbound callback relay configuration: $name"
    }
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    $value = [System.Net.NetworkCredential]::new('', $secureValue).Password
    if ([string]::IsNullOrWhiteSpace($value)) {
      throw "Empty locally saved inbound callback relay configuration: $name"
    }
    Set-Item -Path "Env:$name" -Value $value
  }

  $env:WSLENV = (@($previousWslEnv -split ':' | Where-Object { $_ }) + $allNames | Select-Object -Unique) -join ':'
  $wslProject = (wsl.exe -e wslpath -a $projectRoot).Trim()
  $runtimeSessionExists = $false
  try {
    wsl.exe -e tmux has-session -t pda_runtime 2>$null
    $runtimeSessionExists = $LASTEXITCODE -eq 0
  } catch {
    # A fresh WSL instance has no tmux server yet; that is not a startup failure.
    $runtimeSessionExists = $false
  }
  if ($runtimeSessionExists) {
    wsl.exe -e tmux kill-session -t pda_runtime
  }
  wsl.exe -e tmux new-session -d -s pda_runtime "bash '$wslProject/scripts/start_backend_runtime_wsl.sh' 2>&1 | tee '$wslProject/backend-runtime-live.log'"
  if ($LASTEXITCODE -ne 0) {
    throw 'Backend startup request failed.'
  }
  Write-Output 'Backend is starting: http://127.0.0.1:5173/#/admin'
} finally {
  foreach ($name in $allNames) {
    if ($null -eq $previous[$name]) {
      Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    } else {
      Set-Item -Path "Env:$name" -Value $previous[$name]
    }
  }
  if ($null -eq $previousWslEnv) {
    Remove-Item Env:WSLENV -ErrorAction SilentlyContinue
  } else {
    $env:WSLENV = $previousWslEnv
  }
}
