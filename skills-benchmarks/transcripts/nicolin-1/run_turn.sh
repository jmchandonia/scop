#!/bin/bash
# usage: run_turn.sh <turn-number> <session-uuid> <resume:0|1> <prompt-file>
S=$(pwd)   # holds workspace/ (empty except .claude/skills/ with the two public SCOPe skills) and home/
cd "$S/workspace"
export XDG_CONFIG_HOME="$S/home/.config"
unset CLAUDECODE CLAUDE_PID
for v in $(env | grep '^CLAUDE_CODE_' | cut -d= -f1); do unset $v; done
MCP='{"mcpServers":{"scope":{"type":"http","url":"https://scop.berkeley.edu/mcp"}}}'
if [ "$3" = "1" ]; then SESS=(--resume "$2"); else SESS=(--session-id "$2"); fi
claude -p "$(cat "$4")" --model claude-opus-5-5 --effort high \
  --output-format stream-json --verbose \
  --setting-sources project --strict-mcp-config --mcp-config "$MCP" \
  --dangerously-skip-permissions --max-budget-usd 15 \
  --tools "Read,Glob,Grep,Skill" "${SESS[@]}" > $S/turn$1.stream.jsonl 2> $S/turn$1.stderr
echo "exit $?" >> $S/turn$1.stderr
