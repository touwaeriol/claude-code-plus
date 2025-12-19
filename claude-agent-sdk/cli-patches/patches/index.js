/**
 * 补丁注册表
 *
 * 按顺序加载和应用补丁
 */

const runInBackground = require('./001-run-in-background');
const chromeStatus = require('./002-chrome-status');

// 按优先级排序导出所有补丁
module.exports = [
  runInBackground,
  chromeStatus,
  // 未来可以添加更多补丁:
  // require('./003-another-patch'),
].sort((a, b) => (a.priority || 100) - (b.priority || 100));
