;This file will be executed next to the application bundle image
;I.e. current directory will contain folder CSView with application files
[Setup]
AppId={{net.kothar.csview}}
AppName=CSView
AppVersion=1.1.0
AppVerName=CSView 1.1.0
AppPublisher=Unknown
AppComments=CSView
AppCopyright=Copyright (C) 2016
;AppPublisherURL=http://java.com/
;AppSupportURL=http://java.com/
;AppUpdatesURL=http://java.com/
DefaultDirName={localappdata}\CSView
DisableStartupPrompt=Yes
DisableDirPage=Yes
DisableProgramGroupPage=Yes
DisableReadyPage=Yes
DisableFinishedPage=Yes
DisableWelcomePage=Yes
DefaultGroupName=Unknown
;Optional License
LicenseFile=
;WinXP or above
MinVersion=0,5.1 
OutputBaseFilename=CSView-1.1.0
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest
SetupIconFile=CSView\CSView.ico
UninstallDisplayIcon={app}\CSView.ico
UninstallDisplayName=CSView
WizardImageStretch=No
WizardSmallImageFile=CSView-setup-icon.bmp   
ArchitecturesInstallIn64BitMode=x64
ChangesAssociations = yes

[Registry]
Root: HKCR; Subkey: ".csv";                         ValueData: "CSView";          Flags: uninsdeletevalue; ValueType: string;  ValueName: ""
Root: HKCR; Subkey: "CSView";                       ValueData: "Program CSView";  Flags: uninsdeletekey;   ValueType: string;  ValueName: ""
Root: HKCR; Subkey: "CSView\DefaultIcon";           ValueData: "{app}\CSView.exe,0";                       ValueType: string;  ValueName: ""
Root: HKCR; Subkey: "CSView\shell\open\command";    ValueData: """{app}\CSView.exe"" ""%1""";              ValueType: string;  ValueName: ""

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
