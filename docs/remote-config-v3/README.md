# Remote Config quảng cáo (đề xuất)

Sheet: `ad_config_v3_1sheet.xlsx`
- Tab `ad_config`: **chỉ config đang dùng**. Setting ở trên; bên dưới mỗi ad unit một dòng (ID), cột `placement` merge theo vị trí, hai nhóm cột **FULL** và **BASIC**.
- Tab `giai_thich`: giải thích mọi key, cột, giá trị và mặc định.

Sheet xuất ra 4 parameter trên Firebase (app cũng đọc 1 parameter `ad_config` chứa cả 4 phần, đúng file "Tải tất cả"; parameter riêng thắng):

| Parameter | File mẫu | Chứa gì | Hay sửa? |
|---|---|---|---|
| `ad_settings` | `ad_settings.json` | Cấu hình chung | Thỉnh thoảng |
| `ad_placements` | `ad_placements.json` | `full`: bộ đầy đủ, `basic`: bộ cơ bản | **Hay sửa, hay A/B test** |
| `ad_ids` | `ad_ids.json` | `ad_unit → [id_1, id_2, id_3]` (app thử id_1 → id_3) | Một lần lúc phát hành |
| `ad_templates` | `ad_templates.json` | Mẫu native tự đặt / style đè lên mẫu có sẵn (tab `templates`) | Khi đổi giao diện native |

## Cấu hình khác nhau theo bản app

App luôn đọc cùng các parameter trên, **không tạo parameter theo version** (`ad_config_109`...). Bản mới cần cấu hình khác
thì trên Firebase thêm *Conditional value* cho parameter đó với điều kiện **App build ≥ versionCode của bản mới**; giá trị
mặc định giữ cho bản cũ. Trên sheet: nhân bản tab cấu hình (vd. `version 201`), sửa, đứng ở tab đó rồi Export. Chi tiết:
`docs/BASE_GUIDE.md` mục 5.3.

## Xuất JSON từ Google Sheet

Script `export_ad_config.gs` (Google Apps Script) đọc **tab cấu hình đang mở** (`ad_config`, `version 201`...) + tab `templates` và tạo 4 JSON.

**Cài một lần:**
1. Mở Google Sheet → **Extensions → Apps Script**.
2. Xoá code mẫu, dán toàn bộ `export_ad_config.gs` → **Save**.
3. Tải lại Google Sheet: thanh menu có thêm **Ad config**.

**Mỗi lần xuất:** menu **Ad config → Export JSON** (lần đầu Google hỏi cấp quyền cho script đọc sheet → cho phép). Hộp thoại hiện:
- 4 khối `ad_settings`, `ad_placements`, `ad_ids`, `ad_templates` ở dạng JSON thường (không bọc trong chuỗi), giống hệt các file mẫu trong thư mục này. Nút **Copy** → dán vào parameter cùng tên trên Firebase Remote Config (kiểu JSON). Nút **Tải** → lưu thành file.
- Nút **Tải tất cả** → `ad_config.json` gồm cả 4 khối, để lưu trữ / gửi team xem (không phải để import).
- **Cảnh báo** nếu có ô sai (vd. `format` không hợp lệ, basic có `format` nhưng chưa bật `enable`) hoặc chưa điền ID nào.

Quy tắc script đọc: ô trống = không ghi vào JSON (app dùng mặc định); bộ basic chỉ lấy placement có `enable` TRUE; `pages`, `open_ad_on_clicks` viết `2,4` → `[2, 4]`; `full_inter_gap_ignore` viết cách nhau dấu phẩy → mảng; ad unit không có ID nào → không ghi (app dùng test ID). Đổi tên tab, hoặc xoá các ô tiêu đề `setting`, `ad_unit`, `FULL …`, `BASIC …` thì script không đọc được.

## Hai bộ quảng cáo

`ads_profile` chọn user nào dùng bộ nào:

| `ads_profile` | Bộ full | Bộ basic |
|---|---|---|
| `0` | – | – (tắt mọi quảng cáo) |
| `1` (mặc định) | mọi user | – |
| `2` | user đến từ quảng cáo marketing (theo Adjust) | user tự nhiên / chưa biết nguồn |
| `3` | mọi user trừ máy test | máy nhận test ad |

User Premium luôn không có quảng cáo. App cũng nhận tên (`off`, `full_for_all`, `full_for_paid_users`, `full_except_test_devices`).

- **full** (`ad_placements.full`): chỉ ghi chỗ khác mặc định; placement không ghi = mặc định ở bảng dưới.
- **basic** (`ad_placements.basic`): **chỉ placement có trong đây mới chạy** (`"enable": false` để tắt), còn lại tắt. Bộ mẫu không có native (`format` aoa / inter / banner / banner_collapsible); muốn có native thì thêm placement native vào basic. Dùng chung ad unit + ID với full.

## Các trường của một placement (bộ full)

| Trường | Kiểu | Áp cho | Ý nghĩa |
|---|---|---|---|
| `enable` | true/false | mọi placement | Bật / tắt |
| `format` | chữ | ads_splash, inter_\*, banner_\*, on_resume | Loại quảng cáo (bảng dưới) |
| `template` | chữ | native | Big1…Big9, Small1…4, Tiny1/2, Collap1…3, Full1…5, hoặc tên mẫu tự đặt trong `ad_templates` |
| `pages` | mảng số | native_onboarding, native_full_onboarding | Trang có native / chèn trang native full sau trang |
| `step` | số | inter_\* | Cứ n lần mới hiện 1 lần |
| `wait_ms` | ms | ads_splash, inter_\*, native_full_onboarding, native_welcome_back | Chờ / đếm ngược trước khi đóng được native |
| `refresh_ms` | ms | banner_\* | Tự tải lại ô banner |
| `open_ad_on_clicks` | mảng số | banner_home (native_collapsible) | Lần click thứ mấy (bất kỳ chỗ nào trên quảng cáo) thì mở trang quảng cáo; vd. [2, 4] |

`banner_home` với format `native_collapsible`: `template` chọn **bộ** Collap1 / Collap2 / Collap3 (trống hoặc mẫu khác = Collap1). Dạng lớn có nút thu gọn (Collap1, Collap3) hoặc nút đóng (Collap2); bấm thì thành dải: Collap1 → Tiny1, Collap2 → Tiny2, Collap3 → Tiny1. Style `ad_templates` của mẫu Collap\* áp cho cả dạng lớn và dải.

`format` chọn theo **`type`** của placement (cột `type` trên sheet, cố định theo code, không gửi lên Firebase):

| `type` | Placement | `format` |
|---|---|---|
| `splash` | ads_splash | `aoa` · `inter` · `inter_then_native_full` |
| `inter` | inter_language, inter_onboarding, inter_home | `inter` · `native_full` · `inter_then_native_full` |
| `banner_native` | banner_home | `banner` · `banner_collapsible` · `native` · `native_collapsible` |
| `banner` | banner_setting | `banner` · `banner_collapsible` (không có ad unit native) |
| `resume` | on_resume | `aoa` · `welcome_back` |
| `native` | các placement native_* | không có format |

Trên sheet, cột `type` nằm ngay cạnh `format` (đầu nhóm FULL). Tab **`types`** là bảng `type → format` (tối đa 4 format, thêm type mới thì thêm dòng ở đó). Mỗi dòng của `ad_config` có **4 cột phụ ẩn** (sau nhóm BASIC, không tiêu đề) dùng công thức lấy danh sách format theo `type` của dòng; dropdown `format` (FULL và BASIC) lấy từ các cột phụ đó, nên **đổi `type` là dropdown đổi ngay**, không cần script. Đổi `type` thì `format` cũ không hợp không tự xoá, ô chỉ báo đỏ; script xuất JSON cũng cảnh báo. **Thêm dòng mới: copy cả dòng bên trên rồi paste xuống** để kéo theo công thức phụ và dropdown. Không xoá / dời các cột phụ.

## Mẫu native: ad_templates (tab templates)

Mọi native (Language, Onboarding, Home, feed, native collapsible, native full...) đổi được màu / bo góc / viền / cỡ chữ / kích thước qua parameter
`ad_templates`, không cần ra bản mới. Vị trí các phần (CTA trên / dưới / bên phải, có ảnh lớn hay không) lấy từ mẫu gốc
(`base`): vd. Big1 CTA dưới, Big5 CTA trên, Big2 gọn với CTA bên phải.

```json
{
  "cta_top_purple": { "base": "Big5", "cta_bg": "#7B2FA6", "cta_corner_dp": 12, "card_bg": "#F6EEFB" },
  "Big2": { "cta_bg": "#E53935" }
}
```

- **Tên mới** (`cta_top_purple`): mẫu tự đặt, cột `template` của placement ghi tên này.
- **Tên mẫu có sẵn** (`Big2`): đè style lên mẫu đó ở mọi placement dùng nó (kể cả placement dùng nó làm mặc định); `base` bỏ qua.
- Màu: `cta_bg`, `cta_text`, `card_bg`, `card_stroke`, `title_color`, `body_color` (`#RRGGBB` / `#AARRGGBB`).
- Hình dạng: `cta_corner_dp`, `card_corner_dp`, `card_stroke_dp`.
- Kích thước: `cta_height_dp` (28–80, mặc định 48), `cta_text_sp` (10–24), `title_text_sp` (10–28, mặc định 15),
  `body_text_sp` (9–22, mặc định 12), `media_height_dp` (120–400; mẫu Full* bỏ qua), `icon_size_dp` (24–96, mặc định 48),
  `card_padding_dp` (0–32, mặc định 12). Số ngoài khoảng bị kéo về giới hạn gần nhất.
- Trường thiếu / sai = giữ như mẫu gốc; `base` sai = mẫu mặc định của placement.
- Không trường nào ẩn được nhãn "Ad", ảnh hay nút CTA (đúng chính sách native của AdMob).

Trong sheet: tab `templates` liệt kê mọi mẫu (có sẵn + tự đặt); dropdown cột `template` ở tab `ad_config` lấy từ cột A của tab này.
Script cảnh báo màu / số sai, `base` sai và placement chọn template không có.

## Mặc định của bộ full (khi không ghi)

| Placement | Mặc định |
|---|---|
| ads_splash | bật, `inter` |
| native_splash | **tắt**, Big1 |
| native_language | bật, Big1 |
| native_language_small | **tắt**, Tiny1 |
| inter_language | bật, `inter` |
| native_onboarding | bật, `pages` [1, 2, 3, 4], Big1 |
| native_full_onboarding | **tắt**, Full1 |
| inter_onboarding | bật, `inter` |
| on_resume | bật, `aoa` |
| native_welcome_back | bật, Big1 |
| banner_home | bật, `banner_collapsible` |
| native_home_feed | bật, Big1 |
| inter_home | **tắt**, `inter` |
| banner_setting | bật, `banner_collapsible` |

Mọi placement: `step` 1, `wait_ms` 0, `refresh_ms` 0 (không tự tải lại), `open_ad_on_clicks` [] (click nào cũng xử lý bình thường).

## ad_settings - mặc định

| Key | Mặc định |
|---|---|
| `ads_profile` | `1` (0 tắt · 1 mọi user full · 2 user marketing full · 3 máy test ad basic) |
| `splash_fallback` | không có |
| `language_next_style` | `text` (icon · text) |
| `language_next_delay_ms` | 0 |
| `language_show_loading` | false |
| `onboarding_swipe` | true |
| `onboarding_next_style` | `icon` (icon · text), áp cho mọi trang Onboarding |
| `onboarding_next_position` | `bottom` (bottom · right). *`right` chưa có giao diện, app hiện xử lý như `bottom`.* |
| `onboarding_next_delay_ms` | 0 |
| `onboarding_show_dots` | true |
| `onboarding_full_close_button` | `next` (x · next) |
| `full_inter_gap_ms` | 0 |
| `full_inter_gap_ignore` | [] |
| `basic_inter_gap_ms` | 90000 |
