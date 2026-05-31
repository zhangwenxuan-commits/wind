# JetBrains Codex Debug Record - 2026-05-31

## Scope

- PyCharm 2026.1 Codex connection / activation loop
- Local environment state only
- No project code logic changed

## Symptoms

- PyCharm Codex could not enter a usable connected state
- `codex.oauth.xml` still showed `authenticated=true`
- `idea.log` repeatedly logged:
  - `QuotaManager2Impl`
  - `ResultDoesNotMatchConditionException`
- ACP registry fetch and Codex agent registration were normal

## Key Findings

1. PyCharm had stale Codex auth cache:
   - `~/.cache/JetBrains/PyCharm2026.1/aia/codex/auth.json`
   - `last_refresh = 2026-05-23T11:09:58Z`
2. A working IntelliJ IDEA instance on the same machine had fresher Codex auth:
   - `~/.cache/JetBrains/IntelliJIdea2026.1/aia/codex/auth.json`
   - `last_refresh = 2026-05-31T07:10:31Z`
3. PyCharm had a large accumulated local Codex runtime state:
   - `aia-task-history`
   - `logs_2.sqlite`
   - `state_5.sqlite`
   - session / memory / tmp caches
4. JetBrains has a recent public issue with the same quota-loop symptom:
   - `LLM-27681`
   - title: `cannot complete codex login and start agent chat`
5. Separate plugin packaging risk was also observed:
   - `ml-llm.jar` plugin XML registers `GrazieRepositoryFacade`
   - the packaged classes expose `GrazieRepository`
   - this looks like a JetBrains-side plugin inconsistency, but it was not the main path addressed in this fix

## Actions Performed

1. Stopped only PyCharm-owned Codex helper processes:
   - `codex-acp-x64-linux`
   - `codex-x86_64-unknown-linux-musl app-server`
2. Created a full backup:
   - `/home/wenxuan/jetbrains-codex-backups/pycharm-20260531-231137`
3. Backed up and reset PyCharm AI runtime state:
   - `~/.config/JetBrains/PyCharm2026.1/aia-task-history`
   - `~/.cache/JetBrains/PyCharm2026.1/aia/codex/logs_2.sqlite*`
   - `~/.cache/JetBrains/PyCharm2026.1/aia/codex/state_5.sqlite*`
   - `~/.cache/JetBrains/PyCharm2026.1/aia/codex/sessions`
   - `~/.cache/JetBrains/PyCharm2026.1/aia/codex/memories`
   - `~/.cache/JetBrains/PyCharm2026.1/aia/codex/tmp`
   - `~/.cache/JetBrains/PyCharm2026.1/aia/codex/shell_snapshots`
4. Replaced stale PyCharm Codex auth cache with the fresher working IDEA auth cache
5. Removed persisted PyCharm quota-error state:
   - `~/.config/JetBrains/PyCharm2026.1/options/AIAssistantQuotaManager2.xml`

## Post-Fix State

- PyCharm Codex helper processes were fully stopped
- PyCharm Codex auth cache now shows:
  - `last_refresh = 2026-05-31T07:10:31.383050383Z`
- No project files or application source logic were modified by this environment repair

## Expected Next Behavior

- On next PyCharm Codex use, the IDE should recreate local Codex runtime state from a clean baseline
- If the current PyCharm process keeps the old quota-loop state in memory, one IDE restart may still be required

## Review Notes

- Backup is preserved for rollback and diff inspection
- The likely root cause is JetBrains-side local state corruption plus the known quota-loop bug path
