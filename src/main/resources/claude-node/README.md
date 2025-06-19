# Claude SDK Node Service

This is the compiled Node.js service for the Claude Code Plus IntelliJ plugin.

## Structure
- server.js - Main server entry point
- services/ - Service modules
- routes/ - HTTP and WebSocket route handlers
- start.js - Startup script
- install.js - Dependency installation script

## Usage

1. Install dependencies (first time only):
   ```
   node install.js
   ```

2. Start the server:
   ```
   node start.js --port 18080
   ```

## Requirements
- Node.js >= 18.0.0

## Note
This is a compiled version for the IntelliJ plugin. 
Do not modify these files directly - they will be overwritten during builds.
