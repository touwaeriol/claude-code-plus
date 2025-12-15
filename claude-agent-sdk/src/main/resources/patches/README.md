# CLI Patches

This directory contains patch definitions for enhancing the Claude CLI.

## Patch File Format

Each patch file is a JSON file with the following structure:

```json
{
  "id": "unique_patch_id",
  "description": "Human readable description",
  "version": "2.0.69",
  "priority": 100,
  "enabled": true,
  "patches": [
    {
      "id": "sub_patch_id",
      "description": "What this specific change does",
      "search": "exact string to find",
      "replace": "replacement string",
      "type": "replace|before|after",
      "required": true
    }
  ]
}
```

## Patch Types

- `replace`: Replace the search string with the replacement
- `before`: Insert the replacement before the search string
- `after`: Insert the replacement after the search string

## Execution Order

Patches are applied in order of `priority` (lower numbers first).

## Creating a New Patch

1. Format the CLI code first: `./gradlew formatCli`
2. Find the exact string to search for
3. Create a patch JSON file in this directory
4. Test with: `./gradlew patchCli`

## Patch Application

Patches are applied during build via `patchCli` task, producing:
- `claude-cli-{version}.js` - Original CLI
- `claude-cli-{version}-enhanced.js` - Patched CLI
