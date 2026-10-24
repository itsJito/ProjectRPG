@echo off
rem Astratto Engine - builds the native core (astratto.dll), its self-test, its benchmark and the example C++ gameplay
rem module with the Visual C++ compiler. No CMake needed: CMakeLists.txt beside this file does the same for anyone who
rem has it, and for other platforms.
rem   build.bat          build everything, then run the self-test
rem   build.bat game     incrementally build only the runtime DLL (CMake when available)
rem   build.bat notest   build only
rem   build.bat asan     build everything with AddressSanitizer into bin\windows-x64-asan, then run the self-test there
rem   build.bat checked  build everything with the debug runtime - every container access bounds-checked, the heap
rem                      checked, uninitialised use caught - into bin\windows-x64-checked, then run the self-test there
rem   build.bat bench    build everything, then run the benchmark and hold it to the budgets in tests\budgets.txt
setlocal enabledelayedexpansion
set HERE=%~dp0
set OUT=%HERE%bin\windows-x64
set OBJ=%HERE%obj
set EXTRA=/O2 /DNDEBUG /MT
set NOPDB=/PDB:none
if /i "%1"=="asan" (
  set OUT=%HERE%bin\windows-x64-asan
  set OBJ=%HERE%obj\asan
  rem optimised still, so that the self-test's timings mean something, with the sanitizer's checks on every access
  set EXTRA=/O1 /Zi /FS /fsanitize=address /MD
  set NOPDB=
)
if /i "%1"=="checked" (
  set OUT=%HERE%bin\windows-x64-checked
  set OBJ=%HERE%obj\checked
  set EXTRA=/Od /RTC1 /MTd /D_DEBUG /D_ITERATOR_DEBUG_LEVEL=2 /Zi /FS
  set NOPDB=
)

where cl >nul 2>nul
if not errorlevel 1 goto :compile
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" (
  echo astratto: no Visual Studio installation found
  exit /b 2
)
for /f "usebackq delims=" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VSROOT=%%i"
if not exist "%VSROOT%\VC\Auxiliary\Build\vcvars64.bat" (
  echo astratto: the C++ build tools are not installed
  exit /b 2
)
call "%VSROOT%\VC\Auxiliary\Build\vcvars64.bat" >nul
if errorlevel 1 exit /b 2

:compile
if /i "%1"=="game" (
  set "CMAKE=cmake"
  where cmake >nul 2>nul
  if errorlevel 1 (
    set "CMAKE=%VSROOT%\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
  )
  "!CMAKE!" --version >nul 2>nul
  if not errorlevel 1 (
    "!CMAKE!" -S "%HERE%." -B "%HERE%build-windows-x64-game" -G "NMake Makefiles" -DCMAKE_BUILD_TYPE=Release -DASTRATTO_PLATFORM=windows-x64 -DASTRATTO_WERROR=ON -DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded
    if errorlevel 1 exit /b 1
    "!CMAKE!" --build "%HERE%build-windows-x64-game" --target astratto
    exit /b !errorlevel!
  )
)
if /i "%1"=="asan" if not exist "%VCToolsInstallDir%lib\x64\clang_rt.asan_dynamic-x86_64.lib" (
  echo astratto: AddressSanitizer is not installed with these build tools ^(Visual Studio Installer: "C++ AddressSanitizer"^); "build.bat checked" needs nothing more
  exit /b 2
)
if not exist "%OUT%" mkdir "%OUT%"
for %%d in (lib test module testmodule bench lab) do if not exist "%OBJ%\%%d" mkdir "%OBJ%\%%d"

set FLAGS=/nologo /std:c++17 %EXTRA% /EHsc /W4 /WX /wd4324 /fp:precise /utf-8 /MP /Fd"%OBJ%\vc.pdb" /I"%HERE%include" /I"%HERE%src"
rem   build.bat lab      the Sydney lab only (tests\sydney_lab.cpp, built from Sydney's and Impulsis's own sources)
set LABEXE=astratto_sydney_lab.exe
if not "%2"=="" set LABEXE=%2
if /i "%1"=="lab" (
  cl /nologo /std:c++17 /O2 /DNDEBUG /MT /EHsc /W4 /WX /wd4324 /fp:precise /utf-8 /I"%HERE%include" /I"%HERE%src" "%HERE%tests\sydney_lab.cpp" "%HERE%src\physics\ragdoll.cpp" "%HERE%src\physics\sydney.cpp" "%HERE%src\impulsis\space.cpp" "%HERE%src\impulsis\collide.cpp" "%HERE%src\world\voxel_world.cpp" /Fo"%OBJ%\lab\\" /Fe"%OUT%\%LABEXE%" /link /PDB:none
  exit /b !errorlevel!
)
rem every .cpp under src\ is part of the library
set SRC=
for /r "%HERE%src" %%f in (*.cpp) do set SRC=!SRC! "%%f"

cl %FLAGS% /DASTRATTO_BUILD /LD %SRC% /Fo"%OBJ%\lib\\" /Fe"%OUT%\astratto.dll" /link /IMPLIB:"%OBJ%\astratto.lib" %NOPDB% user32.lib winmm.lib
if errorlevel 1 ( echo astratto: the library did not build & exit /b 1 )
del /q "%OUT%\astratto.exp" 2>nul

if /i "%1"=="game" exit /b 0

cl %FLAGS% "%HERE%tests\selftest.cpp" "%HERE%tests\services.cpp" "%HERE%tests\hardening.cpp" "%HERE%tests\impulsis.cpp" /Fo"%OBJ%\test\\" /Fe"%OUT%\astratto_selftest.exe" /link "%OBJ%\astratto.lib"
if errorlevel 1 ( echo astratto: the self-test did not build & exit /b 1 )

rem the module the self-test loads to show that a module may release itself from inside its own code (not for games)
cl %FLAGS% /LD "%HERE%tests\test_module.cpp" /Fo"%OBJ%\testmodule\\" /Fe"%OUT%\astratto_test_module.dll" /link %NOPDB%
if errorlevel 1 ( echo astratto: the test module did not build & exit /b 1 )
del /q "%OUT%\astratto_test_module.exp" "%OUT%\astratto_test_module.lib" 2>nul

rem the benchmark: times the core's hot paths and holds them to tests\budgets.txt
cl %FLAGS% "%HERE%tests\bench.cpp" /Fo"%OBJ%\bench\\" /Fe"%OUT%\astratto_bench.exe" /link "%OBJ%\astratto.lib"
if errorlevel 1 ( echo astratto: the benchmark did not build & exit /b 1 )

rem the example gameplay module a game can copy: a DLL the engine loads (see include\astratto\astratto_module.h)
if exist "%HERE%modules\example\example_module.cpp" (
  cl %FLAGS% /LD "%HERE%modules\example\example_module.cpp" /Fo"%OBJ%\module\\" /Fe"%OUT%\astratto_example_module.dll" /link %NOPDB%
  if errorlevel 1 ( echo astratto: the example module did not build & exit /b 1 )
  del /q "%OUT%\astratto_example_module.exp" "%OUT%\astratto_example_module.lib" 2>nul
)
del /q "%OUT%\*.ilk" 2>nul

if /i "%1"=="notest" exit /b 0
if /i "%1"=="bench" (
  "%OUT%\astratto_bench.exe" "%HERE%tests\budgets.txt"
  exit /b !errorlevel!
)
if /i "%1"=="asan" (
  rem the sanitizer's runtime library sits beside the compiler
  for %%d in (cl.exe) do set "PATH=%%~dp$PATH:d;!PATH!"
)
"%OUT%\astratto_selftest.exe"
exit /b %errorlevel%
