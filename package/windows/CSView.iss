;This file will be executed next to the application bundle image
;I.e. current directory will contain folder CSView with application files
[Setup]
AppId={{net.kothar.csview}}
AppName=CSView
AppVersion=1.3.0
AppVerName=CSView 1.3.0
AppPublisher=Kothar Labs
AppComments=CSView
AppCopyright=Copyright (C) 2019 Michael Houston
;AppPublisherURL=http://java.com/
;AppSupportURL=http://java.com/
;AppUpdatesURL=http://java.com/
DefaultDirName={pf}\CSView
DisableStartupPrompt=Yes
DisableDirPage=No
DisableProgramGroupPage=Yes
DisableReadyPage=Yes
DisableFinishedPage=No
DisableWelcomePage=Yes
DefaultGroupName=CSView
;Optional License
LicenseFile=
;WinXP or above
MinVersion=0,5.1 
OutputBaseFilename=CSView-1.3.0
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
SetupIconFile=CSView\CSView.ico
UninstallDisplayIcon={app}\CSView.ico
UninstallDisplayName=CSView
WizardImageStretch=No
WizardSmallImageFile=CSView-setup-icon.bmp   
ArchitecturesInstallIn64BitMode=x64


[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "CSView\CSView.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "CSView\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\CSView"; Filename: "{app}\CSView.exe"; IconFilename: "{app}\CSView.ico"; Check: returnTrue()
Name: "{commondesktop}\CSView"; Filename: "{app}\CSView.exe";  IconFilename: "{app}\CSView.ico"; Check: returnFalse()


[Run]
Filename: "{app}\CSView.exe"; Parameters: "-Xappcds:generatecache"; Check: returnFalse()
Filename: "{app}\CSView.exe"; Description: "{cm:LaunchProgram,CSView}"; Flags: nowait postinstall skipifsilent; Check: returnTrue()
Filename: "{app}\CSView.exe"; Parameters: "-install -svcName ""CSView"" -svcDesc ""CSView"" -mainExe ""CSView.exe""  "; Check: returnFalse()

[UninstallRun]
Filename: "{app}\CSView.exe "; Parameters: "-uninstall -svcName CSView -stopOnUninstall"; Check: returnFalse()

[Code]
function returnTrue(): Boolean;
begin
  Result := True;
end;

function returnFalse(): Boolean;
begin
  Result := False;
end;

function InitializeSetup(): Boolean;
begin
// Possible future improvements:
//   if version less or same => just launch app
//   if upgrade => check if same app is running and wait for it to exit
//   Add pack200/unpack200 support? 
  Result := True;
end;  
