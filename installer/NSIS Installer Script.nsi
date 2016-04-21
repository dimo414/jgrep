;NSIS Modern User Interface version 1.70
;jGrep Installer Script
;Written by Stephen Strenn
;http://www.devx.com/Java/Article/30287/1954

;--------------------------------
;Include Modern UI

  !include "MUI.nsh"

;--------------------------------
;General

  ;Name and file
  Name "jGrep 1.0.2"
  OutFile "jGrepInstaller_1.0.2.exe"

  ;Default installation folder
  InstallDir "$PROGRAMFILES\jGrep"
  
  ;Get installation folder from registry if available
  InstallDirRegKey HKCU "Software\jGrep" ""

;--------------------------------
;Interface Settings

  !define MUI_ABORTWARNING
	!define MUI_HEADERIMAGE "jGrepLogo.bmp"
	!define MUI_HEADERIMAGE_BITMAP_NOSTRETCH
	!define MUI_HEADERIMAGE_BITMAP "jGrepLogo.bmp"
	!define MUI_ICON "jGrep.ico"

;--------------------------------
;Pages

  !insertmacro MUI_PAGE_LICENSE "..\LICENSE.txt"
  !insertmacro MUI_PAGE_COMPONENTS
  !insertmacro MUI_PAGE_DIRECTORY
  !insertmacro MUI_PAGE_INSTFILES
  
  !insertmacro MUI_UNPAGE_CONFIRM
  !insertmacro MUI_UNPAGE_INSTFILES
  
;--------------------------------
;Languages
 
  !insertmacro MUI_LANGUAGE "English"

;--------------------------------
;Installer Sections

Section "jGrep (required)" SecDummy

  SectionIn RO

  ;Files to be installed
  SetOutPath "$INSTDIR"
  
  File "jGrep_1.0.2.jar"
  File "jGrep.ico"

  SetOutPath "$INSTDIR"

    ; Write the installation path into the registry
  WriteRegStr HKLM SOFTWARE\jGrep "Install_Dir" "$INSTDIR"
  
  ; Write the uninstall keys for Windows
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\jGrep" "DisplayName" "jGrep"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\jGrep" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\jGrep" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\jGrep" "NoRepair" 1
  WriteUninstaller "uninstall.exe"
  
SectionEnd

; Optional section (can be disabled by the user)
Section "Start Menu Shortcuts"
  CreateDirectory "$SMPROGRAMS\jGrep"
  CreateShortCut "$SMPROGRAMS\jGrep\Uninstall.lnk" "$INSTDIR\uninstall.exe" "" "$INSTDIR\uninstall.exe"
  CreateShortCut "$SMPROGRAMS\jGrep\jGrep.lnk" "$INSTDIR\jGrep_1.0.2.jar" "1" "$INSTDIR\jGrep.ico"
SectionEnd

;--------------------------------
;Uninstaller Section

Section "Uninstall"

  ; Remove registry keys
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\jGrep"
  DeleteRegKey HKLM SOFTWARE\jGrep
  DeleteRegKey /ifempty HKCU "Software\jGrep"

	; Remove shortcuts
  RMDir /r "$SMPROGRAMS\jGrep"

  ; Remove directories used
  RMDir /r "$INSTDIR"

SectionEnd