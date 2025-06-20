"use strict";
var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __esm = (fn, res) => function __init() {
  return fn && (res = (0, fn[__getOwnPropNames(fn)[0]])(fn = 0)), res;
};
var __commonJS = (cb, mod) => function __require() {
  return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
};
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

// node_modules/logform/format.js
var require_format = __commonJS({
  "node_modules/logform/format.js"(exports2, module2) {
    "use strict";
    var InvalidFormatError = class _InvalidFormatError extends Error {
      constructor(formatFn) {
        super(`Format functions must be synchronous taking a two arguments: (info, opts)
Found: ${formatFn.toString().split("\n")[0]}
`);
        Error.captureStackTrace(this, _InvalidFormatError);
      }
    };
    module2.exports = (formatFn) => {
      if (formatFn.length > 2) {
        throw new InvalidFormatError(formatFn);
      }
      function Format(options = {}) {
        this.options = options;
      }
      Format.prototype.transform = formatFn;
      function createFormatWrap(opts) {
        return new Format(opts);
      }
      createFormatWrap.Format = Format;
      return createFormatWrap;
    };
  }
});

// node_modules/@colors/colors/lib/styles.js
var require_styles = __commonJS({
  "node_modules/@colors/colors/lib/styles.js"(exports2, module2) {
    var styles = {};
    module2["exports"] = styles;
    var codes = {
      reset: [0, 0],
      bold: [1, 22],
      dim: [2, 22],
      italic: [3, 23],
      underline: [4, 24],
      inverse: [7, 27],
      hidden: [8, 28],
      strikethrough: [9, 29],
      black: [30, 39],
      red: [31, 39],
      green: [32, 39],
      yellow: [33, 39],
      blue: [34, 39],
      magenta: [35, 39],
      cyan: [36, 39],
      white: [37, 39],
      gray: [90, 39],
      grey: [90, 39],
      brightRed: [91, 39],
      brightGreen: [92, 39],
      brightYellow: [93, 39],
      brightBlue: [94, 39],
      brightMagenta: [95, 39],
      brightCyan: [96, 39],
      brightWhite: [97, 39],
      bgBlack: [40, 49],
      bgRed: [41, 49],
      bgGreen: [42, 49],
      bgYellow: [43, 49],
      bgBlue: [44, 49],
      bgMagenta: [45, 49],
      bgCyan: [46, 49],
      bgWhite: [47, 49],
      bgGray: [100, 49],
      bgGrey: [100, 49],
      bgBrightRed: [101, 49],
      bgBrightGreen: [102, 49],
      bgBrightYellow: [103, 49],
      bgBrightBlue: [104, 49],
      bgBrightMagenta: [105, 49],
      bgBrightCyan: [106, 49],
      bgBrightWhite: [107, 49],
      // legacy styles for colors pre v1.0.0
      blackBG: [40, 49],
      redBG: [41, 49],
      greenBG: [42, 49],
      yellowBG: [43, 49],
      blueBG: [44, 49],
      magentaBG: [45, 49],
      cyanBG: [46, 49],
      whiteBG: [47, 49]
    };
    Object.keys(codes).forEach(function(key) {
      var val = codes[key];
      var style = styles[key] = [];
      style.open = "\x1B[" + val[0] + "m";
      style.close = "\x1B[" + val[1] + "m";
    });
  }
});

// node_modules/@colors/colors/lib/system/has-flag.js
var require_has_flag = __commonJS({
  "node_modules/@colors/colors/lib/system/has-flag.js"(exports2, module2) {
    "use strict";
    module2.exports = function(flag, argv) {
      argv = argv || process.argv || [];
      var terminatorPos = argv.indexOf("--");
      var prefix = /^-{1,2}/.test(flag) ? "" : "--";
      var pos = argv.indexOf(prefix + flag);
      return pos !== -1 && (terminatorPos === -1 ? true : pos < terminatorPos);
    };
  }
});

// node_modules/@colors/colors/lib/system/supports-colors.js
var require_supports_colors = __commonJS({
  "node_modules/@colors/colors/lib/system/supports-colors.js"(exports2, module2) {
    "use strict";
    var os = require("os");
    var hasFlag = require_has_flag();
    var env = process.env;
    var forceColor = void 0;
    if (hasFlag("no-color") || hasFlag("no-colors") || hasFlag("color=false")) {
      forceColor = false;
    } else if (hasFlag("color") || hasFlag("colors") || hasFlag("color=true") || hasFlag("color=always")) {
      forceColor = true;
    }
    if ("FORCE_COLOR" in env) {
      forceColor = env.FORCE_COLOR.length === 0 || parseInt(env.FORCE_COLOR, 10) !== 0;
    }
    function translateLevel(level) {
      if (level === 0) {
        return false;
      }
      return {
        level,
        hasBasic: true,
        has256: level >= 2,
        has16m: level >= 3
      };
    }
    function supportsColor(stream) {
      if (forceColor === false) {
        return 0;
      }
      if (hasFlag("color=16m") || hasFlag("color=full") || hasFlag("color=truecolor")) {
        return 3;
      }
      if (hasFlag("color=256")) {
        return 2;
      }
      if (stream && !stream.isTTY && forceColor !== true) {
        return 0;
      }
      var min = forceColor ? 1 : 0;
      if (process.platform === "win32") {
        var osRelease = os.release().split(".");
        if (Number(process.versions.node.split(".")[0]) >= 8 && Number(osRelease[0]) >= 10 && Number(osRelease[2]) >= 10586) {
          return Number(osRelease[2]) >= 14931 ? 3 : 2;
        }
        return 1;
      }
      if ("CI" in env) {
        if (["TRAVIS", "CIRCLECI", "APPVEYOR", "GITLAB_CI"].some(function(sign) {
          return sign in env;
        }) || env.CI_NAME === "codeship") {
          return 1;
        }
        return min;
      }
      if ("TEAMCITY_VERSION" in env) {
        return /^(9\.(0*[1-9]\d*)\.|\d{2,}\.)/.test(env.TEAMCITY_VERSION) ? 1 : 0;
      }
      if ("TERM_PROGRAM" in env) {
        var version2 = parseInt((env.TERM_PROGRAM_VERSION || "").split(".")[0], 10);
        switch (env.TERM_PROGRAM) {
          case "iTerm.app":
            return version2 >= 3 ? 3 : 2;
          case "Hyper":
            return 3;
          case "Apple_Terminal":
            return 2;
        }
      }
      if (/-256(color)?$/i.test(env.TERM)) {
        return 2;
      }
      if (/^screen|^xterm|^vt100|^rxvt|color|ansi|cygwin|linux/i.test(env.TERM)) {
        return 1;
      }
      if ("COLORTERM" in env) {
        return 1;
      }
      if (env.TERM === "dumb") {
        return min;
      }
      return min;
    }
    function getSupportLevel(stream) {
      var level = supportsColor(stream);
      return translateLevel(level);
    }
    module2.exports = {
      supportsColor: getSupportLevel,
      stdout: getSupportLevel(process.stdout),
      stderr: getSupportLevel(process.stderr)
    };
  }
});

// node_modules/@colors/colors/lib/custom/trap.js
var require_trap = __commonJS({
  "node_modules/@colors/colors/lib/custom/trap.js"(exports2, module2) {
    module2["exports"] = function runTheTrap(text, options) {
      var result = "";
      text = text || "Run the trap, drop the bass";
      text = text.split("");
      var trap = {
        a: ["@", "\u0104", "\u023A", "\u0245", "\u0394", "\u039B", "\u0414"],
        b: ["\xDF", "\u0181", "\u0243", "\u026E", "\u03B2", "\u0E3F"],
        c: ["\xA9", "\u023B", "\u03FE"],
        d: ["\xD0", "\u018A", "\u0500", "\u0501", "\u0502", "\u0503"],
        e: [
          "\xCB",
          "\u0115",
          "\u018E",
          "\u0258",
          "\u03A3",
          "\u03BE",
          "\u04BC",
          "\u0A6C"
        ],
        f: ["\u04FA"],
        g: ["\u0262"],
        h: ["\u0126", "\u0195", "\u04A2", "\u04BA", "\u04C7", "\u050A"],
        i: ["\u0F0F"],
        j: ["\u0134"],
        k: ["\u0138", "\u04A0", "\u04C3", "\u051E"],
        l: ["\u0139"],
        m: ["\u028D", "\u04CD", "\u04CE", "\u0520", "\u0521", "\u0D69"],
        n: ["\xD1", "\u014B", "\u019D", "\u0376", "\u03A0", "\u048A"],
        o: [
          "\xD8",
          "\xF5",
          "\xF8",
          "\u01FE",
          "\u0298",
          "\u047A",
          "\u05DD",
          "\u06DD",
          "\u0E4F"
        ],
        p: ["\u01F7", "\u048E"],
        q: ["\u09CD"],
        r: ["\xAE", "\u01A6", "\u0210", "\u024C", "\u0280", "\u042F"],
        s: ["\xA7", "\u03DE", "\u03DF", "\u03E8"],
        t: ["\u0141", "\u0166", "\u0373"],
        u: ["\u01B1", "\u054D"],
        v: ["\u05D8"],
        w: ["\u0428", "\u0460", "\u047C", "\u0D70"],
        x: ["\u04B2", "\u04FE", "\u04FC", "\u04FD"],
        y: ["\xA5", "\u04B0", "\u04CB"],
        z: ["\u01B5", "\u0240"]
      };
      text.forEach(function(c) {
        c = c.toLowerCase();
        var chars = trap[c] || [" "];
        var rand = Math.floor(Math.random() * chars.length);
        if (typeof trap[c] !== "undefined") {
          result += trap[c][rand];
        } else {
          result += c;
        }
      });
      return result;
    };
  }
});

// node_modules/@colors/colors/lib/custom/zalgo.js
var require_zalgo = __commonJS({
  "node_modules/@colors/colors/lib/custom/zalgo.js"(exports2, module2) {
    module2["exports"] = function zalgo(text, options) {
      text = text || "   he is here   ";
      var soul = {
        "up": [
          "\u030D",
          "\u030E",
          "\u0304",
          "\u0305",
          "\u033F",
          "\u0311",
          "\u0306",
          "\u0310",
          "\u0352",
          "\u0357",
          "\u0351",
          "\u0307",
          "\u0308",
          "\u030A",
          "\u0342",
          "\u0313",
          "\u0308",
          "\u034A",
          "\u034B",
          "\u034C",
          "\u0303",
          "\u0302",
          "\u030C",
          "\u0350",
          "\u0300",
          "\u0301",
          "\u030B",
          "\u030F",
          "\u0312",
          "\u0313",
          "\u0314",
          "\u033D",
          "\u0309",
          "\u0363",
          "\u0364",
          "\u0365",
          "\u0366",
          "\u0367",
          "\u0368",
          "\u0369",
          "\u036A",
          "\u036B",
          "\u036C",
          "\u036D",
          "\u036E",
          "\u036F",
          "\u033E",
          "\u035B",
          "\u0346",
          "\u031A"
        ],
        "down": [
          "\u0316",
          "\u0317",
          "\u0318",
          "\u0319",
          "\u031C",
          "\u031D",
          "\u031E",
          "\u031F",
          "\u0320",
          "\u0324",
          "\u0325",
          "\u0326",
          "\u0329",
          "\u032A",
          "\u032B",
          "\u032C",
          "\u032D",
          "\u032E",
          "\u032F",
          "\u0330",
          "\u0331",
          "\u0332",
          "\u0333",
          "\u0339",
          "\u033A",
          "\u033B",
          "\u033C",
          "\u0345",
          "\u0347",
          "\u0348",
          "\u0349",
          "\u034D",
          "\u034E",
          "\u0353",
          "\u0354",
          "\u0355",
          "\u0356",
          "\u0359",
          "\u035A",
          "\u0323"
        ],
        "mid": [
          "\u0315",
          "\u031B",
          "\u0300",
          "\u0301",
          "\u0358",
          "\u0321",
          "\u0322",
          "\u0327",
          "\u0328",
          "\u0334",
          "\u0335",
          "\u0336",
          "\u035C",
          "\u035D",
          "\u035E",
          "\u035F",
          "\u0360",
          "\u0362",
          "\u0338",
          "\u0337",
          "\u0361",
          " \u0489"
        ]
      };
      var all = [].concat(soul.up, soul.down, soul.mid);
      function randomNumber(range) {
        var r = Math.floor(Math.random() * range);
        return r;
      }
      function isChar(character) {
        var bool = false;
        all.filter(function(i) {
          bool = i === character;
        });
        return bool;
      }
      function heComes(text2, options2) {
        var result = "";
        var counts;
        var l;
        options2 = options2 || {};
        options2["up"] = typeof options2["up"] !== "undefined" ? options2["up"] : true;
        options2["mid"] = typeof options2["mid"] !== "undefined" ? options2["mid"] : true;
        options2["down"] = typeof options2["down"] !== "undefined" ? options2["down"] : true;
        options2["size"] = typeof options2["size"] !== "undefined" ? options2["size"] : "maxi";
        text2 = text2.split("");
        for (l in text2) {
          if (isChar(l)) {
            continue;
          }
          result = result + text2[l];
          counts = { "up": 0, "down": 0, "mid": 0 };
          switch (options2.size) {
            case "mini":
              counts.up = randomNumber(8);
              counts.mid = randomNumber(2);
              counts.down = randomNumber(8);
              break;
            case "maxi":
              counts.up = randomNumber(16) + 3;
              counts.mid = randomNumber(4) + 1;
              counts.down = randomNumber(64) + 3;
              break;
            default:
              counts.up = randomNumber(8) + 1;
              counts.mid = randomNumber(6) / 2;
              counts.down = randomNumber(8) + 1;
              break;
          }
          var arr = ["up", "mid", "down"];
          for (var d in arr) {
            var index = arr[d];
            for (var i = 0; i <= counts[index]; i++) {
              if (options2[index]) {
                result = result + soul[index][randomNumber(soul[index].length)];
              }
            }
          }
        }
        return result;
      }
      return heComes(text, options);
    };
  }
});

// node_modules/@colors/colors/lib/maps/america.js
var require_america = __commonJS({
  "node_modules/@colors/colors/lib/maps/america.js"(exports2, module2) {
    module2["exports"] = function(colors) {
      return function(letter, i, exploded) {
        if (letter === " ") return letter;
        switch (i % 3) {
          case 0:
            return colors.red(letter);
          case 1:
            return colors.white(letter);
          case 2:
            return colors.blue(letter);
        }
      };
    };
  }
});

// node_modules/@colors/colors/lib/maps/zebra.js
var require_zebra = __commonJS({
  "node_modules/@colors/colors/lib/maps/zebra.js"(exports2, module2) {
    module2["exports"] = function(colors) {
      return function(letter, i, exploded) {
        return i % 2 === 0 ? letter : colors.inverse(letter);
      };
    };
  }
});

// node_modules/@colors/colors/lib/maps/rainbow.js
var require_rainbow = __commonJS({
  "node_modules/@colors/colors/lib/maps/rainbow.js"(exports2, module2) {
    module2["exports"] = function(colors) {
      var rainbowColors = ["red", "yellow", "green", "blue", "magenta"];
      return function(letter, i, exploded) {
        if (letter === " ") {
          return letter;
        } else {
          return colors[rainbowColors[i++ % rainbowColors.length]](letter);
        }
      };
    };
  }
});

// node_modules/@colors/colors/lib/maps/random.js
var require_random = __commonJS({
  "node_modules/@colors/colors/lib/maps/random.js"(exports2, module2) {
    module2["exports"] = function(colors) {
      var available = [
        "underline",
        "inverse",
        "grey",
        "yellow",
        "red",
        "green",
        "blue",
        "white",
        "cyan",
        "magenta",
        "brightYellow",
        "brightRed",
        "brightGreen",
        "brightBlue",
        "brightWhite",
        "brightCyan",
        "brightMagenta"
      ];
      return function(letter, i, exploded) {
        return letter === " " ? letter : colors[available[Math.round(Math.random() * (available.length - 2))]](letter);
      };
    };
  }
});

// node_modules/@colors/colors/lib/colors.js
var require_colors = __commonJS({
  "node_modules/@colors/colors/lib/colors.js"(exports2, module2) {
    var colors = {};
    module2["exports"] = colors;
    colors.themes = {};
    var util = require("util");
    var ansiStyles = colors.styles = require_styles();
    var defineProps = Object.defineProperties;
    var newLineRegex = new RegExp(/[\r\n]+/g);
    colors.supportsColor = require_supports_colors().supportsColor;
    if (typeof colors.enabled === "undefined") {
      colors.enabled = colors.supportsColor() !== false;
    }
    colors.enable = function() {
      colors.enabled = true;
    };
    colors.disable = function() {
      colors.enabled = false;
    };
    colors.stripColors = colors.strip = function(str) {
      return ("" + str).replace(/\x1B\[\d+m/g, "");
    };
    var stylize = colors.stylize = function stylize2(str, style) {
      if (!colors.enabled) {
        return str + "";
      }
      var styleMap = ansiStyles[style];
      if (!styleMap && style in colors) {
        return colors[style](str);
      }
      return styleMap.open + str + styleMap.close;
    };
    var matchOperatorsRe = /[|\\{}()[\]^$+*?.]/g;
    var escapeStringRegexp = function(str) {
      if (typeof str !== "string") {
        throw new TypeError("Expected a string");
      }
      return str.replace(matchOperatorsRe, "\\$&");
    };
    function build(_styles) {
      var builder = function builder2() {
        return applyStyle.apply(builder2, arguments);
      };
      builder._styles = _styles;
      builder.__proto__ = proto;
      return builder;
    }
    var styles = function() {
      var ret = {};
      ansiStyles.grey = ansiStyles.gray;
      Object.keys(ansiStyles).forEach(function(key) {
        ansiStyles[key].closeRe = new RegExp(escapeStringRegexp(ansiStyles[key].close), "g");
        ret[key] = {
          get: function() {
            return build(this._styles.concat(key));
          }
        };
      });
      return ret;
    }();
    var proto = defineProps(function colors2() {
    }, styles);
    function applyStyle() {
      var args = Array.prototype.slice.call(arguments);
      var str = args.map(function(arg) {
        if (arg != null && arg.constructor === String) {
          return arg;
        } else {
          return util.inspect(arg);
        }
      }).join(" ");
      if (!colors.enabled || !str) {
        return str;
      }
      var newLinesPresent = str.indexOf("\n") != -1;
      var nestedStyles = this._styles;
      var i = nestedStyles.length;
      while (i--) {
        var code = ansiStyles[nestedStyles[i]];
        str = code.open + str.replace(code.closeRe, code.open) + code.close;
        if (newLinesPresent) {
          str = str.replace(newLineRegex, function(match) {
            return code.close + match + code.open;
          });
        }
      }
      return str;
    }
    colors.setTheme = function(theme) {
      if (typeof theme === "string") {
        console.log("colors.setTheme now only accepts an object, not a string.  If you are trying to set a theme from a file, it is now your (the caller's) responsibility to require the file.  The old syntax looked like colors.setTheme(__dirname + '/../themes/generic-logging.js'); The new syntax looks like colors.setTheme(require(__dirname + '/../themes/generic-logging.js'));");
        return;
      }
      for (var style in theme) {
        (function(style2) {
          colors[style2] = function(str) {
            if (typeof theme[style2] === "object") {
              var out = str;
              for (var i in theme[style2]) {
                out = colors[theme[style2][i]](out);
              }
              return out;
            }
            return colors[theme[style2]](str);
          };
        })(style);
      }
    };
    function init() {
      var ret = {};
      Object.keys(styles).forEach(function(name) {
        ret[name] = {
          get: function() {
            return build([name]);
          }
        };
      });
      return ret;
    }
    var sequencer = function sequencer2(map2, str) {
      var exploded = str.split("");
      exploded = exploded.map(map2);
      return exploded.join("");
    };
    colors.trap = require_trap();
    colors.zalgo = require_zalgo();
    colors.maps = {};
    colors.maps.america = require_america()(colors);
    colors.maps.zebra = require_zebra()(colors);
    colors.maps.rainbow = require_rainbow()(colors);
    colors.maps.random = require_random()(colors);
    for (map in colors.maps) {
      (function(map2) {
        colors[map2] = function(str) {
          return sequencer(colors.maps[map2], str);
        };
      })(map);
    }
    var map;
    defineProps(colors, init());
  }
});

// node_modules/@colors/colors/safe.js
var require_safe = __commonJS({
  "node_modules/@colors/colors/safe.js"(exports2, module2) {
    var colors = require_colors();
    module2["exports"] = colors;
  }
});

// node_modules/triple-beam/config/cli.js
var require_cli = __commonJS({
  "node_modules/triple-beam/config/cli.js"(exports2) {
    "use strict";
    exports2.levels = {
      error: 0,
      warn: 1,
      help: 2,
      data: 3,
      info: 4,
      debug: 5,
      prompt: 6,
      verbose: 7,
      input: 8,
      silly: 9
    };
    exports2.colors = {
      error: "red",
      warn: "yellow",
      help: "cyan",
      data: "grey",
      info: "green",
      debug: "blue",
      prompt: "grey",
      verbose: "cyan",
      input: "grey",
      silly: "magenta"
    };
  }
});

// node_modules/triple-beam/config/npm.js
var require_npm = __commonJS({
  "node_modules/triple-beam/config/npm.js"(exports2) {
    "use strict";
    exports2.levels = {
      error: 0,
      warn: 1,
      info: 2,
      http: 3,
      verbose: 4,
      debug: 5,
      silly: 6
    };
    exports2.colors = {
      error: "red",
      warn: "yellow",
      info: "green",
      http: "green",
      verbose: "cyan",
      debug: "blue",
      silly: "magenta"
    };
  }
});

// node_modules/triple-beam/config/syslog.js
var require_syslog = __commonJS({
  "node_modules/triple-beam/config/syslog.js"(exports2) {
    "use strict";
    exports2.levels = {
      emerg: 0,
      alert: 1,
      crit: 2,
      error: 3,
      warning: 4,
      notice: 5,
      info: 6,
      debug: 7
    };
    exports2.colors = {
      emerg: "red",
      alert: "yellow",
      crit: "red",
      error: "red",
      warning: "red",
      notice: "yellow",
      info: "green",
      debug: "blue"
    };
  }
});

// node_modules/triple-beam/config/index.js
var require_config = __commonJS({
  "node_modules/triple-beam/config/index.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "cli", {
      value: require_cli()
    });
    Object.defineProperty(exports2, "npm", {
      value: require_npm()
    });
    Object.defineProperty(exports2, "syslog", {
      value: require_syslog()
    });
  }
});

// node_modules/triple-beam/index.js
var require_triple_beam = __commonJS({
  "node_modules/triple-beam/index.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "LEVEL", {
      value: Symbol.for("level")
    });
    Object.defineProperty(exports2, "MESSAGE", {
      value: Symbol.for("message")
    });
    Object.defineProperty(exports2, "SPLAT", {
      value: Symbol.for("splat")
    });
    Object.defineProperty(exports2, "configs", {
      value: require_config()
    });
  }
});

// node_modules/logform/colorize.js
var require_colorize = __commonJS({
  "node_modules/logform/colorize.js"(exports2, module2) {
    "use strict";
    var colors = require_safe();
    var { LEVEL, MESSAGE } = require_triple_beam();
    colors.enabled = true;
    var hasSpace = /\s+/;
    var Colorizer = class _Colorizer {
      constructor(opts = {}) {
        if (opts.colors) {
          this.addColors(opts.colors);
        }
        this.options = opts;
      }
      /*
       * Adds the colors Object to the set of allColors
       * known by the Colorizer
       *
       * @param {Object} colors Set of color mappings to add.
       */
      static addColors(clrs) {
        const nextColors = Object.keys(clrs).reduce((acc, level) => {
          acc[level] = hasSpace.test(clrs[level]) ? clrs[level].split(hasSpace) : clrs[level];
          return acc;
        }, {});
        _Colorizer.allColors = Object.assign({}, _Colorizer.allColors || {}, nextColors);
        return _Colorizer.allColors;
      }
      /*
       * Adds the colors Object to the set of allColors
       * known by the Colorizer
       *
       * @param {Object} colors Set of color mappings to add.
       */
      addColors(clrs) {
        return _Colorizer.addColors(clrs);
      }
      /*
       * function colorize (lookup, level, message)
       * Performs multi-step colorization using @colors/colors/safe
       */
      colorize(lookup, level, message) {
        if (typeof message === "undefined") {
          message = level;
        }
        if (!Array.isArray(_Colorizer.allColors[lookup])) {
          return colors[_Colorizer.allColors[lookup]](message);
        }
        for (let i = 0, len = _Colorizer.allColors[lookup].length; i < len; i++) {
          message = colors[_Colorizer.allColors[lookup][i]](message);
        }
        return message;
      }
      /*
       * function transform (info, opts)
       * Attempts to colorize the { level, message } of the given
       * `logform` info object.
       */
      transform(info, opts) {
        if (opts.all && typeof info[MESSAGE] === "string") {
          info[MESSAGE] = this.colorize(info[LEVEL], info.level, info[MESSAGE]);
        }
        if (opts.level || opts.all || !opts.message) {
          info.level = this.colorize(info[LEVEL], info.level);
        }
        if (opts.all || opts.message) {
          info.message = this.colorize(info[LEVEL], info.level, info.message);
        }
        return info;
      }
    };
    module2.exports = (opts) => new Colorizer(opts);
    module2.exports.Colorizer = module2.exports.Format = Colorizer;
  }
});

// node_modules/logform/levels.js
var require_levels = __commonJS({
  "node_modules/logform/levels.js"(exports2, module2) {
    "use strict";
    var { Colorizer } = require_colorize();
    module2.exports = (config) => {
      Colorizer.addColors(config.colors || config);
      return config;
    };
  }
});

// node_modules/logform/align.js
var require_align = __commonJS({
  "node_modules/logform/align.js"(exports2, module2) {
    "use strict";
    var format = require_format();
    module2.exports = format((info) => {
      info.message = `	${info.message}`;
      return info;
    });
  }
});

// node_modules/logform/errors.js
var require_errors = __commonJS({
  "node_modules/logform/errors.js"(exports2, module2) {
    "use strict";
    var format = require_format();
    var { LEVEL, MESSAGE } = require_triple_beam();
    module2.exports = format((einfo, { stack, cause }) => {
      if (einfo instanceof Error) {
        const info = Object.assign({}, einfo, {
          level: einfo.level,
          [LEVEL]: einfo[LEVEL] || einfo.level,
          message: einfo.message,
          [MESSAGE]: einfo[MESSAGE] || einfo.message
        });
        if (stack) info.stack = einfo.stack;
        if (cause) info.cause = einfo.cause;
        return info;
      }
      if (!(einfo.message instanceof Error)) return einfo;
      const err = einfo.message;
      Object.assign(einfo, err);
      einfo.message = err.message;
      einfo[MESSAGE] = err.message;
      if (stack) einfo.stack = err.stack;
      if (cause) einfo.cause = err.cause;
      return einfo;
    });
  }
});

// node_modules/logform/pad-levels.js
var require_pad_levels = __commonJS({
  "node_modules/logform/pad-levels.js"(exports2, module2) {
    "use strict";
    var { configs, LEVEL, MESSAGE } = require_triple_beam();
    var Padder = class _Padder {
      constructor(opts = { levels: configs.npm.levels }) {
        this.paddings = _Padder.paddingForLevels(opts.levels, opts.filler);
        this.options = opts;
      }
      /**
       * Returns the maximum length of keys in the specified `levels` Object.
       * @param  {Object} levels Set of all levels to calculate longest level against.
       * @returns {Number} Maximum length of the longest level string.
       */
      static getLongestLevel(levels) {
        const lvls = Object.keys(levels).map((level) => level.length);
        return Math.max(...lvls);
      }
      /**
       * Returns the padding for the specified `level` assuming that the
       * maximum length of all levels it's associated with is `maxLength`.
       * @param  {String} level Level to calculate padding for.
       * @param  {String} filler Repeatable text to use for padding.
       * @param  {Number} maxLength Length of the longest level
       * @returns {String} Padding string for the `level`
       */
      static paddingForLevel(level, filler, maxLength) {
        const targetLen = maxLength + 1 - level.length;
        const rep = Math.floor(targetLen / filler.length);
        const padding = `${filler}${filler.repeat(rep)}`;
        return padding.slice(0, targetLen);
      }
      /**
       * Returns an object with the string paddings for the given `levels`
       * using the specified `filler`.
       * @param  {Object} levels Set of all levels to calculate padding for.
       * @param  {String} filler Repeatable text to use for padding.
       * @returns {Object} Mapping of level to desired padding.
       */
      static paddingForLevels(levels, filler = " ") {
        const maxLength = _Padder.getLongestLevel(levels);
        return Object.keys(levels).reduce((acc, level) => {
          acc[level] = _Padder.paddingForLevel(level, filler, maxLength);
          return acc;
        }, {});
      }
      /**
       * Prepends the padding onto the `message` based on the `LEVEL` of
       * the `info`. This is based on the behavior of `winston@2` which also
       * prepended the level onto the message.
       *
       * See: https://github.com/winstonjs/winston/blob/2.x/lib/winston/logger.js#L198-L201
       *
       * @param  {Info} info Logform info object
       * @param  {Object} opts Options passed along to this instance.
       * @returns {Info} Modified logform info object.
       */
      transform(info, opts) {
        info.message = `${this.paddings[info[LEVEL]]}${info.message}`;
        if (info[MESSAGE]) {
          info[MESSAGE] = `${this.paddings[info[LEVEL]]}${info[MESSAGE]}`;
        }
        return info;
      }
    };
    module2.exports = (opts) => new Padder(opts);
    module2.exports.Padder = module2.exports.Format = Padder;
  }
});

// node_modules/logform/cli.js
var require_cli2 = __commonJS({
  "node_modules/logform/cli.js"(exports2, module2) {
    "use strict";
    var { Colorizer } = require_colorize();
    var { Padder } = require_pad_levels();
    var { configs, MESSAGE } = require_triple_beam();
    var CliFormat = class {
      constructor(opts = {}) {
        if (!opts.levels) {
          opts.levels = configs.cli.levels;
        }
        this.colorizer = new Colorizer(opts);
        this.padder = new Padder(opts);
        this.options = opts;
      }
      /*
       * function transform (info, opts)
       * Attempts to both:
       * 1. Pad the { level }
       * 2. Colorize the { level, message }
       * of the given `logform` info object depending on the `opts`.
       */
      transform(info, opts) {
        this.colorizer.transform(
          this.padder.transform(info, opts),
          opts
        );
        info[MESSAGE] = `${info.level}:${info.message}`;
        return info;
      }
    };
    module2.exports = (opts) => new CliFormat(opts);
    module2.exports.Format = CliFormat;
  }
});

// node_modules/logform/combine.js
var require_combine = __commonJS({
  "node_modules/logform/combine.js"(exports2, module2) {
    "use strict";
    var format = require_format();
    function cascade(formats) {
      if (!formats.every(isValidFormat)) {
        return;
      }
      return (info) => {
        let obj = info;
        for (let i = 0; i < formats.length; i++) {
          obj = formats[i].transform(obj, formats[i].options);
          if (!obj) {
            return false;
          }
        }
        return obj;
      };
    }
    function isValidFormat(fmt) {
      if (typeof fmt.transform !== "function") {
        throw new Error([
          "No transform function found on format. Did you create a format instance?",
          "const myFormat = format(formatFn);",
          "const instance = myFormat();"
        ].join("\n"));
      }
      return true;
    }
    module2.exports = (...formats) => {
      const combinedFormat = format(cascade(formats));
      const instance = combinedFormat();
      instance.Format = combinedFormat.Format;
      return instance;
    };
    module2.exports.cascade = cascade;
  }
});

// node_modules/safe-stable-stringify/index.js
var require_safe_stable_stringify = __commonJS({
  "node_modules/safe-stable-stringify/index.js"(exports2, module2) {
    "use strict";
    var { hasOwnProperty } = Object.prototype;
    var stringify2 = configure();
    stringify2.configure = configure;
    stringify2.stringify = stringify2;
    stringify2.default = stringify2;
    exports2.stringify = stringify2;
    exports2.configure = configure;
    module2.exports = stringify2;
    var strEscapeSequencesRegExp = /[\u0000-\u001f\u0022\u005c\ud800-\udfff]/;
    function strEscape(str) {
      if (str.length < 5e3 && !strEscapeSequencesRegExp.test(str)) {
        return `"${str}"`;
      }
      return JSON.stringify(str);
    }
    function sort(array, comparator) {
      if (array.length > 200 || comparator) {
        return array.sort(comparator);
      }
      for (let i = 1; i < array.length; i++) {
        const currentValue = array[i];
        let position = i;
        while (position !== 0 && array[position - 1] > currentValue) {
          array[position] = array[position - 1];
          position--;
        }
        array[position] = currentValue;
      }
      return array;
    }
    var typedArrayPrototypeGetSymbolToStringTag = Object.getOwnPropertyDescriptor(
      Object.getPrototypeOf(
        Object.getPrototypeOf(
          new Int8Array()
        )
      ),
      Symbol.toStringTag
    ).get;
    function isTypedArrayWithEntries(value) {
      return typedArrayPrototypeGetSymbolToStringTag.call(value) !== void 0 && value.length !== 0;
    }
    function stringifyTypedArray(array, separator, maximumBreadth) {
      if (array.length < maximumBreadth) {
        maximumBreadth = array.length;
      }
      const whitespace = separator === "," ? "" : " ";
      let res = `"0":${whitespace}${array[0]}`;
      for (let i = 1; i < maximumBreadth; i++) {
        res += `${separator}"${i}":${whitespace}${array[i]}`;
      }
      return res;
    }
    function getCircularValueOption(options) {
      if (hasOwnProperty.call(options, "circularValue")) {
        const circularValue = options.circularValue;
        if (typeof circularValue === "string") {
          return `"${circularValue}"`;
        }
        if (circularValue == null) {
          return circularValue;
        }
        if (circularValue === Error || circularValue === TypeError) {
          return {
            toString() {
              throw new TypeError("Converting circular structure to JSON");
            }
          };
        }
        throw new TypeError('The "circularValue" argument must be of type string or the value null or undefined');
      }
      return '"[Circular]"';
    }
    function getDeterministicOption(options) {
      let value;
      if (hasOwnProperty.call(options, "deterministic")) {
        value = options.deterministic;
        if (typeof value !== "boolean" && typeof value !== "function") {
          throw new TypeError('The "deterministic" argument must be of type boolean or comparator function');
        }
      }
      return value === void 0 ? true : value;
    }
    function getBooleanOption(options, key) {
      let value;
      if (hasOwnProperty.call(options, key)) {
        value = options[key];
        if (typeof value !== "boolean") {
          throw new TypeError(`The "${key}" argument must be of type boolean`);
        }
      }
      return value === void 0 ? true : value;
    }
    function getPositiveIntegerOption(options, key) {
      let value;
      if (hasOwnProperty.call(options, key)) {
        value = options[key];
        if (typeof value !== "number") {
          throw new TypeError(`The "${key}" argument must be of type number`);
        }
        if (!Number.isInteger(value)) {
          throw new TypeError(`The "${key}" argument must be an integer`);
        }
        if (value < 1) {
          throw new RangeError(`The "${key}" argument must be >= 1`);
        }
      }
      return value === void 0 ? Infinity : value;
    }
    function getItemCount(number) {
      if (number === 1) {
        return "1 item";
      }
      return `${number} items`;
    }
    function getUniqueReplacerSet(replacerArray) {
      const replacerSet = /* @__PURE__ */ new Set();
      for (const value of replacerArray) {
        if (typeof value === "string" || typeof value === "number") {
          replacerSet.add(String(value));
        }
      }
      return replacerSet;
    }
    function getStrictOption(options) {
      if (hasOwnProperty.call(options, "strict")) {
        const value = options.strict;
        if (typeof value !== "boolean") {
          throw new TypeError('The "strict" argument must be of type boolean');
        }
        if (value) {
          return (value2) => {
            let message = `Object can not safely be stringified. Received type ${typeof value2}`;
            if (typeof value2 !== "function") message += ` (${value2.toString()})`;
            throw new Error(message);
          };
        }
      }
    }
    function configure(options) {
      options = { ...options };
      const fail = getStrictOption(options);
      if (fail) {
        if (options.bigint === void 0) {
          options.bigint = false;
        }
        if (!("circularValue" in options)) {
          options.circularValue = Error;
        }
      }
      const circularValue = getCircularValueOption(options);
      const bigint = getBooleanOption(options, "bigint");
      const deterministic = getDeterministicOption(options);
      const comparator = typeof deterministic === "function" ? deterministic : void 0;
      const maximumDepth = getPositiveIntegerOption(options, "maximumDepth");
      const maximumBreadth = getPositiveIntegerOption(options, "maximumBreadth");
      function stringifyFnReplacer(key, parent, stack, replacer, spacer, indentation) {
        let value = parent[key];
        if (typeof value === "object" && value !== null && typeof value.toJSON === "function") {
          value = value.toJSON(key);
        }
        value = replacer.call(parent, key, value);
        switch (typeof value) {
          case "string":
            return strEscape(value);
          case "object": {
            if (value === null) {
              return "null";
            }
            if (stack.indexOf(value) !== -1) {
              return circularValue;
            }
            let res = "";
            let join = ",";
            const originalIndentation = indentation;
            if (Array.isArray(value)) {
              if (value.length === 0) {
                return "[]";
              }
              if (maximumDepth < stack.length + 1) {
                return '"[Array]"';
              }
              stack.push(value);
              if (spacer !== "") {
                indentation += spacer;
                res += `
${indentation}`;
                join = `,
${indentation}`;
              }
              const maximumValuesToStringify = Math.min(value.length, maximumBreadth);
              let i = 0;
              for (; i < maximumValuesToStringify - 1; i++) {
                const tmp2 = stringifyFnReplacer(String(i), value, stack, replacer, spacer, indentation);
                res += tmp2 !== void 0 ? tmp2 : "null";
                res += join;
              }
              const tmp = stringifyFnReplacer(String(i), value, stack, replacer, spacer, indentation);
              res += tmp !== void 0 ? tmp : "null";
              if (value.length - 1 > maximumBreadth) {
                const removedKeys = value.length - maximumBreadth - 1;
                res += `${join}"... ${getItemCount(removedKeys)} not stringified"`;
              }
              if (spacer !== "") {
                res += `
${originalIndentation}`;
              }
              stack.pop();
              return `[${res}]`;
            }
            let keys = Object.keys(value);
            const keyLength = keys.length;
            if (keyLength === 0) {
              return "{}";
            }
            if (maximumDepth < stack.length + 1) {
              return '"[Object]"';
            }
            let whitespace = "";
            let separator = "";
            if (spacer !== "") {
              indentation += spacer;
              join = `,
${indentation}`;
              whitespace = " ";
            }
            const maximumPropertiesToStringify = Math.min(keyLength, maximumBreadth);
            if (deterministic && !isTypedArrayWithEntries(value)) {
              keys = sort(keys, comparator);
            }
            stack.push(value);
            for (let i = 0; i < maximumPropertiesToStringify; i++) {
              const key2 = keys[i];
              const tmp = stringifyFnReplacer(key2, value, stack, replacer, spacer, indentation);
              if (tmp !== void 0) {
                res += `${separator}${strEscape(key2)}:${whitespace}${tmp}`;
                separator = join;
              }
            }
            if (keyLength > maximumBreadth) {
              const removedKeys = keyLength - maximumBreadth;
              res += `${separator}"...":${whitespace}"${getItemCount(removedKeys)} not stringified"`;
              separator = join;
            }
            if (spacer !== "" && separator.length > 1) {
              res = `
${indentation}${res}
${originalIndentation}`;
            }
            stack.pop();
            return `{${res}}`;
          }
          case "number":
            return isFinite(value) ? String(value) : fail ? fail(value) : "null";
          case "boolean":
            return value === true ? "true" : "false";
          case "undefined":
            return void 0;
          case "bigint":
            if (bigint) {
              return String(value);
            }
          // fallthrough
          default:
            return fail ? fail(value) : void 0;
        }
      }
      function stringifyArrayReplacer(key, value, stack, replacer, spacer, indentation) {
        if (typeof value === "object" && value !== null && typeof value.toJSON === "function") {
          value = value.toJSON(key);
        }
        switch (typeof value) {
          case "string":
            return strEscape(value);
          case "object": {
            if (value === null) {
              return "null";
            }
            if (stack.indexOf(value) !== -1) {
              return circularValue;
            }
            const originalIndentation = indentation;
            let res = "";
            let join = ",";
            if (Array.isArray(value)) {
              if (value.length === 0) {
                return "[]";
              }
              if (maximumDepth < stack.length + 1) {
                return '"[Array]"';
              }
              stack.push(value);
              if (spacer !== "") {
                indentation += spacer;
                res += `
${indentation}`;
                join = `,
${indentation}`;
              }
              const maximumValuesToStringify = Math.min(value.length, maximumBreadth);
              let i = 0;
              for (; i < maximumValuesToStringify - 1; i++) {
                const tmp2 = stringifyArrayReplacer(String(i), value[i], stack, replacer, spacer, indentation);
                res += tmp2 !== void 0 ? tmp2 : "null";
                res += join;
              }
              const tmp = stringifyArrayReplacer(String(i), value[i], stack, replacer, spacer, indentation);
              res += tmp !== void 0 ? tmp : "null";
              if (value.length - 1 > maximumBreadth) {
                const removedKeys = value.length - maximumBreadth - 1;
                res += `${join}"... ${getItemCount(removedKeys)} not stringified"`;
              }
              if (spacer !== "") {
                res += `
${originalIndentation}`;
              }
              stack.pop();
              return `[${res}]`;
            }
            stack.push(value);
            let whitespace = "";
            if (spacer !== "") {
              indentation += spacer;
              join = `,
${indentation}`;
              whitespace = " ";
            }
            let separator = "";
            for (const key2 of replacer) {
              const tmp = stringifyArrayReplacer(key2, value[key2], stack, replacer, spacer, indentation);
              if (tmp !== void 0) {
                res += `${separator}${strEscape(key2)}:${whitespace}${tmp}`;
                separator = join;
              }
            }
            if (spacer !== "" && separator.length > 1) {
              res = `
${indentation}${res}
${originalIndentation}`;
            }
            stack.pop();
            return `{${res}}`;
          }
          case "number":
            return isFinite(value) ? String(value) : fail ? fail(value) : "null";
          case "boolean":
            return value === true ? "true" : "false";
          case "undefined":
            return void 0;
          case "bigint":
            if (bigint) {
              return String(value);
            }
          // fallthrough
          default:
            return fail ? fail(value) : void 0;
        }
      }
      function stringifyIndent(key, value, stack, spacer, indentation) {
        switch (typeof value) {
          case "string":
            return strEscape(value);
          case "object": {
            if (value === null) {
              return "null";
            }
            if (typeof value.toJSON === "function") {
              value = value.toJSON(key);
              if (typeof value !== "object") {
                return stringifyIndent(key, value, stack, spacer, indentation);
              }
              if (value === null) {
                return "null";
              }
            }
            if (stack.indexOf(value) !== -1) {
              return circularValue;
            }
            const originalIndentation = indentation;
            if (Array.isArray(value)) {
              if (value.length === 0) {
                return "[]";
              }
              if (maximumDepth < stack.length + 1) {
                return '"[Array]"';
              }
              stack.push(value);
              indentation += spacer;
              let res2 = `
${indentation}`;
              const join2 = `,
${indentation}`;
              const maximumValuesToStringify = Math.min(value.length, maximumBreadth);
              let i = 0;
              for (; i < maximumValuesToStringify - 1; i++) {
                const tmp2 = stringifyIndent(String(i), value[i], stack, spacer, indentation);
                res2 += tmp2 !== void 0 ? tmp2 : "null";
                res2 += join2;
              }
              const tmp = stringifyIndent(String(i), value[i], stack, spacer, indentation);
              res2 += tmp !== void 0 ? tmp : "null";
              if (value.length - 1 > maximumBreadth) {
                const removedKeys = value.length - maximumBreadth - 1;
                res2 += `${join2}"... ${getItemCount(removedKeys)} not stringified"`;
              }
              res2 += `
${originalIndentation}`;
              stack.pop();
              return `[${res2}]`;
            }
            let keys = Object.keys(value);
            const keyLength = keys.length;
            if (keyLength === 0) {
              return "{}";
            }
            if (maximumDepth < stack.length + 1) {
              return '"[Object]"';
            }
            indentation += spacer;
            const join = `,
${indentation}`;
            let res = "";
            let separator = "";
            let maximumPropertiesToStringify = Math.min(keyLength, maximumBreadth);
            if (isTypedArrayWithEntries(value)) {
              res += stringifyTypedArray(value, join, maximumBreadth);
              keys = keys.slice(value.length);
              maximumPropertiesToStringify -= value.length;
              separator = join;
            }
            if (deterministic) {
              keys = sort(keys, comparator);
            }
            stack.push(value);
            for (let i = 0; i < maximumPropertiesToStringify; i++) {
              const key2 = keys[i];
              const tmp = stringifyIndent(key2, value[key2], stack, spacer, indentation);
              if (tmp !== void 0) {
                res += `${separator}${strEscape(key2)}: ${tmp}`;
                separator = join;
              }
            }
            if (keyLength > maximumBreadth) {
              const removedKeys = keyLength - maximumBreadth;
              res += `${separator}"...": "${getItemCount(removedKeys)} not stringified"`;
              separator = join;
            }
            if (separator !== "") {
              res = `
${indentation}${res}
${originalIndentation}`;
            }
            stack.pop();
            return `{${res}}`;
          }
          case "number":
            return isFinite(value) ? String(value) : fail ? fail(value) : "null";
          case "boolean":
            return value === true ? "true" : "false";
          case "undefined":
            return void 0;
          case "bigint":
            if (bigint) {
              return String(value);
            }
          // fallthrough
          default:
            return fail ? fail(value) : void 0;
        }
      }
      function stringifySimple(key, value, stack) {
        switch (typeof value) {
          case "string":
            return strEscape(value);
          case "object": {
            if (value === null) {
              return "null";
            }
            if (typeof value.toJSON === "function") {
              value = value.toJSON(key);
              if (typeof value !== "object") {
                return stringifySimple(key, value, stack);
              }
              if (value === null) {
                return "null";
              }
            }
            if (stack.indexOf(value) !== -1) {
              return circularValue;
            }
            let res = "";
            const hasLength = value.length !== void 0;
            if (hasLength && Array.isArray(value)) {
              if (value.length === 0) {
                return "[]";
              }
              if (maximumDepth < stack.length + 1) {
                return '"[Array]"';
              }
              stack.push(value);
              const maximumValuesToStringify = Math.min(value.length, maximumBreadth);
              let i = 0;
              for (; i < maximumValuesToStringify - 1; i++) {
                const tmp2 = stringifySimple(String(i), value[i], stack);
                res += tmp2 !== void 0 ? tmp2 : "null";
                res += ",";
              }
              const tmp = stringifySimple(String(i), value[i], stack);
              res += tmp !== void 0 ? tmp : "null";
              if (value.length - 1 > maximumBreadth) {
                const removedKeys = value.length - maximumBreadth - 1;
                res += `,"... ${getItemCount(removedKeys)} not stringified"`;
              }
              stack.pop();
              return `[${res}]`;
            }
            let keys = Object.keys(value);
            const keyLength = keys.length;
            if (keyLength === 0) {
              return "{}";
            }
            if (maximumDepth < stack.length + 1) {
              return '"[Object]"';
            }
            let separator = "";
            let maximumPropertiesToStringify = Math.min(keyLength, maximumBreadth);
            if (hasLength && isTypedArrayWithEntries(value)) {
              res += stringifyTypedArray(value, ",", maximumBreadth);
              keys = keys.slice(value.length);
              maximumPropertiesToStringify -= value.length;
              separator = ",";
            }
            if (deterministic) {
              keys = sort(keys, comparator);
            }
            stack.push(value);
            for (let i = 0; i < maximumPropertiesToStringify; i++) {
              const key2 = keys[i];
              const tmp = stringifySimple(key2, value[key2], stack);
              if (tmp !== void 0) {
                res += `${separator}${strEscape(key2)}:${tmp}`;
                separator = ",";
              }
            }
            if (keyLength > maximumBreadth) {
              const removedKeys = keyLength - maximumBreadth;
              res += `${separator}"...":"${getItemCount(removedKeys)} not stringified"`;
            }
            stack.pop();
            return `{${res}}`;
          }
          case "number":
            return isFinite(value) ? String(value) : fail ? fail(value) : "null";
          case "boolean":
            return value === true ? "true" : "false";
          case "undefined":
            return void 0;
          case "bigint":
            if (bigint) {
              return String(value);
            }
          // fallthrough
          default:
            return fail ? fail(value) : void 0;
        }
      }
      function stringify3(value, replacer, space) {
        if (arguments.length > 1) {
          let spacer = "";
          if (typeof space === "number") {
            spacer = " ".repeat(Math.min(space, 10));
          } else if (typeof space === "string") {
            spacer = space.slice(0, 10);
          }
          if (replacer != null) {
            if (typeof replacer === "function") {
              return stringifyFnReplacer("", { "": value }, [], replacer, spacer, "");
            }
            if (Array.isArray(replacer)) {
              return stringifyArrayReplacer("", value, [], getUniqueReplacerSet(replacer), spacer, "");
            }
          }
          if (spacer.length !== 0) {
            return stringifyIndent("", value, [], spacer, "");
          }
        }
        return stringifySimple("", value, []);
      }
      return stringify3;
    }
  }
});

// node_modules/logform/json.js
var require_json = __commonJS({
  "node_modules/logform/json.js"(exports2, module2) {
    "use strict";
    var format = require_format();
    var { MESSAGE } = require_triple_beam();
    var stringify2 = require_safe_stable_stringify();
    function replacer(key, value) {
      if (typeof value === "bigint")
        return value.toString();
      return value;
    }
    module2.exports = format((info, opts) => {
      const jsonStringify = stringify2.configure(opts);
      info[MESSAGE] = jsonStringify(info, opts.replacer || replacer, opts.space);
      return info;
    });
  }
});

// node_modules/logform/label.js
var require_label = __commonJS({
  "node_modules/logform/label.js"(exports2, module2) {
    "use strict";
    var format = require_format();
    module2.exports = format((info, opts) => {
      if (opts.message) {
        info.message = `[${opts.label}] ${info.message}`;
        return info;
      }
      info.label = opts.label;
      return info;
    });
  }
});

// node_modules/logform/logstash.js
var require_logstash = __commonJS({
  "node_modules/logform/logstash.js"(exports2, module2) {
    "use strict";
    var format = require_format();
    var { MESSAGE } = require_triple_beam();
    var jsonStringify = require_safe_stable_stringify();
    module2.exports = format((info) => {
      const logstash = {};
      if (info.message) {
        logstash["@message"] = info.message;
        delete info.message;
      }
      if (info.timestamp) {
        logstash["@timestamp"] = info.timestamp;
        delete info.timestamp;
      }
      logstash["@fields"] = info;
      info[MESSAGE] = jsonStringify(logstash);
      return info;
    });
  }
});

// node_modules/logform/metadata.js
var require_metadata = __commonJS({
  "node_modules/logform/metadata.js"(exports2, module2) {
    "use strict";
    var format = require_format();
    function fillExcept(info, fillExceptKeys, metadataKey) {
      const savedKeys = fillExceptKeys.reduce((acc, key) => {
        acc[key] = info[key];
        delete info[key];
        return acc;
      }, {});
      const metadata = Object.keys(info).reduce((acc, key) => {
        acc[key] = info[key];
        delete info[key];
        return acc;
      }, {});
      Object.assign(info, savedKeys, {
        [metadataKey]: metadata
      });
      return info;
    }
    function fillWith(info, fillWithKeys, metadataKey) {
      info[metadataKey] = fillWithKeys.reduce((acc, key) => {
        acc[key] = info[key];
        delete info[key];
        return acc;
      }, {});
      return info;
    }
    module2.exports = format((info, opts = {}) => {
      let metadataKey = "metadata";
      if (opts.key) {
        metadataKey = opts.key;
      }
      let fillExceptKeys = [];
      if (!opts.fillExcept && !opts.fillWith) {
        fillExceptKeys.push("level");
        fillExceptKeys.push("message");
      }
      if (opts.fillExcept) {
        fillExceptKeys = opts.fillExcept;
      }
      if (fillExceptKeys.length > 0) {
        return fillExcept(info, fillExceptKeys, metadataKey);
      }
      if (opts.fillWith) {
        return fillWith(info, opts.fillWith, metadataKey);
      }
      return info;
    });
  }
});

// node_modules/logform/node_modules/ms/index.js
var require_ms = __commonJS({
  "node_modules/logform/node_modules/ms/index.js"(exports2, module2) {
    var s = 1e3;
    var m = s * 60;
    var h = m * 60;
    var d = h * 24;
    var w = d * 7;
    var y = d * 365.25;
    module2.exports = function(val, options) {
      options = options || {};
      var type = typeof val;
      if (type === "string" && val.length > 0) {
        return parse2(val);
      } else if (type === "number" && isFinite(val)) {
        return options.long ? fmtLong(val) : fmtShort(val);
      }
      throw new Error(
        "val is not a non-empty string or a valid number. val=" + JSON.stringify(val)
      );
    };
    function parse2(str) {
      str = String(str);
      if (str.length > 100) {
        return;
      }
      var match = /^(-?(?:\d+)?\.?\d+) *(milliseconds?|msecs?|ms|seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|days?|d|weeks?|w|years?|yrs?|y)?$/i.exec(
        str
      );
      if (!match) {
        return;
      }
      var n = parseFloat(match[1]);
      var type = (match[2] || "ms").toLowerCase();
      switch (type) {
        case "years":
        case "year":
        case "yrs":
        case "yr":
        case "y":
          return n * y;
        case "weeks":
        case "week":
        case "w":
          return n * w;
        case "days":
        case "day":
        case "d":
          return n * d;
        case "hours":
        case "hour":
        case "hrs":
        case "hr":
        case "h":
          return n * h;
        case "minutes":
        case "minute":
        case "mins":
        case "min":
        case "m":
          return n * m;
        case "seconds":
        case "second":
        case "secs":
        case "sec":
        case "s":
          return n * s;
        case "milliseconds":
        case "millisecond":
        case "msecs":
        case "msec":
        case "ms":
          return n;
        default:
          return void 0;
      }
    }
    function fmtShort(ms) {
      var msAbs = Math.abs(ms);
      if (msAbs >= d) {
        return Math.round(ms / d) + "d";
      }
      if (msAbs >= h) {
        return Math.round(ms / h) + "h";
      }
      if (msAbs >= m) {
        return Math.round(ms / m) + "m";
      }
      if (msAbs >= s) {
        return Math.round(ms / s) + "s";
      }
      return ms + "ms";
    }
    function fmtLong(ms) {
      var msAbs = Math.abs(ms);
      if (msAbs >= d) {
        return plural(ms, msAbs, d, "day");
      }
      if (msAbs >= h) {
        return plural(ms, msAbs, h, "hour");
      }
      if (msAbs >= m) {
        return plural(ms, msAbs, m, "minute");
      }
      if (msAbs >= s) {
        return plural(ms, msAbs, s, "second");
      }
      return ms + " ms";
    }
    function plural(ms, msAbs, n, name) {
      var isPlural = msAbs >= n * 1.5;
      return Math.round(ms / n) + " " + name + (isPlural ? "s" : "");
    }
  }
});

// node_modules/logform/ms.js
var require_ms2 = __commonJS({
  "node_modules/logform/ms.js"(exports2, module2) {
    "use strict";
    var format = require_format();
    var ms = require_ms();
    module2.exports = format((info) => {
      const curr = +/* @__PURE__ */ new Date();
      exports2.diff = curr - (exports2.prevTime || curr);
      exports2.prevTime = curr;
      info.ms = `+${ms(exports2.diff)}`;
      return info;
    });
  }
});

// node_modules/logform/pretty-print.js
var require_pretty_print = __commonJS({
  "node_modules/logform/pretty-print.js"(exports2, module2) {
    "use strict";
    var inspect = require("util").inspect;
    var format = require_format();
    var { LEVEL, MESSAGE, SPLAT } = require_triple_beam();
    module2.exports = format((info, opts = {}) => {
      const stripped = Object.assign({}, info);
      delete stripped[LEVEL];
      delete stripped[MESSAGE];
      delete stripped[SPLAT];
      info[MESSAGE] = inspect(stripped, false, opts.depth || null, opts.colorize);
      return info;
    });
  }
});

// node_modules/logform/printf.js
var require_printf = __commonJS({
  "node_modules/logform/printf.js"(exports2, module2) {
    "use strict";
    var { MESSAGE } = require_triple_beam();
    var Printf = class {
      constructor(templateFn) {
        this.template = templateFn;
      }
      transform(info) {
        info[MESSAGE] = this.template(info);
        return info;
      }
    };
    module2.exports = (opts) => new Printf(opts);
    module2.exports.Printf = module2.exports.Format = Printf;
  }
});

// node_modules/logform/simple.js
var require_simple = __commonJS({
  "node_modules/logform/simple.js"(exports2, module2) {
    "use strict";
    var format = require_format();
    var { MESSAGE } = require_triple_beam();
    var jsonStringify = require_safe_stable_stringify();
    module2.exports = format((info) => {
      const stringifiedRest = jsonStringify(Object.assign({}, info, {
        level: void 0,
        message: void 0,
        splat: void 0
      }));
      const padding = info.padding && info.padding[info.level] || "";
      if (stringifiedRest !== "{}") {
        info[MESSAGE] = `${info.level}:${padding} ${info.message} ${stringifiedRest}`;
      } else {
        info[MESSAGE] = `${info.level}:${padding} ${info.message}`;
      }
      return info;
    });
  }
});

// node_modules/logform/splat.js
var require_splat = __commonJS({
  "node_modules/logform/splat.js"(exports2, module2) {
    "use strict";
    var util = require("util");
    var { SPLAT } = require_triple_beam();
    var formatRegExp = /%[scdjifoO%]/g;
    var escapedPercent = /%%/g;
    var Splatter = class {
      constructor(opts) {
        this.options = opts;
      }
      /**
         * Check to see if tokens <= splat.length, assign { splat, meta } into the
         * `info` accordingly, and write to this instance.
         *
         * @param  {Info} info Logform info message.
         * @param  {String[]} tokens Set of string interpolation tokens.
         * @returns {Info} Modified info message
         * @private
         */
      _splat(info, tokens) {
        const msg = info.message;
        const splat = info[SPLAT] || info.splat || [];
        const percents = msg.match(escapedPercent);
        const escapes = percents && percents.length || 0;
        const expectedSplat = tokens.length - escapes;
        const extraSplat = expectedSplat - splat.length;
        const metas = extraSplat < 0 ? splat.splice(extraSplat, -1 * extraSplat) : [];
        const metalen = metas.length;
        if (metalen) {
          for (let i = 0; i < metalen; i++) {
            Object.assign(info, metas[i]);
          }
        }
        info.message = util.format(msg, ...splat);
        return info;
      }
      /**
        * Transforms the `info` message by using `util.format` to complete
        * any `info.message` provided it has string interpolation tokens.
        * If no tokens exist then `info` is immutable.
        *
        * @param  {Info} info Logform info message.
        * @param  {Object} opts Options for this instance.
        * @returns {Info} Modified info message
        */
      transform(info) {
        const msg = info.message;
        const splat = info[SPLAT] || info.splat;
        if (!splat || !splat.length) {
          return info;
        }
        const tokens = msg && msg.match && msg.match(formatRegExp);
        if (!tokens && (splat || splat.length)) {
          const metas = splat.length > 1 ? splat.splice(0) : splat;
          const metalen = metas.length;
          if (metalen) {
            for (let i = 0; i < metalen; i++) {
              Object.assign(info, metas[i]);
            }
          }
          return info;
        }
        if (tokens) {
          return this._splat(info, tokens);
        }
        return info;
      }
    };
    module2.exports = (opts) => new Splatter(opts);
  }
});

// node_modules/fecha/lib/fecha.umd.js
var require_fecha_umd = __commonJS({
  "node_modules/fecha/lib/fecha.umd.js"(exports2, module2) {
    (function(global2, factory) {
      typeof exports2 === "object" && typeof module2 !== "undefined" ? factory(exports2) : typeof define === "function" && define.amd ? define(["exports"], factory) : factory(global2.fecha = {});
    })(exports2, function(exports3) {
      "use strict";
      var token = /d{1,4}|M{1,4}|YY(?:YY)?|S{1,3}|Do|ZZ|Z|([HhMsDm])\1?|[aA]|"[^"]*"|'[^']*'/g;
      var twoDigitsOptional = "\\d\\d?";
      var twoDigits = "\\d\\d";
      var threeDigits = "\\d{3}";
      var fourDigits = "\\d{4}";
      var word = "[^\\s]+";
      var literal = /\[([^]*?)\]/gm;
      function shorten(arr, sLen) {
        var newArr = [];
        for (var i = 0, len = arr.length; i < len; i++) {
          newArr.push(arr[i].substr(0, sLen));
        }
        return newArr;
      }
      var monthUpdate = function(arrName) {
        return function(v, i18n) {
          var lowerCaseArr = i18n[arrName].map(function(v2) {
            return v2.toLowerCase();
          });
          var index = lowerCaseArr.indexOf(v.toLowerCase());
          if (index > -1) {
            return index;
          }
          return null;
        };
      };
      function assign(origObj) {
        var args = [];
        for (var _i = 1; _i < arguments.length; _i++) {
          args[_i - 1] = arguments[_i];
        }
        for (var _a = 0, args_1 = args; _a < args_1.length; _a++) {
          var obj = args_1[_a];
          for (var key in obj) {
            origObj[key] = obj[key];
          }
        }
        return origObj;
      }
      var dayNames = [
        "Sunday",
        "Monday",
        "Tuesday",
        "Wednesday",
        "Thursday",
        "Friday",
        "Saturday"
      ];
      var monthNames = [
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"
      ];
      var monthNamesShort = shorten(monthNames, 3);
      var dayNamesShort = shorten(dayNames, 3);
      var defaultI18n = {
        dayNamesShort,
        dayNames,
        monthNamesShort,
        monthNames,
        amPm: ["am", "pm"],
        DoFn: function(dayOfMonth) {
          return dayOfMonth + ["th", "st", "nd", "rd"][dayOfMonth % 10 > 3 ? 0 : (dayOfMonth - dayOfMonth % 10 !== 10 ? 1 : 0) * dayOfMonth % 10];
        }
      };
      var globalI18n = assign({}, defaultI18n);
      var setGlobalDateI18n = function(i18n) {
        return globalI18n = assign(globalI18n, i18n);
      };
      var regexEscape = function(str) {
        return str.replace(/[|\\{()[^$+*?.-]/g, "\\$&");
      };
      var pad = function(val, len) {
        if (len === void 0) {
          len = 2;
        }
        val = String(val);
        while (val.length < len) {
          val = "0" + val;
        }
        return val;
      };
      var formatFlags = {
        D: function(dateObj) {
          return String(dateObj.getDate());
        },
        DD: function(dateObj) {
          return pad(dateObj.getDate());
        },
        Do: function(dateObj, i18n) {
          return i18n.DoFn(dateObj.getDate());
        },
        d: function(dateObj) {
          return String(dateObj.getDay());
        },
        dd: function(dateObj) {
          return pad(dateObj.getDay());
        },
        ddd: function(dateObj, i18n) {
          return i18n.dayNamesShort[dateObj.getDay()];
        },
        dddd: function(dateObj, i18n) {
          return i18n.dayNames[dateObj.getDay()];
        },
        M: function(dateObj) {
          return String(dateObj.getMonth() + 1);
        },
        MM: function(dateObj) {
          return pad(dateObj.getMonth() + 1);
        },
        MMM: function(dateObj, i18n) {
          return i18n.monthNamesShort[dateObj.getMonth()];
        },
        MMMM: function(dateObj, i18n) {
          return i18n.monthNames[dateObj.getMonth()];
        },
        YY: function(dateObj) {
          return pad(String(dateObj.getFullYear()), 4).substr(2);
        },
        YYYY: function(dateObj) {
          return pad(dateObj.getFullYear(), 4);
        },
        h: function(dateObj) {
          return String(dateObj.getHours() % 12 || 12);
        },
        hh: function(dateObj) {
          return pad(dateObj.getHours() % 12 || 12);
        },
        H: function(dateObj) {
          return String(dateObj.getHours());
        },
        HH: function(dateObj) {
          return pad(dateObj.getHours());
        },
        m: function(dateObj) {
          return String(dateObj.getMinutes());
        },
        mm: function(dateObj) {
          return pad(dateObj.getMinutes());
        },
        s: function(dateObj) {
          return String(dateObj.getSeconds());
        },
        ss: function(dateObj) {
          return pad(dateObj.getSeconds());
        },
        S: function(dateObj) {
          return String(Math.round(dateObj.getMilliseconds() / 100));
        },
        SS: function(dateObj) {
          return pad(Math.round(dateObj.getMilliseconds() / 10), 2);
        },
        SSS: function(dateObj) {
          return pad(dateObj.getMilliseconds(), 3);
        },
        a: function(dateObj, i18n) {
          return dateObj.getHours() < 12 ? i18n.amPm[0] : i18n.amPm[1];
        },
        A: function(dateObj, i18n) {
          return dateObj.getHours() < 12 ? i18n.amPm[0].toUpperCase() : i18n.amPm[1].toUpperCase();
        },
        ZZ: function(dateObj) {
          var offset = dateObj.getTimezoneOffset();
          return (offset > 0 ? "-" : "+") + pad(Math.floor(Math.abs(offset) / 60) * 100 + Math.abs(offset) % 60, 4);
        },
        Z: function(dateObj) {
          var offset = dateObj.getTimezoneOffset();
          return (offset > 0 ? "-" : "+") + pad(Math.floor(Math.abs(offset) / 60), 2) + ":" + pad(Math.abs(offset) % 60, 2);
        }
      };
      var monthParse = function(v) {
        return +v - 1;
      };
      var emptyDigits = [null, twoDigitsOptional];
      var emptyWord = [null, word];
      var amPm = [
        "isPm",
        word,
        function(v, i18n) {
          var val = v.toLowerCase();
          if (val === i18n.amPm[0]) {
            return 0;
          } else if (val === i18n.amPm[1]) {
            return 1;
          }
          return null;
        }
      ];
      var timezoneOffset = [
        "timezoneOffset",
        "[^\\s]*?[\\+\\-]\\d\\d:?\\d\\d|[^\\s]*?Z?",
        function(v) {
          var parts = (v + "").match(/([+-]|\d\d)/gi);
          if (parts) {
            var minutes = +parts[1] * 60 + parseInt(parts[2], 10);
            return parts[0] === "+" ? minutes : -minutes;
          }
          return 0;
        }
      ];
      var parseFlags = {
        D: ["day", twoDigitsOptional],
        DD: ["day", twoDigits],
        Do: ["day", twoDigitsOptional + word, function(v) {
          return parseInt(v, 10);
        }],
        M: ["month", twoDigitsOptional, monthParse],
        MM: ["month", twoDigits, monthParse],
        YY: [
          "year",
          twoDigits,
          function(v) {
            var now = /* @__PURE__ */ new Date();
            var cent = +("" + now.getFullYear()).substr(0, 2);
            return +("" + (+v > 68 ? cent - 1 : cent) + v);
          }
        ],
        h: ["hour", twoDigitsOptional, void 0, "isPm"],
        hh: ["hour", twoDigits, void 0, "isPm"],
        H: ["hour", twoDigitsOptional],
        HH: ["hour", twoDigits],
        m: ["minute", twoDigitsOptional],
        mm: ["minute", twoDigits],
        s: ["second", twoDigitsOptional],
        ss: ["second", twoDigits],
        YYYY: ["year", fourDigits],
        S: ["millisecond", "\\d", function(v) {
          return +v * 100;
        }],
        SS: ["millisecond", twoDigits, function(v) {
          return +v * 10;
        }],
        SSS: ["millisecond", threeDigits],
        d: emptyDigits,
        dd: emptyDigits,
        ddd: emptyWord,
        dddd: emptyWord,
        MMM: ["month", word, monthUpdate("monthNamesShort")],
        MMMM: ["month", word, monthUpdate("monthNames")],
        a: amPm,
        A: amPm,
        ZZ: timezoneOffset,
        Z: timezoneOffset
      };
      var globalMasks = {
        default: "ddd MMM DD YYYY HH:mm:ss",
        shortDate: "M/D/YY",
        mediumDate: "MMM D, YYYY",
        longDate: "MMMM D, YYYY",
        fullDate: "dddd, MMMM D, YYYY",
        isoDate: "YYYY-MM-DD",
        isoDateTime: "YYYY-MM-DDTHH:mm:ssZ",
        shortTime: "HH:mm",
        mediumTime: "HH:mm:ss",
        longTime: "HH:mm:ss.SSS"
      };
      var setGlobalDateMasks = function(masks) {
        return assign(globalMasks, masks);
      };
      var format = function(dateObj, mask, i18n) {
        if (mask === void 0) {
          mask = globalMasks["default"];
        }
        if (i18n === void 0) {
          i18n = {};
        }
        if (typeof dateObj === "number") {
          dateObj = new Date(dateObj);
        }
        if (Object.prototype.toString.call(dateObj) !== "[object Date]" || isNaN(dateObj.getTime())) {
          throw new Error("Invalid Date pass to format");
        }
        mask = globalMasks[mask] || mask;
        var literals = [];
        mask = mask.replace(literal, function($0, $1) {
          literals.push($1);
          return "@@@";
        });
        var combinedI18nSettings = assign(assign({}, globalI18n), i18n);
        mask = mask.replace(token, function($0) {
          return formatFlags[$0](dateObj, combinedI18nSettings);
        });
        return mask.replace(/@@@/g, function() {
          return literals.shift();
        });
      };
      function parse2(dateStr, format2, i18n) {
        if (i18n === void 0) {
          i18n = {};
        }
        if (typeof format2 !== "string") {
          throw new Error("Invalid format in fecha parse");
        }
        format2 = globalMasks[format2] || format2;
        if (dateStr.length > 1e3) {
          return null;
        }
        var today = /* @__PURE__ */ new Date();
        var dateInfo = {
          year: today.getFullYear(),
          month: 0,
          day: 1,
          hour: 0,
          minute: 0,
          second: 0,
          millisecond: 0,
          isPm: null,
          timezoneOffset: null
        };
        var parseInfo = [];
        var literals = [];
        var newFormat = format2.replace(literal, function($0, $1) {
          literals.push(regexEscape($1));
          return "@@@";
        });
        var specifiedFields = {};
        var requiredFields = {};
        newFormat = regexEscape(newFormat).replace(token, function($0) {
          var info = parseFlags[$0];
          var field2 = info[0], regex = info[1], requiredField = info[3];
          if (specifiedFields[field2]) {
            throw new Error("Invalid format. " + field2 + " specified twice in format");
          }
          specifiedFields[field2] = true;
          if (requiredField) {
            requiredFields[requiredField] = true;
          }
          parseInfo.push(info);
          return "(" + regex + ")";
        });
        Object.keys(requiredFields).forEach(function(field2) {
          if (!specifiedFields[field2]) {
            throw new Error("Invalid format. " + field2 + " is required in specified format");
          }
        });
        newFormat = newFormat.replace(/@@@/g, function() {
          return literals.shift();
        });
        var matches = dateStr.match(new RegExp(newFormat, "i"));
        if (!matches) {
          return null;
        }
        var combinedI18nSettings = assign(assign({}, globalI18n), i18n);
        for (var i = 1; i < matches.length; i++) {
          var _a = parseInfo[i - 1], field = _a[0], parser = _a[2];
          var value = parser ? parser(matches[i], combinedI18nSettings) : +matches[i];
          if (value == null) {
            return null;
          }
          dateInfo[field] = value;
        }
        if (dateInfo.isPm === 1 && dateInfo.hour != null && +dateInfo.hour !== 12) {
          dateInfo.hour = +dateInfo.hour + 12;
        } else if (dateInfo.isPm === 0 && +dateInfo.hour === 12) {
          dateInfo.hour = 0;
        }
        var dateTZ;
        if (dateInfo.timezoneOffset == null) {
          dateTZ = new Date(dateInfo.year, dateInfo.month, dateInfo.day, dateInfo.hour, dateInfo.minute, dateInfo.second, dateInfo.millisecond);
          var validateFields = [
            ["month", "getMonth"],
            ["day", "getDate"],
            ["hour", "getHours"],
            ["minute", "getMinutes"],
            ["second", "getSeconds"]
          ];
          for (var i = 0, len = validateFields.length; i < len; i++) {
            if (specifiedFields[validateFields[i][0]] && dateInfo[validateFields[i][0]] !== dateTZ[validateFields[i][1]]()) {
              return null;
            }
          }
        } else {
          dateTZ = new Date(Date.UTC(dateInfo.year, dateInfo.month, dateInfo.day, dateInfo.hour, dateInfo.minute - dateInfo.timezoneOffset, dateInfo.second, dateInfo.millisecond));
          if (dateInfo.month > 11 || dateInfo.month < 0 || dateInfo.day > 31 || dateInfo.day < 1 || dateInfo.hour > 23 || dateInfo.hour < 0 || dateInfo.minute > 59 || dateInfo.minute < 0 || dateInfo.second > 59 || dateInfo.second < 0) {
            return null;
          }
        }
        return dateTZ;
      }
      var fecha = {
        format,
        parse: parse2,
        defaultI18n,
        setGlobalDateI18n,
        setGlobalDateMasks
      };
      exports3.assign = assign;
      exports3.default = fecha;
      exports3.format = format;
      exports3.parse = parse2;
      exports3.defaultI18n = defaultI18n;
      exports3.setGlobalDateI18n = setGlobalDateI18n;
      exports3.setGlobalDateMasks = setGlobalDateMasks;
      Object.defineProperty(exports3, "__esModule", { value: true });
    });
  }
});

// node_modules/logform/timestamp.js
var require_timestamp = __commonJS({
  "node_modules/logform/timestamp.js"(exports2, module2) {
    "use strict";
    var fecha = require_fecha_umd();
    var format = require_format();
    module2.exports = format((info, opts = {}) => {
      if (opts.format) {
        info.timestamp = typeof opts.format === "function" ? opts.format() : fecha.format(/* @__PURE__ */ new Date(), opts.format);
      }
      if (!info.timestamp) {
        info.timestamp = (/* @__PURE__ */ new Date()).toISOString();
      }
      if (opts.alias) {
        info[opts.alias] = info.timestamp;
      }
      return info;
    });
  }
});

// node_modules/logform/uncolorize.js
var require_uncolorize = __commonJS({
  "node_modules/logform/uncolorize.js"(exports2, module2) {
    "use strict";
    var colors = require_safe();
    var format = require_format();
    var { MESSAGE } = require_triple_beam();
    module2.exports = format((info, opts) => {
      if (opts.level !== false) {
        info.level = colors.strip(info.level);
      }
      if (opts.message !== false) {
        info.message = colors.strip(String(info.message));
      }
      if (opts.raw !== false && info[MESSAGE]) {
        info[MESSAGE] = colors.strip(String(info[MESSAGE]));
      }
      return info;
    });
  }
});

// node_modules/logform/index.js
var require_logform = __commonJS({
  "node_modules/logform/index.js"(exports2) {
    "use strict";
    var format = exports2.format = require_format();
    exports2.levels = require_levels();
    function exposeFormat(name, requireFormat) {
      Object.defineProperty(format, name, {
        get() {
          return requireFormat();
        },
        configurable: true
      });
    }
    exposeFormat("align", function() {
      return require_align();
    });
    exposeFormat("errors", function() {
      return require_errors();
    });
    exposeFormat("cli", function() {
      return require_cli2();
    });
    exposeFormat("combine", function() {
      return require_combine();
    });
    exposeFormat("colorize", function() {
      return require_colorize();
    });
    exposeFormat("json", function() {
      return require_json();
    });
    exposeFormat("label", function() {
      return require_label();
    });
    exposeFormat("logstash", function() {
      return require_logstash();
    });
    exposeFormat("metadata", function() {
      return require_metadata();
    });
    exposeFormat("ms", function() {
      return require_ms2();
    });
    exposeFormat("padLevels", function() {
      return require_pad_levels();
    });
    exposeFormat("prettyPrint", function() {
      return require_pretty_print();
    });
    exposeFormat("printf", function() {
      return require_printf();
    });
    exposeFormat("simple", function() {
      return require_simple();
    });
    exposeFormat("splat", function() {
      return require_splat();
    });
    exposeFormat("timestamp", function() {
      return require_timestamp();
    });
    exposeFormat("uncolorize", function() {
      return require_uncolorize();
    });
  }
});

// node_modules/winston/lib/winston/common.js
var require_common = __commonJS({
  "node_modules/winston/lib/winston/common.js"(exports2) {
    "use strict";
    var { format } = require("util");
    exports2.warn = {
      deprecated(prop) {
        return () => {
          throw new Error(format("{ %s } was removed in winston@3.0.0.", prop));
        };
      },
      useFormat(prop) {
        return () => {
          throw new Error([
            format("{ %s } was removed in winston@3.0.0.", prop),
            "Use a custom winston.format = winston.format(function) instead."
          ].join("\n"));
        };
      },
      forFunctions(obj, type, props) {
        props.forEach((prop) => {
          obj[prop] = exports2.warn[type](prop);
        });
      },
      forProperties(obj, type, props) {
        props.forEach((prop) => {
          const notice = exports2.warn[type](prop);
          Object.defineProperty(obj, prop, {
            get: notice,
            set: notice
          });
        });
      }
    };
  }
});

// node_modules/winston/package.json
var require_package = __commonJS({
  "node_modules/winston/package.json"(exports2, module2) {
    module2.exports = {
      name: "winston",
      description: "A logger for just about everything.",
      version: "3.17.0",
      author: "Charlie Robbins <charlie.robbins@gmail.com>",
      maintainers: [
        "David Hyde <dabh@alumni.stanford.edu>"
      ],
      repository: {
        type: "git",
        url: "https://github.com/winstonjs/winston.git"
      },
      keywords: [
        "winston",
        "logger",
        "logging",
        "logs",
        "sysadmin",
        "bunyan",
        "pino",
        "loglevel",
        "tools",
        "json",
        "stream"
      ],
      dependencies: {
        "@dabh/diagnostics": "^2.0.2",
        "@colors/colors": "^1.6.0",
        async: "^3.2.3",
        "is-stream": "^2.0.0",
        logform: "^2.7.0",
        "one-time": "^1.0.0",
        "readable-stream": "^3.4.0",
        "safe-stable-stringify": "^2.3.1",
        "stack-trace": "0.0.x",
        "triple-beam": "^1.3.0",
        "winston-transport": "^4.9.0"
      },
      devDependencies: {
        "@babel/cli": "^7.23.9",
        "@babel/core": "^7.24.0",
        "@babel/preset-env": "^7.24.0",
        "@dabh/eslint-config-populist": "^4.4.0",
        "@types/node": "^20.11.24",
        "abstract-winston-transport": "^0.5.1",
        assume: "^2.2.0",
        "cross-spawn-async": "^2.2.5",
        eslint: "^8.57.0",
        hock: "^1.4.1",
        mocha: "^10.3.0",
        nyc: "^17.1.0",
        rimraf: "5.0.1",
        split2: "^4.1.0",
        "std-mocks": "^2.0.0",
        through2: "^4.0.2",
        "winston-compat": "^0.1.5"
      },
      main: "./lib/winston.js",
      browser: "./dist/winston",
      types: "./index.d.ts",
      scripts: {
        lint: "eslint lib/*.js lib/winston/*.js lib/winston/**/*.js --resolve-plugins-relative-to ./node_modules/@dabh/eslint-config-populist",
        test: "rimraf test/fixtures/logs/* && mocha",
        "test:coverage": "nyc npm run test:unit",
        "test:unit": "mocha test/unit",
        "test:integration": "mocha test/integration",
        build: "rimraf dist && babel lib -d dist",
        prepublishOnly: "npm run build"
      },
      engines: {
        node: ">= 12.0.0"
      },
      license: "MIT"
    };
  }
});

// node_modules/util-deprecate/node.js
var require_node = __commonJS({
  "node_modules/util-deprecate/node.js"(exports2, module2) {
    module2.exports = require("util").deprecate;
  }
});

// node_modules/readable-stream/lib/internal/streams/stream.js
var require_stream = __commonJS({
  "node_modules/readable-stream/lib/internal/streams/stream.js"(exports2, module2) {
    module2.exports = require("stream");
  }
});

// node_modules/readable-stream/lib/internal/streams/destroy.js
var require_destroy = __commonJS({
  "node_modules/readable-stream/lib/internal/streams/destroy.js"(exports2, module2) {
    "use strict";
    function destroy(err, cb) {
      var _this = this;
      var readableDestroyed = this._readableState && this._readableState.destroyed;
      var writableDestroyed = this._writableState && this._writableState.destroyed;
      if (readableDestroyed || writableDestroyed) {
        if (cb) {
          cb(err);
        } else if (err) {
          if (!this._writableState) {
            process.nextTick(emitErrorNT, this, err);
          } else if (!this._writableState.errorEmitted) {
            this._writableState.errorEmitted = true;
            process.nextTick(emitErrorNT, this, err);
          }
        }
        return this;
      }
      if (this._readableState) {
        this._readableState.destroyed = true;
      }
      if (this._writableState) {
        this._writableState.destroyed = true;
      }
      this._destroy(err || null, function(err2) {
        if (!cb && err2) {
          if (!_this._writableState) {
            process.nextTick(emitErrorAndCloseNT, _this, err2);
          } else if (!_this._writableState.errorEmitted) {
            _this._writableState.errorEmitted = true;
            process.nextTick(emitErrorAndCloseNT, _this, err2);
          } else {
            process.nextTick(emitCloseNT, _this);
          }
        } else if (cb) {
          process.nextTick(emitCloseNT, _this);
          cb(err2);
        } else {
          process.nextTick(emitCloseNT, _this);
        }
      });
      return this;
    }
    function emitErrorAndCloseNT(self2, err) {
      emitErrorNT(self2, err);
      emitCloseNT(self2);
    }
    function emitCloseNT(self2) {
      if (self2._writableState && !self2._writableState.emitClose) return;
      if (self2._readableState && !self2._readableState.emitClose) return;
      self2.emit("close");
    }
    function undestroy() {
      if (this._readableState) {
        this._readableState.destroyed = false;
        this._readableState.reading = false;
        this._readableState.ended = false;
        this._readableState.endEmitted = false;
      }
      if (this._writableState) {
        this._writableState.destroyed = false;
        this._writableState.ended = false;
        this._writableState.ending = false;
        this._writableState.finalCalled = false;
        this._writableState.prefinished = false;
        this._writableState.finished = false;
        this._writableState.errorEmitted = false;
      }
    }
    function emitErrorNT(self2, err) {
      self2.emit("error", err);
    }
    function errorOrDestroy(stream, err) {
      var rState = stream._readableState;
      var wState = stream._writableState;
      if (rState && rState.autoDestroy || wState && wState.autoDestroy) stream.destroy(err);
      else stream.emit("error", err);
    }
    module2.exports = {
      destroy,
      undestroy,
      errorOrDestroy
    };
  }
});

// node_modules/readable-stream/errors.js
var require_errors2 = __commonJS({
  "node_modules/readable-stream/errors.js"(exports2, module2) {
    "use strict";
    var codes = {};
    function createErrorType(code, message, Base) {
      if (!Base) {
        Base = Error;
      }
      function getMessage(arg1, arg2, arg3) {
        if (typeof message === "string") {
          return message;
        } else {
          return message(arg1, arg2, arg3);
        }
      }
      class NodeError extends Base {
        constructor(arg1, arg2, arg3) {
          super(getMessage(arg1, arg2, arg3));
        }
      }
      NodeError.prototype.name = Base.name;
      NodeError.prototype.code = code;
      codes[code] = NodeError;
    }
    function oneOf(expected, thing) {
      if (Array.isArray(expected)) {
        const len = expected.length;
        expected = expected.map((i) => String(i));
        if (len > 2) {
          return `one of ${thing} ${expected.slice(0, len - 1).join(", ")}, or ` + expected[len - 1];
        } else if (len === 2) {
          return `one of ${thing} ${expected[0]} or ${expected[1]}`;
        } else {
          return `of ${thing} ${expected[0]}`;
        }
      } else {
        return `of ${thing} ${String(expected)}`;
      }
    }
    function startsWith(str, search, pos) {
      return str.substr(!pos || pos < 0 ? 0 : +pos, search.length) === search;
    }
    function endsWith(str, search, this_len) {
      if (this_len === void 0 || this_len > str.length) {
        this_len = str.length;
      }
      return str.substring(this_len - search.length, this_len) === search;
    }
    function includes(str, search, start) {
      if (typeof start !== "number") {
        start = 0;
      }
      if (start + search.length > str.length) {
        return false;
      } else {
        return str.indexOf(search, start) !== -1;
      }
    }
    createErrorType("ERR_INVALID_OPT_VALUE", function(name, value) {
      return 'The value "' + value + '" is invalid for option "' + name + '"';
    }, TypeError);
    createErrorType("ERR_INVALID_ARG_TYPE", function(name, expected, actual) {
      let determiner;
      if (typeof expected === "string" && startsWith(expected, "not ")) {
        determiner = "must not be";
        expected = expected.replace(/^not /, "");
      } else {
        determiner = "must be";
      }
      let msg;
      if (endsWith(name, " argument")) {
        msg = `The ${name} ${determiner} ${oneOf(expected, "type")}`;
      } else {
        const type = includes(name, ".") ? "property" : "argument";
        msg = `The "${name}" ${type} ${determiner} ${oneOf(expected, "type")}`;
      }
      msg += `. Received type ${typeof actual}`;
      return msg;
    }, TypeError);
    createErrorType("ERR_STREAM_PUSH_AFTER_EOF", "stream.push() after EOF");
    createErrorType("ERR_METHOD_NOT_IMPLEMENTED", function(name) {
      return "The " + name + " method is not implemented";
    });
    createErrorType("ERR_STREAM_PREMATURE_CLOSE", "Premature close");
    createErrorType("ERR_STREAM_DESTROYED", function(name) {
      return "Cannot call " + name + " after a stream was destroyed";
    });
    createErrorType("ERR_MULTIPLE_CALLBACK", "Callback called multiple times");
    createErrorType("ERR_STREAM_CANNOT_PIPE", "Cannot pipe, not readable");
    createErrorType("ERR_STREAM_WRITE_AFTER_END", "write after end");
    createErrorType("ERR_STREAM_NULL_VALUES", "May not write null values to stream", TypeError);
    createErrorType("ERR_UNKNOWN_ENCODING", function(arg) {
      return "Unknown encoding: " + arg;
    }, TypeError);
    createErrorType("ERR_STREAM_UNSHIFT_AFTER_END_EVENT", "stream.unshift() after end event");
    module2.exports.codes = codes;
  }
});

// node_modules/readable-stream/lib/internal/streams/state.js
var require_state = __commonJS({
  "node_modules/readable-stream/lib/internal/streams/state.js"(exports2, module2) {
    "use strict";
    var ERR_INVALID_OPT_VALUE = require_errors2().codes.ERR_INVALID_OPT_VALUE;
    function highWaterMarkFrom(options, isDuplex, duplexKey) {
      return options.highWaterMark != null ? options.highWaterMark : isDuplex ? options[duplexKey] : null;
    }
    function getHighWaterMark(state, options, duplexKey, isDuplex) {
      var hwm = highWaterMarkFrom(options, isDuplex, duplexKey);
      if (hwm != null) {
        if (!(isFinite(hwm) && Math.floor(hwm) === hwm) || hwm < 0) {
          var name = isDuplex ? duplexKey : "highWaterMark";
          throw new ERR_INVALID_OPT_VALUE(name, hwm);
        }
        return Math.floor(hwm);
      }
      return state.objectMode ? 16 : 16 * 1024;
    }
    module2.exports = {
      getHighWaterMark
    };
  }
});

// node_modules/inherits/inherits_browser.js
var require_inherits_browser = __commonJS({
  "node_modules/inherits/inherits_browser.js"(exports2, module2) {
    if (typeof Object.create === "function") {
      module2.exports = function inherits(ctor, superCtor) {
        if (superCtor) {
          ctor.super_ = superCtor;
          ctor.prototype = Object.create(superCtor.prototype, {
            constructor: {
              value: ctor,
              enumerable: false,
              writable: true,
              configurable: true
            }
          });
        }
      };
    } else {
      module2.exports = function inherits(ctor, superCtor) {
        if (superCtor) {
          ctor.super_ = superCtor;
          var TempCtor = function() {
          };
          TempCtor.prototype = superCtor.prototype;
          ctor.prototype = new TempCtor();
          ctor.prototype.constructor = ctor;
        }
      };
    }
  }
});

// node_modules/inherits/inherits.js
var require_inherits = __commonJS({
  "node_modules/inherits/inherits.js"(exports2, module2) {
    try {
      util = require("util");
      if (typeof util.inherits !== "function") throw "";
      module2.exports = util.inherits;
    } catch (e) {
      module2.exports = require_inherits_browser();
    }
    var util;
  }
});

// node_modules/readable-stream/lib/internal/streams/buffer_list.js
var require_buffer_list = __commonJS({
  "node_modules/readable-stream/lib/internal/streams/buffer_list.js"(exports2, module2) {
    "use strict";
    function ownKeys(object, enumerableOnly) {
      var keys = Object.keys(object);
      if (Object.getOwnPropertySymbols) {
        var symbols = Object.getOwnPropertySymbols(object);
        enumerableOnly && (symbols = symbols.filter(function(sym) {
          return Object.getOwnPropertyDescriptor(object, sym).enumerable;
        })), keys.push.apply(keys, symbols);
      }
      return keys;
    }
    function _objectSpread(target) {
      for (var i = 1; i < arguments.length; i++) {
        var source = null != arguments[i] ? arguments[i] : {};
        i % 2 ? ownKeys(Object(source), true).forEach(function(key) {
          _defineProperty(target, key, source[key]);
        }) : Object.getOwnPropertyDescriptors ? Object.defineProperties(target, Object.getOwnPropertyDescriptors(source)) : ownKeys(Object(source)).forEach(function(key) {
          Object.defineProperty(target, key, Object.getOwnPropertyDescriptor(source, key));
        });
      }
      return target;
    }
    function _defineProperty(obj, key, value) {
      key = _toPropertyKey(key);
      if (key in obj) {
        Object.defineProperty(obj, key, { value, enumerable: true, configurable: true, writable: true });
      } else {
        obj[key] = value;
      }
      return obj;
    }
    function _classCallCheck(instance, Constructor) {
      if (!(instance instanceof Constructor)) {
        throw new TypeError("Cannot call a class as a function");
      }
    }
    function _defineProperties(target, props) {
      for (var i = 0; i < props.length; i++) {
        var descriptor = props[i];
        descriptor.enumerable = descriptor.enumerable || false;
        descriptor.configurable = true;
        if ("value" in descriptor) descriptor.writable = true;
        Object.defineProperty(target, _toPropertyKey(descriptor.key), descriptor);
      }
    }
    function _createClass(Constructor, protoProps, staticProps) {
      if (protoProps) _defineProperties(Constructor.prototype, protoProps);
      if (staticProps) _defineProperties(Constructor, staticProps);
      Object.defineProperty(Constructor, "prototype", { writable: false });
      return Constructor;
    }
    function _toPropertyKey(arg) {
      var key = _toPrimitive(arg, "string");
      return typeof key === "symbol" ? key : String(key);
    }
    function _toPrimitive(input, hint) {
      if (typeof input !== "object" || input === null) return input;
      var prim = input[Symbol.toPrimitive];
      if (prim !== void 0) {
        var res = prim.call(input, hint || "default");
        if (typeof res !== "object") return res;
        throw new TypeError("@@toPrimitive must return a primitive value.");
      }
      return (hint === "string" ? String : Number)(input);
    }
    var _require = require("buffer");
    var Buffer2 = _require.Buffer;
    var _require2 = require("util");
    var inspect = _require2.inspect;
    var custom = inspect && inspect.custom || "inspect";
    function copyBuffer(src, target, offset) {
      Buffer2.prototype.copy.call(src, target, offset);
    }
    module2.exports = /* @__PURE__ */ function() {
      function BufferList() {
        _classCallCheck(this, BufferList);
        this.head = null;
        this.tail = null;
        this.length = 0;
      }
      _createClass(BufferList, [{
        key: "push",
        value: function push(v) {
          var entry = {
            data: v,
            next: null
          };
          if (this.length > 0) this.tail.next = entry;
          else this.head = entry;
          this.tail = entry;
          ++this.length;
        }
      }, {
        key: "unshift",
        value: function unshift(v) {
          var entry = {
            data: v,
            next: this.head
          };
          if (this.length === 0) this.tail = entry;
          this.head = entry;
          ++this.length;
        }
      }, {
        key: "shift",
        value: function shift() {
          if (this.length === 0) return;
          var ret = this.head.data;
          if (this.length === 1) this.head = this.tail = null;
          else this.head = this.head.next;
          --this.length;
          return ret;
        }
      }, {
        key: "clear",
        value: function clear() {
          this.head = this.tail = null;
          this.length = 0;
        }
      }, {
        key: "join",
        value: function join(s) {
          if (this.length === 0) return "";
          var p = this.head;
          var ret = "" + p.data;
          while (p = p.next) ret += s + p.data;
          return ret;
        }
      }, {
        key: "concat",
        value: function concat(n) {
          if (this.length === 0) return Buffer2.alloc(0);
          var ret = Buffer2.allocUnsafe(n >>> 0);
          var p = this.head;
          var i = 0;
          while (p) {
            copyBuffer(p.data, ret, i);
            i += p.data.length;
            p = p.next;
          }
          return ret;
        }
        // Consumes a specified amount of bytes or characters from the buffered data.
      }, {
        key: "consume",
        value: function consume(n, hasStrings) {
          var ret;
          if (n < this.head.data.length) {
            ret = this.head.data.slice(0, n);
            this.head.data = this.head.data.slice(n);
          } else if (n === this.head.data.length) {
            ret = this.shift();
          } else {
            ret = hasStrings ? this._getString(n) : this._getBuffer(n);
          }
          return ret;
        }
      }, {
        key: "first",
        value: function first() {
          return this.head.data;
        }
        // Consumes a specified amount of characters from the buffered data.
      }, {
        key: "_getString",
        value: function _getString(n) {
          var p = this.head;
          var c = 1;
          var ret = p.data;
          n -= ret.length;
          while (p = p.next) {
            var str = p.data;
            var nb = n > str.length ? str.length : n;
            if (nb === str.length) ret += str;
            else ret += str.slice(0, n);
            n -= nb;
            if (n === 0) {
              if (nb === str.length) {
                ++c;
                if (p.next) this.head = p.next;
                else this.head = this.tail = null;
              } else {
                this.head = p;
                p.data = str.slice(nb);
              }
              break;
            }
            ++c;
          }
          this.length -= c;
          return ret;
        }
        // Consumes a specified amount of bytes from the buffered data.
      }, {
        key: "_getBuffer",
        value: function _getBuffer(n) {
          var ret = Buffer2.allocUnsafe(n);
          var p = this.head;
          var c = 1;
          p.data.copy(ret);
          n -= p.data.length;
          while (p = p.next) {
            var buf = p.data;
            var nb = n > buf.length ? buf.length : n;
            buf.copy(ret, ret.length - n, 0, nb);
            n -= nb;
            if (n === 0) {
              if (nb === buf.length) {
                ++c;
                if (p.next) this.head = p.next;
                else this.head = this.tail = null;
              } else {
                this.head = p;
                p.data = buf.slice(nb);
              }
              break;
            }
            ++c;
          }
          this.length -= c;
          return ret;
        }
        // Make sure the linked list only shows the minimal necessary information.
      }, {
        key: custom,
        value: function value(_, options) {
          return inspect(this, _objectSpread(_objectSpread({}, options), {}, {
            // Only inspect one level.
            depth: 0,
            // It should not recurse.
            customInspect: false
          }));
        }
      }]);
      return BufferList;
    }();
  }
});

// node_modules/readable-stream/lib/internal/streams/end-of-stream.js
var require_end_of_stream = __commonJS({
  "node_modules/readable-stream/lib/internal/streams/end-of-stream.js"(exports2, module2) {
    "use strict";
    var ERR_STREAM_PREMATURE_CLOSE = require_errors2().codes.ERR_STREAM_PREMATURE_CLOSE;
    function once(callback) {
      var called = false;
      return function() {
        if (called) return;
        called = true;
        for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
          args[_key] = arguments[_key];
        }
        callback.apply(this, args);
      };
    }
    function noop() {
    }
    function isRequest(stream) {
      return stream.setHeader && typeof stream.abort === "function";
    }
    function eos(stream, opts, callback) {
      if (typeof opts === "function") return eos(stream, null, opts);
      if (!opts) opts = {};
      callback = once(callback || noop);
      var readable = opts.readable || opts.readable !== false && stream.readable;
      var writable = opts.writable || opts.writable !== false && stream.writable;
      var onlegacyfinish = function onlegacyfinish2() {
        if (!stream.writable) onfinish();
      };
      var writableEnded = stream._writableState && stream._writableState.finished;
      var onfinish = function onfinish2() {
        writable = false;
        writableEnded = true;
        if (!readable) callback.call(stream);
      };
      var readableEnded = stream._readableState && stream._readableState.endEmitted;
      var onend = function onend2() {
        readable = false;
        readableEnded = true;
        if (!writable) callback.call(stream);
      };
      var onerror = function onerror2(err) {
        callback.call(stream, err);
      };
      var onclose = function onclose2() {
        var err;
        if (readable && !readableEnded) {
          if (!stream._readableState || !stream._readableState.ended) err = new ERR_STREAM_PREMATURE_CLOSE();
          return callback.call(stream, err);
        }
        if (writable && !writableEnded) {
          if (!stream._writableState || !stream._writableState.ended) err = new ERR_STREAM_PREMATURE_CLOSE();
          return callback.call(stream, err);
        }
      };
      var onrequest = function onrequest2() {
        stream.req.on("finish", onfinish);
      };
      if (isRequest(stream)) {
        stream.on("complete", onfinish);
        stream.on("abort", onclose);
        if (stream.req) onrequest();
        else stream.on("request", onrequest);
      } else if (writable && !stream._writableState) {
        stream.on("end", onlegacyfinish);
        stream.on("close", onlegacyfinish);
      }
      stream.on("end", onend);
      stream.on("finish", onfinish);
      if (opts.error !== false) stream.on("error", onerror);
      stream.on("close", onclose);
      return function() {
        stream.removeListener("complete", onfinish);
        stream.removeListener("abort", onclose);
        stream.removeListener("request", onrequest);
        if (stream.req) stream.req.removeListener("finish", onfinish);
        stream.removeListener("end", onlegacyfinish);
        stream.removeListener("close", onlegacyfinish);
        stream.removeListener("finish", onfinish);
        stream.removeListener("end", onend);
        stream.removeListener("error", onerror);
        stream.removeListener("close", onclose);
      };
    }
    module2.exports = eos;
  }
});

// node_modules/readable-stream/lib/internal/streams/async_iterator.js
var require_async_iterator = __commonJS({
  "node_modules/readable-stream/lib/internal/streams/async_iterator.js"(exports2, module2) {
    "use strict";
    var _Object$setPrototypeO;
    function _defineProperty(obj, key, value) {
      key = _toPropertyKey(key);
      if (key in obj) {
        Object.defineProperty(obj, key, { value, enumerable: true, configurable: true, writable: true });
      } else {
        obj[key] = value;
      }
      return obj;
    }
    function _toPropertyKey(arg) {
      var key = _toPrimitive(arg, "string");
      return typeof key === "symbol" ? key : String(key);
    }
    function _toPrimitive(input, hint) {
      if (typeof input !== "object" || input === null) return input;
      var prim = input[Symbol.toPrimitive];
      if (prim !== void 0) {
        var res = prim.call(input, hint || "default");
        if (typeof res !== "object") return res;
        throw new TypeError("@@toPrimitive must return a primitive value.");
      }
      return (hint === "string" ? String : Number)(input);
    }
    var finished = require_end_of_stream();
    var kLastResolve = Symbol("lastResolve");
    var kLastReject = Symbol("lastReject");
    var kError = Symbol("error");
    var kEnded = Symbol("ended");
    var kLastPromise = Symbol("lastPromise");
    var kHandlePromise = Symbol("handlePromise");
    var kStream = Symbol("stream");
    function createIterResult(value, done) {
      return {
        value,
        done
      };
    }
    function readAndResolve(iter) {
      var resolve = iter[kLastResolve];
      if (resolve !== null) {
        var data = iter[kStream].read();
        if (data !== null) {
          iter[kLastPromise] = null;
          iter[kLastResolve] = null;
          iter[kLastReject] = null;
          resolve(createIterResult(data, false));
        }
      }
    }
    function onReadable(iter) {
      process.nextTick(readAndResolve, iter);
    }
    function wrapForNext(lastPromise, iter) {
      return function(resolve, reject) {
        lastPromise.then(function() {
          if (iter[kEnded]) {
            resolve(createIterResult(void 0, true));
            return;
          }
          iter[kHandlePromise](resolve, reject);
        }, reject);
      };
    }
    var AsyncIteratorPrototype = Object.getPrototypeOf(function() {
    });
    var ReadableStreamAsyncIteratorPrototype = Object.setPrototypeOf((_Object$setPrototypeO = {
      get stream() {
        return this[kStream];
      },
      next: function next() {
        var _this = this;
        var error = this[kError];
        if (error !== null) {
          return Promise.reject(error);
        }
        if (this[kEnded]) {
          return Promise.resolve(createIterResult(void 0, true));
        }
        if (this[kStream].destroyed) {
          return new Promise(function(resolve, reject) {
            process.nextTick(function() {
              if (_this[kError]) {
                reject(_this[kError]);
              } else {
                resolve(createIterResult(void 0, true));
              }
            });
          });
        }
        var lastPromise = this[kLastPromise];
        var promise;
        if (lastPromise) {
          promise = new Promise(wrapForNext(lastPromise, this));
        } else {
          var data = this[kStream].read();
          if (data !== null) {
            return Promise.resolve(createIterResult(data, false));
          }
          promise = new Promise(this[kHandlePromise]);
        }
        this[kLastPromise] = promise;
        return promise;
      }
    }, _defineProperty(_Object$setPrototypeO, Symbol.asyncIterator, function() {
      return this;
    }), _defineProperty(_Object$setPrototypeO, "return", function _return() {
      var _this2 = this;
      return new Promise(function(resolve, reject) {
        _this2[kStream].destroy(null, function(err) {
          if (err) {
            reject(err);
            return;
          }
          resolve(createIterResult(void 0, true));
        });
      });
    }), _Object$setPrototypeO), AsyncIteratorPrototype);
    var createReadableStreamAsyncIterator = function createReadableStreamAsyncIterator2(stream) {
      var _Object$create;
      var iterator = Object.create(ReadableStreamAsyncIteratorPrototype, (_Object$create = {}, _defineProperty(_Object$create, kStream, {
        value: stream,
        writable: true
      }), _defineProperty(_Object$create, kLastResolve, {
        value: null,
        writable: true
      }), _defineProperty(_Object$create, kLastReject, {
        value: null,
        writable: true
      }), _defineProperty(_Object$create, kError, {
        value: null,
        writable: true
      }), _defineProperty(_Object$create, kEnded, {
        value: stream._readableState.endEmitted,
        writable: true
      }), _defineProperty(_Object$create, kHandlePromise, {
        value: function value(resolve, reject) {
          var data = iterator[kStream].read();
          if (data) {
            iterator[kLastPromise] = null;
            iterator[kLastResolve] = null;
            iterator[kLastReject] = null;
            resolve(createIterResult(data, false));
          } else {
            iterator[kLastResolve] = resolve;
            iterator[kLastReject] = reject;
          }
        },
        writable: true
      }), _Object$create));
      iterator[kLastPromise] = null;
      finished(stream, function(err) {
        if (err && err.code !== "ERR_STREAM_PREMATURE_CLOSE") {
          var reject = iterator[kLastReject];
          if (reject !== null) {
            iterator[kLastPromise] = null;
            iterator[kLastResolve] = null;
            iterator[kLastReject] = null;
            reject(err);
          }
          iterator[kError] = err;
          return;
        }
        var resolve = iterator[kLastResolve];
        if (resolve !== null) {
          iterator[kLastPromise] = null;
          iterator[kLastResolve] = null;
          iterator[kLastReject] = null;
          resolve(createIterResult(void 0, true));
        }
        iterator[kEnded] = true;
      });
      stream.on("readable", onReadable.bind(null, iterator));
      return iterator;
    };
    module2.exports = createReadableStreamAsyncIterator;
  }
});

// node_modules/readable-stream/lib/internal/streams/from.js
var require_from = __commonJS({
  "node_modules/readable-stream/lib/internal/streams/from.js"(exports2, module2) {
    "use strict";
    function asyncGeneratorStep(gen, resolve, reject, _next, _throw, key, arg) {
      try {
        var info = gen[key](arg);
        var value = info.value;
      } catch (error) {
        reject(error);
        return;
      }
      if (info.done) {
        resolve(value);
      } else {
        Promise.resolve(value).then(_next, _throw);
      }
    }
    function _asyncToGenerator(fn) {
      return function() {
        var self2 = this, args = arguments;
        return new Promise(function(resolve, reject) {
          var gen = fn.apply(self2, args);
          function _next(value) {
            asyncGeneratorStep(gen, resolve, reject, _next, _throw, "next", value);
          }
          function _throw(err) {
            asyncGeneratorStep(gen, resolve, reject, _next, _throw, "throw", err);
          }
          _next(void 0);
        });
      };
    }
    function ownKeys(object, enumerableOnly) {
      var keys = Object.keys(object);
      if (Object.getOwnPropertySymbols) {
        var symbols = Object.getOwnPropertySymbols(object);
        enumerableOnly && (symbols = symbols.filter(function(sym) {
          return Object.getOwnPropertyDescriptor(object, sym).enumerable;
        })), keys.push.apply(keys, symbols);
      }
      return keys;
    }
    function _objectSpread(target) {
      for (var i = 1; i < arguments.length; i++) {
        var source = null != arguments[i] ? arguments[i] : {};
        i % 2 ? ownKeys(Object(source), true).forEach(function(key) {
          _defineProperty(target, key, source[key]);
        }) : Object.getOwnPropertyDescriptors ? Object.defineProperties(target, Object.getOwnPropertyDescriptors(source)) : ownKeys(Object(source)).forEach(function(key) {
          Object.defineProperty(target, key, Object.getOwnPropertyDescriptor(source, key));
        });
      }
      return target;
    }
    function _defineProperty(obj, key, value) {
      key = _toPropertyKey(key);
      if (key in obj) {
        Object.defineProperty(obj, key, { value, enumerable: true, configurable: true, writable: true });
      } else {
        obj[key] = value;
      }
      return obj;
    }
    function _toPropertyKey(arg) {
      var key = _toPrimitive(arg, "string");
      return typeof key === "symbol" ? key : String(key);
    }
    function _toPrimitive(input, hint) {
      if (typeof input !== "object" || input === null) return input;
      var prim = input[Symbol.toPrimitive];
      if (prim !== void 0) {
        var res = prim.call(input, hint || "default");
        if (typeof res !== "object") return res;
        throw new TypeError("@@toPrimitive must return a primitive value.");
      }
      return (hint === "string" ? String : Number)(input);
    }
    var ERR_INVALID_ARG_TYPE = require_errors2().codes.ERR_INVALID_ARG_TYPE;
    function from(Readable, iterable, opts) {
      var iterator;
      if (iterable && typeof iterable.next === "function") {
        iterator = iterable;
      } else if (iterable && iterable[Symbol.asyncIterator]) iterator = iterable[Symbol.asyncIterator]();
      else if (iterable && iterable[Symbol.iterator]) iterator = iterable[Symbol.iterator]();
      else throw new ERR_INVALID_ARG_TYPE("iterable", ["Iterable"], iterable);
      var readable = new Readable(_objectSpread({
        objectMode: true
      }, opts));
      var reading = false;
      readable._read = function() {
        if (!reading) {
          reading = true;
          next();
        }
      };
      function next() {
        return _next2.apply(this, arguments);
      }
      function _next2() {
        _next2 = _asyncToGenerator(function* () {
          try {
            var _yield$iterator$next = yield iterator.next(), value = _yield$iterator$next.value, done = _yield$iterator$next.done;
            if (done) {
              readable.push(null);
            } else if (readable.push(yield value)) {
              next();
            } else {
              reading = false;
            }
          } catch (err) {
            readable.destroy(err);
          }
        });
        return _next2.apply(this, arguments);
      }
      return readable;
    }
    module2.exports = from;
  }
});

// node_modules/readable-stream/lib/_stream_readable.js
var require_stream_readable = __commonJS({
  "node_modules/readable-stream/lib/_stream_readable.js"(exports2, module2) {
    "use strict";
    module2.exports = Readable;
    var Duplex;
    Readable.ReadableState = ReadableState;
    var EE = require("events").EventEmitter;
    var EElistenerCount = function EElistenerCount2(emitter, type) {
      return emitter.listeners(type).length;
    };
    var Stream = require_stream();
    var Buffer2 = require("buffer").Buffer;
    var OurUint8Array = (typeof global !== "undefined" ? global : typeof window !== "undefined" ? window : typeof self !== "undefined" ? self : {}).Uint8Array || function() {
    };
    function _uint8ArrayToBuffer(chunk) {
      return Buffer2.from(chunk);
    }
    function _isUint8Array(obj) {
      return Buffer2.isBuffer(obj) || obj instanceof OurUint8Array;
    }
    var debugUtil = require("util");
    var debug;
    if (debugUtil && debugUtil.debuglog) {
      debug = debugUtil.debuglog("stream");
    } else {
      debug = function debug2() {
      };
    }
    var BufferList = require_buffer_list();
    var destroyImpl = require_destroy();
    var _require = require_state();
    var getHighWaterMark = _require.getHighWaterMark;
    var _require$codes = require_errors2().codes;
    var ERR_INVALID_ARG_TYPE = _require$codes.ERR_INVALID_ARG_TYPE;
    var ERR_STREAM_PUSH_AFTER_EOF = _require$codes.ERR_STREAM_PUSH_AFTER_EOF;
    var ERR_METHOD_NOT_IMPLEMENTED = _require$codes.ERR_METHOD_NOT_IMPLEMENTED;
    var ERR_STREAM_UNSHIFT_AFTER_END_EVENT = _require$codes.ERR_STREAM_UNSHIFT_AFTER_END_EVENT;
    var StringDecoder;
    var createReadableStreamAsyncIterator;
    var from;
    require_inherits()(Readable, Stream);
    var errorOrDestroy = destroyImpl.errorOrDestroy;
    var kProxyEvents = ["error", "close", "destroy", "pause", "resume"];
    function prependListener(emitter, event, fn) {
      if (typeof emitter.prependListener === "function") return emitter.prependListener(event, fn);
      if (!emitter._events || !emitter._events[event]) emitter.on(event, fn);
      else if (Array.isArray(emitter._events[event])) emitter._events[event].unshift(fn);
      else emitter._events[event] = [fn, emitter._events[event]];
    }
    function ReadableState(options, stream, isDuplex) {
      Duplex = Duplex || require_stream_duplex();
      options = options || {};
      if (typeof isDuplex !== "boolean") isDuplex = stream instanceof Duplex;
      this.objectMode = !!options.objectMode;
      if (isDuplex) this.objectMode = this.objectMode || !!options.readableObjectMode;
      this.highWaterMark = getHighWaterMark(this, options, "readableHighWaterMark", isDuplex);
      this.buffer = new BufferList();
      this.length = 0;
      this.pipes = null;
      this.pipesCount = 0;
      this.flowing = null;
      this.ended = false;
      this.endEmitted = false;
      this.reading = false;
      this.sync = true;
      this.needReadable = false;
      this.emittedReadable = false;
      this.readableListening = false;
      this.resumeScheduled = false;
      this.paused = true;
      this.emitClose = options.emitClose !== false;
      this.autoDestroy = !!options.autoDestroy;
      this.destroyed = false;
      this.defaultEncoding = options.defaultEncoding || "utf8";
      this.awaitDrain = 0;
      this.readingMore = false;
      this.decoder = null;
      this.encoding = null;
      if (options.encoding) {
        if (!StringDecoder) StringDecoder = require("string_decoder/").StringDecoder;
        this.decoder = new StringDecoder(options.encoding);
        this.encoding = options.encoding;
      }
    }
    function Readable(options) {
      Duplex = Duplex || require_stream_duplex();
      if (!(this instanceof Readable)) return new Readable(options);
      var isDuplex = this instanceof Duplex;
      this._readableState = new ReadableState(options, this, isDuplex);
      this.readable = true;
      if (options) {
        if (typeof options.read === "function") this._read = options.read;
        if (typeof options.destroy === "function") this._destroy = options.destroy;
      }
      Stream.call(this);
    }
    Object.defineProperty(Readable.prototype, "destroyed", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        if (this._readableState === void 0) {
          return false;
        }
        return this._readableState.destroyed;
      },
      set: function set(value) {
        if (!this._readableState) {
          return;
        }
        this._readableState.destroyed = value;
      }
    });
    Readable.prototype.destroy = destroyImpl.destroy;
    Readable.prototype._undestroy = destroyImpl.undestroy;
    Readable.prototype._destroy = function(err, cb) {
      cb(err);
    };
    Readable.prototype.push = function(chunk, encoding) {
      var state = this._readableState;
      var skipChunkCheck;
      if (!state.objectMode) {
        if (typeof chunk === "string") {
          encoding = encoding || state.defaultEncoding;
          if (encoding !== state.encoding) {
            chunk = Buffer2.from(chunk, encoding);
            encoding = "";
          }
          skipChunkCheck = true;
        }
      } else {
        skipChunkCheck = true;
      }
      return readableAddChunk(this, chunk, encoding, false, skipChunkCheck);
    };
    Readable.prototype.unshift = function(chunk) {
      return readableAddChunk(this, chunk, null, true, false);
    };
    function readableAddChunk(stream, chunk, encoding, addToFront, skipChunkCheck) {
      debug("readableAddChunk", chunk);
      var state = stream._readableState;
      if (chunk === null) {
        state.reading = false;
        onEofChunk(stream, state);
      } else {
        var er;
        if (!skipChunkCheck) er = chunkInvalid(state, chunk);
        if (er) {
          errorOrDestroy(stream, er);
        } else if (state.objectMode || chunk && chunk.length > 0) {
          if (typeof chunk !== "string" && !state.objectMode && Object.getPrototypeOf(chunk) !== Buffer2.prototype) {
            chunk = _uint8ArrayToBuffer(chunk);
          }
          if (addToFront) {
            if (state.endEmitted) errorOrDestroy(stream, new ERR_STREAM_UNSHIFT_AFTER_END_EVENT());
            else addChunk(stream, state, chunk, true);
          } else if (state.ended) {
            errorOrDestroy(stream, new ERR_STREAM_PUSH_AFTER_EOF());
          } else if (state.destroyed) {
            return false;
          } else {
            state.reading = false;
            if (state.decoder && !encoding) {
              chunk = state.decoder.write(chunk);
              if (state.objectMode || chunk.length !== 0) addChunk(stream, state, chunk, false);
              else maybeReadMore(stream, state);
            } else {
              addChunk(stream, state, chunk, false);
            }
          }
        } else if (!addToFront) {
          state.reading = false;
          maybeReadMore(stream, state);
        }
      }
      return !state.ended && (state.length < state.highWaterMark || state.length === 0);
    }
    function addChunk(stream, state, chunk, addToFront) {
      if (state.flowing && state.length === 0 && !state.sync) {
        state.awaitDrain = 0;
        stream.emit("data", chunk);
      } else {
        state.length += state.objectMode ? 1 : chunk.length;
        if (addToFront) state.buffer.unshift(chunk);
        else state.buffer.push(chunk);
        if (state.needReadable) emitReadable(stream);
      }
      maybeReadMore(stream, state);
    }
    function chunkInvalid(state, chunk) {
      var er;
      if (!_isUint8Array(chunk) && typeof chunk !== "string" && chunk !== void 0 && !state.objectMode) {
        er = new ERR_INVALID_ARG_TYPE("chunk", ["string", "Buffer", "Uint8Array"], chunk);
      }
      return er;
    }
    Readable.prototype.isPaused = function() {
      return this._readableState.flowing === false;
    };
    Readable.prototype.setEncoding = function(enc) {
      if (!StringDecoder) StringDecoder = require("string_decoder/").StringDecoder;
      var decoder = new StringDecoder(enc);
      this._readableState.decoder = decoder;
      this._readableState.encoding = this._readableState.decoder.encoding;
      var p = this._readableState.buffer.head;
      var content = "";
      while (p !== null) {
        content += decoder.write(p.data);
        p = p.next;
      }
      this._readableState.buffer.clear();
      if (content !== "") this._readableState.buffer.push(content);
      this._readableState.length = content.length;
      return this;
    };
    var MAX_HWM = 1073741824;
    function computeNewHighWaterMark(n) {
      if (n >= MAX_HWM) {
        n = MAX_HWM;
      } else {
        n--;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        n++;
      }
      return n;
    }
    function howMuchToRead(n, state) {
      if (n <= 0 || state.length === 0 && state.ended) return 0;
      if (state.objectMode) return 1;
      if (n !== n) {
        if (state.flowing && state.length) return state.buffer.head.data.length;
        else return state.length;
      }
      if (n > state.highWaterMark) state.highWaterMark = computeNewHighWaterMark(n);
      if (n <= state.length) return n;
      if (!state.ended) {
        state.needReadable = true;
        return 0;
      }
      return state.length;
    }
    Readable.prototype.read = function(n) {
      debug("read", n);
      n = parseInt(n, 10);
      var state = this._readableState;
      var nOrig = n;
      if (n !== 0) state.emittedReadable = false;
      if (n === 0 && state.needReadable && ((state.highWaterMark !== 0 ? state.length >= state.highWaterMark : state.length > 0) || state.ended)) {
        debug("read: emitReadable", state.length, state.ended);
        if (state.length === 0 && state.ended) endReadable(this);
        else emitReadable(this);
        return null;
      }
      n = howMuchToRead(n, state);
      if (n === 0 && state.ended) {
        if (state.length === 0) endReadable(this);
        return null;
      }
      var doRead = state.needReadable;
      debug("need readable", doRead);
      if (state.length === 0 || state.length - n < state.highWaterMark) {
        doRead = true;
        debug("length less than watermark", doRead);
      }
      if (state.ended || state.reading) {
        doRead = false;
        debug("reading or ended", doRead);
      } else if (doRead) {
        debug("do read");
        state.reading = true;
        state.sync = true;
        if (state.length === 0) state.needReadable = true;
        this._read(state.highWaterMark);
        state.sync = false;
        if (!state.reading) n = howMuchToRead(nOrig, state);
      }
      var ret;
      if (n > 0) ret = fromList(n, state);
      else ret = null;
      if (ret === null) {
        state.needReadable = state.length <= state.highWaterMark;
        n = 0;
      } else {
        state.length -= n;
        state.awaitDrain = 0;
      }
      if (state.length === 0) {
        if (!state.ended) state.needReadable = true;
        if (nOrig !== n && state.ended) endReadable(this);
      }
      if (ret !== null) this.emit("data", ret);
      return ret;
    };
    function onEofChunk(stream, state) {
      debug("onEofChunk");
      if (state.ended) return;
      if (state.decoder) {
        var chunk = state.decoder.end();
        if (chunk && chunk.length) {
          state.buffer.push(chunk);
          state.length += state.objectMode ? 1 : chunk.length;
        }
      }
      state.ended = true;
      if (state.sync) {
        emitReadable(stream);
      } else {
        state.needReadable = false;
        if (!state.emittedReadable) {
          state.emittedReadable = true;
          emitReadable_(stream);
        }
      }
    }
    function emitReadable(stream) {
      var state = stream._readableState;
      debug("emitReadable", state.needReadable, state.emittedReadable);
      state.needReadable = false;
      if (!state.emittedReadable) {
        debug("emitReadable", state.flowing);
        state.emittedReadable = true;
        process.nextTick(emitReadable_, stream);
      }
    }
    function emitReadable_(stream) {
      var state = stream._readableState;
      debug("emitReadable_", state.destroyed, state.length, state.ended);
      if (!state.destroyed && (state.length || state.ended)) {
        stream.emit("readable");
        state.emittedReadable = false;
      }
      state.needReadable = !state.flowing && !state.ended && state.length <= state.highWaterMark;
      flow(stream);
    }
    function maybeReadMore(stream, state) {
      if (!state.readingMore) {
        state.readingMore = true;
        process.nextTick(maybeReadMore_, stream, state);
      }
    }
    function maybeReadMore_(stream, state) {
      while (!state.reading && !state.ended && (state.length < state.highWaterMark || state.flowing && state.length === 0)) {
        var len = state.length;
        debug("maybeReadMore read 0");
        stream.read(0);
        if (len === state.length)
          break;
      }
      state.readingMore = false;
    }
    Readable.prototype._read = function(n) {
      errorOrDestroy(this, new ERR_METHOD_NOT_IMPLEMENTED("_read()"));
    };
    Readable.prototype.pipe = function(dest, pipeOpts) {
      var src = this;
      var state = this._readableState;
      switch (state.pipesCount) {
        case 0:
          state.pipes = dest;
          break;
        case 1:
          state.pipes = [state.pipes, dest];
          break;
        default:
          state.pipes.push(dest);
          break;
      }
      state.pipesCount += 1;
      debug("pipe count=%d opts=%j", state.pipesCount, pipeOpts);
      var doEnd = (!pipeOpts || pipeOpts.end !== false) && dest !== process.stdout && dest !== process.stderr;
      var endFn = doEnd ? onend : unpipe;
      if (state.endEmitted) process.nextTick(endFn);
      else src.once("end", endFn);
      dest.on("unpipe", onunpipe);
      function onunpipe(readable, unpipeInfo) {
        debug("onunpipe");
        if (readable === src) {
          if (unpipeInfo && unpipeInfo.hasUnpiped === false) {
            unpipeInfo.hasUnpiped = true;
            cleanup();
          }
        }
      }
      function onend() {
        debug("onend");
        dest.end();
      }
      var ondrain = pipeOnDrain(src);
      dest.on("drain", ondrain);
      var cleanedUp = false;
      function cleanup() {
        debug("cleanup");
        dest.removeListener("close", onclose);
        dest.removeListener("finish", onfinish);
        dest.removeListener("drain", ondrain);
        dest.removeListener("error", onerror);
        dest.removeListener("unpipe", onunpipe);
        src.removeListener("end", onend);
        src.removeListener("end", unpipe);
        src.removeListener("data", ondata);
        cleanedUp = true;
        if (state.awaitDrain && (!dest._writableState || dest._writableState.needDrain)) ondrain();
      }
      src.on("data", ondata);
      function ondata(chunk) {
        debug("ondata");
        var ret = dest.write(chunk);
        debug("dest.write", ret);
        if (ret === false) {
          if ((state.pipesCount === 1 && state.pipes === dest || state.pipesCount > 1 && indexOf(state.pipes, dest) !== -1) && !cleanedUp) {
            debug("false write response, pause", state.awaitDrain);
            state.awaitDrain++;
          }
          src.pause();
        }
      }
      function onerror(er) {
        debug("onerror", er);
        unpipe();
        dest.removeListener("error", onerror);
        if (EElistenerCount(dest, "error") === 0) errorOrDestroy(dest, er);
      }
      prependListener(dest, "error", onerror);
      function onclose() {
        dest.removeListener("finish", onfinish);
        unpipe();
      }
      dest.once("close", onclose);
      function onfinish() {
        debug("onfinish");
        dest.removeListener("close", onclose);
        unpipe();
      }
      dest.once("finish", onfinish);
      function unpipe() {
        debug("unpipe");
        src.unpipe(dest);
      }
      dest.emit("pipe", src);
      if (!state.flowing) {
        debug("pipe resume");
        src.resume();
      }
      return dest;
    };
    function pipeOnDrain(src) {
      return function pipeOnDrainFunctionResult() {
        var state = src._readableState;
        debug("pipeOnDrain", state.awaitDrain);
        if (state.awaitDrain) state.awaitDrain--;
        if (state.awaitDrain === 0 && EElistenerCount(src, "data")) {
          state.flowing = true;
          flow(src);
        }
      };
    }
    Readable.prototype.unpipe = function(dest) {
      var state = this._readableState;
      var unpipeInfo = {
        hasUnpiped: false
      };
      if (state.pipesCount === 0) return this;
      if (state.pipesCount === 1) {
        if (dest && dest !== state.pipes) return this;
        if (!dest) dest = state.pipes;
        state.pipes = null;
        state.pipesCount = 0;
        state.flowing = false;
        if (dest) dest.emit("unpipe", this, unpipeInfo);
        return this;
      }
      if (!dest) {
        var dests = state.pipes;
        var len = state.pipesCount;
        state.pipes = null;
        state.pipesCount = 0;
        state.flowing = false;
        for (var i = 0; i < len; i++) dests[i].emit("unpipe", this, {
          hasUnpiped: false
        });
        return this;
      }
      var index = indexOf(state.pipes, dest);
      if (index === -1) return this;
      state.pipes.splice(index, 1);
      state.pipesCount -= 1;
      if (state.pipesCount === 1) state.pipes = state.pipes[0];
      dest.emit("unpipe", this, unpipeInfo);
      return this;
    };
    Readable.prototype.on = function(ev, fn) {
      var res = Stream.prototype.on.call(this, ev, fn);
      var state = this._readableState;
      if (ev === "data") {
        state.readableListening = this.listenerCount("readable") > 0;
        if (state.flowing !== false) this.resume();
      } else if (ev === "readable") {
        if (!state.endEmitted && !state.readableListening) {
          state.readableListening = state.needReadable = true;
          state.flowing = false;
          state.emittedReadable = false;
          debug("on readable", state.length, state.reading);
          if (state.length) {
            emitReadable(this);
          } else if (!state.reading) {
            process.nextTick(nReadingNextTick, this);
          }
        }
      }
      return res;
    };
    Readable.prototype.addListener = Readable.prototype.on;
    Readable.prototype.removeListener = function(ev, fn) {
      var res = Stream.prototype.removeListener.call(this, ev, fn);
      if (ev === "readable") {
        process.nextTick(updateReadableListening, this);
      }
      return res;
    };
    Readable.prototype.removeAllListeners = function(ev) {
      var res = Stream.prototype.removeAllListeners.apply(this, arguments);
      if (ev === "readable" || ev === void 0) {
        process.nextTick(updateReadableListening, this);
      }
      return res;
    };
    function updateReadableListening(self2) {
      var state = self2._readableState;
      state.readableListening = self2.listenerCount("readable") > 0;
      if (state.resumeScheduled && !state.paused) {
        state.flowing = true;
      } else if (self2.listenerCount("data") > 0) {
        self2.resume();
      }
    }
    function nReadingNextTick(self2) {
      debug("readable nexttick read 0");
      self2.read(0);
    }
    Readable.prototype.resume = function() {
      var state = this._readableState;
      if (!state.flowing) {
        debug("resume");
        state.flowing = !state.readableListening;
        resume(this, state);
      }
      state.paused = false;
      return this;
    };
    function resume(stream, state) {
      if (!state.resumeScheduled) {
        state.resumeScheduled = true;
        process.nextTick(resume_, stream, state);
      }
    }
    function resume_(stream, state) {
      debug("resume", state.reading);
      if (!state.reading) {
        stream.read(0);
      }
      state.resumeScheduled = false;
      stream.emit("resume");
      flow(stream);
      if (state.flowing && !state.reading) stream.read(0);
    }
    Readable.prototype.pause = function() {
      debug("call pause flowing=%j", this._readableState.flowing);
      if (this._readableState.flowing !== false) {
        debug("pause");
        this._readableState.flowing = false;
        this.emit("pause");
      }
      this._readableState.paused = true;
      return this;
    };
    function flow(stream) {
      var state = stream._readableState;
      debug("flow", state.flowing);
      while (state.flowing && stream.read() !== null) ;
    }
    Readable.prototype.wrap = function(stream) {
      var _this = this;
      var state = this._readableState;
      var paused = false;
      stream.on("end", function() {
        debug("wrapped end");
        if (state.decoder && !state.ended) {
          var chunk = state.decoder.end();
          if (chunk && chunk.length) _this.push(chunk);
        }
        _this.push(null);
      });
      stream.on("data", function(chunk) {
        debug("wrapped data");
        if (state.decoder) chunk = state.decoder.write(chunk);
        if (state.objectMode && (chunk === null || chunk === void 0)) return;
        else if (!state.objectMode && (!chunk || !chunk.length)) return;
        var ret = _this.push(chunk);
        if (!ret) {
          paused = true;
          stream.pause();
        }
      });
      for (var i in stream) {
        if (this[i] === void 0 && typeof stream[i] === "function") {
          this[i] = /* @__PURE__ */ function methodWrap(method) {
            return function methodWrapReturnFunction() {
              return stream[method].apply(stream, arguments);
            };
          }(i);
        }
      }
      for (var n = 0; n < kProxyEvents.length; n++) {
        stream.on(kProxyEvents[n], this.emit.bind(this, kProxyEvents[n]));
      }
      this._read = function(n2) {
        debug("wrapped _read", n2);
        if (paused) {
          paused = false;
          stream.resume();
        }
      };
      return this;
    };
    if (typeof Symbol === "function") {
      Readable.prototype[Symbol.asyncIterator] = function() {
        if (createReadableStreamAsyncIterator === void 0) {
          createReadableStreamAsyncIterator = require_async_iterator();
        }
        return createReadableStreamAsyncIterator(this);
      };
    }
    Object.defineProperty(Readable.prototype, "readableHighWaterMark", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        return this._readableState.highWaterMark;
      }
    });
    Object.defineProperty(Readable.prototype, "readableBuffer", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        return this._readableState && this._readableState.buffer;
      }
    });
    Object.defineProperty(Readable.prototype, "readableFlowing", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        return this._readableState.flowing;
      },
      set: function set(state) {
        if (this._readableState) {
          this._readableState.flowing = state;
        }
      }
    });
    Readable._fromList = fromList;
    Object.defineProperty(Readable.prototype, "readableLength", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        return this._readableState.length;
      }
    });
    function fromList(n, state) {
      if (state.length === 0) return null;
      var ret;
      if (state.objectMode) ret = state.buffer.shift();
      else if (!n || n >= state.length) {
        if (state.decoder) ret = state.buffer.join("");
        else if (state.buffer.length === 1) ret = state.buffer.first();
        else ret = state.buffer.concat(state.length);
        state.buffer.clear();
      } else {
        ret = state.buffer.consume(n, state.decoder);
      }
      return ret;
    }
    function endReadable(stream) {
      var state = stream._readableState;
      debug("endReadable", state.endEmitted);
      if (!state.endEmitted) {
        state.ended = true;
        process.nextTick(endReadableNT, state, stream);
      }
    }
    function endReadableNT(state, stream) {
      debug("endReadableNT", state.endEmitted, state.length);
      if (!state.endEmitted && state.length === 0) {
        state.endEmitted = true;
        stream.readable = false;
        stream.emit("end");
        if (state.autoDestroy) {
          var wState = stream._writableState;
          if (!wState || wState.autoDestroy && wState.finished) {
            stream.destroy();
          }
        }
      }
    }
    if (typeof Symbol === "function") {
      Readable.from = function(iterable, opts) {
        if (from === void 0) {
          from = require_from();
        }
        return from(Readable, iterable, opts);
      };
    }
    function indexOf(xs, x) {
      for (var i = 0, l = xs.length; i < l; i++) {
        if (xs[i] === x) return i;
      }
      return -1;
    }
  }
});

// node_modules/readable-stream/lib/_stream_duplex.js
var require_stream_duplex = __commonJS({
  "node_modules/readable-stream/lib/_stream_duplex.js"(exports2, module2) {
    "use strict";
    var objectKeys = Object.keys || function(obj) {
      var keys2 = [];
      for (var key in obj) keys2.push(key);
      return keys2;
    };
    module2.exports = Duplex;
    var Readable = require_stream_readable();
    var Writable = require_stream_writable();
    require_inherits()(Duplex, Readable);
    {
      keys = objectKeys(Writable.prototype);
      for (v = 0; v < keys.length; v++) {
        method = keys[v];
        if (!Duplex.prototype[method]) Duplex.prototype[method] = Writable.prototype[method];
      }
    }
    var keys;
    var method;
    var v;
    function Duplex(options) {
      if (!(this instanceof Duplex)) return new Duplex(options);
      Readable.call(this, options);
      Writable.call(this, options);
      this.allowHalfOpen = true;
      if (options) {
        if (options.readable === false) this.readable = false;
        if (options.writable === false) this.writable = false;
        if (options.allowHalfOpen === false) {
          this.allowHalfOpen = false;
          this.once("end", onend);
        }
      }
    }
    Object.defineProperty(Duplex.prototype, "writableHighWaterMark", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        return this._writableState.highWaterMark;
      }
    });
    Object.defineProperty(Duplex.prototype, "writableBuffer", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        return this._writableState && this._writableState.getBuffer();
      }
    });
    Object.defineProperty(Duplex.prototype, "writableLength", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        return this._writableState.length;
      }
    });
    function onend() {
      if (this._writableState.ended) return;
      process.nextTick(onEndNT, this);
    }
    function onEndNT(self2) {
      self2.end();
    }
    Object.defineProperty(Duplex.prototype, "destroyed", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        if (this._readableState === void 0 || this._writableState === void 0) {
          return false;
        }
        return this._readableState.destroyed && this._writableState.destroyed;
      },
      set: function set(value) {
        if (this._readableState === void 0 || this._writableState === void 0) {
          return;
        }
        this._readableState.destroyed = value;
        this._writableState.destroyed = value;
      }
    });
  }
});

// node_modules/readable-stream/lib/_stream_writable.js
var require_stream_writable = __commonJS({
  "node_modules/readable-stream/lib/_stream_writable.js"(exports2, module2) {
    "use strict";
    module2.exports = Writable;
    function CorkedRequest(state) {
      var _this = this;
      this.next = null;
      this.entry = null;
      this.finish = function() {
        onCorkedFinish(_this, state);
      };
    }
    var Duplex;
    Writable.WritableState = WritableState;
    var internalUtil = {
      deprecate: require_node()
    };
    var Stream = require_stream();
    var Buffer2 = require("buffer").Buffer;
    var OurUint8Array = (typeof global !== "undefined" ? global : typeof window !== "undefined" ? window : typeof self !== "undefined" ? self : {}).Uint8Array || function() {
    };
    function _uint8ArrayToBuffer(chunk) {
      return Buffer2.from(chunk);
    }
    function _isUint8Array(obj) {
      return Buffer2.isBuffer(obj) || obj instanceof OurUint8Array;
    }
    var destroyImpl = require_destroy();
    var _require = require_state();
    var getHighWaterMark = _require.getHighWaterMark;
    var _require$codes = require_errors2().codes;
    var ERR_INVALID_ARG_TYPE = _require$codes.ERR_INVALID_ARG_TYPE;
    var ERR_METHOD_NOT_IMPLEMENTED = _require$codes.ERR_METHOD_NOT_IMPLEMENTED;
    var ERR_MULTIPLE_CALLBACK = _require$codes.ERR_MULTIPLE_CALLBACK;
    var ERR_STREAM_CANNOT_PIPE = _require$codes.ERR_STREAM_CANNOT_PIPE;
    var ERR_STREAM_DESTROYED = _require$codes.ERR_STREAM_DESTROYED;
    var ERR_STREAM_NULL_VALUES = _require$codes.ERR_STREAM_NULL_VALUES;
    var ERR_STREAM_WRITE_AFTER_END = _require$codes.ERR_STREAM_WRITE_AFTER_END;
    var ERR_UNKNOWN_ENCODING = _require$codes.ERR_UNKNOWN_ENCODING;
    var errorOrDestroy = destroyImpl.errorOrDestroy;
    require_inherits()(Writable, Stream);
    function nop() {
    }
    function WritableState(options, stream, isDuplex) {
      Duplex = Duplex || require_stream_duplex();
      options = options || {};
      if (typeof isDuplex !== "boolean") isDuplex = stream instanceof Duplex;
      this.objectMode = !!options.objectMode;
      if (isDuplex) this.objectMode = this.objectMode || !!options.writableObjectMode;
      this.highWaterMark = getHighWaterMark(this, options, "writableHighWaterMark", isDuplex);
      this.finalCalled = false;
      this.needDrain = false;
      this.ending = false;
      this.ended = false;
      this.finished = false;
      this.destroyed = false;
      var noDecode = options.decodeStrings === false;
      this.decodeStrings = !noDecode;
      this.defaultEncoding = options.defaultEncoding || "utf8";
      this.length = 0;
      this.writing = false;
      this.corked = 0;
      this.sync = true;
      this.bufferProcessing = false;
      this.onwrite = function(er) {
        onwrite(stream, er);
      };
      this.writecb = null;
      this.writelen = 0;
      this.bufferedRequest = null;
      this.lastBufferedRequest = null;
      this.pendingcb = 0;
      this.prefinished = false;
      this.errorEmitted = false;
      this.emitClose = options.emitClose !== false;
      this.autoDestroy = !!options.autoDestroy;
      this.bufferedRequestCount = 0;
      this.corkedRequestsFree = new CorkedRequest(this);
    }
    WritableState.prototype.getBuffer = function getBuffer() {
      var current = this.bufferedRequest;
      var out = [];
      while (current) {
        out.push(current);
        current = current.next;
      }
      return out;
    };
    (function() {
      try {
        Object.defineProperty(WritableState.prototype, "buffer", {
          get: internalUtil.deprecate(function writableStateBufferGetter() {
            return this.getBuffer();
          }, "_writableState.buffer is deprecated. Use _writableState.getBuffer instead.", "DEP0003")
        });
      } catch (_) {
      }
    })();
    var realHasInstance;
    if (typeof Symbol === "function" && Symbol.hasInstance && typeof Function.prototype[Symbol.hasInstance] === "function") {
      realHasInstance = Function.prototype[Symbol.hasInstance];
      Object.defineProperty(Writable, Symbol.hasInstance, {
        value: function value(object) {
          if (realHasInstance.call(this, object)) return true;
          if (this !== Writable) return false;
          return object && object._writableState instanceof WritableState;
        }
      });
    } else {
      realHasInstance = function realHasInstance2(object) {
        return object instanceof this;
      };
    }
    function Writable(options) {
      Duplex = Duplex || require_stream_duplex();
      var isDuplex = this instanceof Duplex;
      if (!isDuplex && !realHasInstance.call(Writable, this)) return new Writable(options);
      this._writableState = new WritableState(options, this, isDuplex);
      this.writable = true;
      if (options) {
        if (typeof options.write === "function") this._write = options.write;
        if (typeof options.writev === "function") this._writev = options.writev;
        if (typeof options.destroy === "function") this._destroy = options.destroy;
        if (typeof options.final === "function") this._final = options.final;
      }
      Stream.call(this);
    }
    Writable.prototype.pipe = function() {
      errorOrDestroy(this, new ERR_STREAM_CANNOT_PIPE());
    };
    function writeAfterEnd(stream, cb) {
      var er = new ERR_STREAM_WRITE_AFTER_END();
      errorOrDestroy(stream, er);
      process.nextTick(cb, er);
    }
    function validChunk(stream, state, chunk, cb) {
      var er;
      if (chunk === null) {
        er = new ERR_STREAM_NULL_VALUES();
      } else if (typeof chunk !== "string" && !state.objectMode) {
        er = new ERR_INVALID_ARG_TYPE("chunk", ["string", "Buffer"], chunk);
      }
      if (er) {
        errorOrDestroy(stream, er);
        process.nextTick(cb, er);
        return false;
      }
      return true;
    }
    Writable.prototype.write = function(chunk, encoding, cb) {
      var state = this._writableState;
      var ret = false;
      var isBuf = !state.objectMode && _isUint8Array(chunk);
      if (isBuf && !Buffer2.isBuffer(chunk)) {
        chunk = _uint8ArrayToBuffer(chunk);
      }
      if (typeof encoding === "function") {
        cb = encoding;
        encoding = null;
      }
      if (isBuf) encoding = "buffer";
      else if (!encoding) encoding = state.defaultEncoding;
      if (typeof cb !== "function") cb = nop;
      if (state.ending) writeAfterEnd(this, cb);
      else if (isBuf || validChunk(this, state, chunk, cb)) {
        state.pendingcb++;
        ret = writeOrBuffer(this, state, isBuf, chunk, encoding, cb);
      }
      return ret;
    };
    Writable.prototype.cork = function() {
      this._writableState.corked++;
    };
    Writable.prototype.uncork = function() {
      var state = this._writableState;
      if (state.corked) {
        state.corked--;
        if (!state.writing && !state.corked && !state.bufferProcessing && state.bufferedRequest) clearBuffer(this, state);
      }
    };
    Writable.prototype.setDefaultEncoding = function setDefaultEncoding(encoding) {
      if (typeof encoding === "string") encoding = encoding.toLowerCase();
      if (!(["hex", "utf8", "utf-8", "ascii", "binary", "base64", "ucs2", "ucs-2", "utf16le", "utf-16le", "raw"].indexOf((encoding + "").toLowerCase()) > -1)) throw new ERR_UNKNOWN_ENCODING(encoding);
      this._writableState.defaultEncoding = encoding;
      return this;
    };
    Object.defineProperty(Writable.prototype, "writableBuffer", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        return this._writableState && this._writableState.getBuffer();
      }
    });
    function decodeChunk(state, chunk, encoding) {
      if (!state.objectMode && state.decodeStrings !== false && typeof chunk === "string") {
        chunk = Buffer2.from(chunk, encoding);
      }
      return chunk;
    }
    Object.defineProperty(Writable.prototype, "writableHighWaterMark", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        return this._writableState.highWaterMark;
      }
    });
    function writeOrBuffer(stream, state, isBuf, chunk, encoding, cb) {
      if (!isBuf) {
        var newChunk = decodeChunk(state, chunk, encoding);
        if (chunk !== newChunk) {
          isBuf = true;
          encoding = "buffer";
          chunk = newChunk;
        }
      }
      var len = state.objectMode ? 1 : chunk.length;
      state.length += len;
      var ret = state.length < state.highWaterMark;
      if (!ret) state.needDrain = true;
      if (state.writing || state.corked) {
        var last = state.lastBufferedRequest;
        state.lastBufferedRequest = {
          chunk,
          encoding,
          isBuf,
          callback: cb,
          next: null
        };
        if (last) {
          last.next = state.lastBufferedRequest;
        } else {
          state.bufferedRequest = state.lastBufferedRequest;
        }
        state.bufferedRequestCount += 1;
      } else {
        doWrite(stream, state, false, len, chunk, encoding, cb);
      }
      return ret;
    }
    function doWrite(stream, state, writev, len, chunk, encoding, cb) {
      state.writelen = len;
      state.writecb = cb;
      state.writing = true;
      state.sync = true;
      if (state.destroyed) state.onwrite(new ERR_STREAM_DESTROYED("write"));
      else if (writev) stream._writev(chunk, state.onwrite);
      else stream._write(chunk, encoding, state.onwrite);
      state.sync = false;
    }
    function onwriteError(stream, state, sync, er, cb) {
      --state.pendingcb;
      if (sync) {
        process.nextTick(cb, er);
        process.nextTick(finishMaybe, stream, state);
        stream._writableState.errorEmitted = true;
        errorOrDestroy(stream, er);
      } else {
        cb(er);
        stream._writableState.errorEmitted = true;
        errorOrDestroy(stream, er);
        finishMaybe(stream, state);
      }
    }
    function onwriteStateUpdate(state) {
      state.writing = false;
      state.writecb = null;
      state.length -= state.writelen;
      state.writelen = 0;
    }
    function onwrite(stream, er) {
      var state = stream._writableState;
      var sync = state.sync;
      var cb = state.writecb;
      if (typeof cb !== "function") throw new ERR_MULTIPLE_CALLBACK();
      onwriteStateUpdate(state);
      if (er) onwriteError(stream, state, sync, er, cb);
      else {
        var finished = needFinish(state) || stream.destroyed;
        if (!finished && !state.corked && !state.bufferProcessing && state.bufferedRequest) {
          clearBuffer(stream, state);
        }
        if (sync) {
          process.nextTick(afterWrite, stream, state, finished, cb);
        } else {
          afterWrite(stream, state, finished, cb);
        }
      }
    }
    function afterWrite(stream, state, finished, cb) {
      if (!finished) onwriteDrain(stream, state);
      state.pendingcb--;
      cb();
      finishMaybe(stream, state);
    }
    function onwriteDrain(stream, state) {
      if (state.length === 0 && state.needDrain) {
        state.needDrain = false;
        stream.emit("drain");
      }
    }
    function clearBuffer(stream, state) {
      state.bufferProcessing = true;
      var entry = state.bufferedRequest;
      if (stream._writev && entry && entry.next) {
        var l = state.bufferedRequestCount;
        var buffer = new Array(l);
        var holder = state.corkedRequestsFree;
        holder.entry = entry;
        var count = 0;
        var allBuffers = true;
        while (entry) {
          buffer[count] = entry;
          if (!entry.isBuf) allBuffers = false;
          entry = entry.next;
          count += 1;
        }
        buffer.allBuffers = allBuffers;
        doWrite(stream, state, true, state.length, buffer, "", holder.finish);
        state.pendingcb++;
        state.lastBufferedRequest = null;
        if (holder.next) {
          state.corkedRequestsFree = holder.next;
          holder.next = null;
        } else {
          state.corkedRequestsFree = new CorkedRequest(state);
        }
        state.bufferedRequestCount = 0;
      } else {
        while (entry) {
          var chunk = entry.chunk;
          var encoding = entry.encoding;
          var cb = entry.callback;
          var len = state.objectMode ? 1 : chunk.length;
          doWrite(stream, state, false, len, chunk, encoding, cb);
          entry = entry.next;
          state.bufferedRequestCount--;
          if (state.writing) {
            break;
          }
        }
        if (entry === null) state.lastBufferedRequest = null;
      }
      state.bufferedRequest = entry;
      state.bufferProcessing = false;
    }
    Writable.prototype._write = function(chunk, encoding, cb) {
      cb(new ERR_METHOD_NOT_IMPLEMENTED("_write()"));
    };
    Writable.prototype._writev = null;
    Writable.prototype.end = function(chunk, encoding, cb) {
      var state = this._writableState;
      if (typeof chunk === "function") {
        cb = chunk;
        chunk = null;
        encoding = null;
      } else if (typeof encoding === "function") {
        cb = encoding;
        encoding = null;
      }
      if (chunk !== null && chunk !== void 0) this.write(chunk, encoding);
      if (state.corked) {
        state.corked = 1;
        this.uncork();
      }
      if (!state.ending) endWritable(this, state, cb);
      return this;
    };
    Object.defineProperty(Writable.prototype, "writableLength", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        return this._writableState.length;
      }
    });
    function needFinish(state) {
      return state.ending && state.length === 0 && state.bufferedRequest === null && !state.finished && !state.writing;
    }
    function callFinal(stream, state) {
      stream._final(function(err) {
        state.pendingcb--;
        if (err) {
          errorOrDestroy(stream, err);
        }
        state.prefinished = true;
        stream.emit("prefinish");
        finishMaybe(stream, state);
      });
    }
    function prefinish(stream, state) {
      if (!state.prefinished && !state.finalCalled) {
        if (typeof stream._final === "function" && !state.destroyed) {
          state.pendingcb++;
          state.finalCalled = true;
          process.nextTick(callFinal, stream, state);
        } else {
          state.prefinished = true;
          stream.emit("prefinish");
        }
      }
    }
    function finishMaybe(stream, state) {
      var need = needFinish(state);
      if (need) {
        prefinish(stream, state);
        if (state.pendingcb === 0) {
          state.finished = true;
          stream.emit("finish");
          if (state.autoDestroy) {
            var rState = stream._readableState;
            if (!rState || rState.autoDestroy && rState.endEmitted) {
              stream.destroy();
            }
          }
        }
      }
      return need;
    }
    function endWritable(stream, state, cb) {
      state.ending = true;
      finishMaybe(stream, state);
      if (cb) {
        if (state.finished) process.nextTick(cb);
        else stream.once("finish", cb);
      }
      state.ended = true;
      stream.writable = false;
    }
    function onCorkedFinish(corkReq, state, err) {
      var entry = corkReq.entry;
      corkReq.entry = null;
      while (entry) {
        var cb = entry.callback;
        state.pendingcb--;
        cb(err);
        entry = entry.next;
      }
      state.corkedRequestsFree.next = corkReq;
    }
    Object.defineProperty(Writable.prototype, "destroyed", {
      // making it explicit this property is not enumerable
      // because otherwise some prototype manipulation in
      // userland will fail
      enumerable: false,
      get: function get() {
        if (this._writableState === void 0) {
          return false;
        }
        return this._writableState.destroyed;
      },
      set: function set(value) {
        if (!this._writableState) {
          return;
        }
        this._writableState.destroyed = value;
      }
    });
    Writable.prototype.destroy = destroyImpl.destroy;
    Writable.prototype._undestroy = destroyImpl.undestroy;
    Writable.prototype._destroy = function(err, cb) {
      cb(err);
    };
  }
});

// node_modules/winston-transport/modern.js
var require_modern = __commonJS({
  "node_modules/winston-transport/modern.js"(exports2, module2) {
    "use strict";
    var util = require("util");
    var Writable = require_stream_writable();
    var { LEVEL } = require_triple_beam();
    var TransportStream = module2.exports = function TransportStream2(options = {}) {
      Writable.call(this, { objectMode: true, highWaterMark: options.highWaterMark });
      this.format = options.format;
      this.level = options.level;
      this.handleExceptions = options.handleExceptions;
      this.handleRejections = options.handleRejections;
      this.silent = options.silent;
      if (options.log) this.log = options.log;
      if (options.logv) this.logv = options.logv;
      if (options.close) this.close = options.close;
      this.once("pipe", (logger) => {
        this.levels = logger.levels;
        this.parent = logger;
      });
      this.once("unpipe", (src) => {
        if (src === this.parent) {
          this.parent = null;
          if (this.close) {
            this.close();
          }
        }
      });
    };
    util.inherits(TransportStream, Writable);
    TransportStream.prototype._write = function _write(info, enc, callback) {
      if (this.silent || info.exception === true && !this.handleExceptions) {
        return callback(null);
      }
      const level = this.level || this.parent && this.parent.level;
      if (!level || this.levels[level] >= this.levels[info[LEVEL]]) {
        if (info && !this.format) {
          return this.log(info, callback);
        }
        let errState;
        let transformed;
        try {
          transformed = this.format.transform(Object.assign({}, info), this.format.options);
        } catch (err) {
          errState = err;
        }
        if (errState || !transformed) {
          callback();
          if (errState) throw errState;
          return;
        }
        return this.log(transformed, callback);
      }
      this._writableState.sync = false;
      return callback(null);
    };
    TransportStream.prototype._writev = function _writev(chunks, callback) {
      if (this.logv) {
        const infos = chunks.filter(this._accept, this);
        if (!infos.length) {
          return callback(null);
        }
        return this.logv(infos, callback);
      }
      for (let i = 0; i < chunks.length; i++) {
        if (!this._accept(chunks[i])) continue;
        if (chunks[i].chunk && !this.format) {
          this.log(chunks[i].chunk, chunks[i].callback);
          continue;
        }
        let errState;
        let transformed;
        try {
          transformed = this.format.transform(
            Object.assign({}, chunks[i].chunk),
            this.format.options
          );
        } catch (err) {
          errState = err;
        }
        if (errState || !transformed) {
          chunks[i].callback();
          if (errState) {
            callback(null);
            throw errState;
          }
        } else {
          this.log(transformed, chunks[i].callback);
        }
      }
      return callback(null);
    };
    TransportStream.prototype._accept = function _accept(write) {
      const info = write.chunk;
      if (this.silent) {
        return false;
      }
      const level = this.level || this.parent && this.parent.level;
      if (info.exception === true || !level || this.levels[level] >= this.levels[info[LEVEL]]) {
        if (this.handleExceptions || info.exception !== true) {
          return true;
        }
      }
      return false;
    };
    TransportStream.prototype._nop = function _nop() {
      return void 0;
    };
  }
});

// node_modules/winston-transport/legacy.js
var require_legacy = __commonJS({
  "node_modules/winston-transport/legacy.js"(exports2, module2) {
    "use strict";
    var util = require("util");
    var { LEVEL } = require_triple_beam();
    var TransportStream = require_modern();
    var LegacyTransportStream = module2.exports = function LegacyTransportStream2(options = {}) {
      TransportStream.call(this, options);
      if (!options.transport || typeof options.transport.log !== "function") {
        throw new Error("Invalid transport, must be an object with a log method.");
      }
      this.transport = options.transport;
      this.level = this.level || options.transport.level;
      this.handleExceptions = this.handleExceptions || options.transport.handleExceptions;
      this._deprecated();
      function transportError(err) {
        this.emit("error", err, this.transport);
      }
      if (!this.transport.__winstonError) {
        this.transport.__winstonError = transportError.bind(this);
        this.transport.on("error", this.transport.__winstonError);
      }
    };
    util.inherits(LegacyTransportStream, TransportStream);
    LegacyTransportStream.prototype._write = function _write(info, enc, callback) {
      if (this.silent || info.exception === true && !this.handleExceptions) {
        return callback(null);
      }
      if (!this.level || this.levels[this.level] >= this.levels[info[LEVEL]]) {
        this.transport.log(info[LEVEL], info.message, info, this._nop);
      }
      callback(null);
    };
    LegacyTransportStream.prototype._writev = function _writev(chunks, callback) {
      for (let i = 0; i < chunks.length; i++) {
        if (this._accept(chunks[i])) {
          this.transport.log(
            chunks[i].chunk[LEVEL],
            chunks[i].chunk.message,
            chunks[i].chunk,
            this._nop
          );
          chunks[i].callback();
        }
      }
      return callback(null);
    };
    LegacyTransportStream.prototype._deprecated = function _deprecated() {
      console.error([
        `${this.transport.name} is a legacy winston transport. Consider upgrading: `,
        "- Upgrade docs: https://github.com/winstonjs/winston/blob/master/UPGRADE-3.0.md"
      ].join("\n"));
    };
    LegacyTransportStream.prototype.close = function close() {
      if (this.transport.close) {
        this.transport.close();
      }
      if (this.transport.__winstonError) {
        this.transport.removeListener("error", this.transport.__winstonError);
        this.transport.__winstonError = null;
      }
    };
  }
});

// node_modules/winston-transport/index.js
var require_winston_transport = __commonJS({
  "node_modules/winston-transport/index.js"(exports2, module2) {
    "use strict";
    module2.exports = require_modern();
    module2.exports.LegacyTransportStream = require_legacy();
  }
});

// node_modules/winston/lib/winston/transports/console.js
var require_console = __commonJS({
  "node_modules/winston/lib/winston/transports/console.js"(exports2, module2) {
    "use strict";
    var os = require("os");
    var { LEVEL, MESSAGE } = require_triple_beam();
    var TransportStream = require_winston_transport();
    module2.exports = class Console extends TransportStream {
      /**
       * Constructor function for the Console transport object responsible for
       * persisting log messages and metadata to a terminal or TTY.
       * @param {!Object} [options={}] - Options for this instance.
       */
      constructor(options = {}) {
        super(options);
        this.name = options.name || "console";
        this.stderrLevels = this._stringArrayToSet(options.stderrLevels);
        this.consoleWarnLevels = this._stringArrayToSet(options.consoleWarnLevels);
        this.eol = typeof options.eol === "string" ? options.eol : os.EOL;
        this.forceConsole = options.forceConsole || false;
        this._consoleLog = console.log.bind(console);
        this._consoleWarn = console.warn.bind(console);
        this._consoleError = console.error.bind(console);
        this.setMaxListeners(30);
      }
      /**
       * Core logging method exposed to Winston.
       * @param {Object} info - TODO: add param description.
       * @param {Function} callback - TODO: add param description.
       * @returns {undefined}
       */
      log(info, callback) {
        setImmediate(() => this.emit("logged", info));
        if (this.stderrLevels[info[LEVEL]]) {
          if (console._stderr && !this.forceConsole) {
            console._stderr.write(`${info[MESSAGE]}${this.eol}`);
          } else {
            this._consoleError(info[MESSAGE]);
          }
          if (callback) {
            callback();
          }
          return;
        } else if (this.consoleWarnLevels[info[LEVEL]]) {
          if (console._stderr && !this.forceConsole) {
            console._stderr.write(`${info[MESSAGE]}${this.eol}`);
          } else {
            this._consoleWarn(info[MESSAGE]);
          }
          if (callback) {
            callback();
          }
          return;
        }
        if (console._stdout && !this.forceConsole) {
          console._stdout.write(`${info[MESSAGE]}${this.eol}`);
        } else {
          this._consoleLog(info[MESSAGE]);
        }
        if (callback) {
          callback();
        }
      }
      /**
       * Returns a Set-like object with strArray's elements as keys (each with the
       * value true).
       * @param {Array} strArray - Array of Set-elements as strings.
       * @param {?string} [errMsg] - Custom error message thrown on invalid input.
       * @returns {Object} - TODO: add return description.
       * @private
       */
      _stringArrayToSet(strArray, errMsg) {
        if (!strArray) return {};
        errMsg = errMsg || "Cannot make set from type other than Array of string elements";
        if (!Array.isArray(strArray)) {
          throw new Error(errMsg);
        }
        return strArray.reduce((set, el) => {
          if (typeof el !== "string") {
            throw new Error(errMsg);
          }
          set[el] = true;
          return set;
        }, {});
      }
    };
  }
});

// node_modules/async/internal/isArrayLike.js
var require_isArrayLike = __commonJS({
  "node_modules/async/internal/isArrayLike.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.default = isArrayLike;
    function isArrayLike(value) {
      return value && typeof value.length === "number" && value.length >= 0 && value.length % 1 === 0;
    }
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/initialParams.js
var require_initialParams = __commonJS({
  "node_modules/async/internal/initialParams.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.default = function(fn) {
      return function(...args) {
        var callback = args.pop();
        return fn.call(this, args, callback);
      };
    };
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/setImmediate.js
var require_setImmediate = __commonJS({
  "node_modules/async/internal/setImmediate.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.fallback = fallback;
    exports2.wrap = wrap;
    var hasQueueMicrotask = exports2.hasQueueMicrotask = typeof queueMicrotask === "function" && queueMicrotask;
    var hasSetImmediate = exports2.hasSetImmediate = typeof setImmediate === "function" && setImmediate;
    var hasNextTick = exports2.hasNextTick = typeof process === "object" && typeof process.nextTick === "function";
    function fallback(fn) {
      setTimeout(fn, 0);
    }
    function wrap(defer) {
      return (fn, ...args) => defer(() => fn(...args));
    }
    var _defer;
    if (hasQueueMicrotask) {
      _defer = queueMicrotask;
    } else if (hasSetImmediate) {
      _defer = setImmediate;
    } else if (hasNextTick) {
      _defer = process.nextTick;
    } else {
      _defer = fallback;
    }
    exports2.default = wrap(_defer);
  }
});

// node_modules/async/asyncify.js
var require_asyncify = __commonJS({
  "node_modules/async/asyncify.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.default = asyncify;
    var _initialParams = require_initialParams();
    var _initialParams2 = _interopRequireDefault(_initialParams);
    var _setImmediate = require_setImmediate();
    var _setImmediate2 = _interopRequireDefault(_setImmediate);
    var _wrapAsync = require_wrapAsync();
    function _interopRequireDefault(obj) {
      return obj && obj.__esModule ? obj : { default: obj };
    }
    function asyncify(func) {
      if ((0, _wrapAsync.isAsync)(func)) {
        return function(...args) {
          const callback = args.pop();
          const promise = func.apply(this, args);
          return handlePromise(promise, callback);
        };
      }
      return (0, _initialParams2.default)(function(args, callback) {
        var result;
        try {
          result = func.apply(this, args);
        } catch (e) {
          return callback(e);
        }
        if (result && typeof result.then === "function") {
          return handlePromise(result, callback);
        } else {
          callback(null, result);
        }
      });
    }
    function handlePromise(promise, callback) {
      return promise.then((value) => {
        invokeCallback(callback, null, value);
      }, (err) => {
        invokeCallback(callback, err && (err instanceof Error || err.message) ? err : new Error(err));
      });
    }
    function invokeCallback(callback, error, value) {
      try {
        callback(error, value);
      } catch (err) {
        (0, _setImmediate2.default)((e) => {
          throw e;
        }, err);
      }
    }
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/wrapAsync.js
var require_wrapAsync = __commonJS({
  "node_modules/async/internal/wrapAsync.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.isAsyncIterable = exports2.isAsyncGenerator = exports2.isAsync = void 0;
    var _asyncify = require_asyncify();
    var _asyncify2 = _interopRequireDefault(_asyncify);
    function _interopRequireDefault(obj) {
      return obj && obj.__esModule ? obj : { default: obj };
    }
    function isAsync(fn) {
      return fn[Symbol.toStringTag] === "AsyncFunction";
    }
    function isAsyncGenerator(fn) {
      return fn[Symbol.toStringTag] === "AsyncGenerator";
    }
    function isAsyncIterable(obj) {
      return typeof obj[Symbol.asyncIterator] === "function";
    }
    function wrapAsync(asyncFn) {
      if (typeof asyncFn !== "function") throw new Error("expected a function");
      return isAsync(asyncFn) ? (0, _asyncify2.default)(asyncFn) : asyncFn;
    }
    exports2.default = wrapAsync;
    exports2.isAsync = isAsync;
    exports2.isAsyncGenerator = isAsyncGenerator;
    exports2.isAsyncIterable = isAsyncIterable;
  }
});

// node_modules/async/internal/awaitify.js
var require_awaitify = __commonJS({
  "node_modules/async/internal/awaitify.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.default = awaitify;
    function awaitify(asyncFn, arity) {
      if (!arity) arity = asyncFn.length;
      if (!arity) throw new Error("arity is undefined");
      function awaitable(...args) {
        if (typeof args[arity - 1] === "function") {
          return asyncFn.apply(this, args);
        }
        return new Promise((resolve, reject) => {
          args[arity - 1] = (err, ...cbArgs) => {
            if (err) return reject(err);
            resolve(cbArgs.length > 1 ? cbArgs : cbArgs[0]);
          };
          asyncFn.apply(this, args);
        });
      }
      return awaitable;
    }
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/parallel.js
var require_parallel = __commonJS({
  "node_modules/async/internal/parallel.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    var _isArrayLike = require_isArrayLike();
    var _isArrayLike2 = _interopRequireDefault(_isArrayLike);
    var _wrapAsync = require_wrapAsync();
    var _wrapAsync2 = _interopRequireDefault(_wrapAsync);
    var _awaitify = require_awaitify();
    var _awaitify2 = _interopRequireDefault(_awaitify);
    function _interopRequireDefault(obj) {
      return obj && obj.__esModule ? obj : { default: obj };
    }
    exports2.default = (0, _awaitify2.default)((eachfn, tasks, callback) => {
      var results = (0, _isArrayLike2.default)(tasks) ? [] : {};
      eachfn(tasks, (task, key, taskCb) => {
        (0, _wrapAsync2.default)(task)((err, ...result) => {
          if (result.length < 2) {
            [result] = result;
          }
          results[key] = result;
          taskCb(err);
        });
      }, (err) => callback(err, results));
    }, 3);
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/once.js
var require_once = __commonJS({
  "node_modules/async/internal/once.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.default = once;
    function once(fn) {
      function wrapper(...args) {
        if (fn === null) return;
        var callFn = fn;
        fn = null;
        callFn.apply(this, args);
      }
      Object.assign(wrapper, fn);
      return wrapper;
    }
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/getIterator.js
var require_getIterator = __commonJS({
  "node_modules/async/internal/getIterator.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.default = function(coll) {
      return coll[Symbol.iterator] && coll[Symbol.iterator]();
    };
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/iterator.js
var require_iterator = __commonJS({
  "node_modules/async/internal/iterator.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.default = createIterator;
    var _isArrayLike = require_isArrayLike();
    var _isArrayLike2 = _interopRequireDefault(_isArrayLike);
    var _getIterator = require_getIterator();
    var _getIterator2 = _interopRequireDefault(_getIterator);
    function _interopRequireDefault(obj) {
      return obj && obj.__esModule ? obj : { default: obj };
    }
    function createArrayIterator(coll) {
      var i = -1;
      var len = coll.length;
      return function next() {
        return ++i < len ? { value: coll[i], key: i } : null;
      };
    }
    function createES2015Iterator(iterator) {
      var i = -1;
      return function next() {
        var item = iterator.next();
        if (item.done) return null;
        i++;
        return { value: item.value, key: i };
      };
    }
    function createObjectIterator(obj) {
      var okeys = obj ? Object.keys(obj) : [];
      var i = -1;
      var len = okeys.length;
      return function next() {
        var key = okeys[++i];
        if (key === "__proto__") {
          return next();
        }
        return i < len ? { value: obj[key], key } : null;
      };
    }
    function createIterator(coll) {
      if ((0, _isArrayLike2.default)(coll)) {
        return createArrayIterator(coll);
      }
      var iterator = (0, _getIterator2.default)(coll);
      return iterator ? createES2015Iterator(iterator) : createObjectIterator(coll);
    }
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/onlyOnce.js
var require_onlyOnce = __commonJS({
  "node_modules/async/internal/onlyOnce.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.default = onlyOnce;
    function onlyOnce(fn) {
      return function(...args) {
        if (fn === null) throw new Error("Callback was already called.");
        var callFn = fn;
        fn = null;
        callFn.apply(this, args);
      };
    }
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/breakLoop.js
var require_breakLoop = __commonJS({
  "node_modules/async/internal/breakLoop.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    var breakLoop = {};
    exports2.default = breakLoop;
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/asyncEachOfLimit.js
var require_asyncEachOfLimit = __commonJS({
  "node_modules/async/internal/asyncEachOfLimit.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.default = asyncEachOfLimit;
    var _breakLoop = require_breakLoop();
    var _breakLoop2 = _interopRequireDefault(_breakLoop);
    function _interopRequireDefault(obj) {
      return obj && obj.__esModule ? obj : { default: obj };
    }
    function asyncEachOfLimit(generator, limit, iteratee, callback) {
      let done = false;
      let canceled = false;
      let awaiting = false;
      let running = 0;
      let idx = 0;
      function replenish() {
        if (running >= limit || awaiting || done) return;
        awaiting = true;
        generator.next().then(({ value, done: iterDone }) => {
          if (canceled || done) return;
          awaiting = false;
          if (iterDone) {
            done = true;
            if (running <= 0) {
              callback(null);
            }
            return;
          }
          running++;
          iteratee(value, idx, iterateeCallback);
          idx++;
          replenish();
        }).catch(handleError);
      }
      function iterateeCallback(err, result) {
        running -= 1;
        if (canceled) return;
        if (err) return handleError(err);
        if (err === false) {
          done = true;
          canceled = true;
          return;
        }
        if (result === _breakLoop2.default || done && running <= 0) {
          done = true;
          return callback(null);
        }
        replenish();
      }
      function handleError(err) {
        if (canceled) return;
        awaiting = false;
        done = true;
        callback(err);
      }
      replenish();
    }
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/eachOfLimit.js
var require_eachOfLimit = __commonJS({
  "node_modules/async/internal/eachOfLimit.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    var _once = require_once();
    var _once2 = _interopRequireDefault(_once);
    var _iterator = require_iterator();
    var _iterator2 = _interopRequireDefault(_iterator);
    var _onlyOnce = require_onlyOnce();
    var _onlyOnce2 = _interopRequireDefault(_onlyOnce);
    var _wrapAsync = require_wrapAsync();
    var _asyncEachOfLimit = require_asyncEachOfLimit();
    var _asyncEachOfLimit2 = _interopRequireDefault(_asyncEachOfLimit);
    var _breakLoop = require_breakLoop();
    var _breakLoop2 = _interopRequireDefault(_breakLoop);
    function _interopRequireDefault(obj) {
      return obj && obj.__esModule ? obj : { default: obj };
    }
    exports2.default = (limit) => {
      return (obj, iteratee, callback) => {
        callback = (0, _once2.default)(callback);
        if (limit <= 0) {
          throw new RangeError("concurrency limit cannot be less than 1");
        }
        if (!obj) {
          return callback(null);
        }
        if ((0, _wrapAsync.isAsyncGenerator)(obj)) {
          return (0, _asyncEachOfLimit2.default)(obj, limit, iteratee, callback);
        }
        if ((0, _wrapAsync.isAsyncIterable)(obj)) {
          return (0, _asyncEachOfLimit2.default)(obj[Symbol.asyncIterator](), limit, iteratee, callback);
        }
        var nextElem = (0, _iterator2.default)(obj);
        var done = false;
        var canceled = false;
        var running = 0;
        var looping = false;
        function iterateeCallback(err, value) {
          if (canceled) return;
          running -= 1;
          if (err) {
            done = true;
            callback(err);
          } else if (err === false) {
            done = true;
            canceled = true;
          } else if (value === _breakLoop2.default || done && running <= 0) {
            done = true;
            return callback(null);
          } else if (!looping) {
            replenish();
          }
        }
        function replenish() {
          looping = true;
          while (running < limit && !done) {
            var elem = nextElem();
            if (elem === null) {
              done = true;
              if (running <= 0) {
                callback(null);
              }
              return;
            }
            running += 1;
            iteratee(elem.value, elem.key, (0, _onlyOnce2.default)(iterateeCallback));
          }
          looping = false;
        }
        replenish();
      };
    };
    module2.exports = exports2.default;
  }
});

// node_modules/async/eachOfLimit.js
var require_eachOfLimit2 = __commonJS({
  "node_modules/async/eachOfLimit.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    var _eachOfLimit2 = require_eachOfLimit();
    var _eachOfLimit3 = _interopRequireDefault(_eachOfLimit2);
    var _wrapAsync = require_wrapAsync();
    var _wrapAsync2 = _interopRequireDefault(_wrapAsync);
    var _awaitify = require_awaitify();
    var _awaitify2 = _interopRequireDefault(_awaitify);
    function _interopRequireDefault(obj) {
      return obj && obj.__esModule ? obj : { default: obj };
    }
    function eachOfLimit(coll, limit, iteratee, callback) {
      return (0, _eachOfLimit3.default)(limit)(coll, (0, _wrapAsync2.default)(iteratee), callback);
    }
    exports2.default = (0, _awaitify2.default)(eachOfLimit, 4);
    module2.exports = exports2.default;
  }
});

// node_modules/async/eachOfSeries.js
var require_eachOfSeries = __commonJS({
  "node_modules/async/eachOfSeries.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    var _eachOfLimit = require_eachOfLimit2();
    var _eachOfLimit2 = _interopRequireDefault(_eachOfLimit);
    var _awaitify = require_awaitify();
    var _awaitify2 = _interopRequireDefault(_awaitify);
    function _interopRequireDefault(obj) {
      return obj && obj.__esModule ? obj : { default: obj };
    }
    function eachOfSeries(coll, iteratee, callback) {
      return (0, _eachOfLimit2.default)(coll, 1, iteratee, callback);
    }
    exports2.default = (0, _awaitify2.default)(eachOfSeries, 3);
    module2.exports = exports2.default;
  }
});

// node_modules/async/series.js
var require_series = __commonJS({
  "node_modules/async/series.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.default = series;
    var _parallel2 = require_parallel();
    var _parallel3 = _interopRequireDefault(_parallel2);
    var _eachOfSeries = require_eachOfSeries();
    var _eachOfSeries2 = _interopRequireDefault(_eachOfSeries);
    function _interopRequireDefault(obj) {
      return obj && obj.__esModule ? obj : { default: obj };
    }
    function series(tasks, callback) {
      return (0, _parallel3.default)(_eachOfSeries2.default, tasks, callback);
    }
    module2.exports = exports2.default;
  }
});

// node_modules/readable-stream/lib/_stream_transform.js
var require_stream_transform = __commonJS({
  "node_modules/readable-stream/lib/_stream_transform.js"(exports2, module2) {
    "use strict";
    module2.exports = Transform;
    var _require$codes = require_errors2().codes;
    var ERR_METHOD_NOT_IMPLEMENTED = _require$codes.ERR_METHOD_NOT_IMPLEMENTED;
    var ERR_MULTIPLE_CALLBACK = _require$codes.ERR_MULTIPLE_CALLBACK;
    var ERR_TRANSFORM_ALREADY_TRANSFORMING = _require$codes.ERR_TRANSFORM_ALREADY_TRANSFORMING;
    var ERR_TRANSFORM_WITH_LENGTH_0 = _require$codes.ERR_TRANSFORM_WITH_LENGTH_0;
    var Duplex = require_stream_duplex();
    require_inherits()(Transform, Duplex);
    function afterTransform(er, data) {
      var ts = this._transformState;
      ts.transforming = false;
      var cb = ts.writecb;
      if (cb === null) {
        return this.emit("error", new ERR_MULTIPLE_CALLBACK());
      }
      ts.writechunk = null;
      ts.writecb = null;
      if (data != null)
        this.push(data);
      cb(er);
      var rs = this._readableState;
      rs.reading = false;
      if (rs.needReadable || rs.length < rs.highWaterMark) {
        this._read(rs.highWaterMark);
      }
    }
    function Transform(options) {
      if (!(this instanceof Transform)) return new Transform(options);
      Duplex.call(this, options);
      this._transformState = {
        afterTransform: afterTransform.bind(this),
        needTransform: false,
        transforming: false,
        writecb: null,
        writechunk: null,
        writeencoding: null
      };
      this._readableState.needReadable = true;
      this._readableState.sync = false;
      if (options) {
        if (typeof options.transform === "function") this._transform = options.transform;
        if (typeof options.flush === "function") this._flush = options.flush;
      }
      this.on("prefinish", prefinish);
    }
    function prefinish() {
      var _this = this;
      if (typeof this._flush === "function" && !this._readableState.destroyed) {
        this._flush(function(er, data) {
          done(_this, er, data);
        });
      } else {
        done(this, null, null);
      }
    }
    Transform.prototype.push = function(chunk, encoding) {
      this._transformState.needTransform = false;
      return Duplex.prototype.push.call(this, chunk, encoding);
    };
    Transform.prototype._transform = function(chunk, encoding, cb) {
      cb(new ERR_METHOD_NOT_IMPLEMENTED("_transform()"));
    };
    Transform.prototype._write = function(chunk, encoding, cb) {
      var ts = this._transformState;
      ts.writecb = cb;
      ts.writechunk = chunk;
      ts.writeencoding = encoding;
      if (!ts.transforming) {
        var rs = this._readableState;
        if (ts.needTransform || rs.needReadable || rs.length < rs.highWaterMark) this._read(rs.highWaterMark);
      }
    };
    Transform.prototype._read = function(n) {
      var ts = this._transformState;
      if (ts.writechunk !== null && !ts.transforming) {
        ts.transforming = true;
        this._transform(ts.writechunk, ts.writeencoding, ts.afterTransform);
      } else {
        ts.needTransform = true;
      }
    };
    Transform.prototype._destroy = function(err, cb) {
      Duplex.prototype._destroy.call(this, err, function(err2) {
        cb(err2);
      });
    };
    function done(stream, er, data) {
      if (er) return stream.emit("error", er);
      if (data != null)
        stream.push(data);
      if (stream._writableState.length) throw new ERR_TRANSFORM_WITH_LENGTH_0();
      if (stream._transformState.transforming) throw new ERR_TRANSFORM_ALREADY_TRANSFORMING();
      return stream.push(null);
    }
  }
});

// node_modules/readable-stream/lib/_stream_passthrough.js
var require_stream_passthrough = __commonJS({
  "node_modules/readable-stream/lib/_stream_passthrough.js"(exports2, module2) {
    "use strict";
    module2.exports = PassThrough;
    var Transform = require_stream_transform();
    require_inherits()(PassThrough, Transform);
    function PassThrough(options) {
      if (!(this instanceof PassThrough)) return new PassThrough(options);
      Transform.call(this, options);
    }
    PassThrough.prototype._transform = function(chunk, encoding, cb) {
      cb(null, chunk);
    };
  }
});

// node_modules/readable-stream/lib/internal/streams/pipeline.js
var require_pipeline = __commonJS({
  "node_modules/readable-stream/lib/internal/streams/pipeline.js"(exports2, module2) {
    "use strict";
    var eos;
    function once(callback) {
      var called = false;
      return function() {
        if (called) return;
        called = true;
        callback.apply(void 0, arguments);
      };
    }
    var _require$codes = require_errors2().codes;
    var ERR_MISSING_ARGS = _require$codes.ERR_MISSING_ARGS;
    var ERR_STREAM_DESTROYED = _require$codes.ERR_STREAM_DESTROYED;
    function noop(err) {
      if (err) throw err;
    }
    function isRequest(stream) {
      return stream.setHeader && typeof stream.abort === "function";
    }
    function destroyer(stream, reading, writing, callback) {
      callback = once(callback);
      var closed = false;
      stream.on("close", function() {
        closed = true;
      });
      if (eos === void 0) eos = require_end_of_stream();
      eos(stream, {
        readable: reading,
        writable: writing
      }, function(err) {
        if (err) return callback(err);
        closed = true;
        callback();
      });
      var destroyed = false;
      return function(err) {
        if (closed) return;
        if (destroyed) return;
        destroyed = true;
        if (isRequest(stream)) return stream.abort();
        if (typeof stream.destroy === "function") return stream.destroy();
        callback(err || new ERR_STREAM_DESTROYED("pipe"));
      };
    }
    function call(fn) {
      fn();
    }
    function pipe(from, to) {
      return from.pipe(to);
    }
    function popCallback(streams) {
      if (!streams.length) return noop;
      if (typeof streams[streams.length - 1] !== "function") return noop;
      return streams.pop();
    }
    function pipeline() {
      for (var _len = arguments.length, streams = new Array(_len), _key = 0; _key < _len; _key++) {
        streams[_key] = arguments[_key];
      }
      var callback = popCallback(streams);
      if (Array.isArray(streams[0])) streams = streams[0];
      if (streams.length < 2) {
        throw new ERR_MISSING_ARGS("streams");
      }
      var error;
      var destroys = streams.map(function(stream, i) {
        var reading = i < streams.length - 1;
        var writing = i > 0;
        return destroyer(stream, reading, writing, function(err) {
          if (!error) error = err;
          if (err) destroys.forEach(call);
          if (reading) return;
          destroys.forEach(call);
          callback(error);
        });
      });
      return streams.reduce(pipe);
    }
    module2.exports = pipeline;
  }
});

// node_modules/readable-stream/readable.js
var require_readable = __commonJS({
  "node_modules/readable-stream/readable.js"(exports2, module2) {
    var Stream = require("stream");
    if (process.env.READABLE_STREAM === "disable" && Stream) {
      module2.exports = Stream.Readable;
      Object.assign(module2.exports, Stream);
      module2.exports.Stream = Stream;
    } else {
      exports2 = module2.exports = require_stream_readable();
      exports2.Stream = Stream || exports2;
      exports2.Readable = exports2;
      exports2.Writable = require_stream_writable();
      exports2.Duplex = require_stream_duplex();
      exports2.Transform = require_stream_transform();
      exports2.PassThrough = require_stream_passthrough();
      exports2.finished = require_end_of_stream();
      exports2.pipeline = require_pipeline();
    }
  }
});

// node_modules/@dabh/diagnostics/diagnostics.js
var require_diagnostics = __commonJS({
  "node_modules/@dabh/diagnostics/diagnostics.js"(exports2, module2) {
    var adapters = [];
    var modifiers = [];
    var logger = function devnull() {
    };
    function use(adapter) {
      if (~adapters.indexOf(adapter)) return false;
      adapters.push(adapter);
      return true;
    }
    function set(custom) {
      logger = custom;
    }
    function enabled(namespace) {
      var async = [];
      for (var i = 0; i < adapters.length; i++) {
        if (adapters[i].async) {
          async.push(adapters[i]);
          continue;
        }
        if (adapters[i](namespace)) return true;
      }
      if (!async.length) return false;
      return new Promise(function pinky(resolve) {
        Promise.all(
          async.map(function prebind(fn) {
            return fn(namespace);
          })
        ).then(function resolved(values) {
          resolve(values.some(Boolean));
        });
      });
    }
    function modify(fn) {
      if (~modifiers.indexOf(fn)) return false;
      modifiers.push(fn);
      return true;
    }
    function write() {
      logger.apply(logger, arguments);
    }
    function process2(message) {
      for (var i = 0; i < modifiers.length; i++) {
        message = modifiers[i].apply(modifiers[i], arguments);
      }
      return message;
    }
    function introduce(fn, options) {
      var has = Object.prototype.hasOwnProperty;
      for (var key in options) {
        if (has.call(options, key)) {
          fn[key] = options[key];
        }
      }
      return fn;
    }
    function nope(options) {
      options.enabled = false;
      options.modify = modify;
      options.set = set;
      options.use = use;
      return introduce(function diagnopes() {
        return false;
      }, options);
    }
    function yep(options) {
      function diagnostics() {
        var args = Array.prototype.slice.call(arguments, 0);
        write.call(write, options, process2(args, options));
        return true;
      }
      options.enabled = true;
      options.modify = modify;
      options.set = set;
      options.use = use;
      return introduce(diagnostics, options);
    }
    module2.exports = function create(diagnostics) {
      diagnostics.introduce = introduce;
      diagnostics.enabled = enabled;
      diagnostics.process = process2;
      diagnostics.modify = modify;
      diagnostics.write = write;
      diagnostics.nope = nope;
      diagnostics.yep = yep;
      diagnostics.set = set;
      diagnostics.use = use;
      return diagnostics;
    };
  }
});

// node_modules/@dabh/diagnostics/node/production.js
var require_production = __commonJS({
  "node_modules/@dabh/diagnostics/node/production.js"(exports2, module2) {
    var create = require_diagnostics();
    var diagnostics = create(function prod(namespace, options) {
      options = options || {};
      options.namespace = namespace;
      options.prod = true;
      options.dev = false;
      if (!(options.force || prod.force)) return prod.nope(options);
      return prod.yep(options);
    });
    module2.exports = diagnostics;
  }
});

// node_modules/@dabh/diagnostics/node/index.js
var require_node2 = __commonJS({
  "node_modules/@dabh/diagnostics/node/index.js"(exports2, module2) {
    if (true) {
      module2.exports = require_production();
    } else {
      module2.exports = null;
    }
  }
});

// node_modules/winston/lib/winston/tail-file.js
var require_tail_file = __commonJS({
  "node_modules/winston/lib/winston/tail-file.js"(exports2, module2) {
    "use strict";
    var fs = require("fs");
    var { StringDecoder } = require("string_decoder");
    var { Stream } = require_readable();
    function noop() {
    }
    module2.exports = (options, iter) => {
      const buffer = Buffer.alloc(64 * 1024);
      const decode = new StringDecoder("utf8");
      const stream = new Stream();
      let buff = "";
      let pos = 0;
      let row = 0;
      if (options.start === -1) {
        delete options.start;
      }
      stream.readable = true;
      stream.destroy = () => {
        stream.destroyed = true;
        stream.emit("end");
        stream.emit("close");
      };
      fs.open(options.file, "a+", "0644", (err, fd) => {
        if (err) {
          if (!iter) {
            stream.emit("error", err);
          } else {
            iter(err);
          }
          stream.destroy();
          return;
        }
        (function read() {
          if (stream.destroyed) {
            fs.close(fd, noop);
            return;
          }
          return fs.read(fd, buffer, 0, buffer.length, pos, (error, bytes) => {
            if (error) {
              if (!iter) {
                stream.emit("error", error);
              } else {
                iter(error);
              }
              stream.destroy();
              return;
            }
            if (!bytes) {
              if (buff) {
                if (options.start == null || row > options.start) {
                  if (!iter) {
                    stream.emit("line", buff);
                  } else {
                    iter(null, buff);
                  }
                }
                row++;
                buff = "";
              }
              return setTimeout(read, 1e3);
            }
            let data = decode.write(buffer.slice(0, bytes));
            if (!iter) {
              stream.emit("data", data);
            }
            data = (buff + data).split(/\n+/);
            const l = data.length - 1;
            let i = 0;
            for (; i < l; i++) {
              if (options.start == null || row > options.start) {
                if (!iter) {
                  stream.emit("line", data[i]);
                } else {
                  iter(null, data[i]);
                }
              }
              row++;
            }
            buff = data[l];
            pos += bytes;
            return read();
          });
        })();
      });
      if (!iter) {
        return stream;
      }
      return stream.destroy;
    };
  }
});

// node_modules/winston/lib/winston/transports/file.js
var require_file = __commonJS({
  "node_modules/winston/lib/winston/transports/file.js"(exports2, module2) {
    "use strict";
    var fs = require("fs");
    var path = require("path");
    var asyncSeries = require_series();
    var zlib = require("zlib");
    var { MESSAGE } = require_triple_beam();
    var { Stream, PassThrough } = require_readable();
    var TransportStream = require_winston_transport();
    var debug = require_node2()("winston:file");
    var os = require("os");
    var tailFile = require_tail_file();
    module2.exports = class File extends TransportStream {
      /**
       * Constructor function for the File transport object responsible for
       * persisting log messages and metadata to one or more files.
       * @param {Object} options - Options for this instance.
       */
      constructor(options = {}) {
        super(options);
        this.name = options.name || "file";
        function throwIf(target, ...args) {
          args.slice(1).forEach((name) => {
            if (options[name]) {
              throw new Error(`Cannot set ${name} and ${target} together`);
            }
          });
        }
        this._stream = new PassThrough();
        this._stream.setMaxListeners(30);
        this._onError = this._onError.bind(this);
        if (options.filename || options.dirname) {
          throwIf("filename or dirname", "stream");
          this._basename = this.filename = options.filename ? path.basename(options.filename) : "winston.log";
          this.dirname = options.dirname || path.dirname(options.filename);
          this.options = options.options || { flags: "a" };
        } else if (options.stream) {
          console.warn("options.stream will be removed in winston@4. Use winston.transports.Stream");
          throwIf("stream", "filename", "maxsize");
          this._dest = this._stream.pipe(this._setupStream(options.stream));
          this.dirname = path.dirname(this._dest.path);
        } else {
          throw new Error("Cannot log to file without filename or stream.");
        }
        this.maxsize = options.maxsize || null;
        this.rotationFormat = options.rotationFormat || false;
        this.zippedArchive = options.zippedArchive || false;
        this.maxFiles = options.maxFiles || null;
        this.eol = typeof options.eol === "string" ? options.eol : os.EOL;
        this.tailable = options.tailable || false;
        this.lazy = options.lazy || false;
        this._size = 0;
        this._pendingSize = 0;
        this._created = 0;
        this._drain = false;
        this._opening = false;
        this._ending = false;
        this._fileExist = false;
        if (this.dirname) this._createLogDirIfNotExist(this.dirname);
        if (!this.lazy) this.open();
      }
      finishIfEnding() {
        if (this._ending) {
          if (this._opening) {
            this.once("open", () => {
              this._stream.once("finish", () => this.emit("finish"));
              setImmediate(() => this._stream.end());
            });
          } else {
            this._stream.once("finish", () => this.emit("finish"));
            setImmediate(() => this._stream.end());
          }
        }
      }
      /**
       * Core logging method exposed to Winston. Metadata is optional.
       * @param {Object} info - TODO: add param description.
       * @param {Function} callback - TODO: add param description.
       * @returns {undefined}
       */
      log(info, callback = () => {
      }) {
        if (this.silent) {
          callback();
          return true;
        }
        if (this._drain) {
          this._stream.once("drain", () => {
            this._drain = false;
            this.log(info, callback);
          });
          return;
        }
        if (this._rotate) {
          this._stream.once("rotate", () => {
            this._rotate = false;
            this.log(info, callback);
          });
          return;
        }
        if (this.lazy) {
          if (!this._fileExist) {
            if (!this._opening) {
              this.open();
            }
            this.once("open", () => {
              this._fileExist = true;
              this.log(info, callback);
              return;
            });
            return;
          }
          if (this._needsNewFile(this._pendingSize)) {
            this._dest.once("close", () => {
              if (!this._opening) {
                this.open();
              }
              this.once("open", () => {
                this.log(info, callback);
                return;
              });
              return;
            });
            return;
          }
        }
        const output = `${info[MESSAGE]}${this.eol}`;
        const bytes = Buffer.byteLength(output);
        function logged() {
          this._size += bytes;
          this._pendingSize -= bytes;
          debug("logged %s %s", this._size, output);
          this.emit("logged", info);
          if (this._rotate) {
            return;
          }
          if (this._opening) {
            return;
          }
          if (!this._needsNewFile()) {
            return;
          }
          if (this.lazy) {
            this._endStream(() => {
              this.emit("fileclosed");
            });
            return;
          }
          this._rotate = true;
          this._endStream(() => this._rotateFile());
        }
        this._pendingSize += bytes;
        if (this._opening && !this.rotatedWhileOpening && this._needsNewFile(this._size + this._pendingSize)) {
          this.rotatedWhileOpening = true;
        }
        const written = this._stream.write(output, logged.bind(this));
        if (!written) {
          this._drain = true;
          this._stream.once("drain", () => {
            this._drain = false;
            callback();
          });
        } else {
          callback();
        }
        debug("written", written, this._drain);
        this.finishIfEnding();
        return written;
      }
      /**
       * Query the transport. Options object is optional.
       * @param {Object} options - Loggly-like query options for this instance.
       * @param {function} callback - Continuation to respond to when complete.
       * TODO: Refactor me.
       */
      query(options, callback) {
        if (typeof options === "function") {
          callback = options;
          options = {};
        }
        options = normalizeQuery(options);
        const file = path.join(this.dirname, this.filename);
        let buff = "";
        let results = [];
        let row = 0;
        const stream = fs.createReadStream(file, {
          encoding: "utf8"
        });
        stream.on("error", (err) => {
          if (stream.readable) {
            stream.destroy();
          }
          if (!callback) {
            return;
          }
          return err.code !== "ENOENT" ? callback(err) : callback(null, results);
        });
        stream.on("data", (data) => {
          data = (buff + data).split(/\n+/);
          const l = data.length - 1;
          let i = 0;
          for (; i < l; i++) {
            if (!options.start || row >= options.start) {
              add(data[i]);
            }
            row++;
          }
          buff = data[l];
        });
        stream.on("close", () => {
          if (buff) {
            add(buff, true);
          }
          if (options.order === "desc") {
            results = results.reverse();
          }
          if (callback) callback(null, results);
        });
        function add(buff2, attempt) {
          try {
            const log = JSON.parse(buff2);
            if (check(log)) {
              push(log);
            }
          } catch (e) {
            if (!attempt) {
              stream.emit("error", e);
            }
          }
        }
        function push(log) {
          if (options.rows && results.length >= options.rows && options.order !== "desc") {
            if (stream.readable) {
              stream.destroy();
            }
            return;
          }
          if (options.fields) {
            log = options.fields.reduce((obj, key) => {
              obj[key] = log[key];
              return obj;
            }, {});
          }
          if (options.order === "desc") {
            if (results.length >= options.rows) {
              results.shift();
            }
          }
          results.push(log);
        }
        function check(log) {
          if (!log) {
            return;
          }
          if (typeof log !== "object") {
            return;
          }
          const time = new Date(log.timestamp);
          if (options.from && time < options.from || options.until && time > options.until || options.level && options.level !== log.level) {
            return;
          }
          return true;
        }
        function normalizeQuery(options2) {
          options2 = options2 || {};
          options2.rows = options2.rows || options2.limit || 10;
          options2.start = options2.start || 0;
          options2.until = options2.until || /* @__PURE__ */ new Date();
          if (typeof options2.until !== "object") {
            options2.until = new Date(options2.until);
          }
          options2.from = options2.from || options2.until - 24 * 60 * 60 * 1e3;
          if (typeof options2.from !== "object") {
            options2.from = new Date(options2.from);
          }
          options2.order = options2.order || "desc";
          return options2;
        }
      }
      /**
       * Returns a log stream for this transport. Options object is optional.
       * @param {Object} options - Stream options for this instance.
       * @returns {Stream} - TODO: add return description.
       * TODO: Refactor me.
       */
      stream(options = {}) {
        const file = path.join(this.dirname, this.filename);
        const stream = new Stream();
        const tail = {
          file,
          start: options.start
        };
        stream.destroy = tailFile(tail, (err, line) => {
          if (err) {
            return stream.emit("error", err);
          }
          try {
            stream.emit("data", line);
            line = JSON.parse(line);
            stream.emit("log", line);
          } catch (e) {
            stream.emit("error", e);
          }
        });
        return stream;
      }
      /**
       * Checks to see the filesize of.
       * @returns {undefined}
       */
      open() {
        if (!this.filename) return;
        if (this._opening) return;
        this._opening = true;
        this.stat((err, size) => {
          if (err) {
            return this.emit("error", err);
          }
          debug("stat done: %s { size: %s }", this.filename, size);
          this._size = size;
          this._dest = this._createStream(this._stream);
          this._opening = false;
          this.once("open", () => {
            if (!this._stream.emit("rotate")) {
              this._rotate = false;
            }
          });
        });
      }
      /**
       * Stat the file and assess information in order to create the proper stream.
       * @param {function} callback - TODO: add param description.
       * @returns {undefined}
       */
      stat(callback) {
        const target = this._getFile();
        const fullpath = path.join(this.dirname, target);
        fs.stat(fullpath, (err, stat) => {
          if (err && err.code === "ENOENT") {
            debug("ENOENT\xA0ok", fullpath);
            this.filename = target;
            return callback(null, 0);
          }
          if (err) {
            debug(`err ${err.code} ${fullpath}`);
            return callback(err);
          }
          if (!stat || this._needsNewFile(stat.size)) {
            return this._incFile(() => this.stat(callback));
          }
          this.filename = target;
          callback(null, stat.size);
        });
      }
      /**
       * Closes the stream associated with this instance.
       * @param {function} cb - TODO: add param description.
       * @returns {undefined}
       */
      close(cb) {
        if (!this._stream) {
          return;
        }
        this._stream.end(() => {
          if (cb) {
            cb();
          }
          this.emit("flush");
          this.emit("closed");
        });
      }
      /**
       * TODO: add method description.
       * @param {number} size - TODO: add param description.
       * @returns {undefined}
       */
      _needsNewFile(size) {
        size = size || this._size;
        return this.maxsize && size >= this.maxsize;
      }
      /**
       * TODO: add method description.
       * @param {Error} err - TODO: add param description.
       * @returns {undefined}
       */
      _onError(err) {
        this.emit("error", err);
      }
      /**
       * TODO: add method description.
       * @param {Stream} stream - TODO: add param description.
       * @returns {mixed} - TODO: add return description.
       */
      _setupStream(stream) {
        stream.on("error", this._onError);
        return stream;
      }
      /**
       * TODO: add method description.
       * @param {Stream} stream - TODO: add param description.
       * @returns {mixed} - TODO: add return description.
       */
      _cleanupStream(stream) {
        stream.removeListener("error", this._onError);
        stream.destroy();
        return stream;
      }
      /**
       * TODO: add method description.
       */
      _rotateFile() {
        this._incFile(() => this.open());
      }
      /**
       * Unpipe from the stream that has been marked as full and end it so it
       * flushes to disk.
       *
       * @param {function} callback - Callback for when the current file has closed.
       * @private
       */
      _endStream(callback = () => {
      }) {
        if (this._dest) {
          this._stream.unpipe(this._dest);
          this._dest.end(() => {
            this._cleanupStream(this._dest);
            callback();
          });
        } else {
          callback();
        }
      }
      /**
       * Returns the WritableStream for the active file on this instance. If we
       * should gzip the file then a zlib stream is returned.
       *
       * @param {ReadableStream} source –PassThrough to pipe to the file when open.
       * @returns {WritableStream} Stream that writes to disk for the active file.
       */
      _createStream(source) {
        const fullpath = path.join(this.dirname, this.filename);
        debug("create stream start", fullpath, this.options);
        const dest = fs.createWriteStream(fullpath, this.options).on("error", (err) => debug(err)).on("close", () => debug("close", dest.path, dest.bytesWritten)).on("open", () => {
          debug("file open ok", fullpath);
          this.emit("open", fullpath);
          source.pipe(dest);
          if (this.rotatedWhileOpening) {
            this._stream = new PassThrough();
            this._stream.setMaxListeners(30);
            this._rotateFile();
            this.rotatedWhileOpening = false;
            this._cleanupStream(dest);
            source.end();
          }
        });
        debug("create stream ok", fullpath);
        return dest;
      }
      /**
       * TODO: add method description.
       * @param {function} callback - TODO: add param description.
       * @returns {undefined}
       */
      _incFile(callback) {
        debug("_incFile", this.filename);
        const ext = path.extname(this._basename);
        const basename = path.basename(this._basename, ext);
        const tasks = [];
        if (this.zippedArchive) {
          tasks.push(
            function(cb) {
              const num = this._created > 0 && !this.tailable ? this._created : "";
              this._compressFile(
                path.join(this.dirname, `${basename}${num}${ext}`),
                path.join(this.dirname, `${basename}${num}${ext}.gz`),
                cb
              );
            }.bind(this)
          );
        }
        tasks.push(
          function(cb) {
            if (!this.tailable) {
              this._created += 1;
              this._checkMaxFilesIncrementing(ext, basename, cb);
            } else {
              this._checkMaxFilesTailable(ext, basename, cb);
            }
          }.bind(this)
        );
        asyncSeries(tasks, callback);
      }
      /**
       * Gets the next filename to use for this instance in the case that log
       * filesizes are being capped.
       * @returns {string} - TODO: add return description.
       * @private
       */
      _getFile() {
        const ext = path.extname(this._basename);
        const basename = path.basename(this._basename, ext);
        const isRotation = this.rotationFormat ? this.rotationFormat() : this._created;
        return !this.tailable && this._created ? `${basename}${isRotation}${ext}` : `${basename}${ext}`;
      }
      /**
       * Increment the number of files created or checked by this instance.
       * @param {mixed} ext - TODO: add param description.
       * @param {mixed} basename - TODO: add param description.
       * @param {mixed} callback - TODO: add param description.
       * @returns {undefined}
       * @private
       */
      _checkMaxFilesIncrementing(ext, basename, callback) {
        if (!this.maxFiles || this._created < this.maxFiles) {
          return setImmediate(callback);
        }
        const oldest = this._created - this.maxFiles;
        const isOldest = oldest !== 0 ? oldest : "";
        const isZipped = this.zippedArchive ? ".gz" : "";
        const filePath = `${basename}${isOldest}${ext}${isZipped}`;
        const target = path.join(this.dirname, filePath);
        fs.unlink(target, callback);
      }
      /**
       * Roll files forward based on integer, up to maxFiles. e.g. if base if
       * file.log and it becomes oversized, roll to file1.log, and allow file.log
       * to be re-used. If file is oversized again, roll file1.log to file2.log,
       * roll file.log to file1.log, and so on.
       * @param {mixed} ext - TODO: add param description.
       * @param {mixed} basename - TODO: add param description.
       * @param {mixed} callback - TODO: add param description.
       * @returns {undefined}
       * @private
       */
      _checkMaxFilesTailable(ext, basename, callback) {
        const tasks = [];
        if (!this.maxFiles) {
          return;
        }
        const isZipped = this.zippedArchive ? ".gz" : "";
        for (let x = this.maxFiles - 1; x > 1; x--) {
          tasks.push(function(i, cb) {
            let fileName = `${basename}${i - 1}${ext}${isZipped}`;
            const tmppath = path.join(this.dirname, fileName);
            fs.exists(tmppath, (exists) => {
              if (!exists) {
                return cb(null);
              }
              fileName = `${basename}${i}${ext}${isZipped}`;
              fs.rename(tmppath, path.join(this.dirname, fileName), cb);
            });
          }.bind(this, x));
        }
        asyncSeries(tasks, () => {
          fs.rename(
            path.join(this.dirname, `${basename}${ext}${isZipped}`),
            path.join(this.dirname, `${basename}1${ext}${isZipped}`),
            callback
          );
        });
      }
      /**
       * Compresses src to dest with gzip and unlinks src
       * @param {string} src - path to source file.
       * @param {string} dest - path to zipped destination file.
       * @param {Function} callback - callback called after file has been compressed.
       * @returns {undefined}
       * @private
       */
      _compressFile(src, dest, callback) {
        fs.access(src, fs.F_OK, (err) => {
          if (err) {
            return callback();
          }
          var gzip = zlib.createGzip();
          var inp = fs.createReadStream(src);
          var out = fs.createWriteStream(dest);
          out.on("finish", () => {
            fs.unlink(src, callback);
          });
          inp.pipe(gzip).pipe(out);
        });
      }
      _createLogDirIfNotExist(dirPath) {
        if (!fs.existsSync(dirPath)) {
          fs.mkdirSync(dirPath, { recursive: true });
        }
      }
    };
  }
});

// node_modules/winston/lib/winston/transports/http.js
var require_http = __commonJS({
  "node_modules/winston/lib/winston/transports/http.js"(exports2, module2) {
    "use strict";
    var http = require("http");
    var https = require("https");
    var { Stream } = require_readable();
    var TransportStream = require_winston_transport();
    var { configure } = require_safe_stable_stringify();
    module2.exports = class Http extends TransportStream {
      /**
       * Constructor function for the Http transport object responsible for
       * persisting log messages and metadata to a terminal or TTY.
       * @param {!Object} [options={}] - Options for this instance.
       */
      // eslint-disable-next-line max-statements
      constructor(options = {}) {
        super(options);
        this.options = options;
        this.name = options.name || "http";
        this.ssl = !!options.ssl;
        this.host = options.host || "localhost";
        this.port = options.port;
        this.auth = options.auth;
        this.path = options.path || "";
        this.maximumDepth = options.maximumDepth;
        this.agent = options.agent;
        this.headers = options.headers || {};
        this.headers["content-type"] = "application/json";
        this.batch = options.batch || false;
        this.batchInterval = options.batchInterval || 5e3;
        this.batchCount = options.batchCount || 10;
        this.batchOptions = [];
        this.batchTimeoutID = -1;
        this.batchCallback = {};
        if (!this.port) {
          this.port = this.ssl ? 443 : 80;
        }
      }
      /**
       * Core logging method exposed to Winston.
       * @param {Object} info - TODO: add param description.
       * @param {function} callback - TODO: add param description.
       * @returns {undefined}
       */
      log(info, callback) {
        this._request(info, null, null, (err, res) => {
          if (res && res.statusCode !== 200) {
            err = new Error(`Invalid HTTP Status Code: ${res.statusCode}`);
          }
          if (err) {
            this.emit("warn", err);
          } else {
            this.emit("logged", info);
          }
        });
        if (callback) {
          setImmediate(callback);
        }
      }
      /**
       * Query the transport. Options object is optional.
       * @param {Object} options -  Loggly-like query options for this instance.
       * @param {function} callback - Continuation to respond to when complete.
       * @returns {undefined}
       */
      query(options, callback) {
        if (typeof options === "function") {
          callback = options;
          options = {};
        }
        options = {
          method: "query",
          params: this.normalizeQuery(options)
        };
        const auth = options.params.auth || null;
        delete options.params.auth;
        const path = options.params.path || null;
        delete options.params.path;
        this._request(options, auth, path, (err, res, body) => {
          if (res && res.statusCode !== 200) {
            err = new Error(`Invalid HTTP Status Code: ${res.statusCode}`);
          }
          if (err) {
            return callback(err);
          }
          if (typeof body === "string") {
            try {
              body = JSON.parse(body);
            } catch (e) {
              return callback(e);
            }
          }
          callback(null, body);
        });
      }
      /**
       * Returns a log stream for this transport. Options object is optional.
       * @param {Object} options - Stream options for this instance.
       * @returns {Stream} - TODO: add return description
       */
      stream(options = {}) {
        const stream = new Stream();
        options = {
          method: "stream",
          params: options
        };
        const path = options.params.path || null;
        delete options.params.path;
        const auth = options.params.auth || null;
        delete options.params.auth;
        let buff = "";
        const req = this._request(options, auth, path);
        stream.destroy = () => req.destroy();
        req.on("data", (data) => {
          data = (buff + data).split(/\n+/);
          const l = data.length - 1;
          let i = 0;
          for (; i < l; i++) {
            try {
              stream.emit("log", JSON.parse(data[i]));
            } catch (e) {
              stream.emit("error", e);
            }
          }
          buff = data[l];
        });
        req.on("error", (err) => stream.emit("error", err));
        return stream;
      }
      /**
       * Make a request to a winstond server or any http server which can
       * handle json-rpc.
       * @param {function} options - Options to sent the request.
       * @param {Object?} auth - authentication options
       * @param {string} path - request path
       * @param {function} callback - Continuation to respond to when complete.
       */
      _request(options, auth, path, callback) {
        options = options || {};
        auth = auth || this.auth;
        path = path || this.path || "";
        if (this.batch) {
          this._doBatch(options, callback, auth, path);
        } else {
          this._doRequest(options, callback, auth, path);
        }
      }
      /**
       * Send or memorize the options according to batch configuration
       * @param {function} options - Options to sent the request.
       * @param {function} callback - Continuation to respond to when complete.
       * @param {Object?} auth - authentication options
       * @param {string} path - request path
       */
      _doBatch(options, callback, auth, path) {
        this.batchOptions.push(options);
        if (this.batchOptions.length === 1) {
          const me = this;
          this.batchCallback = callback;
          this.batchTimeoutID = setTimeout(function() {
            me.batchTimeoutID = -1;
            me._doBatchRequest(me.batchCallback, auth, path);
          }, this.batchInterval);
        }
        if (this.batchOptions.length === this.batchCount) {
          this._doBatchRequest(this.batchCallback, auth, path);
        }
      }
      /**
       * Initiate a request with the memorized batch options, stop the batch timeout
       * @param {function} callback - Continuation to respond to when complete.
       * @param {Object?} auth - authentication options
       * @param {string} path - request path
       */
      _doBatchRequest(callback, auth, path) {
        if (this.batchTimeoutID > 0) {
          clearTimeout(this.batchTimeoutID);
          this.batchTimeoutID = -1;
        }
        const batchOptionsCopy = this.batchOptions.slice();
        this.batchOptions = [];
        this._doRequest(batchOptionsCopy, callback, auth, path);
      }
      /**
       * Make a request to a winstond server or any http server which can
       * handle json-rpc.
       * @param {function} options - Options to sent the request.
       * @param {function} callback - Continuation to respond to when complete.
       * @param {Object?} auth - authentication options
       * @param {string} path - request path
       */
      _doRequest(options, callback, auth, path) {
        const headers = Object.assign({}, this.headers);
        if (auth && auth.bearer) {
          headers.Authorization = `Bearer ${auth.bearer}`;
        }
        const req = (this.ssl ? https : http).request({
          ...this.options,
          method: "POST",
          host: this.host,
          port: this.port,
          path: `/${path.replace(/^\//, "")}`,
          headers,
          auth: auth && auth.username && auth.password ? `${auth.username}:${auth.password}` : "",
          agent: this.agent
        });
        req.on("error", callback);
        req.on("response", (res) => res.on("end", () => callback(null, res)).resume());
        const jsonStringify = configure({
          ...this.maximumDepth && { maximumDepth: this.maximumDepth }
        });
        req.end(Buffer.from(jsonStringify(options, this.options.replacer), "utf8"));
      }
    };
  }
});

// node_modules/is-stream/index.js
var require_is_stream = __commonJS({
  "node_modules/is-stream/index.js"(exports2, module2) {
    "use strict";
    var isStream = (stream) => stream !== null && typeof stream === "object" && typeof stream.pipe === "function";
    isStream.writable = (stream) => isStream(stream) && stream.writable !== false && typeof stream._write === "function" && typeof stream._writableState === "object";
    isStream.readable = (stream) => isStream(stream) && stream.readable !== false && typeof stream._read === "function" && typeof stream._readableState === "object";
    isStream.duplex = (stream) => isStream.writable(stream) && isStream.readable(stream);
    isStream.transform = (stream) => isStream.duplex(stream) && typeof stream._transform === "function";
    module2.exports = isStream;
  }
});

// node_modules/winston/lib/winston/transports/stream.js
var require_stream2 = __commonJS({
  "node_modules/winston/lib/winston/transports/stream.js"(exports2, module2) {
    "use strict";
    var isStream = require_is_stream();
    var { MESSAGE } = require_triple_beam();
    var os = require("os");
    var TransportStream = require_winston_transport();
    module2.exports = class Stream extends TransportStream {
      /**
       * Constructor function for the Console transport object responsible for
       * persisting log messages and metadata to a terminal or TTY.
       * @param {!Object} [options={}] - Options for this instance.
       */
      constructor(options = {}) {
        super(options);
        if (!options.stream || !isStream(options.stream)) {
          throw new Error("options.stream is required.");
        }
        this._stream = options.stream;
        this._stream.setMaxListeners(Infinity);
        this.isObjectMode = options.stream._writableState.objectMode;
        this.eol = typeof options.eol === "string" ? options.eol : os.EOL;
      }
      /**
       * Core logging method exposed to Winston.
       * @param {Object} info - TODO: add param description.
       * @param {Function} callback - TODO: add param description.
       * @returns {undefined}
       */
      log(info, callback) {
        setImmediate(() => this.emit("logged", info));
        if (this.isObjectMode) {
          this._stream.write(info);
          if (callback) {
            callback();
          }
          return;
        }
        this._stream.write(`${info[MESSAGE]}${this.eol}`);
        if (callback) {
          callback();
        }
        return;
      }
    };
  }
});

// node_modules/winston/lib/winston/transports/index.js
var require_transports = __commonJS({
  "node_modules/winston/lib/winston/transports/index.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "Console", {
      configurable: true,
      enumerable: true,
      get() {
        return require_console();
      }
    });
    Object.defineProperty(exports2, "File", {
      configurable: true,
      enumerable: true,
      get() {
        return require_file();
      }
    });
    Object.defineProperty(exports2, "Http", {
      configurable: true,
      enumerable: true,
      get() {
        return require_http();
      }
    });
    Object.defineProperty(exports2, "Stream", {
      configurable: true,
      enumerable: true,
      get() {
        return require_stream2();
      }
    });
  }
});

// node_modules/winston/lib/winston/config/index.js
var require_config2 = __commonJS({
  "node_modules/winston/lib/winston/config/index.js"(exports2) {
    "use strict";
    var logform = require_logform();
    var { configs } = require_triple_beam();
    exports2.cli = logform.levels(configs.cli);
    exports2.npm = logform.levels(configs.npm);
    exports2.syslog = logform.levels(configs.syslog);
    exports2.addColors = logform.levels;
  }
});

// node_modules/async/eachOf.js
var require_eachOf = __commonJS({
  "node_modules/async/eachOf.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    var _isArrayLike = require_isArrayLike();
    var _isArrayLike2 = _interopRequireDefault(_isArrayLike);
    var _breakLoop = require_breakLoop();
    var _breakLoop2 = _interopRequireDefault(_breakLoop);
    var _eachOfLimit = require_eachOfLimit2();
    var _eachOfLimit2 = _interopRequireDefault(_eachOfLimit);
    var _once = require_once();
    var _once2 = _interopRequireDefault(_once);
    var _onlyOnce = require_onlyOnce();
    var _onlyOnce2 = _interopRequireDefault(_onlyOnce);
    var _wrapAsync = require_wrapAsync();
    var _wrapAsync2 = _interopRequireDefault(_wrapAsync);
    var _awaitify = require_awaitify();
    var _awaitify2 = _interopRequireDefault(_awaitify);
    function _interopRequireDefault(obj) {
      return obj && obj.__esModule ? obj : { default: obj };
    }
    function eachOfArrayLike(coll, iteratee, callback) {
      callback = (0, _once2.default)(callback);
      var index = 0, completed = 0, { length } = coll, canceled = false;
      if (length === 0) {
        callback(null);
      }
      function iteratorCallback(err, value) {
        if (err === false) {
          canceled = true;
        }
        if (canceled === true) return;
        if (err) {
          callback(err);
        } else if (++completed === length || value === _breakLoop2.default) {
          callback(null);
        }
      }
      for (; index < length; index++) {
        iteratee(coll[index], index, (0, _onlyOnce2.default)(iteratorCallback));
      }
    }
    function eachOfGeneric(coll, iteratee, callback) {
      return (0, _eachOfLimit2.default)(coll, Infinity, iteratee, callback);
    }
    function eachOf(coll, iteratee, callback) {
      var eachOfImplementation = (0, _isArrayLike2.default)(coll) ? eachOfArrayLike : eachOfGeneric;
      return eachOfImplementation(coll, (0, _wrapAsync2.default)(iteratee), callback);
    }
    exports2.default = (0, _awaitify2.default)(eachOf, 3);
    module2.exports = exports2.default;
  }
});

// node_modules/async/internal/withoutIndex.js
var require_withoutIndex = __commonJS({
  "node_modules/async/internal/withoutIndex.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    exports2.default = _withoutIndex;
    function _withoutIndex(iteratee) {
      return (value, index, callback) => iteratee(value, callback);
    }
    module2.exports = exports2.default;
  }
});

// node_modules/async/forEach.js
var require_forEach = __commonJS({
  "node_modules/async/forEach.js"(exports2, module2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", {
      value: true
    });
    var _eachOf = require_eachOf();
    var _eachOf2 = _interopRequireDefault(_eachOf);
    var _withoutIndex = require_withoutIndex();
    var _withoutIndex2 = _interopRequireDefault(_withoutIndex);
    var _wrapAsync = require_wrapAsync();
    var _wrapAsync2 = _interopRequireDefault(_wrapAsync);
    var _awaitify = require_awaitify();
    var _awaitify2 = _interopRequireDefault(_awaitify);
    function _interopRequireDefault(obj) {
      return obj && obj.__esModule ? obj : { default: obj };
    }
    function eachLimit(coll, iteratee, callback) {
      return (0, _eachOf2.default)(coll, (0, _withoutIndex2.default)((0, _wrapAsync2.default)(iteratee)), callback);
    }
    exports2.default = (0, _awaitify2.default)(eachLimit, 3);
    module2.exports = exports2.default;
  }
});

// node_modules/fn.name/index.js
var require_fn = __commonJS({
  "node_modules/fn.name/index.js"(exports2, module2) {
    "use strict";
    var toString = Object.prototype.toString;
    module2.exports = function name(fn) {
      if ("string" === typeof fn.displayName && fn.constructor.name) {
        return fn.displayName;
      } else if ("string" === typeof fn.name && fn.name) {
        return fn.name;
      }
      if ("object" === typeof fn && fn.constructor && "string" === typeof fn.constructor.name) return fn.constructor.name;
      var named = fn.toString(), type = toString.call(fn).slice(8, -1);
      if ("Function" === type) {
        named = named.substring(named.indexOf("(") + 1, named.indexOf(")"));
      } else {
        named = type;
      }
      return named || "anonymous";
    };
  }
});

// node_modules/one-time/index.js
var require_one_time = __commonJS({
  "node_modules/one-time/index.js"(exports2, module2) {
    "use strict";
    var name = require_fn();
    module2.exports = function one(fn) {
      var called = 0, value;
      function onetime() {
        if (called) return value;
        called = 1;
        value = fn.apply(this, arguments);
        fn = null;
        return value;
      }
      onetime.displayName = name(fn);
      return onetime;
    };
  }
});

// node_modules/stack-trace/lib/stack-trace.js
var require_stack_trace = __commonJS({
  "node_modules/stack-trace/lib/stack-trace.js"(exports2) {
    exports2.get = function(belowFn) {
      var oldLimit = Error.stackTraceLimit;
      Error.stackTraceLimit = Infinity;
      var dummyObject = {};
      var v8Handler = Error.prepareStackTrace;
      Error.prepareStackTrace = function(dummyObject2, v8StackTrace2) {
        return v8StackTrace2;
      };
      Error.captureStackTrace(dummyObject, belowFn || exports2.get);
      var v8StackTrace = dummyObject.stack;
      Error.prepareStackTrace = v8Handler;
      Error.stackTraceLimit = oldLimit;
      return v8StackTrace;
    };
    exports2.parse = function(err) {
      if (!err.stack) {
        return [];
      }
      var self2 = this;
      var lines = err.stack.split("\n").slice(1);
      return lines.map(function(line) {
        if (line.match(/^\s*[-]{4,}$/)) {
          return self2._createParsedCallSite({
            fileName: line,
            lineNumber: null,
            functionName: null,
            typeName: null,
            methodName: null,
            columnNumber: null,
            "native": null
          });
        }
        var lineMatch = line.match(/at (?:(.+)\s+\()?(?:(.+?):(\d+)(?::(\d+))?|([^)]+))\)?/);
        if (!lineMatch) {
          return;
        }
        var object = null;
        var method = null;
        var functionName = null;
        var typeName = null;
        var methodName = null;
        var isNative = lineMatch[5] === "native";
        if (lineMatch[1]) {
          functionName = lineMatch[1];
          var methodStart = functionName.lastIndexOf(".");
          if (functionName[methodStart - 1] == ".")
            methodStart--;
          if (methodStart > 0) {
            object = functionName.substr(0, methodStart);
            method = functionName.substr(methodStart + 1);
            var objectEnd = object.indexOf(".Module");
            if (objectEnd > 0) {
              functionName = functionName.substr(objectEnd + 1);
              object = object.substr(0, objectEnd);
            }
          }
          typeName = null;
        }
        if (method) {
          typeName = object;
          methodName = method;
        }
        if (method === "<anonymous>") {
          methodName = null;
          functionName = null;
        }
        var properties = {
          fileName: lineMatch[2] || null,
          lineNumber: parseInt(lineMatch[3], 10) || null,
          functionName,
          typeName,
          methodName,
          columnNumber: parseInt(lineMatch[4], 10) || null,
          "native": isNative
        };
        return self2._createParsedCallSite(properties);
      }).filter(function(callSite) {
        return !!callSite;
      });
    };
    function CallSite(properties) {
      for (var property in properties) {
        this[property] = properties[property];
      }
    }
    var strProperties = [
      "this",
      "typeName",
      "functionName",
      "methodName",
      "fileName",
      "lineNumber",
      "columnNumber",
      "function",
      "evalOrigin"
    ];
    var boolProperties = [
      "topLevel",
      "eval",
      "native",
      "constructor"
    ];
    strProperties.forEach(function(property) {
      CallSite.prototype[property] = null;
      CallSite.prototype["get" + property[0].toUpperCase() + property.substr(1)] = function() {
        return this[property];
      };
    });
    boolProperties.forEach(function(property) {
      CallSite.prototype[property] = false;
      CallSite.prototype["is" + property[0].toUpperCase() + property.substr(1)] = function() {
        return this[property];
      };
    });
    exports2._createParsedCallSite = function(properties) {
      return new CallSite(properties);
    };
  }
});

// node_modules/winston/lib/winston/exception-stream.js
var require_exception_stream = __commonJS({
  "node_modules/winston/lib/winston/exception-stream.js"(exports2, module2) {
    "use strict";
    var { Writable } = require_readable();
    module2.exports = class ExceptionStream extends Writable {
      /**
       * Constructor function for the ExceptionStream responsible for wrapping a
       * TransportStream; only allowing writes of `info` objects with
       * `info.exception` set to true.
       * @param {!TransportStream} transport - Stream to filter to exceptions
       */
      constructor(transport) {
        super({ objectMode: true });
        if (!transport) {
          throw new Error("ExceptionStream requires a TransportStream instance.");
        }
        this.handleExceptions = true;
        this.transport = transport;
      }
      /**
       * Writes the info object to our transport instance if (and only if) the
       * `exception` property is set on the info.
       * @param {mixed} info - TODO: add param description.
       * @param {mixed} enc - TODO: add param description.
       * @param {mixed} callback - TODO: add param description.
       * @returns {mixed} - TODO: add return description.
       * @private
       */
      _write(info, enc, callback) {
        if (info.exception) {
          return this.transport.log(info, callback);
        }
        callback();
        return true;
      }
    };
  }
});

// node_modules/winston/lib/winston/exception-handler.js
var require_exception_handler = __commonJS({
  "node_modules/winston/lib/winston/exception-handler.js"(exports2, module2) {
    "use strict";
    var os = require("os");
    var asyncForEach = require_forEach();
    var debug = require_node2()("winston:exception");
    var once = require_one_time();
    var stackTrace = require_stack_trace();
    var ExceptionStream = require_exception_stream();
    module2.exports = class ExceptionHandler {
      /**
       * TODO: add contructor description
       * @param {!Logger} logger - TODO: add param description
       */
      constructor(logger) {
        if (!logger) {
          throw new Error("Logger is required to handle exceptions");
        }
        this.logger = logger;
        this.handlers = /* @__PURE__ */ new Map();
      }
      /**
       * Handles `uncaughtException` events for the current process by adding any
       * handlers passed in.
       * @returns {undefined}
       */
      handle(...args) {
        args.forEach((arg) => {
          if (Array.isArray(arg)) {
            return arg.forEach((handler) => this._addHandler(handler));
          }
          this._addHandler(arg);
        });
        if (!this.catcher) {
          this.catcher = this._uncaughtException.bind(this);
          process.on("uncaughtException", this.catcher);
        }
      }
      /**
       * Removes any handlers to `uncaughtException` events for the current
       * process. This does not modify the state of the `this.handlers` set.
       * @returns {undefined}
       */
      unhandle() {
        if (this.catcher) {
          process.removeListener("uncaughtException", this.catcher);
          this.catcher = false;
          Array.from(this.handlers.values()).forEach((wrapper) => this.logger.unpipe(wrapper));
        }
      }
      /**
       * TODO: add method description
       * @param {Error} err - Error to get information about.
       * @returns {mixed} - TODO: add return description.
       */
      getAllInfo(err) {
        let message = null;
        if (err) {
          message = typeof err === "string" ? err : err.message;
        }
        return {
          error: err,
          // TODO (indexzero): how do we configure this?
          level: "error",
          message: [
            `uncaughtException: ${message || "(no error message)"}`,
            err && err.stack || "  No stack trace"
          ].join("\n"),
          stack: err && err.stack,
          exception: true,
          date: (/* @__PURE__ */ new Date()).toString(),
          process: this.getProcessInfo(),
          os: this.getOsInfo(),
          trace: this.getTrace(err)
        };
      }
      /**
       * Gets all relevant process information for the currently running process.
       * @returns {mixed} - TODO: add return description.
       */
      getProcessInfo() {
        return {
          pid: process.pid,
          uid: process.getuid ? process.getuid() : null,
          gid: process.getgid ? process.getgid() : null,
          cwd: process.cwd(),
          execPath: process.execPath,
          version: process.version,
          argv: process.argv,
          memoryUsage: process.memoryUsage()
        };
      }
      /**
       * Gets all relevant OS information for the currently running process.
       * @returns {mixed} - TODO: add return description.
       */
      getOsInfo() {
        return {
          loadavg: os.loadavg(),
          uptime: os.uptime()
        };
      }
      /**
       * Gets a stack trace for the specified error.
       * @param {mixed} err - TODO: add param description.
       * @returns {mixed} - TODO: add return description.
       */
      getTrace(err) {
        const trace = err ? stackTrace.parse(err) : stackTrace.get();
        return trace.map((site) => {
          return {
            column: site.getColumnNumber(),
            file: site.getFileName(),
            function: site.getFunctionName(),
            line: site.getLineNumber(),
            method: site.getMethodName(),
            native: site.isNative()
          };
        });
      }
      /**
       * Helper method to add a transport as an exception handler.
       * @param {Transport} handler - The transport to add as an exception handler.
       * @returns {void}
       */
      _addHandler(handler) {
        if (!this.handlers.has(handler)) {
          handler.handleExceptions = true;
          const wrapper = new ExceptionStream(handler);
          this.handlers.set(handler, wrapper);
          this.logger.pipe(wrapper);
        }
      }
      /**
       * Logs all relevant information around the `err` and exits the current
       * process.
       * @param {Error} err - Error to handle
       * @returns {mixed} - TODO: add return description.
       * @private
       */
      _uncaughtException(err) {
        const info = this.getAllInfo(err);
        const handlers = this._getExceptionHandlers();
        let doExit = typeof this.logger.exitOnError === "function" ? this.logger.exitOnError(err) : this.logger.exitOnError;
        let timeout;
        if (!handlers.length && doExit) {
          console.warn("winston: exitOnError cannot be true with no exception handlers.");
          console.warn("winston: not exiting process.");
          doExit = false;
        }
        function gracefulExit() {
          debug("doExit", doExit);
          debug("process._exiting", process._exiting);
          if (doExit && !process._exiting) {
            if (timeout) {
              clearTimeout(timeout);
            }
            process.exit(1);
          }
        }
        if (!handlers || handlers.length === 0) {
          return process.nextTick(gracefulExit);
        }
        asyncForEach(handlers, (handler, next) => {
          const done = once(next);
          const transport = handler.transport || handler;
          function onDone(event) {
            return () => {
              debug(event);
              done();
            };
          }
          transport._ending = true;
          transport.once("finish", onDone("finished"));
          transport.once("error", onDone("error"));
        }, () => doExit && gracefulExit());
        this.logger.log(info);
        if (doExit) {
          timeout = setTimeout(gracefulExit, 3e3);
        }
      }
      /**
       * Returns the list of transports and exceptionHandlers for this instance.
       * @returns {Array} - List of transports and exceptionHandlers for this
       * instance.
       * @private
       */
      _getExceptionHandlers() {
        return this.logger.transports.filter((wrap) => {
          const transport = wrap.transport || wrap;
          return transport.handleExceptions;
        });
      }
    };
  }
});

// node_modules/winston/lib/winston/rejection-stream.js
var require_rejection_stream = __commonJS({
  "node_modules/winston/lib/winston/rejection-stream.js"(exports2, module2) {
    "use strict";
    var { Writable } = require_readable();
    module2.exports = class RejectionStream extends Writable {
      /**
       * Constructor function for the RejectionStream responsible for wrapping a
       * TransportStream; only allowing writes of `info` objects with
       * `info.rejection` set to true.
       * @param {!TransportStream} transport - Stream to filter to rejections
       */
      constructor(transport) {
        super({ objectMode: true });
        if (!transport) {
          throw new Error("RejectionStream requires a TransportStream instance.");
        }
        this.handleRejections = true;
        this.transport = transport;
      }
      /**
       * Writes the info object to our transport instance if (and only if) the
       * `rejection` property is set on the info.
       * @param {mixed} info - TODO: add param description.
       * @param {mixed} enc - TODO: add param description.
       * @param {mixed} callback - TODO: add param description.
       * @returns {mixed} - TODO: add return description.
       * @private
       */
      _write(info, enc, callback) {
        if (info.rejection) {
          return this.transport.log(info, callback);
        }
        callback();
        return true;
      }
    };
  }
});

// node_modules/winston/lib/winston/rejection-handler.js
var require_rejection_handler = __commonJS({
  "node_modules/winston/lib/winston/rejection-handler.js"(exports2, module2) {
    "use strict";
    var os = require("os");
    var asyncForEach = require_forEach();
    var debug = require_node2()("winston:rejection");
    var once = require_one_time();
    var stackTrace = require_stack_trace();
    var RejectionStream = require_rejection_stream();
    module2.exports = class RejectionHandler {
      /**
       * TODO: add contructor description
       * @param {!Logger} logger - TODO: add param description
       */
      constructor(logger) {
        if (!logger) {
          throw new Error("Logger is required to handle rejections");
        }
        this.logger = logger;
        this.handlers = /* @__PURE__ */ new Map();
      }
      /**
       * Handles `unhandledRejection` events for the current process by adding any
       * handlers passed in.
       * @returns {undefined}
       */
      handle(...args) {
        args.forEach((arg) => {
          if (Array.isArray(arg)) {
            return arg.forEach((handler) => this._addHandler(handler));
          }
          this._addHandler(arg);
        });
        if (!this.catcher) {
          this.catcher = this._unhandledRejection.bind(this);
          process.on("unhandledRejection", this.catcher);
        }
      }
      /**
       * Removes any handlers to `unhandledRejection` events for the current
       * process. This does not modify the state of the `this.handlers` set.
       * @returns {undefined}
       */
      unhandle() {
        if (this.catcher) {
          process.removeListener("unhandledRejection", this.catcher);
          this.catcher = false;
          Array.from(this.handlers.values()).forEach(
            (wrapper) => this.logger.unpipe(wrapper)
          );
        }
      }
      /**
       * TODO: add method description
       * @param {Error} err - Error to get information about.
       * @returns {mixed} - TODO: add return description.
       */
      getAllInfo(err) {
        let message = null;
        if (err) {
          message = typeof err === "string" ? err : err.message;
        }
        return {
          error: err,
          // TODO (indexzero): how do we configure this?
          level: "error",
          message: [
            `unhandledRejection: ${message || "(no error message)"}`,
            err && err.stack || "  No stack trace"
          ].join("\n"),
          stack: err && err.stack,
          rejection: true,
          date: (/* @__PURE__ */ new Date()).toString(),
          process: this.getProcessInfo(),
          os: this.getOsInfo(),
          trace: this.getTrace(err)
        };
      }
      /**
       * Gets all relevant process information for the currently running process.
       * @returns {mixed} - TODO: add return description.
       */
      getProcessInfo() {
        return {
          pid: process.pid,
          uid: process.getuid ? process.getuid() : null,
          gid: process.getgid ? process.getgid() : null,
          cwd: process.cwd(),
          execPath: process.execPath,
          version: process.version,
          argv: process.argv,
          memoryUsage: process.memoryUsage()
        };
      }
      /**
       * Gets all relevant OS information for the currently running process.
       * @returns {mixed} - TODO: add return description.
       */
      getOsInfo() {
        return {
          loadavg: os.loadavg(),
          uptime: os.uptime()
        };
      }
      /**
       * Gets a stack trace for the specified error.
       * @param {mixed} err - TODO: add param description.
       * @returns {mixed} - TODO: add return description.
       */
      getTrace(err) {
        const trace = err ? stackTrace.parse(err) : stackTrace.get();
        return trace.map((site) => {
          return {
            column: site.getColumnNumber(),
            file: site.getFileName(),
            function: site.getFunctionName(),
            line: site.getLineNumber(),
            method: site.getMethodName(),
            native: site.isNative()
          };
        });
      }
      /**
       * Helper method to add a transport as an exception handler.
       * @param {Transport} handler - The transport to add as an exception handler.
       * @returns {void}
       */
      _addHandler(handler) {
        if (!this.handlers.has(handler)) {
          handler.handleRejections = true;
          const wrapper = new RejectionStream(handler);
          this.handlers.set(handler, wrapper);
          this.logger.pipe(wrapper);
        }
      }
      /**
       * Logs all relevant information around the `err` and exits the current
       * process.
       * @param {Error} err - Error to handle
       * @returns {mixed} - TODO: add return description.
       * @private
       */
      _unhandledRejection(err) {
        const info = this.getAllInfo(err);
        const handlers = this._getRejectionHandlers();
        let doExit = typeof this.logger.exitOnError === "function" ? this.logger.exitOnError(err) : this.logger.exitOnError;
        let timeout;
        if (!handlers.length && doExit) {
          console.warn("winston: exitOnError cannot be true with no rejection handlers.");
          console.warn("winston: not exiting process.");
          doExit = false;
        }
        function gracefulExit() {
          debug("doExit", doExit);
          debug("process._exiting", process._exiting);
          if (doExit && !process._exiting) {
            if (timeout) {
              clearTimeout(timeout);
            }
            process.exit(1);
          }
        }
        if (!handlers || handlers.length === 0) {
          return process.nextTick(gracefulExit);
        }
        asyncForEach(
          handlers,
          (handler, next) => {
            const done = once(next);
            const transport = handler.transport || handler;
            function onDone(event) {
              return () => {
                debug(event);
                done();
              };
            }
            transport._ending = true;
            transport.once("finish", onDone("finished"));
            transport.once("error", onDone("error"));
          },
          () => doExit && gracefulExit()
        );
        this.logger.log(info);
        if (doExit) {
          timeout = setTimeout(gracefulExit, 3e3);
        }
      }
      /**
       * Returns the list of transports and exceptionHandlers for this instance.
       * @returns {Array} - List of transports and exceptionHandlers for this
       * instance.
       * @private
       */
      _getRejectionHandlers() {
        return this.logger.transports.filter((wrap) => {
          const transport = wrap.transport || wrap;
          return transport.handleRejections;
        });
      }
    };
  }
});

// node_modules/winston/lib/winston/profiler.js
var require_profiler = __commonJS({
  "node_modules/winston/lib/winston/profiler.js"(exports2, module2) {
    "use strict";
    var Profiler = class {
      /**
       * Constructor function for the Profiler instance used by
       * `Logger.prototype.startTimer`. When done is called the timer will finish
       * and log the duration.
       * @param {!Logger} logger - TODO: add param description.
       * @private
       */
      constructor(logger) {
        const Logger = require_logger();
        if (typeof logger !== "object" || Array.isArray(logger) || !(logger instanceof Logger)) {
          throw new Error("Logger is required for profiling");
        } else {
          this.logger = logger;
          this.start = Date.now();
        }
      }
      /**
       * Ends the current timer (i.e. Profiler) instance and logs the `msg` along
       * with the duration since creation.
       * @returns {mixed} - TODO: add return description.
       * @private
       */
      done(...args) {
        if (typeof args[args.length - 1] === "function") {
          console.warn("Callback function no longer supported as of winston@3.0.0");
          args.pop();
        }
        const info = typeof args[args.length - 1] === "object" ? args.pop() : {};
        info.level = info.level || "info";
        info.durationMs = Date.now() - this.start;
        return this.logger.write(info);
      }
    };
    module2.exports = Profiler;
  }
});

// node_modules/winston/lib/winston/logger.js
var require_logger = __commonJS({
  "node_modules/winston/lib/winston/logger.js"(exports2, module2) {
    "use strict";
    var { Stream, Transform } = require_readable();
    var asyncForEach = require_forEach();
    var { LEVEL, SPLAT } = require_triple_beam();
    var isStream = require_is_stream();
    var ExceptionHandler = require_exception_handler();
    var RejectionHandler = require_rejection_handler();
    var LegacyTransportStream = require_legacy();
    var Profiler = require_profiler();
    var { warn } = require_common();
    var config = require_config2();
    var formatRegExp = /%[scdjifoO%]/g;
    var Logger = class extends Transform {
      /**
       * Constructor function for the Logger object responsible for persisting log
       * messages and metadata to one or more transports.
       * @param {!Object} options - foo
       */
      constructor(options) {
        super({ objectMode: true });
        this.configure(options);
      }
      child(defaultRequestMetadata) {
        const logger = this;
        return Object.create(logger, {
          write: {
            value: function(info) {
              const infoClone = Object.assign(
                {},
                defaultRequestMetadata,
                info
              );
              if (info instanceof Error) {
                infoClone.stack = info.stack;
                infoClone.message = info.message;
              }
              logger.write(infoClone);
            }
          }
        });
      }
      /**
       * This will wholesale reconfigure this instance by:
       * 1. Resetting all transports. Older transports will be removed implicitly.
       * 2. Set all other options including levels, colors, rewriters, filters,
       *    exceptionHandlers, etc.
       * @param {!Object} options - TODO: add param description.
       * @returns {undefined}
       */
      configure({
        silent,
        format,
        defaultMeta,
        levels,
        level = "info",
        exitOnError = true,
        transports,
        colors,
        emitErrs,
        formatters,
        padLevels,
        rewriters,
        stripColors,
        exceptionHandlers,
        rejectionHandlers
      } = {}) {
        if (this.transports.length) {
          this.clear();
        }
        this.silent = silent;
        this.format = format || this.format || require_json()();
        this.defaultMeta = defaultMeta || null;
        this.levels = levels || this.levels || config.npm.levels;
        this.level = level;
        if (this.exceptions) {
          this.exceptions.unhandle();
        }
        if (this.rejections) {
          this.rejections.unhandle();
        }
        this.exceptions = new ExceptionHandler(this);
        this.rejections = new RejectionHandler(this);
        this.profilers = {};
        this.exitOnError = exitOnError;
        if (transports) {
          transports = Array.isArray(transports) ? transports : [transports];
          transports.forEach((transport) => this.add(transport));
        }
        if (colors || emitErrs || formatters || padLevels || rewriters || stripColors) {
          throw new Error(
            [
              "{ colors, emitErrs, formatters, padLevels, rewriters, stripColors } were removed in winston@3.0.0.",
              "Use a custom winston.format(function) instead.",
              "See: https://github.com/winstonjs/winston/tree/master/UPGRADE-3.0.md"
            ].join("\n")
          );
        }
        if (exceptionHandlers) {
          this.exceptions.handle(exceptionHandlers);
        }
        if (rejectionHandlers) {
          this.rejections.handle(rejectionHandlers);
        }
      }
      isLevelEnabled(level) {
        const givenLevelValue = getLevelValue(this.levels, level);
        if (givenLevelValue === null) {
          return false;
        }
        const configuredLevelValue = getLevelValue(this.levels, this.level);
        if (configuredLevelValue === null) {
          return false;
        }
        if (!this.transports || this.transports.length === 0) {
          return configuredLevelValue >= givenLevelValue;
        }
        const index = this.transports.findIndex((transport) => {
          let transportLevelValue = getLevelValue(this.levels, transport.level);
          if (transportLevelValue === null) {
            transportLevelValue = configuredLevelValue;
          }
          return transportLevelValue >= givenLevelValue;
        });
        return index !== -1;
      }
      /* eslint-disable valid-jsdoc */
      /**
       * Ensure backwards compatibility with a `log` method
       * @param {mixed} level - Level the log message is written at.
       * @param {mixed} msg - TODO: add param description.
       * @param {mixed} meta - TODO: add param description.
       * @returns {Logger} - TODO: add return description.
       *
       * @example
       *    // Supports the existing API:
       *    logger.log('info', 'Hello world', { custom: true });
       *    logger.log('info', new Error('Yo, it\'s on fire'));
       *
       *    // Requires winston.format.splat()
       *    logger.log('info', '%s %d%%', 'A string', 50, { thisIsMeta: true });
       *
       *    // And the new API with a single JSON literal:
       *    logger.log({ level: 'info', message: 'Hello world', custom: true });
       *    logger.log({ level: 'info', message: new Error('Yo, it\'s on fire') });
       *
       *    // Also requires winston.format.splat()
       *    logger.log({
       *      level: 'info',
       *      message: '%s %d%%',
       *      [SPLAT]: ['A string', 50],
       *      meta: { thisIsMeta: true }
       *    });
       *
       */
      /* eslint-enable valid-jsdoc */
      log(level, msg, ...splat) {
        if (arguments.length === 1) {
          level[LEVEL] = level.level;
          this._addDefaultMeta(level);
          this.write(level);
          return this;
        }
        if (arguments.length === 2) {
          if (msg && typeof msg === "object") {
            msg[LEVEL] = msg.level = level;
            this._addDefaultMeta(msg);
            this.write(msg);
            return this;
          }
          msg = { [LEVEL]: level, level, message: msg };
          this._addDefaultMeta(msg);
          this.write(msg);
          return this;
        }
        const [meta] = splat;
        if (typeof meta === "object" && meta !== null) {
          const tokens = msg && msg.match && msg.match(formatRegExp);
          if (!tokens) {
            const info = Object.assign({}, this.defaultMeta, meta, {
              [LEVEL]: level,
              [SPLAT]: splat,
              level,
              message: msg
            });
            if (meta.message) info.message = `${info.message} ${meta.message}`;
            if (meta.stack) info.stack = meta.stack;
            if (meta.cause) info.cause = meta.cause;
            this.write(info);
            return this;
          }
        }
        this.write(Object.assign({}, this.defaultMeta, {
          [LEVEL]: level,
          [SPLAT]: splat,
          level,
          message: msg
        }));
        return this;
      }
      /**
       * Pushes data so that it can be picked up by all of our pipe targets.
       * @param {mixed} info - TODO: add param description.
       * @param {mixed} enc - TODO: add param description.
       * @param {mixed} callback - Continues stream processing.
       * @returns {undefined}
       * @private
       */
      _transform(info, enc, callback) {
        if (this.silent) {
          return callback();
        }
        if (!info[LEVEL]) {
          info[LEVEL] = info.level;
        }
        if (!this.levels[info[LEVEL]] && this.levels[info[LEVEL]] !== 0) {
          console.error("[winston] Unknown logger level: %s", info[LEVEL]);
        }
        if (!this._readableState.pipes) {
          console.error(
            "[winston] Attempt to write logs with no transports, which can increase memory usage: %j",
            info
          );
        }
        try {
          this.push(this.format.transform(info, this.format.options));
        } finally {
          this._writableState.sync = false;
          callback();
        }
      }
      /**
       * Delays the 'finish' event until all transport pipe targets have
       * also emitted 'finish' or are already finished.
       * @param {mixed} callback - Continues stream processing.
       */
      _final(callback) {
        const transports = this.transports.slice();
        asyncForEach(
          transports,
          (transport, next) => {
            if (!transport || transport.finished) return setImmediate(next);
            transport.once("finish", next);
            transport.end();
          },
          callback
        );
      }
      /**
       * Adds the transport to this logger instance by piping to it.
       * @param {mixed} transport - TODO: add param description.
       * @returns {Logger} - TODO: add return description.
       */
      add(transport) {
        const target = !isStream(transport) || transport.log.length > 2 ? new LegacyTransportStream({ transport }) : transport;
        if (!target._writableState || !target._writableState.objectMode) {
          throw new Error(
            "Transports must WritableStreams in objectMode. Set { objectMode: true }."
          );
        }
        this._onEvent("error", target);
        this._onEvent("warn", target);
        this.pipe(target);
        if (transport.handleExceptions) {
          this.exceptions.handle();
        }
        if (transport.handleRejections) {
          this.rejections.handle();
        }
        return this;
      }
      /**
       * Removes the transport from this logger instance by unpiping from it.
       * @param {mixed} transport - TODO: add param description.
       * @returns {Logger} - TODO: add return description.
       */
      remove(transport) {
        if (!transport) return this;
        let target = transport;
        if (!isStream(transport) || transport.log.length > 2) {
          target = this.transports.filter(
            (match) => match.transport === transport
          )[0];
        }
        if (target) {
          this.unpipe(target);
        }
        return this;
      }
      /**
       * Removes all transports from this logger instance.
       * @returns {Logger} - TODO: add return description.
       */
      clear() {
        this.unpipe();
        return this;
      }
      /**
       * Cleans up resources (streams, event listeners) for all transports
       * associated with this instance (if necessary).
       * @returns {Logger} - TODO: add return description.
       */
      close() {
        this.exceptions.unhandle();
        this.rejections.unhandle();
        this.clear();
        this.emit("close");
        return this;
      }
      /**
       * Sets the `target` levels specified on this instance.
       * @param {Object} Target levels to use on this instance.
       */
      setLevels() {
        warn.deprecated("setLevels");
      }
      /**
       * Queries the all transports for this instance with the specified `options`.
       * This will aggregate each transport's results into one object containing
       * a property per transport.
       * @param {Object} options - Query options for this instance.
       * @param {function} callback - Continuation to respond to when complete.
       */
      query(options, callback) {
        if (typeof options === "function") {
          callback = options;
          options = {};
        }
        options = options || {};
        const results = {};
        const queryObject = Object.assign({}, options.query || {});
        function queryTransport(transport, next) {
          if (options.query && typeof transport.formatQuery === "function") {
            options.query = transport.formatQuery(queryObject);
          }
          transport.query(options, (err, res) => {
            if (err) {
              return next(err);
            }
            if (typeof transport.formatResults === "function") {
              res = transport.formatResults(res, options.format);
            }
            next(null, res);
          });
        }
        function addResults(transport, next) {
          queryTransport(transport, (err, result) => {
            if (next) {
              result = err || result;
              if (result) {
                results[transport.name] = result;
              }
              next();
            }
            next = null;
          });
        }
        asyncForEach(
          this.transports.filter((transport) => !!transport.query),
          addResults,
          () => callback(null, results)
        );
      }
      /**
       * Returns a log stream for all transports. Options object is optional.
       * @param{Object} options={} - Stream options for this instance.
       * @returns {Stream} - TODO: add return description.
       */
      stream(options = {}) {
        const out = new Stream();
        const streams = [];
        out._streams = streams;
        out.destroy = () => {
          let i = streams.length;
          while (i--) {
            streams[i].destroy();
          }
        };
        this.transports.filter((transport) => !!transport.stream).forEach((transport) => {
          const str = transport.stream(options);
          if (!str) {
            return;
          }
          streams.push(str);
          str.on("log", (log) => {
            log.transport = log.transport || [];
            log.transport.push(transport.name);
            out.emit("log", log);
          });
          str.on("error", (err) => {
            err.transport = err.transport || [];
            err.transport.push(transport.name);
            out.emit("error", err);
          });
        });
        return out;
      }
      /**
       * Returns an object corresponding to a specific timing. When done is called
       * the timer will finish and log the duration. e.g.:
       * @returns {Profile} - TODO: add return description.
       * @example
       *    const timer = winston.startTimer()
       *    setTimeout(() => {
       *      timer.done({
       *        message: 'Logging message'
       *      });
       *    }, 1000);
       */
      startTimer() {
        return new Profiler(this);
      }
      /**
       * Tracks the time inbetween subsequent calls to this method with the same
       * `id` parameter. The second call to this method will log the difference in
       * milliseconds along with the message.
       * @param {string} id Unique id of the profiler
       * @returns {Logger} - TODO: add return description.
       */
      profile(id, ...args) {
        const time = Date.now();
        if (this.profilers[id]) {
          const timeEnd = this.profilers[id];
          delete this.profilers[id];
          if (typeof args[args.length - 2] === "function") {
            console.warn(
              "Callback function no longer supported as of winston@3.0.0"
            );
            args.pop();
          }
          const info = typeof args[args.length - 1] === "object" ? args.pop() : {};
          info.level = info.level || "info";
          info.durationMs = time - timeEnd;
          info.message = info.message || id;
          return this.write(info);
        }
        this.profilers[id] = time;
        return this;
      }
      /**
       * Backwards compatibility to `exceptions.handle` in winston < 3.0.0.
       * @returns {undefined}
       * @deprecated
       */
      handleExceptions(...args) {
        console.warn(
          "Deprecated: .handleExceptions() will be removed in winston@4. Use .exceptions.handle()"
        );
        this.exceptions.handle(...args);
      }
      /**
       * Backwards compatibility to `exceptions.handle` in winston < 3.0.0.
       * @returns {undefined}
       * @deprecated
       */
      unhandleExceptions(...args) {
        console.warn(
          "Deprecated: .unhandleExceptions() will be removed in winston@4. Use .exceptions.unhandle()"
        );
        this.exceptions.unhandle(...args);
      }
      /**
       * Throw a more meaningful deprecation notice
       * @throws {Error} - TODO: add throws description.
       */
      cli() {
        throw new Error(
          [
            "Logger.cli() was removed in winston@3.0.0",
            "Use a custom winston.formats.cli() instead.",
            "See: https://github.com/winstonjs/winston/tree/master/UPGRADE-3.0.md"
          ].join("\n")
        );
      }
      /**
       * Bubbles the `event` that occured on the specified `transport` up
       * from this instance.
       * @param {string} event - The event that occured
       * @param {Object} transport - Transport on which the event occured
       * @private
       */
      _onEvent(event, transport) {
        function transportEvent(err) {
          if (event === "error" && !this.transports.includes(transport)) {
            this.add(transport);
          }
          this.emit(event, err, transport);
        }
        if (!transport["__winston" + event]) {
          transport["__winston" + event] = transportEvent.bind(this);
          transport.on(event, transport["__winston" + event]);
        }
      }
      _addDefaultMeta(msg) {
        if (this.defaultMeta) {
          Object.assign(msg, this.defaultMeta);
        }
      }
    };
    function getLevelValue(levels, level) {
      const value = levels[level];
      if (!value && value !== 0) {
        return null;
      }
      return value;
    }
    Object.defineProperty(Logger.prototype, "transports", {
      configurable: false,
      enumerable: true,
      get() {
        const { pipes } = this._readableState;
        return !Array.isArray(pipes) ? [pipes].filter(Boolean) : pipes;
      }
    });
    module2.exports = Logger;
  }
});

// node_modules/winston/lib/winston/create-logger.js
var require_create_logger = __commonJS({
  "node_modules/winston/lib/winston/create-logger.js"(exports2, module2) {
    "use strict";
    var { LEVEL } = require_triple_beam();
    var config = require_config2();
    var Logger = require_logger();
    var debug = require_node2()("winston:create-logger");
    function isLevelEnabledFunctionName(level) {
      return "is" + level.charAt(0).toUpperCase() + level.slice(1) + "Enabled";
    }
    module2.exports = function(opts = {}) {
      opts.levels = opts.levels || config.npm.levels;
      class DerivedLogger extends Logger {
        /**
         * Create a new class derived logger for which the levels can be attached to
         * the prototype of. This is a V8 optimization that is well know to increase
         * performance of prototype functions.
         * @param {!Object} options - Options for the created logger.
         */
        constructor(options) {
          super(options);
        }
      }
      const logger = new DerivedLogger(opts);
      Object.keys(opts.levels).forEach(function(level) {
        debug('Define prototype method for "%s"', level);
        if (level === "log") {
          console.warn('Level "log" not defined: conflicts with the method "log". Use a different level name.');
          return;
        }
        DerivedLogger.prototype[level] = function(...args) {
          const self2 = this || logger;
          if (args.length === 1) {
            const [msg] = args;
            const info = msg && msg.message && msg || { message: msg };
            info.level = info[LEVEL] = level;
            self2._addDefaultMeta(info);
            self2.write(info);
            return this || logger;
          }
          if (args.length === 0) {
            self2.log(level, "");
            return self2;
          }
          return self2.log(level, ...args);
        };
        DerivedLogger.prototype[isLevelEnabledFunctionName(level)] = function() {
          return (this || logger).isLevelEnabled(level);
        };
      });
      return logger;
    };
  }
});

// node_modules/winston/lib/winston/container.js
var require_container = __commonJS({
  "node_modules/winston/lib/winston/container.js"(exports2, module2) {
    "use strict";
    var createLogger = require_create_logger();
    module2.exports = class Container {
      /**
       * Constructor function for the Container object responsible for managing a
       * set of `winston.Logger` instances based on string ids.
       * @param {!Object} [options={}] - Default pass-thru options for Loggers.
       */
      constructor(options = {}) {
        this.loggers = /* @__PURE__ */ new Map();
        this.options = options;
      }
      /**
       * Retrieves a `winston.Logger` instance for the specified `id`. If an
       * instance does not exist, one is created.
       * @param {!string} id - The id of the Logger to get.
       * @param {?Object} [options] - Options for the Logger instance.
       * @returns {Logger} - A configured Logger instance with a specified id.
       */
      add(id, options) {
        if (!this.loggers.has(id)) {
          options = Object.assign({}, options || this.options);
          const existing = options.transports || this.options.transports;
          if (existing) {
            options.transports = Array.isArray(existing) ? existing.slice() : [existing];
          } else {
            options.transports = [];
          }
          const logger = createLogger(options);
          logger.on("close", () => this._delete(id));
          this.loggers.set(id, logger);
        }
        return this.loggers.get(id);
      }
      /**
       * Retreives a `winston.Logger` instance for the specified `id`. If
       * an instance does not exist, one is created.
       * @param {!string} id - The id of the Logger to get.
       * @param {?Object} [options] - Options for the Logger instance.
       * @returns {Logger} - A configured Logger instance with a specified id.
       */
      get(id, options) {
        return this.add(id, options);
      }
      /**
       * Check if the container has a logger with the id.
       * @param {?string} id - The id of the Logger instance to find.
       * @returns {boolean} - Boolean value indicating if this instance has a
       * logger with the specified `id`.
       */
      has(id) {
        return !!this.loggers.has(id);
      }
      /**
       * Closes a `Logger` instance with the specified `id` if it exists.
       * If no `id` is supplied then all Loggers are closed.
       * @param {?string} id - The id of the Logger instance to close.
       * @returns {undefined}
       */
      close(id) {
        if (id) {
          return this._removeLogger(id);
        }
        this.loggers.forEach((val, key) => this._removeLogger(key));
      }
      /**
       * Remove a logger based on the id.
       * @param {!string} id - The id of the logger to remove.
       * @returns {undefined}
       * @private
       */
      _removeLogger(id) {
        if (!this.loggers.has(id)) {
          return;
        }
        const logger = this.loggers.get(id);
        logger.close();
        this._delete(id);
      }
      /**
       * Deletes a `Logger` instance with the specified `id`.
       * @param {!string} id - The id of the Logger instance to delete from
       * container.
       * @returns {undefined}
       * @private
       */
      _delete(id) {
        this.loggers.delete(id);
      }
    };
  }
});

// node_modules/winston/lib/winston.js
var require_winston = __commonJS({
  "node_modules/winston/lib/winston.js"(exports2) {
    "use strict";
    var logform = require_logform();
    var { warn } = require_common();
    exports2.version = require_package().version;
    exports2.transports = require_transports();
    exports2.config = require_config2();
    exports2.addColors = logform.levels;
    exports2.format = logform.format;
    exports2.createLogger = require_create_logger();
    exports2.Logger = require_logger();
    exports2.ExceptionHandler = require_exception_handler();
    exports2.RejectionHandler = require_rejection_handler();
    exports2.Container = require_container();
    exports2.Transport = require_winston_transport();
    exports2.loggers = new exports2.Container();
    var defaultLogger = exports2.createLogger();
    Object.keys(exports2.config.npm.levels).concat([
      "log",
      "query",
      "stream",
      "add",
      "remove",
      "clear",
      "profile",
      "startTimer",
      "handleExceptions",
      "unhandleExceptions",
      "handleRejections",
      "unhandleRejections",
      "configure",
      "child"
    ]).forEach(
      (method) => exports2[method] = (...args) => defaultLogger[method](...args)
    );
    Object.defineProperty(exports2, "level", {
      get() {
        return defaultLogger.level;
      },
      set(val) {
        defaultLogger.level = val;
      }
    });
    Object.defineProperty(exports2, "exceptions", {
      get() {
        return defaultLogger.exceptions;
      }
    });
    Object.defineProperty(exports2, "rejections", {
      get() {
        return defaultLogger.rejections;
      }
    });
    ["exitOnError"].forEach((prop) => {
      Object.defineProperty(exports2, prop, {
        get() {
          return defaultLogger[prop];
        },
        set(val) {
          defaultLogger[prop] = val;
        }
      });
    });
    Object.defineProperty(exports2, "default", {
      get() {
        return {
          exceptionHandlers: defaultLogger.exceptionHandlers,
          rejectionHandlers: defaultLogger.rejectionHandlers,
          transports: defaultLogger.transports
        };
      }
    });
    warn.deprecated(exports2, "setLevels");
    warn.forFunctions(exports2, "useFormat", ["cli"]);
    warn.forProperties(exports2, "useFormat", ["padLevels", "stripColors"]);
    warn.forFunctions(exports2, "deprecated", [
      "addRewriter",
      "addFilter",
      "clone",
      "extend"
    ]);
    warn.forProperties(exports2, "deprecated", ["emitErrs", "levelLength"]);
  }
});

// dist/services/claudeService.js
var require_claudeService = __commonJS({
  "dist/services/claudeService.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.ClaudeService = void 0;
    var ClaudeService = class {
      constructor(logger, sessionManager) {
        this.logger = logger;
        this.sessionManager = sessionManager;
        this.isInitialized = false;
        this.isProcessing = false;
        this.currentAbortController = null;
        this.defaultOptions = {
          system_prompt: "You are a helpful assistant.",
          max_turns: 20
        };
        this.logger.info("Claude Service initialized");
      }
      initialize(config) {
        try {
          this.logger.info(`Initializing with config: ${JSON.stringify(config)}`);
          if (config.system_prompt) {
            this.defaultOptions.system_prompt = config.system_prompt;
          }
          if (config.max_turns) {
            this.defaultOptions.max_turns = config.max_turns;
          }
          if (config.cwd) {
            this.defaultOptions.cwd = config.cwd;
          }
          if (config.allowed_tools) {
            this.defaultOptions.allowed_tools = config.allowed_tools;
          }
          if (config.permission_mode) {
            this.defaultOptions.permission_mode = config.permission_mode;
          }
          if (config.max_thinking_tokens) {
            this.defaultOptions.max_thinking_tokens = config.max_thinking_tokens;
          }
          if (config.model) {
            this.defaultOptions.model = config.model;
          }
          this.isInitialized = true;
          this.logger.info("Service initialized successfully");
          return { success: true, message: "Service initialized" };
        } catch (error) {
          this.logger.error(`Failed to initialize: ${error}`);
          return { success: false, error: String(error) };
        }
      }
      async checkHealth() {
        return {
          isHealthy: this.isInitialized,
          isProcessing: this.isProcessing,
          activeSessions: this.sessionManager.getActiveSessions().length
        };
      }
      getStatus() {
        return {
          initialized: this.isInitialized,
          sdk_available: false,
          active_sessions: this.sessionManager.getActiveSessions().length,
          is_processing: this.isProcessing
        };
      }
      abort() {
        if (this.currentAbortController) {
          this.logger.info("Aborting current request...");
          this.currentAbortController.abort();
          this.currentAbortController = null;
          this.isProcessing = false;
          return true;
        }
        return false;
      }
      async *streamMessage(message, sessionId, customOptions) {
        if (this.isProcessing) {
          yield { type: "error", error: "Service is busy processing another request. Please wait or abort the current request." };
          return;
        }
        if (!this.isInitialized) {
          this.logger.info("Service not initialized, attempting auto-initialization...");
          const initConfig = (customOptions === null || customOptions === void 0 ? void 0 : customOptions.cwd) ? { cwd: customOptions.cwd } : {};
          const initResult = this.initialize(initConfig);
          if (!initResult.success) {
            yield { type: "error", error: `Failed to auto-initialize: ${initResult.error}` };
            return;
          }
        }
        this.logger.info(`Stream processing message (session: ${sessionId}): ${message.substring(0, 100)}...`);
        const session = this.sessionManager.getSession(sessionId);
        if (!session) {
          yield { type: "error", error: "Session expired or not found" };
          return;
        }
        this.isProcessing = true;
        this.currentAbortController = new AbortController();
        try {
          yield {
            type: "text",
            content: "Claude SDK integration is temporarily disabled while we update the packaging configuration. The service is running correctly.",
            session_id: sessionId
          };
          this.sessionManager.updateSession(sessionId, {
            is_first_message: false,
            message_count: session.message_count + 1
          });
        } catch (error) {
          this.logger.error(`Error in streamMessage: ${error}`);
          yield { type: "error", error: String(error) };
        } finally {
          this.isProcessing = false;
          this.currentAbortController = null;
        }
      }
      async getSingleResponse(message, sessionId, customOptions) {
        const responseChunks = [];
        let error = null;
        try {
          for await (const chunk of this.streamMessage(message, sessionId, customOptions)) {
            if (chunk.type === "error") {
              error = chunk.error || "Unknown error";
              break;
            } else if (chunk.type === "text") {
              responseChunks.push(chunk.content || "");
            }
          }
          if (error) {
            return { success: false, error, session_id: sessionId };
          }
          return {
            success: true,
            response: responseChunks.join(""),
            session_id: sessionId
          };
        } catch (err) {
          return {
            success: false,
            error: String(err),
            session_id: sessionId
          };
        }
      }
    };
    exports2.ClaudeService = ClaudeService;
  }
});

// node_modules/uuid/dist/esm-node/rng.js
function rng() {
  if (poolPtr > rnds8Pool.length - 16) {
    import_crypto.default.randomFillSync(rnds8Pool);
    poolPtr = 0;
  }
  return rnds8Pool.slice(poolPtr, poolPtr += 16);
}
var import_crypto, rnds8Pool, poolPtr;
var init_rng = __esm({
  "node_modules/uuid/dist/esm-node/rng.js"() {
    import_crypto = __toESM(require("crypto"));
    rnds8Pool = new Uint8Array(256);
    poolPtr = rnds8Pool.length;
  }
});

// node_modules/uuid/dist/esm-node/regex.js
var regex_default;
var init_regex = __esm({
  "node_modules/uuid/dist/esm-node/regex.js"() {
    regex_default = /^(?:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}|00000000-0000-0000-0000-000000000000)$/i;
  }
});

// node_modules/uuid/dist/esm-node/validate.js
function validate(uuid) {
  return typeof uuid === "string" && regex_default.test(uuid);
}
var validate_default;
var init_validate = __esm({
  "node_modules/uuid/dist/esm-node/validate.js"() {
    init_regex();
    validate_default = validate;
  }
});

// node_modules/uuid/dist/esm-node/stringify.js
function unsafeStringify(arr, offset = 0) {
  return byteToHex[arr[offset + 0]] + byteToHex[arr[offset + 1]] + byteToHex[arr[offset + 2]] + byteToHex[arr[offset + 3]] + "-" + byteToHex[arr[offset + 4]] + byteToHex[arr[offset + 5]] + "-" + byteToHex[arr[offset + 6]] + byteToHex[arr[offset + 7]] + "-" + byteToHex[arr[offset + 8]] + byteToHex[arr[offset + 9]] + "-" + byteToHex[arr[offset + 10]] + byteToHex[arr[offset + 11]] + byteToHex[arr[offset + 12]] + byteToHex[arr[offset + 13]] + byteToHex[arr[offset + 14]] + byteToHex[arr[offset + 15]];
}
function stringify(arr, offset = 0) {
  const uuid = unsafeStringify(arr, offset);
  if (!validate_default(uuid)) {
    throw TypeError("Stringified UUID is invalid");
  }
  return uuid;
}
var byteToHex, stringify_default;
var init_stringify = __esm({
  "node_modules/uuid/dist/esm-node/stringify.js"() {
    init_validate();
    byteToHex = [];
    for (let i = 0; i < 256; ++i) {
      byteToHex.push((i + 256).toString(16).slice(1));
    }
    stringify_default = stringify;
  }
});

// node_modules/uuid/dist/esm-node/v1.js
function v1(options, buf, offset) {
  let i = buf && offset || 0;
  const b = buf || new Array(16);
  options = options || {};
  let node = options.node || _nodeId;
  let clockseq = options.clockseq !== void 0 ? options.clockseq : _clockseq;
  if (node == null || clockseq == null) {
    const seedBytes = options.random || (options.rng || rng)();
    if (node == null) {
      node = _nodeId = [seedBytes[0] | 1, seedBytes[1], seedBytes[2], seedBytes[3], seedBytes[4], seedBytes[5]];
    }
    if (clockseq == null) {
      clockseq = _clockseq = (seedBytes[6] << 8 | seedBytes[7]) & 16383;
    }
  }
  let msecs = options.msecs !== void 0 ? options.msecs : Date.now();
  let nsecs = options.nsecs !== void 0 ? options.nsecs : _lastNSecs + 1;
  const dt = msecs - _lastMSecs + (nsecs - _lastNSecs) / 1e4;
  if (dt < 0 && options.clockseq === void 0) {
    clockseq = clockseq + 1 & 16383;
  }
  if ((dt < 0 || msecs > _lastMSecs) && options.nsecs === void 0) {
    nsecs = 0;
  }
  if (nsecs >= 1e4) {
    throw new Error("uuid.v1(): Can't create more than 10M uuids/sec");
  }
  _lastMSecs = msecs;
  _lastNSecs = nsecs;
  _clockseq = clockseq;
  msecs += 122192928e5;
  const tl = ((msecs & 268435455) * 1e4 + nsecs) % 4294967296;
  b[i++] = tl >>> 24 & 255;
  b[i++] = tl >>> 16 & 255;
  b[i++] = tl >>> 8 & 255;
  b[i++] = tl & 255;
  const tmh = msecs / 4294967296 * 1e4 & 268435455;
  b[i++] = tmh >>> 8 & 255;
  b[i++] = tmh & 255;
  b[i++] = tmh >>> 24 & 15 | 16;
  b[i++] = tmh >>> 16 & 255;
  b[i++] = clockseq >>> 8 | 128;
  b[i++] = clockseq & 255;
  for (let n = 0; n < 6; ++n) {
    b[i + n] = node[n];
  }
  return buf || unsafeStringify(b);
}
var _nodeId, _clockseq, _lastMSecs, _lastNSecs, v1_default;
var init_v1 = __esm({
  "node_modules/uuid/dist/esm-node/v1.js"() {
    init_rng();
    init_stringify();
    _lastMSecs = 0;
    _lastNSecs = 0;
    v1_default = v1;
  }
});

// node_modules/uuid/dist/esm-node/parse.js
function parse(uuid) {
  if (!validate_default(uuid)) {
    throw TypeError("Invalid UUID");
  }
  let v;
  const arr = new Uint8Array(16);
  arr[0] = (v = parseInt(uuid.slice(0, 8), 16)) >>> 24;
  arr[1] = v >>> 16 & 255;
  arr[2] = v >>> 8 & 255;
  arr[3] = v & 255;
  arr[4] = (v = parseInt(uuid.slice(9, 13), 16)) >>> 8;
  arr[5] = v & 255;
  arr[6] = (v = parseInt(uuid.slice(14, 18), 16)) >>> 8;
  arr[7] = v & 255;
  arr[8] = (v = parseInt(uuid.slice(19, 23), 16)) >>> 8;
  arr[9] = v & 255;
  arr[10] = (v = parseInt(uuid.slice(24, 36), 16)) / 1099511627776 & 255;
  arr[11] = v / 4294967296 & 255;
  arr[12] = v >>> 24 & 255;
  arr[13] = v >>> 16 & 255;
  arr[14] = v >>> 8 & 255;
  arr[15] = v & 255;
  return arr;
}
var parse_default;
var init_parse = __esm({
  "node_modules/uuid/dist/esm-node/parse.js"() {
    init_validate();
    parse_default = parse;
  }
});

// node_modules/uuid/dist/esm-node/v35.js
function stringToBytes(str) {
  str = unescape(encodeURIComponent(str));
  const bytes = [];
  for (let i = 0; i < str.length; ++i) {
    bytes.push(str.charCodeAt(i));
  }
  return bytes;
}
function v35(name, version2, hashfunc) {
  function generateUUID(value, namespace, buf, offset) {
    var _namespace;
    if (typeof value === "string") {
      value = stringToBytes(value);
    }
    if (typeof namespace === "string") {
      namespace = parse_default(namespace);
    }
    if (((_namespace = namespace) === null || _namespace === void 0 ? void 0 : _namespace.length) !== 16) {
      throw TypeError("Namespace must be array-like (16 iterable integer values, 0-255)");
    }
    let bytes = new Uint8Array(16 + value.length);
    bytes.set(namespace);
    bytes.set(value, namespace.length);
    bytes = hashfunc(bytes);
    bytes[6] = bytes[6] & 15 | version2;
    bytes[8] = bytes[8] & 63 | 128;
    if (buf) {
      offset = offset || 0;
      for (let i = 0; i < 16; ++i) {
        buf[offset + i] = bytes[i];
      }
      return buf;
    }
    return unsafeStringify(bytes);
  }
  try {
    generateUUID.name = name;
  } catch (err) {
  }
  generateUUID.DNS = DNS;
  generateUUID.URL = URL;
  return generateUUID;
}
var DNS, URL;
var init_v35 = __esm({
  "node_modules/uuid/dist/esm-node/v35.js"() {
    init_stringify();
    init_parse();
    DNS = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
    URL = "6ba7b811-9dad-11d1-80b4-00c04fd430c8";
  }
});

// node_modules/uuid/dist/esm-node/md5.js
function md5(bytes) {
  if (Array.isArray(bytes)) {
    bytes = Buffer.from(bytes);
  } else if (typeof bytes === "string") {
    bytes = Buffer.from(bytes, "utf8");
  }
  return import_crypto2.default.createHash("md5").update(bytes).digest();
}
var import_crypto2, md5_default;
var init_md5 = __esm({
  "node_modules/uuid/dist/esm-node/md5.js"() {
    import_crypto2 = __toESM(require("crypto"));
    md5_default = md5;
  }
});

// node_modules/uuid/dist/esm-node/v3.js
var v3, v3_default;
var init_v3 = __esm({
  "node_modules/uuid/dist/esm-node/v3.js"() {
    init_v35();
    init_md5();
    v3 = v35("v3", 48, md5_default);
    v3_default = v3;
  }
});

// node_modules/uuid/dist/esm-node/native.js
var import_crypto3, native_default;
var init_native = __esm({
  "node_modules/uuid/dist/esm-node/native.js"() {
    import_crypto3 = __toESM(require("crypto"));
    native_default = {
      randomUUID: import_crypto3.default.randomUUID
    };
  }
});

// node_modules/uuid/dist/esm-node/v4.js
function v4(options, buf, offset) {
  if (native_default.randomUUID && !buf && !options) {
    return native_default.randomUUID();
  }
  options = options || {};
  const rnds = options.random || (options.rng || rng)();
  rnds[6] = rnds[6] & 15 | 64;
  rnds[8] = rnds[8] & 63 | 128;
  if (buf) {
    offset = offset || 0;
    for (let i = 0; i < 16; ++i) {
      buf[offset + i] = rnds[i];
    }
    return buf;
  }
  return unsafeStringify(rnds);
}
var v4_default;
var init_v4 = __esm({
  "node_modules/uuid/dist/esm-node/v4.js"() {
    init_native();
    init_rng();
    init_stringify();
    v4_default = v4;
  }
});

// node_modules/uuid/dist/esm-node/sha1.js
function sha1(bytes) {
  if (Array.isArray(bytes)) {
    bytes = Buffer.from(bytes);
  } else if (typeof bytes === "string") {
    bytes = Buffer.from(bytes, "utf8");
  }
  return import_crypto4.default.createHash("sha1").update(bytes).digest();
}
var import_crypto4, sha1_default;
var init_sha1 = __esm({
  "node_modules/uuid/dist/esm-node/sha1.js"() {
    import_crypto4 = __toESM(require("crypto"));
    sha1_default = sha1;
  }
});

// node_modules/uuid/dist/esm-node/v5.js
var v5, v5_default;
var init_v5 = __esm({
  "node_modules/uuid/dist/esm-node/v5.js"() {
    init_v35();
    init_sha1();
    v5 = v35("v5", 80, sha1_default);
    v5_default = v5;
  }
});

// node_modules/uuid/dist/esm-node/nil.js
var nil_default;
var init_nil = __esm({
  "node_modules/uuid/dist/esm-node/nil.js"() {
    nil_default = "00000000-0000-0000-0000-000000000000";
  }
});

// node_modules/uuid/dist/esm-node/version.js
function version(uuid) {
  if (!validate_default(uuid)) {
    throw TypeError("Invalid UUID");
  }
  return parseInt(uuid.slice(14, 15), 16);
}
var version_default;
var init_version = __esm({
  "node_modules/uuid/dist/esm-node/version.js"() {
    init_validate();
    version_default = version;
  }
});

// node_modules/uuid/dist/esm-node/index.js
var esm_node_exports = {};
__export(esm_node_exports, {
  NIL: () => nil_default,
  parse: () => parse_default,
  stringify: () => stringify_default,
  v1: () => v1_default,
  v3: () => v3_default,
  v4: () => v4_default,
  v5: () => v5_default,
  validate: () => validate_default,
  version: () => version_default
});
var init_esm_node = __esm({
  "node_modules/uuid/dist/esm-node/index.js"() {
    init_v1();
    init_v3();
    init_v4();
    init_v5();
    init_nil();
    init_version();
    init_validate();
    init_stringify();
    init_parse();
  }
});

// dist/services/sessionManager.js
var require_sessionManager = __commonJS({
  "dist/services/sessionManager.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.SessionManager = void 0;
    var uuid_1 = (init_esm_node(), __toCommonJS(esm_node_exports));
    var SessionManager = class {
      constructor(logger) {
        this.logger = logger;
        this.sessions = /* @__PURE__ */ new Map();
        this.sessionTimeout = 2 * 60 * 60 * 1e3;
        this.defaultSessionId = null;
        setInterval(() => {
          this.cleanExpiredSessions();
        }, 5 * 60 * 1e3);
      }
      createSession() {
        const sessionId = (0, uuid_1.v4)();
        const session = {
          id: sessionId,
          created_at: /* @__PURE__ */ new Date(),
          last_activity: /* @__PURE__ */ new Date(),
          message_count: 0,
          is_first_message: true
        };
        this.sessions.set(sessionId, session);
        this.logger.info(`Created new session: ${sessionId}`);
        return sessionId;
      }
      getSession(sessionId) {
        const session = this.sessions.get(sessionId);
        if (!session) {
          return null;
        }
        if (Date.now() - session.last_activity.getTime() > this.sessionTimeout) {
          this.logger.info(`Session ${sessionId} expired`);
          this.sessions.delete(sessionId);
          if (this.defaultSessionId === sessionId) {
            this.defaultSessionId = null;
          }
          return null;
        }
        return session;
      }
      updateSession(sessionId, updates) {
        const session = this.sessions.get(sessionId);
        if (session) {
          Object.assign(session, updates, { last_activity: /* @__PURE__ */ new Date() });
        }
      }
      getOrCreateDefaultSession() {
        if (this.defaultSessionId) {
          const session = this.getSession(this.defaultSessionId);
          if (session) {
            return this.defaultSessionId;
          }
        }
        this.defaultSessionId = this.createSession();
        this.logger.info(`Created new default session: ${this.defaultSessionId}`);
        return this.defaultSessionId;
      }
      cleanExpiredSessions() {
        const now = Date.now();
        const expired = [];
        for (const [id, session] of this.sessions) {
          if (now - session.last_activity.getTime() > this.sessionTimeout) {
            expired.push(id);
          }
        }
        for (const id of expired) {
          this.logger.info(`Cleaning expired session: ${id}`);
          this.sessions.delete(id);
          if (this.defaultSessionId === id) {
            this.defaultSessionId = null;
          }
        }
      }
      getActiveSessions() {
        this.cleanExpiredSessions();
        return Array.from(this.sessions.values());
      }
    };
    exports2.SessionManager = SessionManager;
  }
});

// dist/utils/logger.js
var require_logger2 = __commonJS({
  "dist/utils/logger.js"(exports2) {
    "use strict";
    var __importDefault = exports2 && exports2.__importDefault || function(mod) {
      return mod && mod.__esModule ? mod : { "default": mod };
    };
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.Logger = void 0;
    var winston_1 = __importDefault(require_winston());
    var Logger = class {
      constructor(name) {
        this.logger = winston_1.default.createLogger({
          level: process.env.LOG_LEVEL || "info",
          format: winston_1.default.format.combine(winston_1.default.format.timestamp(), winston_1.default.format.printf(({ timestamp, level, message }) => {
            return `${timestamp} [${name}] [${level.toUpperCase()}] ${message}`;
          })),
          transports: [
            new winston_1.default.transports.Console()
          ]
        });
      }
      info(message, ...args) {
        this.logger.info(message, ...args);
      }
      warn(message, ...args) {
        this.logger.warn(message, ...args);
      }
      error(message, ...args) {
        this.logger.error(message, ...args);
      }
      debug(message, ...args) {
        this.logger.debug(message, ...args);
      }
    };
    exports2.Logger = Logger;
  }
});

// dist/services/unixSocketService.js
var require_unixSocketService = __commonJS({
  "dist/services/unixSocketService.js"(exports2) {
    "use strict";
    var __createBinding = exports2 && exports2.__createBinding || (Object.create ? function(o, m, k, k2) {
      if (k2 === void 0) k2 = k;
      var desc = Object.getOwnPropertyDescriptor(m, k);
      if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
        desc = { enumerable: true, get: function() {
          return m[k];
        } };
      }
      Object.defineProperty(o, k2, desc);
    } : function(o, m, k, k2) {
      if (k2 === void 0) k2 = k;
      o[k2] = m[k];
    });
    var __setModuleDefault = exports2 && exports2.__setModuleDefault || (Object.create ? function(o, v) {
      Object.defineProperty(o, "default", { enumerable: true, value: v });
    } : function(o, v) {
      o["default"] = v;
    });
    var __importStar = exports2 && exports2.__importStar || /* @__PURE__ */ function() {
      var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function(o2) {
          var ar = [];
          for (var k in o2) if (Object.prototype.hasOwnProperty.call(o2, k)) ar[ar.length] = k;
          return ar;
        };
        return ownKeys(o);
      };
      return function(mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) {
          for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        }
        __setModuleDefault(result, mod);
        return result;
      };
    }();
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.UnixSocketService = void 0;
    var net = __importStar(require("net"));
    var os = __importStar(require("os"));
    var path = __importStar(require("path"));
    var fs = __importStar(require("fs"));
    var logger_1 = require_logger2();
    var uuid_1 = (init_esm_node(), __toCommonJS(esm_node_exports));
    var UnixSocketService = class {
      constructor(claudeService) {
        this.server = null;
        this.clients = /* @__PURE__ */ new Map();
        this.activeStreams = /* @__PURE__ */ new Map();
        this.logger = new logger_1.Logger("UnixSocketService");
        this.claudeService = claudeService;
        const tmpDir = os.tmpdir();
        const timestamp = Date.now();
        const pid = process.pid;
        this.socketPath = path.join(tmpDir, `claude-code-plus-${pid}-${timestamp}.sock`);
      }
      async start() {
        return new Promise((resolve, reject) => {
          try {
            if (fs.existsSync(this.socketPath)) {
              fs.unlinkSync(this.socketPath);
            }
            this.server = net.createServer((socket) => {
              const clientId = (0, uuid_1.v4)();
              this.clients.set(clientId, socket);
              this.logger.info(`Client connected: ${clientId}`);
              socket.setEncoding("utf8");
              let buffer = "";
              socket.on("data", (data) => {
                buffer += data;
                const lines = buffer.split("\n");
                buffer = lines.pop() || "";
                for (const line of lines) {
                  if (line.trim()) {
                    this.handleMessage(clientId, socket, line.trim());
                  }
                }
              });
              socket.on("end", () => {
                this.logger.info(`Client disconnected: ${clientId}`);
                this.clients.delete(clientId);
                for (const [streamId] of this.activeStreams) {
                  if (streamId.startsWith(clientId)) {
                    this.activeStreams.delete(streamId);
                  }
                }
              });
              socket.on("error", (err) => {
                this.logger.error(`Socket error for client ${clientId}:`, err);
                this.clients.delete(clientId);
              });
            });
            this.server.listen(this.socketPath, () => {
              this.logger.info(`Unix socket server listening on: ${this.socketPath}`);
              console.log(`SOCKET_PATH:${this.socketPath}`);
              resolve(this.socketPath);
            });
            this.server.on("error", (err) => {
              this.logger.error("Server error:", err);
              reject(err);
            });
          } catch (error) {
            this.logger.error("Failed to start Unix socket server:", error);
            reject(error);
          }
        });
      }
      async handleMessage(clientId, socket, data) {
        try {
          const request = JSON.parse(data);
          if (request.jsonrpc !== "2.0") {
            this.sendError(socket, request.id, -32600, "Invalid Request");
            return;
          }
          this.logger.debug(`Received request: ${request.method} (${request.id})`);
          switch (request.method) {
            case "health":
              await this.handleHealth(socket, request);
              break;
            case "stream":
              await this.handleStream(clientId, socket, request);
              break;
            case "abort":
              await this.handleAbort(clientId, socket, request);
              break;
            default:
              this.sendError(socket, request.id, -32601, `Method not found: ${request.method}`);
          }
        } catch (error) {
          this.logger.error("Error handling message:", error);
          try {
            const errRequest = JSON.parse(data);
            this.sendError(socket, errRequest.id || null, -32700, "Parse error");
          } catch (_a) {
            this.sendError(socket, null, -32700, "Parse error");
          }
        }
      }
      async handleHealth(socket, request) {
        try {
          const health = await this.claudeService.checkHealth();
          this.sendResponse(socket, request.id, health);
        } catch (error) {
          this.sendError(socket, request.id, -32603, error.message);
        }
      }
      async handleStream(clientId, socket, request) {
        const { message, options } = request.params || {};
        if (!message) {
          this.sendError(socket, request.id, -32602, "Invalid params: message is required");
          return;
        }
        const streamId = `${clientId}-${request.id}`;
        this.activeStreams.set(streamId, true);
        try {
          this.sendNotification(socket, "stream.start", {
            id: request.id,
            timestamp: (/* @__PURE__ */ new Date()).toISOString()
          });
          const sessionId = this.claudeService.sessionManager.createSession();
          for await (const chunk of this.claudeService.streamMessage(message, sessionId, options)) {
            if (!this.activeStreams.get(streamId)) {
              throw new Error("Stream aborted");
            }
            if (chunk.type === "content" && chunk.content) {
              this.sendNotification(socket, "stream.chunk", {
                id: request.id,
                chunk: chunk.content
              });
            } else if (chunk.type === "error") {
              throw new Error(chunk.error || "Unknown error");
            }
          }
          this.activeStreams.delete(streamId);
          this.sendResponse(socket, request.id, {
            status: "completed",
            timestamp: (/* @__PURE__ */ new Date()).toISOString()
          });
        } catch (error) {
          this.activeStreams.delete(streamId);
          if (error.message === "Stream aborted") {
            this.sendResponse(socket, request.id, {
              status: "aborted",
              timestamp: (/* @__PURE__ */ new Date()).toISOString()
            });
          } else {
            this.sendError(socket, request.id, -32603, error.message);
          }
        }
      }
      async handleAbort(clientId, socket, request) {
        try {
          const { streamId } = request.params || {};
          if (streamId) {
            const fullStreamId = `${clientId}-${streamId}`;
            if (this.activeStreams.has(fullStreamId)) {
              this.activeStreams.delete(fullStreamId);
              this.sendResponse(socket, request.id, { aborted: true, streamId });
              return;
            }
          }
          const aborted = this.claudeService.abort();
          this.sendResponse(socket, request.id, { aborted });
        } catch (error) {
          this.sendError(socket, request.id, -32603, error.message);
        }
      }
      sendResponse(socket, id, result) {
        const response = {
          jsonrpc: "2.0",
          id,
          result
        };
        this.send(socket, response);
      }
      sendError(socket, id, code, message, data) {
        const response = {
          jsonrpc: "2.0",
          id: id || 0,
          error: { code, message, data }
        };
        this.send(socket, response);
      }
      sendNotification(socket, method, params) {
        const notification = {
          jsonrpc: "2.0",
          method,
          params
        };
        this.send(socket, notification);
      }
      send(socket, data) {
        try {
          const json = JSON.stringify(data) + "\n";
          socket.write(json);
        } catch (error) {
          this.logger.error("Failed to send data:", error);
        }
      }
      stop() {
        try {
          for (const [clientId, socket] of this.clients) {
            socket.end();
          }
          this.clients.clear();
          this.activeStreams.clear();
          if (this.server) {
            this.server.close();
            this.server = null;
          }
          if (fs.existsSync(this.socketPath)) {
            fs.unlinkSync(this.socketPath);
          }
          this.logger.info("Unix socket server stopped");
        } catch (error) {
          this.logger.error("Error stopping server:", error);
        }
      }
      getSocketPath() {
        return this.socketPath;
      }
    };
    exports2.UnixSocketService = UnixSocketService;
  }
});

// dist/server.js
var require_server = __commonJS({
  "dist/server.js"(exports2) {
    "use strict";
    var __importDefault = exports2 && exports2.__importDefault || function(mod) {
      return mod && mod.__esModule ? mod : { "default": mod };
    };
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.logger = void 0;
    var winston_1 = __importDefault(require_winston());
    var claudeService_1 = require_claudeService();
    var sessionManager_1 = require_sessionManager();
    var unixSocketService_1 = require_unixSocketService();
    var logger = winston_1.default.createLogger({
      level: "error",
      format: winston_1.default.format.combine(winston_1.default.format.timestamp(), winston_1.default.format.printf(({ timestamp, level, message }) => {
        return `${timestamp} [${level.toUpperCase()}] ${message}`;
      })),
      transports: [
        new winston_1.default.transports.Console()
      ]
    });
    exports2.logger = logger;
    var sessionManager = new sessionManager_1.SessionManager(logger);
    var claudeService = new claudeService_1.ClaudeService(logger, sessionManager);
    var socketService = new unixSocketService_1.UnixSocketService(claudeService);
    socketService.start().then((socketPath) => {
      logger.info("Server started successfully");
    }).catch((error) => {
      logger.error("Failed to start server:", error);
      process.exit(1);
    });
    var shutdown = () => {
      logger.info("Shutting down server...");
      socketService.stop();
      process.exit(0);
    };
    process.on("SIGINT", shutdown);
    process.on("SIGTERM", shutdown);
    process.on("uncaughtException", (error) => {
      logger.error("Uncaught exception:", error);
      shutdown();
    });
    process.on("unhandledRejection", (reason, promise) => {
      logger.error("Unhandled rejection at:", promise, "reason:", reason);
      shutdown();
    });
  }
});

// dist/_temp_entry.js
var server = require_server();
if (require.main === module) {
}
if (typeof global !== "undefined" && !global.import) {
  global.import = { meta: { url: "file://" + __filename } };
}
module.exports = server;
