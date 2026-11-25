# Makes Windows executables use UTF-8 for the "ANSI" Windows calls, by adding
#   <activeCodePage xmlns="http://schemas.microsoft.com/SMI/2019/WindowsSettings">UTF-8</activeCodePage>
# to the application manifest embedded in each one (Windows 10 1903 and later honour it).
#
# The JDK's java.exe and javaw.exe make those calls, so in a folder named outside the system code
# page (a Japanese name on an English system) they could not find their own java.dll, and such a
# file name reached Java as ??.csv. The build runs this on the jlinked runtime before javapackager
# copies it into the app. Rewriting the manifest removes the files' Authenticode signatures.
#
# Usage: use-utf8-code-page.ps1 <exe> [<exe> ...]

param(
    [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)]
    [string[]] $Executables
)

$ErrorActionPreference = 'Stop'

# Written for the C# 5 compiler that Windows PowerShell 5.1 uses.
Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;

public static class EmbeddedManifest {
    static readonly IntPtr RT_MANIFEST = (IntPtr) 24;
    // The manifest Windows reads when it creates a process from an .exe.
    static readonly IntPtr CREATEPROCESS_MANIFEST_RESOURCE_ID = (IntPtr) 1;
    const uint LOAD_LIBRARY_AS_DATAFILE = 0x2;

    delegate bool EnumResLangProc(IntPtr module, IntPtr type, IntPtr name, ushort language, IntPtr param);

    [DllImport("kernel32", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern IntPtr LoadLibraryEx(string file, IntPtr reserved, uint flags);
    [DllImport("kernel32", SetLastError = true)]
    static extern bool FreeLibrary(IntPtr module);
    [DllImport("kernel32", SetLastError = true)]
    static extern bool EnumResourceLanguages(IntPtr module, IntPtr type, IntPtr name, EnumResLangProc callback, IntPtr param);
    [DllImport("kernel32", SetLastError = true)]
    static extern IntPtr FindResourceEx(IntPtr module, IntPtr type, IntPtr name, ushort language);
    [DllImport("kernel32", SetLastError = true)]
    static extern IntPtr LoadResource(IntPtr module, IntPtr resource);
    [DllImport("kernel32", SetLastError = true)]
    static extern IntPtr LockResource(IntPtr data);
    [DllImport("kernel32", SetLastError = true)]
    static extern uint SizeofResource(IntPtr module, IntPtr resource);
    [DllImport("kernel32", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern IntPtr BeginUpdateResource(string file, bool deleteExistingResources);
    [DllImport("kernel32", SetLastError = true)]
    static extern bool UpdateResource(IntPtr update, IntPtr type, IntPtr name, ushort language, byte[] data, uint size);
    [DllImport("kernel32", SetLastError = true)]
    static extern bool EndUpdateResource(IntPtr update, bool discard);

    // Returns the manifest's bytes, and in language the language it is stored under, so that it
    // can be replaced in place rather than joined by a second manifest in another language.
    public static byte[] Read(string file, out ushort language) {
        IntPtr module = LoadLibraryEx(file, IntPtr.Zero, LOAD_LIBRARY_AS_DATAFILE);
        if (module == IntPtr.Zero) throw new Win32Exception();
        try {
            ushort found = 0;
            int count = 0;
            EnumResLangProc callback = delegate(IntPtr m, IntPtr t, IntPtr n, ushort l, IntPtr p) {
                found = l;
                count++;
                return true;
            };
            if (!EnumResourceLanguages(module, RT_MANIFEST, CREATEPROCESS_MANIFEST_RESOURCE_ID, callback, IntPtr.Zero) || count == 0) {
                throw new InvalidOperationException(file + " has no application manifest");
            }
            if (count > 1) {
                throw new InvalidOperationException(file + " has " + count + " application manifests");
            }
            language = found;

            IntPtr resource = FindResourceEx(module, RT_MANIFEST, CREATEPROCESS_MANIFEST_RESOURCE_ID, found);
            if (resource == IntPtr.Zero) throw new Win32Exception();
            uint size = SizeofResource(module, resource);
            IntPtr data = LockResource(LoadResource(module, resource));
            if (data == IntPtr.Zero) throw new Win32Exception();
            byte[] bytes = new byte[size];
            Marshal.Copy(data, bytes, 0, (int) size);
            return bytes;
        } finally {
            FreeLibrary(module);
        }
    }

    public static void Write(string file, ushort language, byte[] manifest) {
        IntPtr update = BeginUpdateResource(file, false);
        if (update == IntPtr.Zero) throw new Win32Exception();
        if (!UpdateResource(update, RT_MANIFEST, CREATEPROCESS_MANIFEST_RESOURCE_ID, language, manifest, (uint) manifest.Length)) {
            int error = Marshal.GetLastWin32Error();
            EndUpdateResource(update, true);
            throw new Win32Exception(error);
        }
        if (!EndUpdateResource(update, false)) throw new Win32Exception();
    }
}
'@

$asmV3 = 'urn:schemas-microsoft-com:asm.v3'
$settings2019 = 'http://schemas.microsoft.com/SMI/2019/WindowsSettings'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Get-Manifest([string] $file, [ref] $language) {
    $lang = [uint16] 0
    $bytes = [EmbeddedManifest]::Read($file, [ref] $lang)
    $language.Value = $lang
    $doc = New-Object System.Xml.XmlDocument
    $doc.PreserveWhitespace = $true
    $doc.LoadXml($utf8.GetString($bytes).TrimStart([char] 0xFEFF))
    return $doc
}

function Get-ActiveCodePage([System.Xml.XmlDocument] $doc) {
    $ns = New-Object System.Xml.XmlNamespaceManager($doc.NameTable)
    $ns.AddNamespace('ws', $settings2019)
    return $doc.SelectSingleNode('//ws:activeCodePage', $ns)
}

foreach ($exe in $Executables) {
    $file = (Resolve-Path -LiteralPath $exe).ProviderPath
    $language = [uint16] 0
    $doc = Get-Manifest $file ([ref] $language)

    $existing = Get-ActiveCodePage $doc
    if ($existing -ne $null) {
        if ($existing.InnerText -ne 'UTF-8') {
            throw "$file already sets activeCodePage to '$($existing.InnerText)'"
        }
        Write-Host "$file already uses the UTF-8 code page"
        continue
    }

    # Add to the manifest's existing application/windowsSettings, if it has them, beside settings
    # such as dpiAware, rather than starting a second application element.
    $ns = New-Object System.Xml.XmlNamespaceManager($doc.NameTable)
    $ns.AddNamespace('v3', $asmV3)
    $windowsSettings = $doc.SelectSingleNode('/*/v3:application/v3:windowsSettings', $ns)
    if ($windowsSettings -eq $null) {
        $application = $doc.SelectSingleNode('/*/v3:application', $ns)
        if ($application -eq $null) {
            $application = $doc.DocumentElement.AppendChild($doc.CreateElement('application', $asmV3))
        }
        $windowsSettings = $application.AppendChild($doc.CreateElement('windowsSettings', $asmV3))
    }
    $codePage = $doc.CreateElement('activeCodePage', $settings2019)
    $codePage.InnerText = 'UTF-8'
    [void] $windowsSettings.AppendChild($codePage)

    $stream = New-Object System.IO.MemoryStream
    $writerSettings = New-Object System.Xml.XmlWriterSettings
    $writerSettings.Encoding = $utf8
    $writer = [System.Xml.XmlWriter]::Create($stream, $writerSettings)
    $doc.Save($writer)
    $writer.Close()

    [EmbeddedManifest]::Write($file, $language, $stream.ToArray())

    # Read it back, so a write that silently did nothing fails the build.
    $check = Get-Manifest $file ([ref] $language)
    $written = Get-ActiveCodePage $check
    if ($written -eq $null -or $written.InnerText -ne 'UTF-8') {
        throw "$file does not carry activeCodePage UTF-8 after rewriting its manifest"
    }
    Write-Host "$file now uses the UTF-8 code page"
}
