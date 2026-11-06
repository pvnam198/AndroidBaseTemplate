# Remote Config quảng cáo (đề xuất)

Sheet: `ad_config_v3_1sheet.xlsx`
- Tab `ad_config`: **chỉ config đang dùng**. Setting ở trên; bên dưới mỗi ad unit một dòng (ID), cột `placement` merge theo vị trí, hai nhóm cột **FULL** và **BASIC**.
- Tab `giai_thich`: giải thích mọi key, cột, giá trị và mặc định.

Sheet xuất ra 3 parameter trên Firebase (app cũng đọc 1 parameter `ad_config` chứa cả 3 phần, đúng file "Tải tất cả"; parameter riêng thắng):

| Parameter | File mẫu | Chứa gì | Hay sửa? |
|---|---|---|---|
| `ad_settings` | `ad_settings.json` | Cấu hình chung | Thỉnh thoảng |
| `ad_placements` | `ad_placements.json` | `full`: bộ đầy đủ, `basic`: bộ cơ bản | **Hay sửa, hay A/B test** |
| `ad_ids` | `ad_ids.json` | `ad_unit → [id_high, id_medium, id_low]` (app thử theo thứ tự) | Một lần lúc phát hành |

## Xuất JSON từ Google Sheet

Script `export_ad_config.gs` (Google Apps Script) đọc tab `ad_config` và tạo 3 JSON.

**Cài một lần:**
1. Mở Google Sheet → **Extensions → Apps Script**.
2. Xoá code mẫu, dán toàn bộ `export_ad_config.gs` → **Save**.
3. Tải lại Google Sheet: thanh menu có thêm **Ad config**.

**Mỗi lần xuất:** menu **Ad config → Export JSON** (lần đầu Google hỏi cấp quyền cho script đọc sheet → cho phép). Hộp thoại hiện:
- 3 khối `ad_settings`, `ad_placements`, `ad_ids` ở dạng JSON thường (không bọc trong chuỗi), giống hệt các file mẫu trong thư mục này. Nút **Copy** → dán vào parameter cùng tên trên Firebase Remote Config (kiểu JSON). Nút **Tải** → lưu thành file.
- Nút **Tải tất cả** → `ad_config.json` gồm cả 3 khối, để lưu trữ / gửi team xem (không phải để import).
- **Cảnh báo** nếu có ô sai (vd. `format` không hợp lệ, basic có `format` nhưng chưa bật `enable`) hoặc chưa điền ID nào.

Quy tắc script đọc: ô trống = không ghi vào JSON (app dùng mặc định); bộ basic chỉ lấy placement có `enable` TRUE; `pages`, `open_ad_on_clicks` viết `2,4` → `[2, 4]`; `full_inter_gap_ignore` viết cách nhau dấu phẩy → mảng; ad unit không có ID nào → không ghi (app dùng test ID). Đổi tên tab, hoặc xoá các ô tiêu đề `setting`, `ad_unit`, `FULL …`, `BASIC …` thì script không đọc được.

## Hai bộ quảng cáo

`ads_profile` chọn user nào dùng bộ nào:

| `ads_profile` | Bộ full | Bộ basic |
|---|---|---|
| `off` | – | – (tắt mọi quảng cáo) |
| `full_for_all` (mặc định) | mọi user | – |
| `full_for_paid_users` | user đến từ quảng cáo marketing (theo Adjust) | user tự nhiên / chưa biết nguồn |
| `full_except_test_devices` | mọi user trừ máy test | máy nhận test ad |

- **full** (`ad_placements.full`): chỉ ghi chỗ khác mặc định; placement không ghi = mặc định ở bảng dưới.
- **basic** (`ad_placements.basic`): **chỉ placement có trong đây mới chạy** (`"enable": false` để tắt), còn lại tắt. Bộ mẫu không có native (`format` aoa / inter / banner / banner_collapsible); muốn có native thì thêm placement native vào basic. Dùng chung ad unit + ID với full.

## Các trường của một placement (bộ full)

| Trường | Kiểu | Áp cho | Ý nghĩa |
|---|---|---|---|
| `enable` | true/false | mọi placement | Bật / tắt |
| `format` | chữ | ads_splash, inter_\*, banner_\*, on_resume | Loại quảng cáo (bảng dưới) |
| `template` | chữ | native | Big1…Big9, Small1…4, Tiny1/2, Collap1…3, Full1…5 |
| `pages` | mảng số | native_onboarding, native_full_onboarding | Trang có native / chèn trang native full sau trang |
| `step` | số | inter_\* | Cứ n lần mới hiện 1 lần |
| `wait_ms` | ms | ads_splash, inter_\*, native_full_onboarding, native_welcome_back | Chờ / đếm ngược trước khi đóng được native |
| `refresh_ms` | ms | banner_\* | Tự tải lại ô banner |
| `open_ad_on_clicks` | mảng số | banner_home (native_collapsible) | Lần click thứ mấy (bất kỳ chỗ nào trên quảng cáo) thì mở trang quảng cáo; vd. [2, 4] |

`format`: ads_splash `aoa` · `inter` · `inter_then_native_full` — inter_\* `inter` · `native_full` · `inter_then_native_full` — banner_\* `banner` · `banner_collapsible` · `native` · `native_collapsible` — on_resume `aoa` · `welcome_back`.

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
| `ads_profile` | `full_for_all` |
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
