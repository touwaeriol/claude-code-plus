/**
 * 补丁注册表
 *
 * 按顺序加载和应用补丁
 */

const runInBackground = require('./001-run-in-background');
const chromeStatus = require('./002-chrome-status');
const parentUuid = require('./003-parent-uuid');
const mcpReconnect = require('./004-mcp-reconnect');
const mcpTools = require('./005-mcp-tools');
const mcpDisableEnable = require('./006-mcp-disable-enable');

// 按优先级排序导出所有补丁
module.exports = [
  runInBackground,
  chromeStatus,
  parentUuid,
  mcpReconnect,
  mcpTools,
  mcpDisableEnable,
].sort((a, b) => (a.priority || 100) - (b.priority || 100));
