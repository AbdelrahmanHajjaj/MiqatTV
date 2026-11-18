/* Approved medallions, connected rounded modules and theme-derived colours. ES5 for old LG. */
(function (root) {
  'use strict';
  function rgb(hex) {
    if (typeof hex !== 'string' || !/^#[0-9a-f]{6}$/i.test(hex)) throw new Error('Invalid QR colour');
    return [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
  }
  function luminance(hex) {
    return rgb(hex).map(function (x) { x /= 255; return x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4); })
      .reduce(function (sum, x, i) { return sum + x * [0.2126, 0.7152, 0.0722][i]; }, 0);
  }
  function shade(hex, factor) { return '#' + rgb(hex).map(function (x) { return ('0' + Math.round(x * factor).toString(16)).slice(-2); }).join(''); }
  function palette(colors, style, fallback) {
    var policy = style.palette, paper = luminance(colors['plaster-100']) >= policy.minPaperLuminance ? colors['plaster-100'] : fallback;
    function dark(hex, minimum) {
      var i = 0;
      while ((luminance(paper) + 0.05) / (luminance(hex) + 0.05) < minimum && i++ < 64) hex = shade(hex, policy.darken);
      return hex;
    }
    var ground = rgb(colors['lapis-900']), warm = ground[0] > ground[1] * policy.warmRedRatio && ground[1] > ground[2] * policy.warmGreenRatio;
    var accent = policy.accentShades.map(function (factor) { return dark(shade(colors['gold-400'], factor), policy.minContrast); });
    var body = [colors['lapis-900'], colors['lapis-700']].map(function (hex) { return dark(hex, policy.minContrast); });
    rgb(colors['gold-400']); rgb(paper);
    return {paper: paper, body: warm ? accent : body, rim: warm ? body : accent, centre: warm ? accent : body, inlay: dark(colors['gold-400'], policy.minInlayContrast)};
  }
  function path(commands) { return commands.map(function (p) { return p[0] + p.slice(1).join(' '); }).join(''); }
  function rect(r, fill, stroke, width) { return '<rect x="' + r[0] + '" y="' + r[1] + '" width="' + r[2] + '" height="' + r[3] + '" rx="' + r[4] + '" fill="' + fill + '"' + (stroke ? ' stroke="' + stroke + '" stroke-width="' + width + '"' : '') + '/>'; }
  function svg(code, style, colors, fallback, logo) {
    if (typeof logo !== 'string' || !/^data:image\/png;base64,[A-Za-z0-9+/]+=*$/.test(logo)) throw new Error('Local QR mark required');
    var n = code.getModuleCount(), m = style.quietZone, size = n + 2 * m, body = '', x, y, p = palette(colors, style, fallback), defs = '', pupils = path(style.pupil);
    function finder(x, y) { return x < 7 && y < 7 || x >= n - 7 && y < 7 || x < 7 && y >= n - 7; }
    function dark(x, y) { return x >= 0 && y >= 0 && x < n && y < n && !finder(x, y) && code.isDark(y, x); }
    ['body', 'rim', 'centre'].forEach(function (type) {
      defs += '<linearGradient id="miqat-qr-' + type + '" ' + (type === 'body' ? 'gradientUnits="userSpaceOnUse" x1="' + m + '" y1="' + m + '" x2="' + (n + m) + '" y2="' + (n + m) + '"' : 'x1="0%" y1="0%" x2="100%" y2="100%"') + '><stop offset="0%" stop-color="' + p[type][0] + '"/><stop offset="100%" stop-color="' + p[type][1] + '"/></linearGradient>';
    });
    for (y = 0; y < n; y++) for (x = 0; x < n; x++) {
      if (!dark(x, y)) continue;
      var r = style.cornerRadius, tl = !dark(x - 1, y) && !dark(x, y - 1) ? r : 0, tr = !dark(x + 1, y) && !dark(x, y - 1) ? r : 0;
      var br = !dark(x + 1, y) && !dark(x, y + 1) ? r : 0, bl = !dark(x - 1, y) && !dark(x, y + 1) ? r : 0;
      var xx = x + m, yy = y + m;
      var d = x === 6 || y === 6 ? 'M' + xx + ' ' + yy + 'h1v1h-1Z' :
        'M' + (xx + tl) + ' ' + yy + 'H' + (xx + 1 - tr) + 'Q' + (xx + 1) + ' ' + yy + ' ' + (xx + 1) + ' ' + (yy + tr) +
        'V' + (yy + 1 - br) + 'Q' + (xx + 1) + ' ' + (yy + 1) + ' ' + (xx + 1 - br) + ' ' + (yy + 1) + 'H' + (xx + bl) +
        'Q' + xx + ' ' + (yy + 1) + ' ' + xx + ' ' + (yy + 1 - bl) + 'V' + (yy + tl) + 'Q' + xx + ' ' + yy + ' ' + (xx + tl) + ' ' + yy + 'Z';
      body += '<path d="' + d + '"/>';
    }
    var eyes = '';
    [[0, 0], [n - 7, 0], [0, n - 7]].forEach(function (at) {
      eyes += '<g transform="translate(' + (at[0] + m) + ' ' + (at[1] + m) + ')">' + rect(style.eye, 'url(#miqat-qr-rim)') + rect(style.hole, p.paper) +
        '<path d="' + pupils + '" fill="url(#miqat-qr-centre)"/>' + rect(style.inlay, 'none', p.inlay, style.inlayWidth) +
        '<path d="' + pupils + '" fill="none" stroke="' + p.inlay + '" stroke-width="' + style.inlayWidth + '" stroke-linejoin="round"/></g>';
    });
    var centre = style.centre, span = centre.size, left = m + (n - span) / 2, top = left, gap = centre.clearance, art = centre.artwork;
    defs += '<filter id="miqat-qr-logo-tint" color-interpolation-filters="sRGB"><feFlood flood-color="' + colors['gold-400'] + '"/><feComposite in2="SourceAlpha" operator="in"/></filter>';
    var mark = '<g class="qr-mark" data-clearance="' + gap + '">' + rect([left - gap, top - gap, span + 2 * gap, span + 2 * gap, centre.radius + gap], p.paper) +
      rect([left, top, span, span, centre.radius], 'url(#miqat-qr-body)') +
      '<svg x="' + (left + art[0]) + '" y="' + (top + art[1]) + '" width="' + art[2] + '" height="' + art[3] + '" viewBox="' + centre.viewBox.join(' ') + '">' +
      '<image width="1254" height="1254" xlink:href="' + logo + '" filter="url(#miqat-qr-logo-tint)"/></svg></g>';
    return '<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="400" height="400" viewBox="0 0 ' + size + ' ' + size + '" aria-hidden="true"><defs>' + defs + '</defs><rect width="' + size + '" height="' + size + '" fill="' + p.paper + '"/><g fill="url(#miqat-qr-body)">' + body + '</g>' + eyes + mark + '</svg>';
  }
  svg.palette = palette;
  if (typeof module === 'object' && module.exports) module.exports = svg; else root.mihrabQr = svg;
}(this));
