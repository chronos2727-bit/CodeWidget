// Scriptable 互換ランタイム
// Android 側の橋渡しオブジェクト window.__native を使って、
// Scriptable の主要 API (ListWidget / Request / Color / Font など) を再現する。
(function () {
  'use strict';
  const N = window.__native;
  const CTX = JSON.parse(N.getContext());
  const G = window;

  // ---------------------------------------------------------------- console
  function fmt(args) {
    return args.map(a => {
      if (typeof a === 'string') return a;
      if (a instanceof Error) return a.name + ': ' + a.message;
      try { return JSON.stringify(a, null, 2); } catch (e) { return String(a); }
    }).join(' ');
  }
  G.console = {
    log: (...a) => N.log('log', fmt(a)),
    info: (...a) => N.log('log', fmt(a)),
    debug: (...a) => N.log('log', fmt(a)),
    warn: (...a) => N.log('warn', fmt(a)),
    error: (...a) => N.log('error', fmt(a)),
  };
  G.log = G.console.log;
  G.logWarning = G.console.warn;
  G.logError = G.console.error;

  function unsupported(name) {
    return function () { throw new Error(name + ' はこのアプリでは未対応です'); };
  }

  // ---------------------------------------------------------------- 基本型
  class Point { constructor(x, y) { this.x = x; this.y = y; } }
  class Size { constructor(w, h) { this.width = w; this.height = h; } }
  class Rect {
    constructor(x, y, w, h) { this.x = x; this.y = y; this.width = w; this.height = h; }
    get minX() { return this.x; } get minY() { return this.y; }
    get maxX() { return this.x + this.width; } get maxY() { return this.y + this.height; }
    get midX() { return this.x + this.width / 2; } get midY() { return this.y + this.height / 2; }
    get origin() { return new Point(this.x, this.y); } get size() { return new Size(this.width, this.height); }
  }

  class Color {
    constructor(hex, alpha) {
      let h = String(hex == null ? '000000' : hex).trim().replace(/^#/, '');
      if (h.length === 3) h = h.split('').map(c => c + c).join('');
      let a = 1;
      if (h.length === 8) { a = parseInt(h.slice(6, 8), 16) / 255; h = h.slice(0, 6); }
      if (!/^[0-9a-fA-F]{6}$/.test(h)) h = '000000';
      this.hex = h.toUpperCase();
      this.alpha = alpha == null ? a : Number(alpha);
    }
    get red() { return parseInt(this.hex.slice(0, 2), 16) / 255; }
    get green() { return parseInt(this.hex.slice(2, 4), 16) / 255; }
    get blue() { return parseInt(this.hex.slice(4, 6), 16) / 255; }
    static black() { return new Color('000000'); }
    static darkGray() { return new Color('555555'); }
    static lightGray() { return new Color('AAAAAA'); }
    static white() { return new Color('FFFFFF'); }
    static gray() { return new Color('8E8E93'); }
    static red() { return new Color('FF3B30'); }
    static green() { return new Color('34C759'); }
    static blue() { return new Color('007AFF'); }
    static cyan() { return new Color('00FFFF'); }
    static yellow() { return new Color('FFCC00'); }
    static magenta() { return new Color('FF00FF'); }
    static orange() { return new Color('FF9500'); }
    static purple() { return new Color('AF52DE'); }
    static brown() { return new Color('A2845E'); }
    static clear() { return new Color('000000', 0); }
    static dynamic(light, dark) { return CTX.dark ? dark : light; }
  }
  function colorStr(c) {
    if (!c) return null;
    if (typeof c === 'string') c = new Color(c);
    const a = Math.max(0, Math.min(255, Math.round((c.alpha == null ? 1 : c.alpha) * 255)));
    return '#' + a.toString(16).padStart(2, '0') + c.hex;
  }

  class Font {
    constructor(name, size) {
      this.name = String(name || '');
      this.size = Number(size) || 17;
      const n = this.name.toLowerCase();
      this.weight = /black|heavy/.test(n) ? 800 : /bold/.test(n) ? 700 : /semibold/.test(n) ? 600 : /medium/.test(n) ? 500 : /light/.test(n) ? 300 : 400;
      this.italic = /italic|oblique/.test(n);
      this.mono = /mono|menlo|courier|code/.test(n);
    }
    static _f(size, weight, opt) {
      const f = new Font('', size);
      f.weight = weight; f.italic = !!(opt && opt.italic); f.mono = !!(opt && opt.mono);
      return f;
    }
    static systemFont(s) { return Font._f(s, 400); }
    static ultraLightSystemFont(s) { return Font._f(s, 100); }
    static thinSystemFont(s) { return Font._f(s, 200); }
    static lightSystemFont(s) { return Font._f(s, 300); }
    static regularSystemFont(s) { return Font._f(s, 400); }
    static mediumSystemFont(s) { return Font._f(s, 500); }
    static semiboldSystemFont(s) { return Font._f(s, 600); }
    static boldSystemFont(s) { return Font._f(s, 700); }
    static heavySystemFont(s) { return Font._f(s, 800); }
    static blackSystemFont(s) { return Font._f(s, 900); }
    static italicSystemFont(s) { return Font._f(s, 400, { italic: true }); }
    static monospacedSystemFont(s) { return Font._f(s, 400, { mono: true }); }
    static ultraLightMonospacedSystemFont(s) { return Font._f(s, 100, { mono: true }); }
    static thinMonospacedSystemFont(s) { return Font._f(s, 200, { mono: true }); }
    static lightMonospacedSystemFont(s) { return Font._f(s, 300, { mono: true }); }
    static regularMonospacedSystemFont(s) { return Font._f(s, 400, { mono: true }); }
    static mediumMonospacedSystemFont(s) { return Font._f(s, 500, { mono: true }); }
    static semiboldMonospacedSystemFont(s) { return Font._f(s, 600, { mono: true }); }
    static boldMonospacedSystemFont(s) { return Font._f(s, 700, { mono: true }); }
    static heavyMonospacedSystemFont(s) { return Font._f(s, 800, { mono: true }); }
    static blackMonospacedSystemFont(s) { return Font._f(s, 900, { mono: true }); }
    static ultraLightRoundedSystemFont(s) { return Font._f(s, 100); }
    static thinRoundedSystemFont(s) { return Font._f(s, 200); }
    static lightRoundedSystemFont(s) { return Font._f(s, 300); }
    static regularRoundedSystemFont(s) { return Font._f(s, 400); }
    static mediumRoundedSystemFont(s) { return Font._f(s, 500); }
    static semiboldRoundedSystemFont(s) { return Font._f(s, 600); }
    static boldRoundedSystemFont(s) { return Font._f(s, 700); }
    static heavyRoundedSystemFont(s) { return Font._f(s, 800); }
    static blackRoundedSystemFont(s) { return Font._f(s, 900); }
    static largeTitle() { return Font._f(34, 400); }
    static title1() { return Font._f(28, 400); }
    static title2() { return Font._f(22, 400); }
    static title3() { return Font._f(20, 400); }
    static headline() { return Font._f(17, 600); }
    static subheadline() { return Font._f(15, 400); }
    static body() { return Font._f(17, 400); }
    static callout() { return Font._f(16, 400); }
    static footnote() { return Font._f(13, 400); }
    static caption1() { return Font._f(12, 400); }
    static caption2() { return Font._f(11, 400); }
  }
  function fontObj(f) {
    if (!f) return null;
    return { s: f.size, w: f.weight || 400, i: !!f.italic, m: !!f.mono };
  }

  class LinearGradient {
    constructor() { this.colors = []; this.locations = []; this.startPoint = new Point(0, 0); this.endPoint = new Point(0, 1); }
  }
  function gradObj(g) {
    if (!g || !g.colors || !g.colors.length) return null;
    return {
      colors: g.colors.map(colorStr),
      locations: (g.locations && g.locations.length === g.colors.length) ? g.locations.map(Number) : null,
      sx: g.startPoint ? g.startPoint.x : 0, sy: g.startPoint ? g.startPoint.y : 0,
      ex: g.endPoint ? g.endPoint.x : 0, ey: g.endPoint ? g.endPoint.y : 1,
    };
  }

  // ---------------------------------------------------------------- Data / Image
  function b64ToStr(b64) {
    const bin = atob(b64); const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return new TextDecoder('utf-8').decode(bytes);
  }
  function strToB64(s) {
    const bytes = new TextEncoder().encode(String(s)); let bin = '';
    for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
    return btoa(bin);
  }
  class Data {
    constructor(b64) { this._b64 = b64 || ''; }
    static fromString(s) { return new Data(strToB64(s)); }
    static fromBase64String(s) { return new Data(String(s).replace(/\s/g, '')); }
    static fromFile(p) { const r = N.fileReadB64(String(p)); return r == null ? null : new Data(r); }
    static fromPNG(img) { return img && img._b64 ? new Data(img._b64) : null; }
    static fromJPEG(img) { return Data.fromPNG(img); }
    toRawString() { return b64ToStr(this._b64); }
    toBase64String() { return this._b64; }
    getBytes() { const bin = atob(this._b64); const out = []; for (let i = 0; i < bin.length; i++) out.push(bin.charCodeAt(i)); return out; }
  }

  class Image {
    constructor(src, w, h) {
      // src: {b64} | {draw} | {sym}
      Object.assign(this, src || {});
      this._w = w || 0; this._h = h || 0;
    }
    get size() { return new Size(this._w, this._h); }
    static fromData(d) {
      if (!d) return null;
      const info = JSON.parse(N.imageInfo(d._b64));
      return info.ok ? new Image({ _b64: d._b64 }, info.w, info.h) : null;
    }
    static fromFile(p) { const d = Data.fromFile(p); return d ? Image.fromData(d) : null; }
  }
  function imageObj(img) {
    if (!img) return null;
    if (img._b64) return { b64: img._b64 };
    if (img._draw) return { draw: img._draw };
    if (img._sym) return { sym: img._sym, w: img._w, h: img._h };
    return null;
  }

  class SFSymbol {
    constructor(name) { this._name = name; this._weight = 400; }
    static named(name) { return new SFSymbol(String(name)); }
    get image() { return new Image({ _sym: { name: this._name, weight: this._weight } }, 24, 24); }
    applyFont(f) { if (f && f.weight) this._weight = f.weight; }
    applyUltraLightWeight() { this._weight = 100; } applyThinWeight() { this._weight = 200; }
    applyLightWeight() { this._weight = 300; } applyRegularWeight() { this._weight = 400; }
    applyMediumWeight() { this._weight = 500; } applySemiboldWeight() { this._weight = 600; }
    applyBoldWeight() { this._weight = 700; } applyHeavyWeight() { this._weight = 800; }
    applyBlackWeight() { this._weight = 900; }
  }

  // ---------------------------------------------------------------- DrawContext
  class Path {
    constructor() { this._ops = []; }
    move(p) { this._ops.push(['M', p.x, p.y]); }
    addLine(p) { this._ops.push(['L', p.x, p.y]); }
    addLines(ps) { ps.forEach((p, i) => this._ops.push([i === 0 ? 'M' : 'L', p.x, p.y])); }
    addRect(r) { this._ops.push(['R', r.x, r.y, r.width, r.height]); }
    addRects(rs) { rs.forEach(r => this.addRect(r)); }
    addEllipse(r) { this._ops.push(['E', r.x, r.y, r.width, r.height]); }
    addRoundedRect(r, cw, ch) { this._ops.push(['RR', r.x, r.y, r.width, r.height, cw, ch == null ? cw : ch]); }
    addCurve(p, c1, c2) { this._ops.push(['C', c1.x, c1.y, c2.x, c2.y, p.x, p.y]); }
    addQuadCurve(p, c) { this._ops.push(['Q', c.x, c.y, p.x, p.y]); }
    closeSubpath() { this._ops.push(['Z']); }
  }
  class DrawContext {
    constructor() {
      this.size = new Size(200, 200); this.opaque = true; this.respectScreenScale = false;
      this._ops = []; this._align = 'left';
    }
    _p(...op) { this._ops.push(op); }
    setFillColor(c) { this._p('fill', colorStr(c)); }
    setStrokeColor(c) { this._p('stroke', colorStr(c)); }
    setLineWidth(w) { this._p('lw', w); }
    setFont(f) { this._p('font', fontObj(f)); }
    setTextColor(c) { this._p('tc', colorStr(c)); }
    setTextAlignedLeft() { this._p('ta', 'left'); }
    setTextAlignedCenter() { this._p('ta', 'center'); }
    setTextAlignedRight() { this._p('ta', 'right'); }
    fill(r) { this._p('fillRect', r.x, r.y, r.width, r.height); }
    fillRect(r) { this.fill(r); }
    fillEllipse(r) { this._p('fillEllipse', r.x, r.y, r.width, r.height); }
    stroke(r) { this._p('strokeRect', r.x, r.y, r.width, r.height); }
    strokeRect(r) { this.stroke(r); }
    strokeEllipse(r) { this._p('strokeEllipse', r.x, r.y, r.width, r.height); }
    addPath(p) { this._p('path', p._ops.slice()); }
    fillPath() { this._p('fillPath'); }
    strokePath() { this._p('strokePath'); }
    drawText(t, p) { this._p('text', String(t), p.x, p.y); }
    drawTextInRect(t, r) { this._p('textRect', String(t), r.x, r.y, r.width, r.height); }
    drawImageAtPoint(img, p) { this._p('img', imageObj(img), p.x, p.y, img._w, img._h); }
    drawImageInRect(img, r) { this._p('img', imageObj(img), r.x, r.y, r.width, r.height); }
    getImage() {
      const d = { w: this.size.width, h: this.size.height, opaque: this.opaque !== false, ops: this._ops.slice() };
      return new Image({ _draw: d }, this.size.width, this.size.height);
    }
  }

  // ---------------------------------------------------------------- 日付フォーマット
  function pad(n, l) { return String(n).padStart(l || 2, '0'); }
  function formatPattern(d, pattern, locale) {
    const isJa = String(locale || '').startsWith('ja');
    const wdLong = (o) => d.toLocaleDateString(locale, { weekday: o });
    const mon = (o) => d.toLocaleDateString(locale, { month: o });
    const tokens = {
      yyyy: () => d.getFullYear(), yy: () => pad(d.getFullYear() % 100), y: () => d.getFullYear(),
      MMMM: () => mon('long'), MMM: () => mon('short'), MM: () => pad(d.getMonth() + 1), M: () => d.getMonth() + 1,
      dd: () => pad(d.getDate()), d: () => d.getDate(),
      EEEE: () => wdLong('long'), EEE: () => wdLong('short'), EE: () => wdLong('short'), E: () => wdLong('short'),
      HH: () => pad(d.getHours()), H: () => d.getHours(),
      hh: () => pad(d.getHours() % 12 || 12), h: () => d.getHours() % 12 || 12,
      mm: () => pad(d.getMinutes()), m: () => d.getMinutes(),
      ss: () => pad(d.getSeconds()), s: () => d.getSeconds(),
      a: () => (d.getHours() < 12 ? (isJa ? '午前' : 'AM') : (isJa ? '午後' : 'PM')),
    };
    return pattern.replace(/'([^']*)'|yyyy|yy|y|MMMM|MMM|MM|M|dd|d|EEEE|EEE|EE|E|HH|H|hh|h|mm|m|ss|s|a/g,
      (m, q) => (q !== undefined ? q : String(tokens[m]())));
  }
  const STYLE_MAP = { none: undefined, short: 'short', medium: 'medium', long: 'long', full: 'full' };
  class DateFormatter {
    constructor() { this.dateFormat = null; this.locale = CTX.locale; this._ds = 'none'; this._ts = 'none'; }
    useNoDateStyle() { this._ds = 'none'; } useShortDateStyle() { this._ds = 'short'; }
    useMediumDateStyle() { this._ds = 'medium'; } useLongDateStyle() { this._ds = 'long'; }
    useFullDateStyle() { this._ds = 'full'; }
    useNoTimeStyle() { this._ts = 'none'; } useShortTimeStyle() { this._ts = 'short'; }
    useMediumTimeStyle() { this._ts = 'medium'; } useLongTimeStyle() { this._ts = 'long'; }
    useFullTimeStyle() { this._ts = 'full'; }
    string(date) {
      const d = new Date(date);
      if (this.dateFormat) return formatPattern(d, this.dateFormat, this.locale);
      const o = {};
      if (STYLE_MAP[this._ds]) o.dateStyle = STYLE_MAP[this._ds];
      if (STYLE_MAP[this._ts]) o.timeStyle = STYLE_MAP[this._ts];
      if (!o.dateStyle && !o.timeStyle) return '';
      return new Intl.DateTimeFormat(this.locale, o).format(d);
    }
    date(str) { const d = new Date(str); return isNaN(d) ? null : d; }
  }
  class RelativeDateTimeFormatter {
    constructor() { this.locale = CTX.locale; this._style = 'numeric'; }
    useNamedDateTimeStyle() { this._style = 'auto'; }
    useNumericDateTimeStyle() { this._style = 'always'; }
    string(date, ref) {
      const diff = (new Date(date) - (ref ? new Date(ref) : new Date())) / 1000;
      const rtf = new Intl.RelativeTimeFormat(this.locale, { numeric: this._style === 'auto' ? 'auto' : 'always' });
      const a = Math.abs(diff);
      if (a < 60) return rtf.format(Math.round(diff), 'second');
      if (a < 3600) return rtf.format(Math.round(diff / 60), 'minute');
      if (a < 86400) return rtf.format(Math.round(diff / 3600), 'hour');
      if (a < 86400 * 30) return rtf.format(Math.round(diff / 86400), 'day');
      if (a < 86400 * 365) return rtf.format(Math.round(diff / 86400 / 30), 'month');
      return rtf.format(Math.round(diff / 86400 / 365), 'year');
    }
  }

  // ---------------------------------------------------------------- ウィジェット要素
  class WidgetSpacer { constructor(len) { this.length = (len == null) ? null : Number(len); } }

  class WidgetText {
    constructor(text) {
      this.text = String(text == null ? '' : text);
      this.textColor = null; this.font = null; this.textOpacity = 1; this.lineLimit = 0;
      this.minimumScaleFactor = 1; this.shadowColor = null; this.shadowRadius = 0; this.shadowOffset = null;
      this.url = null; this._align = null;
    }
    leftAlignText() { this._align = 'left'; } centerAlignText() { this._align = 'center'; } rightAlignText() { this._align = 'right'; }
  }

  class WidgetDate extends WidgetText {
    constructor(date) { super(''); this.date = date; this._style = 'date'; }
    applyTimeStyle() { this._style = 'time'; } applyDateStyle() { this._style = 'date'; }
    applyRelativeStyle() { this._style = 'relative'; } applyOffsetStyle() { this._style = 'offset'; }
    applyTimerStyle() { this._style = 'timer'; }
    _render() {
      const d = new Date(this.date); const now = new Date(); const loc = CTX.locale;
      switch (this._style) {
        case 'time': return d.toLocaleTimeString(loc, { hour: 'numeric', minute: '2-digit' });
        case 'relative': case 'offset': {
          const s = new RelativeDateTimeFormatter().string(d, now);
          return this._style === 'offset' ? ((d - now >= 0 ? '+' : '') + s) : s;
        }
        case 'timer': {
          let t = Math.abs(Math.round((d - now) / 1000));
          const h = Math.floor(t / 3600); t %= 3600;
          const m = Math.floor(t / 60); const s = t % 60;
          return (h ? h + ':' + pad(m) : m) + ':' + pad(s);
        }
        default: return d.toLocaleDateString(loc, { year: 'numeric', month: 'long', day: 'numeric' });
      }
    }
  }

  class WidgetImage {
    constructor(image) {
      this.image = image; this.resizable = true; this.imageSize = null; this.imageOpacity = 1;
      this.cornerRadius = 0; this.borderWidth = 0; this.borderColor = null; this.containerRelativeShape = false;
      this.tintColor = null; this.url = null; this._align = null; this._mode = 'fit';
    }
    leftAlignImage() { this._align = 'left'; } centerAlignImage() { this._align = 'center'; } rightAlignImage() { this._align = 'right'; }
    applyFittingContentMode() { this._mode = 'fit'; } applyFillingContentMode() { this._mode = 'fill'; }
  }

  const ContainerMixin = {
    addText(t) { const x = new WidgetText(t); this._children.push(x); return x; },
    addDate(d) { const x = new WidgetDate(d); this._children.push(x); return x; },
    addImage(img) { const x = new WidgetImage(img); this._children.push(x); return x; },
    addSpacer(len) { const x = new WidgetSpacer(len); this._children.push(x); return x; },
    addStack() { const x = new WidgetStack(); this._children.push(x); return x; },
    setPadding(t, l, b, r) { this._pad = [t, l, b, r].map(Number); },
  };

  class WidgetStack {
    constructor() {
      this._children = []; this.backgroundColor = null; this.backgroundImage = null; this.backgroundGradient = null;
      this.spacing = 0; this.size = null; this.cornerRadius = 0; this.borderWidth = 0; this.borderColor = null;
      this.url = null; this._layout = 'h'; this._align = 'center'; this._pad = null;
    }
    layoutHorizontally() { this._layout = 'h'; } layoutVertically() { this._layout = 'v'; }
    topAlignContent() { this._align = 'start'; } centerAlignContent() { this._align = 'center'; }
    bottomAlignContent() { this._align = 'end'; }
    useDefaultPadding() { this._pad = null; }
  }
  Object.assign(WidgetStack.prototype, ContainerMixin);

  let presented = null;
  class ListWidget {
    constructor() {
      this._children = []; this.backgroundColor = null; this.backgroundImage = null; this.backgroundGradient = null;
      this.spacing = 0; this.url = null; this.refreshAfterDate = null; this._pad = null;
    }
    useDefaultPadding() { this._pad = null; }
    async presentSmall() { presented = { w: this, family: 'small' }; }
    async presentMedium() { presented = { w: this, family: 'medium' }; }
    async presentLarge() { presented = { w: this, family: 'large' }; }
    async presentExtraLarge() { presented = { w: this, family: 'extraLarge' }; }
    async presentAccessoryCircular() { presented = { w: this, family: 'small' }; }
    async presentAccessoryRectangular() { presented = { w: this, family: 'small' }; }
    async presentAccessoryInline() { presented = { w: this, family: 'small' }; }
  }
  Object.assign(ListWidget.prototype, ContainerMixin);

  function serText(t) {
    return {
      t: 'text', text: (t instanceof WidgetDate) ? t._render() : t.text,
      color: colorStr(t.textColor), font: fontObj(t.font), opacity: t.textOpacity,
      lines: t.lineLimit || 0, minScale: t.minimumScaleFactor, align: t._align,
      shadow: t.shadowColor ? { c: colorStr(t.shadowColor), r: t.shadowRadius || 0, x: t.shadowOffset ? t.shadowOffset.x : 0, y: t.shadowOffset ? t.shadowOffset.y : 0 } : null,
      url: t.url,
    };
  }
  function serNode(n) {
    if (n instanceof WidgetSpacer) return { t: 'spacer', len: n.length };
    if (n instanceof WidgetText) return serText(n);
    if (n instanceof WidgetImage) {
      return {
        t: 'image', img: imageObj(n.image), w: n.image ? n.image._w : 0, h: n.image ? n.image._h : 0,
        size: n.imageSize ? [n.imageSize.width, n.imageSize.height] : null,
        opacity: n.imageOpacity, radius: n.containerRelativeShape ? 12 : n.cornerRadius,
        bw: n.borderWidth, bc: colorStr(n.borderColor), tint: colorStr(n.tintColor),
        align: n._align, mode: n._mode, resizable: n.resizable !== false, url: n.url,
      };
    }
    if (n instanceof WidgetStack) {
      return {
        t: 'stack', layout: n._layout, align: n._align, spacing: n.spacing || 0,
        size: n.size ? [n.size.width, n.size.height] : null, pad: n._pad,
        bg: colorStr(n.backgroundColor), bgImg: imageObj(n.backgroundImage), grad: gradObj(n.backgroundGradient),
        radius: n.cornerRadius || 0, bw: n.borderWidth || 0, bc: colorStr(n.borderColor), url: n.url,
        children: n._children.map(serNode),
      };
    }
    return { t: 'spacer', len: 0 };
  }
  function serWidget(w) {
    return {
      t: 'list', spacing: w.spacing || 0, pad: w._pad,
      bg: colorStr(w.backgroundColor), bgImg: imageObj(w.backgroundImage), grad: gradObj(w.backgroundGradient),
      url: w.url, refreshAfter: w.refreshAfterDate ? new Date(w.refreshAfterDate).getTime() : null,
      children: w._children.map(serNode),
    };
  }

  // ---------------------------------------------------------------- ネットワーク
  class Request {
    constructor(url) {
      this.url = url; this.method = 'GET'; this.headers = {}; this.body = null;
      this.timeoutInterval = 60; this.allowInsecureRequest = false; this.response = null;
    }
    _do(mode) {
      const body = this.body == null ? null : (this.body instanceof Data ? { b64: this.body._b64 } : { text: String(this.body) });
      const r = JSON.parse(N.request(JSON.stringify({
        url: String(this.url), method: this.method || 'GET', headers: this.headers || {},
        body: body, mode: mode, timeout: this.timeoutInterval || 60,
      })));
      if (r.error) throw new Error(r.error);
      this.response = { statusCode: r.status, headers: r.headers || {}, url: r.url, mimeType: r.mime, cookies: [] };
      return r;
    }
    async loadString() { return this._do('text').body; }
    async loadJSON() { return JSON.parse(this._do('text').body); }
    async load() { return new Data(this._do('b64').body); }
    async loadImage() { const r = this._do('image'); return new Image({ _b64: r.body }, r.w, r.h); }
    addParameterToMultipart() { throw new Error('multipart は未対応です'); }
  }

  G.fetch = async function (url, opts) {
    opts = opts || {};
    const req = new Request(typeof url === 'string' ? url : url.url);
    req.method = opts.method || 'GET';
    req.headers = opts.headers || {};
    if (opts.body != null) req.body = opts.body;
    const r = req._do('text');
    return {
      ok: r.status >= 200 && r.status < 300, status: r.status, headers: r.headers, url: r.url,
      text: async () => r.body, json: async () => JSON.parse(r.body),
    };
  };

  // ---------------------------------------------------------------- 保存領域
  const Keychain = {
    contains: (k) => N.keychainContains(String(k)),
    get: (k) => { const v = N.keychainGet(String(k)); if (v == null) throw new Error('Keychain に "' + k + '" がありません'); return v; },
    set: (k, v) => N.keychainSet(String(k), String(v)),
    remove: (k) => N.keychainRemove(String(k)),
  };

  class FileManager {
    static local() { return new FileManager(); }
    static iCloud() { return new FileManager(); }
    documentsDirectory() { return CTX.dirs.documents; }
    libraryDirectory() { return CTX.dirs.library; }
    cacheDirectory() { return CTX.dirs.cache; }
    temporaryDirectory() { return CTX.dirs.cache; }
    joinPath(a, b) { return String(a).replace(/\/+$/, '') + '/' + String(b).replace(/^\/+/, ''); }
    readString(p) { const r = N.fileReadB64(String(p)); return r == null ? null : b64ToStr(r); }
    writeString(p, s) { this._chk(N.fileWriteB64(String(p), strToB64(s))); }
    read(p) { const r = N.fileReadB64(String(p)); if (r == null) throw new Error('ファイルが読めません: ' + p); return new Data(r); }
    write(p, d) { this._chk(N.fileWriteB64(String(p), d._b64)); }
    readImage(p) { return Image.fromFile(p); }
    writeImage(p, img) { if (!img || !img._b64) throw new Error('この画像は保存できません'); this._chk(N.fileWriteB64(String(p), img._b64)); }
    fileExists(p) { return N.fileExists(String(p)); }
    isDirectory(p) { return N.fileIsDir(String(p)); }
    createDirectory(p) { this._chk(N.fileMkdir(String(p))); }
    remove(p) { N.fileRemove(String(p)); }
    move(a, b) { this._chk(N.fileMove(String(a), String(b))); }
    copy(a, b) { this.write(b, this.read(a)); }
    listContents(p) { return JSON.parse(N.fileList(String(p))); }
    modificationDate(p) { const t = N.fileModified(String(p)); return t > 0 ? new Date(t) : null; }
    creationDate(p) { return this.modificationDate(p); }
    fileName(p, ext) { const n = String(p).split('/').pop(); return ext ? n : n.replace(/\.[^.]*$/, ''); }
    fileExtension(p) { const m = String(p).match(/\.([^./]+)$/); return m ? m[1] : ''; }
    fileSize(p) { return N.fileSize(String(p)) / 1000; }
    isFileStoredIniCloud() { return false; }
    isFileDownloaded() { return true; }
    async downloadFileFromiCloud() { }
    bookmarkExists() { return false; }
    bookmarkedPath(n) { throw new Error('ブックマーク "' + n + '" は未対応です'); }
    allTags() { return []; } addTag() { } removeTag() { }
    _chk(ok) { if (!ok) throw new Error('ファイル操作に失敗しました'); }
  }

  // ---------------------------------------------------------------- 端末情報など
  const Location = {
    async current() {
      const r = JSON.parse(N.location());
      if (r.error) throw new Error(r.error);
      return r;
    },
    setAccuracyToBest() { }, setAccuracyToTenMeters() { }, setAccuracyToHundredMeters() { },
    setAccuracyToKilometer() { }, setAccuracyToThreeKilometers() { },
    async reverseGeocode(lat, lon, locale) {
      const r = JSON.parse(N.reverseGeocode(Number(lat), Number(lon), locale || CTX.locale));
      if (r.error) throw new Error(r.error);
      return r.results;
    },
  };

  const Device = {
    name: () => CTX.device.model, model: () => CTX.device.model, systemName: () => 'Android',
    systemVersion: () => CTX.device.version, locale: () => CTX.locale.replace('-', '_'),
    language: () => CTX.locale.split('-')[0], preferredLanguages: () => [CTX.locale],
    isUsingDarkAppearance: () => !!CTX.dark, isPhone: () => true, isPad: () => false,
    screenSize: () => new Size(CTX.device.screenW, CTX.device.screenH),
    screenResolution: () => new Size(CTX.device.screenW * CTX.device.scale, CTX.device.screenH * CTX.device.scale),
    screenScale: () => CTX.device.scale, screenBrightness: () => 0.5,
    batteryLevel: () => CTX.device.battery, isCharging: () => !!CTX.device.charging,
    isFullyCharged: () => CTX.device.battery >= 1,
    isInPortrait: () => true, isInLandscapeLeft: () => false, isInLandscapeRight: () => false,
    isFaceUp: () => false, isFaceDown: () => false, isInPortraitUpsideDown: () => false,
    volume: () => 0.5, setScreenBrightness: () => { },
  };

  let completed = false;
  let explicitWidget = null;
  const Script = {
    name: () => CTX.scriptName,
    complete: () => { completed = true; finish(); },
    setWidget: (w) => { explicitWidget = w; },
    setShortcutOutput: () => { },
  };

  G.args = {
    widgetParameter: CTX.widgetParameter || null, queryParameters: {}, plainTexts: [], urls: [],
    fileURLs: [], images: [], notification: null, shortcutParameter: null,
    get length() { return 0; }, get all() { return []; },
  };
  G.config = {
    runsInWidget: !!CTX.runsInWidget, runsInApp: !CTX.runsInWidget, runsInActionExtension: false,
    runsWithSiri: false, runsInNotification: false, runsFromHomeScreen: false,
    widgetFamily: CTX.runsInWidget ? CTX.widgetFamily : null,
  };

  class Notification {
    constructor() { this.title = ''; this.body = ''; this.subtitle = ''; this.identifier = ''; }
    async schedule() { console.warn('Notification は未対応のためスキップしました'); }
    setTriggerDate() { } setDailyTrigger() { } setWeeklyTrigger() { } addAction() { }
    static async removePending() { } static async removeDelivered() { }
    static async allPending() { return []; } static async allDelivered() { return []; }
    static async current() { return null; }
    static async removeAllPending() { } static async removeAllDelivered() { }
  }
  class Alert {
    constructor() { this.title = ''; this.message = ''; }
    addAction() { } addDestructiveAction() { } addCancelAction() { } addTextField() { } addSecureTextField() { }
    textFieldValue() { return ''; }
    async present() { console.warn('Alert は表示できないためスキップしました'); return -1; }
    async presentAlert() { return this.present(); } async presentSheet() { return this.present(); }
  }

  // 別スクリプトをモジュールとして読み込む（Scriptable の importModule 相当）
  function importModule(name) {
    const code = N.readScript(String(name).replace(/\.js$/, ''));
    if (code == null) throw new Error('モジュール "' + name + '" が見つかりません');
    const module = { exports: {} };
    new Function('module', 'exports', 'importModule', code)(module, module.exports, importModule);
    return module.exports;
  }

  Object.assign(G, {
    Point, Size, Rect, Color, Font, LinearGradient, Data, Image, SFSymbol, Path, DrawContext,
    DateFormatter, RelativeDateTimeFormatter, ListWidget, WidgetStack, WidgetText, WidgetDate,
    WidgetImage, WidgetSpacer, Request, Keychain, FileManager, Location, Device, Script,
    Notification, Alert, importModule,
    UUID: { string: () => (crypto.randomUUID ? crypto.randomUUID() : String(Math.random()).slice(2)).toUpperCase() },
    Safari: { open: (u) => console.warn('Safari.open はスキップしました: ' + u), openInApp: async () => { } },
    QuickLook: { present: async () => { } },
    Pasteboard: { copy: () => { }, paste: () => null, copyString: () => { }, pasteString: () => null },
    Speech: { speak: () => { } },
    Calendar: { forEvents: unsupported('Calendar'), defaultForEvents: unsupported('Calendar') },
    CalendarEvent: { today: unsupported('CalendarEvent'), thisWeek: unsupported('CalendarEvent'), between: unsupported('CalendarEvent') },
    Reminder: { allIncomplete: unsupported('Reminder'), all: unsupported('Reminder') },
    Photos: { latestPhoto: unsupported('Photos'), fromLibrary: unsupported('Photos') },
    Contact: { all: unsupported('Contact') },
    Timer: {
      schedule: (ms, repeats, fn) => { const id = repeats ? setInterval(fn, ms) : setTimeout(fn, ms); return { invalidate: () => clearTimeout(id) }; },
    },
  });

  // ---------------------------------------------------------------- 実行
  let finished = false;
  function finish() {
    if (finished) return;
    const target = explicitWidget || (presented && presented.w);
    if (!target) return; // まだウィジェットが作られていない
    finished = true;
    try {
      N.setWidget(JSON.stringify(serWidget(target)));
    } catch (e) {
      N.fail('ウィジェットの変換に失敗: ' + e.message);
    }
  }

  G.__runUserScript = function () {
    const code = N.getCode();
    const AsyncFunction = Object.getPrototypeOf(async function () { }).constructor;
    let fn;
    try {
      fn = new AsyncFunction(code);
    } catch (e) {
      N.fail('構文エラー: ' + e.message);
      return;
    }
    fn().then(() => {
      finish();
      if (!finished) N.scriptEnded(); // ウィジェット未設定のまま終了 → 少し待ってから判定
    }, (e) => {
      const msg = (e && e.message) ? e.message : String(e);
      // スタックから「何行目か」だけ取り出す（ユーザーコードは anonymous 関数として実行される）
      const m = (e && e.stack) ? String(e.stack).match(/<anonymous>:(\d+):(\d+)/) : null;
      N.fail(msg + (m ? '\n（' + (Number(m[1]) - 2) + '行目付近）' : ''));
    });
  };

  // スクリプトが Promise を待たずに後で setWidget するケースへの対応
  G.__pollFinish = function () { finish(); return finished; };
})();
