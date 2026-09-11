@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=C:\Program Files\Java\jdk-17\bin;%PATH%"
"C:\Users\jnnateraa\AppData\Local\Programs\IntelliJ IDEA Ultimate 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd" -s "%~dp0settings.xml" %*
