@echo off
rem Her Windows'ta hazir gelen derleyiciyle exe uretir; ek kurulum gerekmez.
cd /d "%~dp0"
"%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /target:winexe /codepage:65001 /optimize+ /r:System.Windows.Forms.dll /r:System.Drawing.dll /out:FotoGoruntuleyici.exe Program.cs
if errorlevel 1 (pause & exit /b 1)
echo FotoGoruntuleyici.exe hazir.
pause
