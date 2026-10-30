; Установщик Yoru для Windows (Inno Setup 6). Ставится в профиль пользователя без прав администратора,
; поэтому автообновление проходит тихо. Сборка: ISCC /DAppVersion=0.5.0.1 /DSourceDir=...\outputs\Yoru /DOutputDir=... yoru.iss
#ifndef AppVersion
  #define AppVersion "0.5.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\outputs\Yoru"
#endif
#ifndef OutputDir
  #define OutputDir "..\outputs\setup"
#endif

[Setup]
AppId={{6B7E2C1A-9F3D-4E5B-A8C7-0D1E2F3A4B5C}
AppName=Yoru
AppVersion={#AppVersion}
AppVerName=Yoru {#AppVersion}
AppPublisher=maleoipTu
AppPublisherURL=https://github.com/maleoipTu/yoru-anime
DefaultDirName={localappdata}\Programs\Yoru
DefaultGroupName=Yoru
DisableProgramGroupPage=yes
DisableDirPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#OutputDir}
OutputBaseFilename=Yoru-Setup
SetupIconFile=..\Yoru\Assets\yoru.ico
UninstallDisplayIcon={app}\Yoru.exe
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "ru"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "Ярлык на рабочем столе"; GroupDescription: "Дополнительно:"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; Старые файлы предыдущей версии, которых нет в новой, не мешают: каталог очищается перед копированием.
Type: filesandordirs; Name: "{app}\*"

[Icons]
Name: "{autoprograms}\Yoru"; Filename: "{app}\Yoru.exe"
Name: "{autodesktop}\Yoru"; Filename: "{app}\Yoru.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Yoru.exe"; Description: "Запустить Yoru"; Flags: nowait postinstall skipifsilent
; После тихого автообновления приложение запускается снова.
Filename: "{app}\Yoru.exe"; Flags: nowait; Check: WizardSilent
