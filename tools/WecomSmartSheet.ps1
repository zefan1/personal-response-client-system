[CmdletBinding()]
param(
  [ValidateSet('Status', 'Configure', 'Provision', 'Accept', 'Start')]
  [string]$Mode = 'Status',
  [string]$Path = (Join-Path $env:LOCALAPPDATA 'PrivateDomainAssistant\wecom-smartsheet.clixml'),
  [string]$RelayPath = (Join-Path $env:LOCALAPPDATA 'PrivateDomainAssistant\wecom-relay.clixml'),
  [string]$SmartSheetLink,
  [string]$CorpId,
  [string]$DraftSecretPath = (Join-Path $env:LOCALAPPDATA 'PrivateDomainAssistant\wecom-smartsheet-secret-draft.clixml'),
  [string]$ProvisioningStatePath = (Join-Path $env:LOCALAPPDATA 'PrivateDomainAssistant\wecom-smartsheet-provisioning-state.clixml'),
  [string]$DocumentName = 'PrivateDomainAssistant-API-Acceptance',
  [scriptblock]$ReadValue,
  [scriptblock]$DiscoverFields,
  [scriptblock]$CreateDocument,
  [scriptblock]$PrepareSheet,
  [scriptblock]$RunCommand
)

$modulePath = Join-Path $PSScriptRoot 'WecomSmartSheetBootstrap.psm1'
Import-Module $modulePath -Force -ErrorAction Stop

switch ($Mode) {
  'Status' {
    Get-WecomSmartSheetConfigurationStatus -Path $Path
    break
  }
  'Provision' {
    if ([string]::IsNullOrWhiteSpace($CorpId)) {
      throw 'WECOM_CORP_ID is required for Smart Sheet provisioning.'
    }
    if (-not (Test-Path -LiteralPath $DraftSecretPath -PathType Leaf)) {
      throw 'The encrypted WECOM_APP_SECRET draft is missing.'
    }
    $draft = Import-Clixml -LiteralPath $DraftSecretPath -ErrorAction Stop
    if ($draft.Value -isnot [System.Security.SecureString]) {
      throw 'The encrypted WECOM_APP_SECRET draft is invalid.'
    }
    $settings = @{
      WECOM_CORP_ID = $CorpId.Trim()
      WECOM_APP_SECRET = [System.Net.NetworkCredential]::new('', $draft.Value).Password
    }
    if ($null -eq $CreateDocument -or $null -eq $PrepareSheet) {
      $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
      $jarPath = Join-Path $projectRoot 'target\private-domain-assistant-0.1.0-SNAPSHOT.jar'
      $packageInputs = @((Join-Path $projectRoot 'pom.xml'), (Join-Path $projectRoot 'src\main'))
      if (Test-WecomSmartSheetPackageRequired -ArtifactPath $jarPath -InputPaths $packageInputs) {
        & wsl.exe --cd $projectRoot --exec mvn -o -DskipTests package
        if ($LASTEXITCODE -ne 0) {
          throw 'Local package failed; Smart Sheet provisioning was not started.'
        }
      }
      if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw 'Local package did not produce the Smart Sheet provisioning JAR.'
      }
      $invokeProvisioningJava = {
        param($connection, $mode, $name, $created)
        $names = @(
          'WECOM_CORP_ID',
          'WECOM_APP_SECRET',
          'WECOM_SMARTSHEET_DOCUMENT_NAME',
          'WECOM_SMARTSHEET_PROVISIONING_MODE',
          'WECOM_SMARTSHEET_DOC_ID',
          'WECOM_SMARTSHEET_DOCUMENT_URL'
        )
        $previous = @{}
        try {
          foreach ($environmentName in $names) {
            $previous[$environmentName] = [Environment]::GetEnvironmentVariable($environmentName, 'Process')
          }
          $env:WECOM_CORP_ID = [string]$connection['WECOM_CORP_ID']
          $env:WECOM_APP_SECRET = [string]$connection['WECOM_APP_SECRET']
          $env:WECOM_SMARTSHEET_DOCUMENT_NAME = $name
          $env:WECOM_SMARTSHEET_PROVISIONING_MODE = $mode
          if ($null -ne $created) {
            $env:WECOM_SMARTSHEET_DOC_ID = [string]$created.documentId
            $env:WECOM_SMARTSHEET_DOCUMENT_URL = [string]$created.documentUrl
          } else {
            Remove-Item -Path 'Env:WECOM_SMARTSHEET_DOC_ID' -ErrorAction SilentlyContinue
            Remove-Item -Path 'Env:WECOM_SMARTSHEET_DOCUMENT_URL' -ErrorAction SilentlyContinue
          }
          $output = @(& java '-Dloader.main=com.privateflow.modules.tablewrite.client.WecomSmartSheetProvisioningMain' `
            -cp $jarPath 'org.springframework.boot.loader.launch.PropertiesLauncher')
          if ($LASTEXITCODE -ne 0) {
            throw 'WeCom Smart Sheet provisioning failed.'
          }
          $prefix = 'WECOM_SMARTSHEET_RESULT_BASE64='
          $encoded = @($output | Where-Object { $_ -is [string] -and $_.TrimStart().StartsWith($prefix) } |
            Select-Object -Last 1)
          if ($encoded.Count -ne 1) {
            throw 'WeCom Smart Sheet provisioning did not return its identifiers.'
          }
          $payload = $encoded[0].Trim().Substring($prefix.Length)
          $json = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload))
          return $json | ConvertFrom-Json -ErrorAction Stop
        } finally {
          foreach ($environmentName in $names) {
            if ($null -eq $previous[$environmentName]) {
              Remove-Item -Path "Env:$environmentName" -ErrorAction SilentlyContinue
            } else {
              Set-Item -Path "Env:$environmentName" -Value $previous[$environmentName]
            }
          }
        }
      }.GetNewClosure()
      if ($null -eq $CreateDocument) {
        $CreateDocument = {
          param($connection, $name)
          & $invokeProvisioningJava $connection 'CREATE' $name $null
        }.GetNewClosure()
      }
      if ($null -eq $PrepareSheet) {
        $PrepareSheet = {
          param($connection, $created)
          & $invokeProvisioningJava $connection 'PREPARE' $DocumentName $created
        }.GetNewClosure()
      }
    }
    if (Test-Path -LiteralPath $ProvisioningStatePath -PathType Leaf) {
      $created = Import-Clixml -LiteralPath $ProvisioningStatePath -ErrorAction Stop
    } else {
      $created = @(& $CreateDocument $settings $DocumentName) |
        Where-Object { $null -ne $_ -and $null -ne $_.PSObject.Properties['documentId'] } |
        Select-Object -Last 1
      foreach ($propertyName in @('documentId', 'documentUrl')) {
        $property = $created.PSObject.Properties[$propertyName]
        if ($null -eq $property -or [string]::IsNullOrWhiteSpace([string]$property.Value)) {
          throw "Smart Sheet creation result is missing $propertyName."
        }
      }
      $stateDirectory = Split-Path -Parent $ProvisioningStatePath
      if (-not [string]::IsNullOrWhiteSpace($stateDirectory) -and
          -not (Test-Path -LiteralPath $stateDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
      }
      [pscustomobject]@{
        documentId = [string]$created.documentId
        documentUrl = [string]$created.documentUrl
      } | Export-Clixml -LiteralPath $ProvisioningStatePath -Force
    }
    $provisioned = @(& $PrepareSheet $settings $created) |
      Where-Object { $null -ne $_ -and $null -ne $_.PSObject.Properties['documentId'] } |
      Select-Object -Last 1
    $required = @('documentId', 'documentUrl', 'sheetId', 'viewId', 'sourceTable', 'uniqueFieldTitle')
    foreach ($propertyName in $required) {
      $property = $provisioned.PSObject.Properties[$propertyName]
      if ($null -eq $property -or [string]::IsNullOrWhiteSpace([string]$property.Value)) {
        throw "Smart Sheet provisioning result is missing $propertyName."
      }
    }
    $settings['WECOM_SMARTSHEET_DOC_ID'] = [string]$provisioned.documentId
    $settings['WECOM_SMARTSHEET_SHEET_ID'] = [string]$provisioned.sheetId
    $settings['WECOM_SMARTSHEET_VIEW_ID'] = [string]$provisioned.viewId
    $settings['WECOM_SMARTSHEET_SOURCE_TABLE'] = [string]$provisioned.sourceTable
    $settings['WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE'] = [string]$provisioned.uniqueFieldTitle
    $status = Save-WecomSmartSheetConfiguration -Settings $settings -Path $Path
    Remove-Item -LiteralPath $DraftSecretPath -Force
    Remove-Item -LiteralPath $ProvisioningStatePath -Force -ErrorAction SilentlyContinue
    [pscustomobject]@{
      Configured = $status.Configured
      Missing = $status.Missing
      DocumentUrl = [string]$provisioned.documentUrl
    }
    break
  }
  'Configure' {
    if ($null -eq $ReadValue) {
      if ([string]::IsNullOrWhiteSpace($SmartSheetLink)) {
        $SmartSheetLink = Read-Host -Prompt 'WeCom Smart Sheet link (paste it, or press Enter to skip)'
      }
      if (-not [string]::IsNullOrWhiteSpace($SmartSheetLink)) {
        $resolved = Resolve-WecomSmartSheetLink -Link $SmartSheetLink
        $recognized = @()
        if (-not [string]::IsNullOrWhiteSpace($resolved.DocumentId)) { $recognized += 'document ID' }
        if (-not [string]::IsNullOrWhiteSpace($resolved.SheetId)) { $recognized += 'sheet ID' }
        if (-not [string]::IsNullOrWhiteSpace($resolved.ViewId)) { $recognized += 'view ID' }
        if ($recognized.Count -gt 0) {
          Write-Host "Recognized from link: $($recognized -join ', '). These values will not be requested again."
        } else {
          Write-Warning 'No usable IDs found in the link. The script will request them separately.'
        }
      }
      $ReadValue = {
        param($name, $secret)
        if ($secret) {
          Read-Host -Prompt 'Copy the full WECOM_APP_SECRET to the clipboard, then press Enter' -AsSecureString | Out-Null
          return Get-WecomSmartSheetSecretFromClipboard
        }
        return Read-Host -Prompt $name
      }
      if ($null -eq $DiscoverFields) {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
        $jarPath = Join-Path $projectRoot 'target\private-domain-assistant-0.1.0-SNAPSHOT.jar'
        $DiscoverFields = {
          param($settings)
          $packageInputs = @((Join-Path $projectRoot 'pom.xml'), (Join-Path $projectRoot 'src\main'))
          if (Test-WecomSmartSheetPackageRequired -ArtifactPath $jarPath -InputPaths $packageInputs) {
            & wsl.exe --cd $projectRoot --exec mvn -o -DskipTests package
            if ($LASTEXITCODE -ne 0) {
              throw 'Local package failed; field discovery was not started.'
            }
          }
          if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
            throw 'Local package did not produce the Smart Sheet discovery JAR.'
          }
          $names = @(
            'WECOM_CORP_ID',
            'WECOM_APP_SECRET',
            'WECOM_SMARTSHEET_DOC_ID',
            'WECOM_SMARTSHEET_SHEET_ID',
            'WECOM_SMARTSHEET_VIEW_ID'
          )
          $previous = @{}
          try {
            foreach ($name in $names) {
              $value = [string]$settings[$name]
              if ([string]::IsNullOrWhiteSpace($value)) {
                throw "Field discovery is missing $name."
              }
              $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
              Set-Item -Path "Env:$name" -Value $value
            }
            Write-Host 'Reading writable fields from WeCom Smart Sheet. No records will be changed.'
            & java '-Dloader.main=com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldDiscoveryMain' `
              -cp $jarPath 'org.springframework.boot.loader.launch.PropertiesLauncher'
            if ($LASTEXITCODE -ne 0) {
              throw 'WeCom Smart Sheet field discovery failed.'
            }
          } finally {
            foreach ($name in $names) {
              if ($null -eq $previous[$name]) {
                Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
              } else {
                Set-Item -Path "Env:$name" -Value $previous[$name]
              }
            }
          }
        }.GetNewClosure()
      }
    }
    Initialize-WecomSmartSheetConfiguration -Path $Path -ReadValue $ReadValue -SmartSheetLink $SmartSheetLink `
      -DiscoverFields $DiscoverFields
    break
  }
  'Accept' {
    $status = Get-WecomSmartSheetConfigurationStatus -Path $Path
    if (-not $status.Configured) {
      throw "Encrypted WeCom Smart Sheet configuration is incomplete: $($status.Missing -join ', ')"
    }
    if ($null -eq $RunCommand) {
      $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
      $jarPath = Join-Path $projectRoot 'target\private-domain-assistant-0.1.0-SNAPSHOT.jar'
      $packageInputs = @((Join-Path $projectRoot 'pom.xml'), (Join-Path $projectRoot 'src\main'))
      if (Test-WecomSmartSheetPackageRequired -ArtifactPath $jarPath -InputPaths $packageInputs) {
        & wsl.exe --cd $projectRoot --exec mvn -o -DskipTests package
        if ($LASTEXITCODE -ne 0) {
          throw 'Local package failed; Smart Sheet acceptance was not started.'
        }
      }
      if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw 'Local package did not produce the Smart Sheet acceptance JAR.'
      }
      $RunCommand = {
        & java '-Dloader.main=com.privateflow.modules.tablewrite.client.WecomSmartSheetLiveAcceptanceMain' `
          -cp $jarPath 'org.springframework.boot.loader.launch.PropertiesLauncher'
        if ($LASTEXITCODE -ne 0) {
          throw 'WeCom Smart Sheet acceptance failed.'
        }
      }.GetNewClosure()
    }
    Invoke-WithWecomSmartSheetEnvironment -Path $Path -Command $RunCommand
  }
  'Start' {
    $status = Get-WecomSmartSheetConfigurationStatus -Path $Path
    if (-not $status.Configured) {
      throw "Encrypted WeCom Smart Sheet configuration is incomplete: $($status.Missing -join ', ')"
    }
    if ($null -eq $RunCommand) {
      $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
      $RunCommand = {
        & wsl.exe --cd $projectRoot --exec bash scripts/stop_backend_mock_wsl.sh
        if ($LASTEXITCODE -ne 0) {
          throw 'Existing backend could not be stopped.'
        }
        & wsl.exe --cd $projectRoot --exec bash scripts/start_backend_real_wsl.sh
        if ($LASTEXITCODE -ne 0) {
          throw 'Business backend could not be started with WeCom Smart Sheet configuration.'
        }
      }.GetNewClosure()
    }
    $relayNames = @(
      'WECOM_TRANSPORT_MODE',
      'WECOM_RELAY_BASE_URL',
      'WECOM_RELAY_KEY_ID',
      'WECOM_RELAY_SECRET'
    )
    $previousRelayValues = @{}
    $relayConfigured = Test-Path -LiteralPath $RelayPath -PathType Leaf
    if ($relayConfigured) {
      $relayStored = Import-Clixml -LiteralPath $RelayPath -ErrorAction Stop
      foreach ($name in $relayNames) {
        $property = $relayStored.Values.PSObject.Properties[$name]
        if ($null -eq $property -or $property.Value -isnot [System.Security.SecureString]) {
          throw "Encrypted WeCom relay configuration is incomplete: $name"
        }
        $previousRelayValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        $plainValue = [System.Net.NetworkCredential]::new('', $property.Value).Password
        if ([string]::IsNullOrWhiteSpace($plainValue)) {
          throw "Encrypted WeCom relay configuration is incomplete: $name"
        }
        Set-Item -Path "Env:$name" -Value $plainValue
      }
    }
    $previousWslEnv = $env:WSLENV
    try {
      $entries = @()
      if (-not [string]::IsNullOrWhiteSpace($previousWslEnv)) {
        $entries += $previousWslEnv.Split(':', [System.StringSplitOptions]::RemoveEmptyEntries)
      }
      $entries += @(
        'WECOM_CORP_ID',
        'WECOM_APP_SECRET',
        'WECOM_SMARTSHEET_DOC_ID',
        'WECOM_SMARTSHEET_SHEET_ID',
        'WECOM_SMARTSHEET_VIEW_ID',
        'WECOM_SMARTSHEET_SOURCE_TABLE',
        'WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE'
      )
      if ($relayConfigured) {
        $entries += $relayNames
      }
      $env:WSLENV = (@($entries | Select-Object -Unique) -join ':')
      Invoke-WithWecomSmartSheetEnvironment -Path $Path -Command $RunCommand
    } finally {
      if ($null -eq $previousWslEnv) {
        Remove-Item Env:WSLENV -ErrorAction SilentlyContinue
      } else {
        $env:WSLENV = $previousWslEnv
      }
      foreach ($name in $relayNames) {
        if (-not $previousRelayValues.ContainsKey($name)) {
          continue
        }
        if ($null -eq $previousRelayValues[$name]) {
          Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        } else {
          Set-Item -Path "Env:$name" -Value $previousRelayValues[$name]
        }
      }
    }
  }
}
