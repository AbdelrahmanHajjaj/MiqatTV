'use strict';
var fs = require('fs'), crypto = require('crypto'), wire = require('./companion-wire');
// Trust/identity files stay outside launcher preferences and require private POSIX storage.
module.exports = function (directory, name) {
  var flags = fs.constants || require('constants');
  if (!flags || !flags.O_NOFOLLOW || !flags.O_DIRECTORY || typeof process.getuid !== 'function' || !/^[a-z][a-z0-9-]*\.json$/.test(name)) { throw new Error('Unsupported private storage'); }
  var path = directory + '/' + name, owner = process.getuid(), maximum = 65536;
  function safe(info, directory) {
    if (!(directory ? info.isDirectory() : info.isFile()) || info.uid !== owner || (info.mode & 63) !== 0 || !directory && info.size > maximum) { throw new Error('Unsafe companion storage'); }
  }
  function parent() { safe(fs.lstatSync(directory), true); }
  function existing() {
    var info;
    try { info = fs.lstatSync(path); } catch (error) { if (error.code === 'ENOENT') { return null; } throw error; }
    safe(info, false); return info;
  }
  parent();
  return {
    read: function () {
      parent(); var info = existing(); if (info === null) { return null; }
      var fd = fs.openSync(path, flags.O_RDONLY | flags.O_NOFOLLOW), bytes;
      try {
        var opened = fs.fstatSync(fd); safe(opened, false);
        if (opened.ino !== info.ino || opened.dev !== info.dev) { throw new Error('Companion file changed'); }
        bytes = wire.buffer(new Array(opened.size + 1).join('\0'), 'binary');
        var offset = 0, count;
        while (offset < bytes.length) {
          count = fs.readSync(fd, bytes, offset, bytes.length - offset, offset);
          if (count <= 0) { throw new Error('Truncated companion file'); } offset += count;
        }
        if (fs.fstatSync(fd).size !== opened.size) { throw new Error('Companion file changed'); }
      } finally { fs.closeSync(fd); }
      return wire.json(bytes, maximum);
    },
    write: function (value) {
      parent(); existing();
      var bytes = wire.buffer(JSON.stringify(value)); wire.json(bytes, maximum);
      var temporary = path + '.tmp-' + crypto.randomBytes(12).toString('hex'), fd, created = false, renamed = false;
      try {
        fd = fs.openSync(temporary, flags.O_WRONLY | flags.O_CREAT | flags.O_EXCL | flags.O_NOFOLLOW, 384); created = true;
        try {
          safe(fs.fstatSync(fd), false); var offset = 0, count;
          while (offset < bytes.length) {
            count = fs.writeSync(fd, bytes, offset, bytes.length - offset, offset);
            if (count <= 0) { throw new Error('Incomplete companion write'); } offset += count;
          }
          fs.fsyncSync(fd);
        }
        finally { fs.closeSync(fd); }
        parent(); existing(); fs.renameSync(temporary, path); renamed = true;
        fd = fs.openSync(directory, flags.O_RDONLY | flags.O_DIRECTORY | flags.O_NOFOLLOW);
        try { safe(fs.fstatSync(fd), true); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
      } finally {
        if (created && !renamed) { try { fs.unlinkSync(temporary); } catch (cleanup) {} }
      }
    }
  };
};
