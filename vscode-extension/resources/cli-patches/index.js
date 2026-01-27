/**
 * Patch Registry
 *
 * Load and apply patches in order
 */

const runInBackground = require('./001-run-in-background');
const chromeStatus = require('./002-chrome-status');
const parentUuid = require('./003-parent-uuid');
const mcpServerControl = require('./004-mcp-server-control');
const mcpTools = require('./005-mcp-tools');
const runToBackground = require('./007-run-to-background');
const getCapabilities = require('./008-get-capabilities');
const skillParentToolUseId = require('./009-skill-parent-tool-use-id');

// Export all patches sorted by priority
module.exports = [
  runInBackground,
  chromeStatus,
  parentUuid,
  mcpServerControl,
  mcpTools,
  runToBackground,
  getCapabilities,
  skillParentToolUseId,
].sort((a, b) => (a.priority || 100) - (b.priority || 100));
