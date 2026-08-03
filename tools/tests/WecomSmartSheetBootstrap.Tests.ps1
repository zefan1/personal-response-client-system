$modulePath = Join-Path $PSScriptRoot '..\WecomSmartSheetBootstrap.psm1'
$launcherPath = Join-Path $PSScriptRoot '..\WecomSmartSheet.ps1'

Describe 'WeCom Smart Sheet local configuration' {
  It 'provides the local configuration module' {
    Test-Path $modulePath | Should Be $true
  }

  It 'reuses a package until one of its source inputs becomes newer' {
    Import-Module $modulePath -Force -ErrorAction Stop
    $artifact = Join-Path $TestDrive 'app.jar'
    $source = Join-Path $TestDrive 'Source.java'
    Set-Content -LiteralPath $source -Value 'source'
    Set-Content -LiteralPath $artifact -Value 'artifact'
    (Get-Item -LiteralPath $source).LastWriteTimeUtc = [DateTime]::UtcNow.AddMinutes(-2)
    (Get-Item -LiteralPath $artifact).LastWriteTimeUtc = [DateTime]::UtcNow.AddMinutes(-1)

    (Test-WecomSmartSheetPackageRequired -ArtifactPath $artifact -InputPaths @($source)) | Should Be $false

    (Get-Item -LiteralPath $source).LastWriteTimeUtc = [DateTime]::UtcNow
    (Test-WecomSmartSheetPackageRequired -ArtifactPath $artifact -InputPaths @($source)) | Should Be $true
  }

  It 'reads an app secret from the clipboard and clears the clipboard immediately' {
    Import-Module $modulePath -Force -ErrorAction Stop
    $events = New-Object System.Collections.Generic.List[string]
    $clipboardReplacement = $null

    $secret = Get-WecomSmartSheetSecretFromClipboard `
      -ReadClipboard {
        [void]$events.Add('read')
        "test-app-secret`r`n"
      } `
      -ClearClipboard {
        param($replacement)
        $script:clipboardReplacement = $replacement
        [void]$events.Add('clear')
      }

    $secret | Should Be 'test-app-secret'
    ($events -join ',') | Should Be 'read,clear'
    [string]::IsNullOrWhiteSpace($script:clipboardReplacement) | Should Be $false
    $script:clipboardReplacement | Should Not Match 'test-app-secret'
  }

  It 'extracts explicit document, sheet, and view identifiers from a Smart Sheet link' {
    Import-Module $modulePath -Force -ErrorAction Stop

    $resolved = Resolve-WecomSmartSheetLink -Link 'https://doc.weixin.qq.com/sheet/s3_document?sheet_id=s3_sheet&view_id=v1'

    $resolved.DocumentId | Should Be 's3_document'
    $resolved.SheetId | Should Be 's3_sheet'
    $resolved.ViewId | Should Be 'v1'
  }

  It 'extracts document, sheet, and view identifiers from a shared WeCom Smart Sheet URL' {
    Import-Module $modulePath -Force -ErrorAction Stop

    $resolved = Resolve-WecomSmartSheetLink -Link 'https://doc.weixin.qq.com/smartsheet/s3_document?tab=t_sheet&viewId=v1'

    $resolved.DocumentId | Should Be 's3_document'
    $resolved.SheetId | Should Be 't_sheet'
    $resolved.ViewId | Should Be 'v1'
  }

  It 'uses identifiers from a Smart Sheet link before asking the local operator' {
    Import-Module $modulePath -Force -ErrorAction Stop
    $path = Join-Path $TestDrive 'wecom-smartsheet.clixml'
    $requested = New-Object System.Collections.Generic.List[string]
    $values = @{
      WECOM_CORP_ID = 'test-corp'
      WECOM_APP_SECRET = 'test-app-secret'
      WECOM_SMARTSHEET_SOURCE_TABLE = 'Customers'
      WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE = 'Customer ID'
    }

    $status = Initialize-WecomSmartSheetConfiguration -Path $path `
      -SmartSheetLink 'https://doc.weixin.qq.com/sheet/s3_document?sheet_id=s3_sheet&view_id=v1' `
      -ReadValue {
        param($name, $secret)
        $requested.Add($name)
        $values[$name]
      }

    $status.Configured | Should Be $true
    ($requested -join ',') | Should Be (@(
      'WECOM_CORP_ID',
      'WECOM_APP_SECRET',
      'WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE'
    ) -join ',')
  }

  It 'discovers fields after the link and credentials are collected but before asking for the unique field' {
    Import-Module $modulePath -Force -ErrorAction Stop
    $path = Join-Path $TestDrive 'wecom-smartsheet.clixml'
    $values = @{
      WECOM_CORP_ID = 'test-corp'
      WECOM_APP_SECRET = 'test-app-secret'
      WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE = 'Customer ID'
    }
    $discoveryInput = $null

    $status = Initialize-WecomSmartSheetConfiguration -Path $path `
      -SmartSheetLink 'https://doc.weixin.qq.com/smartsheet/s3_document?tab=t_sheet&viewId=v1' `
      -DiscoverFields {
        param($settings)
        $script:discoveryInput = @{} + $settings
      } `
      -ReadValue {
        param($name, $secret)
        $values[$name]
      }

    $status.Configured | Should Be $true
    $script:discoveryInput['WECOM_CORP_ID'] | Should Be 'test-corp'
    $script:discoveryInput['WECOM_SMARTSHEET_DOC_ID'] | Should Be 's3_document'
    $script:discoveryInput['WECOM_SMARTSHEET_SHEET_ID'] | Should Be 't_sheet'
    $script:discoveryInput['WECOM_SMARTSHEET_VIEW_ID'] | Should Be 'v1'
  }

  It 'reports the required names when no encrypted configuration exists' {
    Import-Module $modulePath -Force -ErrorAction Stop

    $status = Get-WecomSmartSheetConfigurationStatus -Path (Join-Path $TestDrive 'missing.clixml')

    $status.Configured | Should Be $false
    ($status.Missing -join ',') | Should Be (@(
      'WECOM_CORP_ID',
      'WECOM_APP_SECRET',
      'WECOM_SMARTSHEET_DOC_ID',
      'WECOM_SMARTSHEET_SHEET_ID',
      'WECOM_SMARTSHEET_VIEW_ID',
      'WECOM_SMARTSHEET_SOURCE_TABLE',
      'WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE'
    ) -join ',')
  }

  It 'recognizes a complete encrypted configuration without storing the secret as text' {
    Import-Module $modulePath -Force -ErrorAction Stop
    $path = Join-Path $TestDrive 'wecom-smartsheet.clixml'
    $settings = @{
      WECOM_CORP_ID = 'test-corp'
      WECOM_APP_SECRET = 'test-app-secret'
      WECOM_SMARTSHEET_DOC_ID = 'test-doc'
      WECOM_SMARTSHEET_SHEET_ID = 'test-sheet'
      WECOM_SMARTSHEET_VIEW_ID = 'test-view'
      WECOM_SMARTSHEET_SOURCE_TABLE = 'Customers'
      WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE = 'External ID'
    }

    Save-WecomSmartSheetConfiguration -Settings $settings -Path $path
    $status = Get-WecomSmartSheetConfigurationStatus -Path $path

    $status.Configured | Should Be $true
    ($status.Missing -join ',') | Should Be ''
    (Get-Content -LiteralPath $path -Raw) | Should Not Match 'test-app-secret'
  }

  It 'injects configuration only while the supplied command runs' {
    Import-Module $modulePath -Force -ErrorAction Stop
    $path = Join-Path $TestDrive 'wecom-smartsheet.clixml'
    $settings = @{
      WECOM_CORP_ID = 'test-corp'
      WECOM_APP_SECRET = 'test-app-secret'
      WECOM_SMARTSHEET_DOC_ID = 'test-doc'
      WECOM_SMARTSHEET_SHEET_ID = 'test-sheet'
      WECOM_SMARTSHEET_VIEW_ID = 'test-view'
      WECOM_SMARTSHEET_SOURCE_TABLE = 'Customers'
      WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE = 'External ID'
    }
    Save-WecomSmartSheetConfiguration -Settings $settings -Path $path | Out-Null
    $env:WECOM_CORP_ID = 'before-test'

    $during = Invoke-WithWecomSmartSheetEnvironment -Path $path -Command {
      $env:WECOM_CORP_ID
    }

    $during | Should Be 'test-corp'
    $env:WECOM_CORP_ID | Should Be 'before-test'
  }

  It 'collects every required value through the supplied local reader' {
    Import-Module $modulePath -Force -ErrorAction Stop
    $path = Join-Path $TestDrive 'wecom-smartsheet.clixml'
    $values = @{
      WECOM_CORP_ID = 'test-corp'
      WECOM_APP_SECRET = 'test-app-secret'
      WECOM_SMARTSHEET_DOC_ID = 'test-doc'
      WECOM_SMARTSHEET_SHEET_ID = 'test-sheet'
      WECOM_SMARTSHEET_VIEW_ID = 'test-view'
      WECOM_SMARTSHEET_SOURCE_TABLE = 'Customers'
      WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE = 'External ID'
    }
    $requested = New-Object System.Collections.Generic.List[string]

    $status = Initialize-WecomSmartSheetConfiguration -Path $path -ReadValue {
      param($name, $secret)
      $requested.Add($name)
      $values[$name]
    }

    $status.Configured | Should Be $true
    ($requested -join ',') | Should Be (@(
      'WECOM_CORP_ID',
      'WECOM_APP_SECRET',
      'WECOM_SMARTSHEET_DOC_ID',
      'WECOM_SMARTSHEET_SHEET_ID',
      'WECOM_SMARTSHEET_VIEW_ID',
      'WECOM_SMARTSHEET_SOURCE_TABLE',
      'WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE'
    ) -join ',')
  }

  It 'provides the local Smart Sheet launcher' {
    Test-Path $launcherPath | Should Be $true
  }

  It 'uses a non-echoing prompt before reading the app secret from the clipboard' {
    $launcherContent = Get-Content -LiteralPath $launcherPath -Raw

    $launcherContent | Should Match "Read-Host -Prompt 'Copy the full WECOM_APP_SECRET to the clipboard, then press Enter' -AsSecureString"
  }

  It 'decodes provisioning identifiers from an ASCII-safe UTF-8 envelope' {
    $launcherContent = Get-Content -LiteralPath $launcherPath -Raw

    $launcherContent | Should Match 'WECOM_SMARTSHEET_RESULT_BASE64='
    $launcherContent | Should Match 'FromBase64String'
  }

  It 'reports missing configuration through the launcher without reading values' {
    $status = & $launcherPath -Mode Status -Path (Join-Path $TestDrive 'missing.clixml')

    $status.Configured | Should Be $false
    ($status.Missing -join ',') | Should Be (@(
      'WECOM_CORP_ID',
      'WECOM_APP_SECRET',
      'WECOM_SMARTSHEET_DOC_ID',
      'WECOM_SMARTSHEET_SHEET_ID',
      'WECOM_SMARTSHEET_VIEW_ID',
      'WECOM_SMARTSHEET_SOURCE_TABLE',
      'WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE'
    ) -join ',')
  }

  It 'configures the encrypted local store through the launcher' {
    $path = Join-Path $TestDrive 'wecom-smartsheet.clixml'
    $values = @{
      WECOM_CORP_ID = 'test-corp'
      WECOM_APP_SECRET = 'test-app-secret'
      WECOM_SMARTSHEET_DOC_ID = 'test-doc'
      WECOM_SMARTSHEET_SHEET_ID = 'test-sheet'
      WECOM_SMARTSHEET_VIEW_ID = 'test-view'
      WECOM_SMARTSHEET_SOURCE_TABLE = 'Customers'
      WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE = 'External ID'
    }

    $status = & $launcherPath -Mode Configure -Path $path -ReadValue {
      param($name, $secret)
      $values[$name]
    }

    $status.Configured | Should Be $true
  }

  It 'passes a Smart Sheet link through the launcher before asking for remaining values' {
    $path = Join-Path $TestDrive 'wecom-smartsheet.clixml'
    $requested = New-Object System.Collections.Generic.List[string]
    $values = @{
      WECOM_CORP_ID = 'test-corp'
      WECOM_APP_SECRET = 'test-app-secret'
      WECOM_SMARTSHEET_SOURCE_TABLE = 'Customers'
      WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE = 'Customer ID'
    }

    $status = & $launcherPath -Mode Configure -Path $path `
      -SmartSheetLink 'https://doc.weixin.qq.com/sheet/s3_document?sheet_id=s3_sheet&view_id=v1' `
      -ReadValue {
        param($name, $secret)
        $requested.Add($name)
        $values[$name]
      }

    $status.Configured | Should Be $true
    ($requested -join ',') | Should Be (@(
      'WECOM_CORP_ID',
      'WECOM_APP_SECRET',
      'WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE'
    ) -join ',')
  }

  It 'passes field discovery through the launcher before requesting the unique field' {
    $path = Join-Path $TestDrive 'wecom-smartsheet.clixml'
    $values = @{
      WECOM_CORP_ID = 'test-corp'
      WECOM_APP_SECRET = 'test-app-secret'
      WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE = 'Customer ID'
    }
    $discoveryCalls = New-Object System.Collections.Generic.List[string]

    $status = & $launcherPath -Mode Configure -Path $path `
      -SmartSheetLink 'https://doc.weixin.qq.com/smartsheet/s3_document?tab=t_sheet&viewId=v1' `
      -DiscoverFields {
        param($settings)
        $discoveryCalls.Add($settings['WECOM_SMARTSHEET_DOC_ID'])
      } `
      -ReadValue {
        param($name, $secret)
        $values[$name]
      }

    $status.Configured | Should Be $true
    ($discoveryCalls -join ',') | Should Be 's3_document'
  }

  It 'refuses the acceptance run before configuration exists' {
    { & $launcherPath -Mode Accept -Path (Join-Path $TestDrive 'missing.clixml') } |
      Should Throw 'Encrypted WeCom Smart Sheet configuration is incomplete'
  }

  It 'runs acceptance through the encrypted process environment' {
    $path = Join-Path $TestDrive 'wecom-smartsheet.clixml'
    $values = @{
      WECOM_CORP_ID = 'test-corp'
      WECOM_APP_SECRET = 'test-app-secret'
      WECOM_SMARTSHEET_DOC_ID = 'test-doc'
      WECOM_SMARTSHEET_SHEET_ID = 'test-sheet'
      WECOM_SMARTSHEET_VIEW_ID = 'test-view'
      WECOM_SMARTSHEET_SOURCE_TABLE = 'Customers'
      WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE = 'External ID'
    }
    Initialize-WecomSmartSheetConfiguration -Path $path -ReadValue {
      param($name, $secret)
      $values[$name]
    } | Out-Null

    $during = & $launcherPath -Mode Accept -Path $path -RunCommand {
      $env:WECOM_CORP_ID
    }

    $during | Should Be 'test-corp'
  }

  It 'starts the business backend with the encrypted Smart Sheet configuration available to WSL' {
    $path = Join-Path $TestDrive 'wecom-smartsheet.clixml'
    $values = @{
      WECOM_CORP_ID = 'test-corp'
      WECOM_APP_SECRET = 'test-app-secret'
      WECOM_SMARTSHEET_DOC_ID = 'test-doc'
      WECOM_SMARTSHEET_SHEET_ID = 'test-sheet'
      WECOM_SMARTSHEET_VIEW_ID = 'test-view'
      WECOM_SMARTSHEET_SOURCE_TABLE = 'Customers'
      WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE = 'External ID'
    }
    Initialize-WecomSmartSheetConfiguration -Path $path -ReadValue {
      param($name, $secret)
      $values[$name]
    } | Out-Null
    $beforeWslEnv = $env:WSLENV
    $beforeCorpId = $env:WECOM_CORP_ID

    $during = & $launcherPath -Mode Start -Path $path -RunCommand {
      [pscustomobject]@{
        CorpId = $env:WECOM_CORP_ID
        DocumentId = $env:WECOM_SMARTSHEET_DOC_ID
        WslEnv = $env:WSLENV
      }
    }

    $during.CorpId | Should Be 'test-corp'
    $during.DocumentId | Should Be 'test-doc'
    $during.WslEnv | Should Match 'WECOM_APP_SECRET'
    $env:WSLENV | Should Be $beforeWslEnv
    $env:WECOM_CORP_ID | Should Be $beforeCorpId
  }

  It 'provisions from the encrypted secret draft and stores a complete configuration' {
    $path = Join-Path $TestDrive 'wecom-smartsheet.clixml'
    $draft = Join-Path $TestDrive 'secret-draft.clixml'
    [pscustomobject]@{
      Version = 1
      Value = ConvertTo-SecureString 'test-app-secret' -AsPlainText -Force
    } | Export-Clixml -LiteralPath $draft

    $state = Join-Path $TestDrive 'provisioning-state.clixml'
    $status = & $launcherPath -Mode Provision -Path $path -DraftSecretPath $draft `
      -ProvisioningStatePath $state -CorpId 'test-corp' -DocumentName 'Private Domain API Acceptance' `
      -CreateDocument {
        param($settings, $documentName)
        $settings['WECOM_CORP_ID'] | Should Be 'test-corp'
        $settings['WECOM_APP_SECRET'] | Should Be 'test-app-secret'
        $documentName | Should Be 'Private Domain API Acceptance'
        [pscustomobject]@{
          documentId = 'doc-1'
          documentUrl = 'https://doc.example/1'
        }
      } -PrepareSheet {
        param($settings, $created)
        $created.documentId | Should Be 'doc-1'
        [pscustomobject]@{
          documentId = $created.documentId
          documentUrl = $created.documentUrl
          sheetId = 'sheet-1'
          viewId = 'view-1'
          sourceTable = 'sheet-1'
          uniqueFieldTitle = 'Customer ID'
        }
      }

    $status.Configured | Should Be $true
    (Test-Path -LiteralPath $draft) | Should Be $false
    (Test-Path -LiteralPath $state) | Should Be $false
    (Get-Content -LiteralPath $path -Raw) | Should Not Match 'test-app-secret'
    $during = Invoke-WithWecomSmartSheetEnvironment -Path $path -Command {
      "$env:WECOM_CORP_ID|$env:WECOM_SMARTSHEET_DOC_ID|$env:WECOM_SMARTSHEET_SHEET_ID|$env:WECOM_SMARTSHEET_VIEW_ID|$env:WECOM_SMARTSHEET_SOURCE_TABLE|$env:WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE"
    }
    $during | Should Be 'test-corp|doc-1|sheet-1|view-1|sheet-1|Customer ID'
  }

  It 'reuses the saved document checkpoint after preparation fails' {
    $path = Join-Path $TestDrive 'resume-config.clixml'
    $draft = Join-Path $TestDrive 'resume-secret.clixml'
    $state = Join-Path $TestDrive 'resume-state.clixml'
    [pscustomobject]@{
      Version = 1
      Value = ConvertTo-SecureString 'test-app-secret' -AsPlainText -Force
    } | Export-Clixml -LiteralPath $draft

    {
      & $launcherPath -Mode Provision -Path $path -DraftSecretPath $draft `
        -ProvisioningStatePath $state -CorpId 'test-corp' -CreateDocument {
          [pscustomobject]@{ documentId = 'doc-once'; documentUrl = 'https://doc.example/once' }
        } -PrepareSheet { throw 'temporary preparation failure' }
    } | Should Throw 'temporary preparation failure'

    (Test-Path -LiteralPath $draft) | Should Be $true
    (Test-Path -LiteralPath $state) | Should Be $true
    $status = & $launcherPath -Mode Provision -Path $path -DraftSecretPath $draft `
      -ProvisioningStatePath $state -CorpId 'test-corp' -CreateDocument {
        throw 'must not create a second document'
      } -PrepareSheet {
        param($settings, $created)
        [pscustomobject]@{
          documentId = $created.documentId
          documentUrl = $created.documentUrl
          sheetId = 'sheet-1'
          viewId = 'view-1'
          sourceTable = 'sheet-1'
          uniqueFieldTitle = 'Customer ID'
        }
      }

    $status.Configured | Should Be $true
    (Test-Path -LiteralPath $state) | Should Be $false
  }
}
