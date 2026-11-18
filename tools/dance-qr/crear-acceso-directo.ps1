# Crea en el Escritorio el acceso directo "QR bailable Kronos" (con tu ícono) que abre la página como una ventana
# propia, sin pestañas ni barras. Luego se ancla a la barra de tareas con clic derecho.
$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$html = Join-Path $aqui 'index.html'
$ico  = Join-Path $aqui 'icono.ico'

if (-not (Test-Path $html)) { Write-Host "No encuentro index.html junto a este archivo ($aqui)."; Read-Host "Pulsa Enter para salir"; exit 1 }

$url = ([System.Uri]$html).AbsoluteUri
$candidatos = @(
  "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
  "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
  "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe",
  "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
  "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"
)
$navegador = $candidatos | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $navegador) { Write-Host "No encontré Chrome ni Edge instalados."; Read-Host "Pulsa Enter para salir"; exit 1 }

$escritorio = [Environment]::GetFolderPath('Desktop')
$acceso = Join-Path $escritorio 'QR bailable Kronos.lnk'
$ws = New-Object -ComObject WScript.Shell
$a = $ws.CreateShortcut($acceso)
$a.TargetPath = $navegador
$a.Arguments = "--app=`"$url`""
$a.WorkingDirectory = $aqui
if (Test-Path $ico) { $a.IconLocation = "$ico,0" }
$a.Description = 'QR bailable de Kronos'
$a.Save()

Write-Host ""
Write-Host "Listo: creé '$acceso'."
Write-Host "Ahora, en el Escritorio: clic derecho en 'QR bailable Kronos' -> (Mostrar más opciones) -> Anclar a la barra de tareas."
Read-Host "Pulsa Enter para cerrar"
