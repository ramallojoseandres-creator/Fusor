@echo off
REM One-shot: expose data\lista_importada.m3u for SEÑAL APKs (until server.js is updated).
set ROOT=%~dp0
if not exist "%ROOT%public\downloads" mkdir "%ROOT%public\downloads"
copy /Y "%ROOT%data\lista_importada.m3u" "%ROOT%public\downloads\lista.m3u"
copy /Y "%ROOT%data\lista_importada.m3u" "%ROOT%public\downloads\lista_importada.m3u"
echo OK: public\downloads\lista*.m3u updated from data\lista_importada.m3u
pause
