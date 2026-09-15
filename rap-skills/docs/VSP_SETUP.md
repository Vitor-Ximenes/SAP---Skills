# VSP (Vibing Steampunk) Setup Guide

VSP is the MCP server that connects Claude Code to your SAP system. It exposes ABAP Development Tools (ADT) APIs as MCP tools, giving Claude the ability to read/write source code, activate objects, run tests, debug, and more.

---

## Installation

### Option A — Pre-built Binary

Download from [GitHub Releases](https://github.com/oisee/vibing-steampunk/releases) for your OS.

```bash
# macOS / Linux
mv vsp /usr/local/bin/vsp
chmod +x /usr/local/bin/vsp
vsp --version
```

### Option B — Build from Source

```bash
git clone https://github.com/oisee/vibing-steampunk.git
cd vibing-steampunk
make build
mv bin/vsp /usr/local/bin/vsp
```

---

## Configuration

### MCP Config (`.mcp.json`)

Create `.mcp.json` in your project root:

```json
{
  "mcpServers": {
    "vsp-btp": {
      "command": "/usr/local/bin/vsp",
      "env": {
        "SAP_URL": "https://your-sap-host:44300",
        "SAP_USER": "YOUR_USER",
        "SAP_PASSWORD": "YOUR_PASSWORD",
        "SAP_CLIENT": "001"
      }
    }
  }
}
```

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `SAP_URL` | Yes | SAP system URL with port |
| `SAP_USER` | Yes | Your SAP username |
| `SAP_PASSWORD` | Yes | Your SAP password |
| `SAP_CLIENT` | No | Client number (default: `001`) |
| `SAP_MODE` | No | `focused` (52 tools) or `expert` (99 tools) |
| `SAP_INSECURE` | No | `true` to skip TLS verification (dev only) |
| `SAP_ALLOW_TRANSPORTABLE_EDITS` | No | `true` to edit transportable objects |

### BTP / ABAP Cloud Connection

For BTP ABAP Environment, see the detailed walkthrough:
[VSP for ABAP Cloud — Mac + Claude](https://medium.com/@warren_eiserman/vibe-steam-punk-vsp-for-abap-cloud-mac-claude-2864d601978f)

---

## Verify Connection

```bash
claude
> Check my SAP connection info
```

Claude will call `GetConnectionInfo` and display your system URL, user, and client.

---

## Optional: ZADT_VSP WebSocket Handler

For advanced features (debugging, RFC, reports, abapGit), install the WebSocket handler:

```
> Install the ZADT_VSP handler on the system
```

Features unlocked:
- ABAP debugging (breakpoints, stepping, variable inspection)
- AMDP (HANA SQLScript) debugging
- Report execution with selection screen parameters
- Function module calls via RFC
- abapGit-format export/import
- Object package moves

After installation, complete the manual SAPC and SICF setup steps (Claude will guide you).

---

## Security

- **Never commit `.mcp.json` with real credentials** — it's in `.gitignore` by default
- Use environment variables or a credential manager for shared environments
- `SAP_INSECURE` should only be used for dev systems with self-signed certificates
