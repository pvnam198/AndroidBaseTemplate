/**
 * Xuất tab "ad_config" (+ tab "templates") ra 4 parameter Remote Config: ad_settings, ad_placements, ad_ids,
 * ad_templates.
 *
 * Cài: trong Google Sheet → Extensions → Apps Script → dán toàn bộ file này → Save → tải lại sheet.
 * Dùng: menu "Ad config" → "Export JSON". Hộp thoại hiện 4 JSON dễ đọc, mỗi cái ứng với 1 parameter trên Firebase
 * Remote Config (kiểu JSON): nút Copy để dán vào parameter cùng tên, nút Tải để lưu file. Nút "Tải tất cả" lưu
 * ad_config.json gồm cả 4 (để lưu trữ / gửi team xem, không phải để import).
 *
 * Tab được xuất: tab đang mở nếu nó là tab cấu hình (có ô "setting" và "ad_unit" ở cột A) - nên mỗi bản app có thể
 * một tab riêng ("version 109", "version 201"...), mở tab nào thì xuất tab đó. Đang mở tab khác thì dùng tab "ad_config".
 *
 * Bố cục tab cấu hình mà script đọc:
 * - hàng có ô đầu "setting": tên key; hàng ngay dưới ("value"): giá trị. Ô value trống = bỏ qua (app dùng mặc định).
 * - hàng tiêu đề nhóm: ô bắt đầu bằng "FULL" và ô bắt đầu bằng "BASIC" đánh dấu cột đầu của mỗi nhóm.
 * - hàng có ô đầu "ad_unit": tên cột (ad_unit, id_1..id_3, placement, type, rồi cột FULL / BASIC). Mỗi hàng dưới là 1 ad
 *   unit; ô placement trống = thuộc placement của hàng trên (ô merge). Cột type chỉ để kiểm tra format, không gửi đi.
 * - 4 cột ẩn cuối bảng (không tiêu đề) là cột phụ cho dropdown format, script bỏ qua. Tab "types" là bảng type → format.
 *
 * Tab "templates": hàng 1 là tiêu đề (template, base, cta_bg, ...), mỗi hàng dưới 1 mẫu native. Hàng không điền style
 * nào thì bỏ qua (mẫu có sẵn dùng như thiết kế gốc). Mẫu có sẵn được đè style thì không gửi base.
 */

var SHEET_NAME = 'ad_config';
var TYPES_SHEET_NAME = 'types';
var TEMPLATES_SHEET_NAME = 'templates';
// Mẫu native có sẵn trong app (tên không phân biệt hoa thường).
var BUILT_IN_TEMPLATES = ['big1', 'big2', 'big3', 'big4', 'big5', 'big6', 'big7', 'big8', 'big9', 'small1', 'small2',
  'small3', 'small4', 'tiny1', 'tiny2', 'collap1', 'collap2', 'collap3', 'full1', 'full2', 'full3', 'full4', 'full5'];
var STYLE_NUMBER_FIELDS = ['cta_corner_dp', 'card_corner_dp', 'card_stroke_dp', 'cta_height_dp', 'cta_text_sp',
  'title_text_sp', 'body_text_sp', 'media_height_dp', 'icon_size_dp', 'card_padding_dp'];
var LIST_SETTINGS = ['full_inter_gap_ignore'];          // setting dạng danh sách chữ
var NUMBER_LIST_FIELDS = ['pages', 'open_ad_on_clicks'];  // cột dạng danh sách số "2,4"
var ID_COLUMNS = ['id_1', 'id_2', 'id_3'];       // thứ tự app thử: id_1 → id_3
// type của placement (cột "type") → format được chọn - chỉ để kiểm tra, cột type không gửi lên Firebase.
// Dùng khi sheet không có tab "types"; có tab đó thì đọc bảng trong tab (cùng nguồn với dropdown).
var TYPE_FORMATS = {
  splash: ['aoa', 'inter', 'inter_then_native_full'],
  inter: ['inter', 'native_full', 'inter_then_native_full'],
  banner: ['banner', 'banner_collapsible'],
  banner_native: ['banner', 'banner_collapsible', 'native', 'native_collapsible'],
  resume: ['aoa', 'welcome_back'],
  native: [],
  reward: [],
  reward_inter: []
};

function onOpen() {
  SpreadsheetApp.getUi().createMenu('Ad config').addItem('Export JSON', 'showExport').addToUi();
}

function showExport() {
  // Xuất tab đang mở (mỗi bản app có thể một tab: "version 109", "version 201"...); đang mở tab khác (templates,
  // types...) thì dùng tab "ad_config".
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getActiveSheet();
  if (!isConfigSheet(sheet)) sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet || !isConfigSheet(sheet)) {
    throw new Error('Mở tab cấu hình (tab có hàng "setting" và "ad_unit", vd. "version 109") rồi chạy lại Export JSON');
  }
  var range = sheet.getDataRange();
  var typesSheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(TYPES_SHEET_NAME);
  var typeFormats = typesSheet ? readTypeFormats(typesSheet.getDataRange().getDisplayValues()) : TYPE_FORMATS;
  var result = buildAdConfig(range.getValues(), range.getDisplayValues(), typeFormats);
  var templatesSheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(TEMPLATES_SHEET_NAME);
  addTemplates(result, templatesSheet ? templatesSheet.getDataRange().getDisplayValues() : []);
  var html = HtmlService.createHtmlOutput(exportDialogHtml(result))
    .setWidth(900).setHeight(700);
  SpreadsheetApp.getUi().showModalDialog(html, 'Export ad config - tab "' + sheet.getName() + '"');
}

/** Tab cấu hình = cột A có ô "setting" và ô "ad_unit". */
function isConfigSheet(sheet) {
  if (!sheet) return false;
  var col = sheet.getRange(1, 1, Math.min(sheet.getLastRow() || 1, 30), 1).getDisplayValues().map(function (r) { return String(r[0]).trim(); });
  return col.indexOf('setting') >= 0 && col.indexOf('ad_unit') >= 0;
}

/** Phần xử lý thuần (không gọi API Google) để test được ngoài Apps Script. */
/** Bảng tab types: cột A = type, các cột sau = format (dừng ở ô trống / cột "ghi chú"). */
function readTypeFormats(rows) {
  var map = {};
  for (var r = 1; r < rows.length; r++) {
    var type = String(rows[r][0]).trim();
    if (!type || type.indexOf(' ') >= 0) continue; // bỏ dòng ghi chú
    var formats = [];
    for (var c = 1; c < rows[r].length; c++) {
      var f = String(rows[r][c]).trim();
      if (!f) break;
      formats.push(f);
    }
    map[type] = formats;
  }
  return map;
}

/**
 * Tab templates → result.ad_templates; cảnh báo base sai, màu sai, và placement chọn template không có.
 * rows: giá trị hiển thị của tab (hàng 0 = tiêu đề).
 */
function addTemplates(result, rows) {
  var ad_templates = {}, known = BUILT_IN_TEMPLATES.slice(), warnings = result.warnings;
  var head = (rows[0] || []).map(function (v) { return String(v).trim(); });
  for (var r = 1; r < rows.length; r++) {
    var name = String(rows[r][0]).trim();
    if (!name) continue;
    var key = name.toLowerCase(), builtIn = BUILT_IN_TEMPLATES.indexOf(key) >= 0;
    if (!builtIn) known.push(key);
    var style = {};
    for (var c = 2; c < head.length; c++) {
      var field = head[c], raw = String(rows[r][c]).trim();
      if (!field || raw === '') continue;
      if (STYLE_NUMBER_FIELDS.indexOf(field) >= 0) {
        var n = Number(raw.replace(',', '.'));
        if (isNaN(n) || n < 0) { warnings.push('template ' + name + ': ' + field + ' "' + raw + '" không phải số ≥ 0'); continue; }
        style[field] = n;
      } else {
        if (!/^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.test(raw)) { warnings.push('template ' + name + ': ' + field + ' "' + raw + '" không phải màu #RRGGBB / #AARRGGBB'); continue; }
        style[field] = raw.toUpperCase();
      }
    }
    var base = String(rows[r][1] || '').trim();
    if (builtIn) {
      if (Object.keys(style).length) ad_templates[name] = style;
      continue;
    }
    if (BUILT_IN_TEMPLATES.indexOf(base.toLowerCase()) < 0) {
      warnings.push('template ' + name + ': base "' + base + '" không phải mẫu có sẵn (app sẽ dùng mẫu mặc định của placement)');
    }
    var entry = { base: base };
    Object.keys(style).forEach(function (k) { entry[k] = style[k]; });
    ad_templates[name] = entry;
  }
  result.ad_templates = ad_templates;
  ['full'].forEach(function (set) {
    var placements = result.ad_placements[set];
    Object.keys(placements).forEach(function (p) {
      var t = placements[p].template;
      if (t && known.indexOf(String(t).toLowerCase()) < 0) warnings.push(p + ': template "' + t + '" không có (app dùng mẫu mặc định)');
    });
  });
  return result;
}

function buildAdConfig(values, displays, typeFormats) {
  typeFormats = typeFormats || TYPE_FORMATS;
  var warnings = [];
  var text = function (r, c) { return String(displays[r][c] == null ? '' : displays[r][c]).trim(); };
  var firstCol = function (label) {
    for (var r = 0; r < values.length; r++) if (text(r, 0) === label) return r;
    return -1;
  };

  // ---- setting
  var ad_settings = {};
  var sr = firstCol('setting');
  if (sr < 0) throw new Error('Không thấy hàng "setting"');
  for (var c = 1; c < values[sr].length; c++) {
    var key = text(sr, c);
    if (!key || text(sr + 1, c) === '') continue;
    ad_settings[key] = LIST_SETTINGS.indexOf(key) >= 0 ? splitList(text(sr + 1, c)) : typed(values[sr + 1][c], text(sr + 1, c));
  }

  // ---- nhóm cột FULL / BASIC
  var hr = firstCol('ad_unit');
  if (hr < 0) throw new Error('Không thấy hàng tiêu đề "ad_unit"');
  var gr = hr - 1, fullStart = -1, basicStart = -1;
  for (c = 0; c < values[gr].length; c++) {
    var t = text(gr, c).toUpperCase();
    if (t.indexOf('FULL') === 0) fullStart = c;
    if (t.indexOf('BASIC') === 0) basicStart = c;
  }
  if (fullStart < 0 || basicStart < 0) throw new Error('Hàng trên "ad_unit" phải có ô "FULL ..." và "BASIC ..."');
  var head = values[hr].map(function (v, i) { return text(hr, i); });
  var colOf = function (name) { return head.indexOf(name); };
  var placementCol = colOf('placement');
  var typeCol = colOf('type');

  // ---- từng dòng ad unit
  var full = {}, basic = {}, ad_ids = {}, current = '';
  for (var r = hr + 1; r < values.length; r++) {
    var unit = text(r, colOf('ad_unit'));
    if (!unit) break; // hết bảng
    var ids = ID_COLUMNS.map(function (n) { return text(r, colOf(n)); }).filter(function (s) { return s; });
    if (ids.length) ad_ids[unit] = ids;
    var placement = text(r, placementCol);
    if (!placement) continue;          // dòng tiếp theo của placement đã đọc (ô merge)
    current = placement;

    var cfg = {};
    for (c = fullStart; c < basicStart; c++) {
      var field = head[c], raw = text(r, c);
      if (!field || raw === '') continue;
      cfg[field] = NUMBER_LIST_FIELDS.indexOf(field) >= 0 ? splitNumbers(raw) : typed(values[r][c], raw);
    }
    var type = typeCol >= 0 ? text(r, typeCol) : '';
    var allowed = typeFormats[type];
    if (typeCol >= 0 && !allowed) warnings.push(current + ': type "' + type + '" không hợp lệ');
    var checkFormat = function (format, label) {
      if (format && allowed && allowed.indexOf(format) < 0) {
        warnings.push(current + label + ': type ' + type + ' không có format "' + format + '"' +
          (allowed.length ? ' (chỉ ' + allowed.join(' / ') + ')' : ' (placement này không có format)'));
      }
    };
    checkFormat(cfg.format, '');
    if (Object.keys(cfg).length) full[current] = cfg;

    var b = {};
    for (c = basicStart; c < head.length; c++) {
      if (head[c] && text(r, c) !== '') b[head[c]] = typed(values[r][c], text(r, c));
    }
    if (b.enable === true) {
      delete b.enable;
      checkFormat(b.format, ' (basic)');
      basic[current] = b;
    } else if (b.format) {
      warnings.push(current + ' (basic): có format nhưng enable không phải TRUE nên bị bỏ qua');
    }
  }
  return { ad_settings: ad_settings, ad_placements: { full: full, basic: basic }, ad_ids: ad_ids, warnings: warnings };
}

/** TRUE/FALSE → boolean, số → number, còn lại giữ chữ. */
function typed(value, display) {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value;
  var s = String(display).trim();
  if (/^(true|false)$/i.test(s)) return s.toLowerCase() === 'true';
  if (/^-?\d+$/.test(s)) return Number(s);
  return s;
}

function splitList(s) {
  return String(s).split(',').map(function (x) { return x.trim(); }).filter(function (x) { return x; });
}

/** "2,4" → [2, 4]. Đọc từ chữ hiển thị để locale (dấu phẩy thập phân) không biến "2,4" thành 2.4. */
function splitNumbers(s) {
  return splitList(s).map(function (x) { return Number(x); });
}

/** JSON chuẩn, thụt lề 2 dấu cách. */
function pretty(obj) {
  return JSON.stringify(obj, null, 2);
}

function exportDialogHtml(result) {
  var params = [
    ['ad_settings', pretty(result.ad_settings), 'Cấu hình chung'],
    ['ad_placements', pretty(result.ad_placements), 'full: bộ đầy đủ · basic: bộ cơ bản'],
    ['ad_ids', pretty(result.ad_ids), 'ID quảng cáo theo ad unit'],
    ['ad_templates', pretty(result.ad_templates || {}), 'Mẫu native tự đặt / style đè lên mẫu có sẵn (tab templates)']
  ];
  var warnings = result.warnings.slice();
  if (!Object.keys(result.ad_ids).length) warnings.push('Chưa điền ID nào: app sẽ dùng test ID cho mọi ad unit.');
  var all = pretty({ ad_settings: result.ad_settings, ad_placements: result.ad_placements, ad_ids: result.ad_ids,
    ad_templates: result.ad_templates || {} });
  var html = '<style>body{font-family:Arial,sans-serif;font-size:13px}' +
    'textarea{width:100%;font-family:Consolas,monospace;font-size:12px;white-space:pre;overflow:auto}' +
    '.warn{background:#fff3cd;padding:8px;margin-bottom:8px}.p{border:1px solid #ddd;padding:8px;margin-bottom:10px}' +
    'h3{margin:0 0 4px}.hint{color:#666;margin-bottom:6px}button{margin-left:6px}</style>';
  if (warnings.length) html += '<div class="warn"><b>Cảnh báo:</b><br>' + warnings.map(escapeHtml).join('<br>') + '</div>';
  html += '<div class="hint">Mỗi khối dán vào parameter cùng tên trên Firebase Remote Config (kiểu JSON). ' +
    '<button onclick="save(\'all\',\'ad_config.json\')">Tải tất cả (ad_config.json)</button></div>';
  params.forEach(function (p) {
    var lines = p[1].split('\n').length;
    html += '<div class="p"><h3>' + p[0] + '<button onclick="copy(\'' + p[0] + '\')">Copy</button>' +
      '<button onclick="save(\'' + p[0] + '\',\'' + p[0] + '.json\')">Tải ' + p[0] + '.json</button></h3>' +
      '<div class="hint">' + p[2] + '</div><textarea id="' + p[0] + '" rows="' + Math.min(lines + 1, 18) + '" readonly>' +
      escapeHtml(p[1]) + '</textarea></div>';
  });
  html += '<textarea id="all" style="display:none">' + escapeHtml(all) + '</textarea>' +
    '<script>function copy(id){var t=document.getElementById(id);t.select();document.execCommand("copy");}' +
    'function save(id,name){var a=document.createElement("a");a.href=URL.createObjectURL(new Blob([document.getElementById(id).value],' +
    '{type:"application/json"}));a.download=name;a.click();}</script>';
  return html;
}

function escapeHtml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

if (typeof module !== 'undefined') module.exports = { buildAdConfig: buildAdConfig, pretty: pretty, readTypeFormats: readTypeFormats, addTemplates: addTemplates };
