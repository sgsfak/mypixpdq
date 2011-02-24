@echo off

if not "%MAVEN_HOME%" == "" goto run
SET MAVEN_HOME=d:\apache-maven-2.2.1

:run
%MAVEN_HOME%\bin\mvn exec:java -Dexec.mainClass="gr.forth.ics.icardea.pid.PatientIndex" -Dexec.classpathScope=runtime -Dexec.args="config.ini"
