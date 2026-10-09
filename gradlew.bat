@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

if exist "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
)

"C:\Users\Lenovo\.gradle\wrapper\dists\gradle-4.10.3-bin\31t79e2qsceia4mkbojplrgx\gradle-4.10.3\bin\gradle.bat" %*
