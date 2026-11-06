/**
 * Xuất tab "ad_config" ra 3 parameter Remote Config: ad_settings, ad_placements, ad_ids.
 *
 * Cài: trong Google Sheet → Extensions → Apps Script → dán toàn bộ file này → Save → tải lại sheet.
 * Dùng: menu "Ad config" → "Export JSON". Hộp thoại hiện 3 JSON dễ đọc, mỗi cái ứng với 1 parameter trên Firebase
 * Remote Config (kiểu JSON): nút Copy để dán vào parameter cùng tên, nút Tải để lưu file. Nút "Tải tất cả" lưu
 * ad_config.json gồm cả 3 (để lưu trữ / gửi team xem, không phải để import).
 *
 * Bố cục tab ad_config mà script đọc:
 * - hàng có ô đầu "setting": tên key; hàng ngay dưới ("value"): giá trị. Ô value trống = bỏ qua (app dùng mặc định).
 * - hàng tiêu đề nhóm: ô bắt đầu bằng "FULL" và ô bắt đầu bằng "BASIC" đánh dấu cột đầu của mỗi nhóm.
 * - hàng có ô đầu "ad_unit": tên cột. Mỗi hàng dưới là 1 ad unit; ô placement trống = thuộc placement của hàng trên (ô merge).
 */

var SHEET_NAME = 'ad_config';
var LIST_SETTINGS = ['full_inter_gap_ignore'];          // setting dạng danh sách chữ
var NUMBER_LIST_FIELDS = ['pages', 'open_ad_on_clicks'];  // cột dạng danh sách số "2,4"
var ID_COLUMNS = ['id_high', 'id_medium', 'id_low'];
var FULL_FORMATS = ['aoa', 'inter', 'native_full', 'inter_then_native_full', 'banner', 'banner_collapsible', 'native',
  'native_collapsible', 'welcome_back'];
var BASIC_FORMATS = ['aoa', 'inter', 'banner', 'banner_collapsible'];

function onOpen() {
  SpreadsheetApp.getUi().createMenu('Ad config').addItem('Export JSON', 'showExport').addToUi();
}

function showExport() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEET_NAME);
  if (!sheet) throw new Error('Không thấy tab "' + SHEET_NAME + '"');
  var range = sheet.getDataRange();
  var result = buildAdConfig(range.getValues(), range.getDisplayValues());
  var html = HtmlService.createHtmlOutput(exportDialogHtml(result))
    .setWidth(900).setHeight(700);
  SpreadsheetApp.getUi().showModalDialog(html, 'Export ad config');
}

/** Phần xử lý thuần (không gọi API Google) để test được ngoài Apps Script. */
function buildAdConfig(values, displays) {
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
    if (cfg.format && FULL_FORMATS.indexOf(cfg.format) < 0) warnings.push(current + ': format "' + cfg.format + '" không hợp lệ');
    if (Object.keys(cfg).length) full[current] = cfg;

    var b = {};
    for (c = basicStart; c < head.length; c++) {
      if (head[c] && text(r, c) !== '') b[head[c]] = typed(values[r][c], text(r, c));
    }
    if (b.enable === true) {
      delete b.enable;
      if (b.format && BASIC_FORMATS.indexOf(b.format) < 0) warnings.push(current + ' (basic): format "' + b.format + '" không hợp lệ');
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
    ['ad_ids', pretty(result.ad_ids), 'ID quảng cáo theo ad unit']
  ];
  var warnings = result.warnings.slice();
  if (!Object.keys(result.ad_ids).length) warnings.push('Chưa điền ID nào: app sẽ dùng test ID cho mọi ad unit.');
  var all = pretty({ ad_settings: result.ad_settings, ad_placements: result.ad_placements, ad_ids: result.ad_ids });
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

if (typeof module !== 'undefined') module.exports = { buildAdConfig: buildAdConfig, pretty: pretty };
