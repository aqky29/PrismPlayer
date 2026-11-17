# AGENTS.md

## Resource & Process Cleanup Rules

When performing **compilation, debugging, testing, linting, or static analysis** that consumes significant RAM or CPU, **always clean up and terminate leftover processes as soon as the task is complete** (including after failures or timeouts).

### Mandatory Cleanup Steps

1. **Gradle & JVM / Kotlin Daemons**
   - Prefer `--no-daemon` for one-off builds, compiles, or checks when daemon persistence is unnecessary.
   - After finishing a build, compile, test, or lint session, stop Gradle daemons:
     ```powershell
     .\gradlew.bat --stop
     ```
   - Ensure no orphaned `java.exe` / Gradle / Kotlin compiler daemon processes spawned by the session remain running unnecessarily.

2. **Debug, Logcat & Analysis Processes**
   - Always terminate any background processes started during debugging or analysis (e.g., `adb logcat`, profilers, file watchers, test servers, or long-running CLI tools) once inspection is done.
   - If using agent background tasks, explicitly kill/clean up any lingering tasks before finishing your response.

3. **Verification Before Finishing**
   - Before reporting completion of a build, debug, or analysis task, verify that heavy temporary processes spawned during the session have exited cleanly.

### Safety Guardrails
- **Do not** kill unrelated user processes (such as the user's IDE, browser, or pre-existing emulator/device connections not started by the agent).
- Only clean up processes, daemons, and background tasks associated with the current build, debug, or analysis workflow.
