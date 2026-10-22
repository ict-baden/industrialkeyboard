<#
.SYNOPSIS
    Prüft die Syntax aller PowerShell-Skripte unter .github/ und aller PowerShell-Schritte der Workflows.
.DESCRIPTION
    Eingabe: JSON aus ps-syntax.py (Name und Code je Schritt). ${{ … }}-Ausdrücke werden vor dem Parsen ersetzt.
    Exitcode = Anzahl der Fehler.
#>
param([Parameter(Mandatory)][string]$Blocks, [string]$Root = '.github')
$fail = 0
$items = @(Get-Content $Blocks -Raw | ConvertFrom-Json)
foreach ($b in $items) {
    $code = $b.code -replace '\$\{\{[^}]*\}\}', 'X'
    $tokens = $null; $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseInput($code, [ref]$tokens, [ref]$errors)
    foreach ($e in $errors) { $fail++; Write-Host "::error::$($b.name), Zeile $($e.Extent.StartLineNumber): $($e.Message)" }
}
$files = @(Get-ChildItem -Recurse -Path $Root -Filter *.ps1)
foreach ($f in $files) {
    $tokens = $null; $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($f.FullName, [ref]$tokens, [ref]$errors)
    foreach ($e in $errors) { $fail++; Write-Host "::error file=$($f.FullName),line=$($e.Extent.StartLineNumber)::$($e.Message)" }
}
Write-Host "PowerShell-Syntax: $($items.Count) Workflow-Schritte, $($files.Count) Skripte, $fail Fehler"
exit $fail
