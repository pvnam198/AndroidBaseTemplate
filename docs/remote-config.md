# Remote Config (cho dev)

Cấu hình quảng cáo và luồng mở app điều khiển từ xa qua **Firebase Remote Config**.
**Đặc tả format (các trường, giá trị, mặc định): [`remote-config-v3/README.md`](remote-config-v3/README.md).**
Sheet cho team điền + script xuất JSON: [`remote-config-v3/`](remote-config-v3/).

## Trên Firebase

4 parameter kiểu JSON, mỗi cái một việc:

| Parameter | Nội dung |
|---|---|
| `ad_settings` | Cấu hình chung: `ads_profile`, `splash_fallback`, nút Language / Onboarding, khoảng cách inter |
| `ad_placements` | `{ "full": {...}, "basic": {...} }` - mỗi placement một object, chỉ ghi chỗ khác mặc định |
| `ad_ids` | `{ ad_unit: [id_1, id_2, id_3] }` |
| `ad_templates` | `{ tên mẫu: { base, cta_bg, ... } }` - mẫu native tự đặt / style đè lên mẫu có sẵn (xem `remote-config-v3/README.md`) |

App cũng đọc **một parameter `ad_config`** chứa cả 4 phần (`{ "ad_settings": …, "ad_placements": …, "ad_ids": …, "ad_templates": … }`, đúng file "Tải tất cả" của sheet). Parameter riêng nào có giá trị thì thắng phần tương ứng trong `ad_config`.
Mẫu: [`app/src/test/resources/ad_config_sample.json`](../app/src/test/resources/ad_config_sample.json) (dùng luôn cho unit test).

## Cơ chế

1. **Seed từ cache** — khi `RemoteConfig.initialize()`, các parameter được nạp bản của lần fetch thành công gần nhất (`AppPreferences`, tiền tố `cache_v3_`).
2. **Mặc định** — phần nào thiếu / hỏng: placement dùng `AdKey.fullDefault` (bộ full) hoặc tắt (bộ basic); setting dùng mặc định trong getter của `RemoteConfig`.
3. **Fetch + activate** — xong thì lưu cache; `RemoteConfig.isReady` lật `true` (Splash chờ tối đa 8s). DEBUG fetch mỗi lần mở, RELEASE 3600 giây.
4. **Chọn bộ** — `AdPolicy.profile` từ `ads_profile` (0 tắt · 1 mọi user full · 2 user marketing full · 3 máy test ad basic; nhận cả tên) + `AdAudience` (nguồn user, máy test ad) + Premium → OFF / FULL / BASIC. Code quảng cáo luôn đọc qua `AdPolicy.config(key)` / `AdPolicy.format(key)`.
5. **Debug override** (chỉ bản debug, màn *Cài đặt → Debug config*): cả parameter (`ad_config`, `ad_settings`, `ad_placements`, `ad_ids`, `ad_templates`), một setting (vd `ads_profile`), một placement bộ full (vd `inter_home`) hoặc bộ basic (`basic.inter_home`). Giá trị placement: JSON object, `true` / `false`, hoặc tên format. Override thắng Firebase.

Mọi thời gian tính bằng **mili-giây**.

## Bản đồ code

| File | Vai trò |
|---|---|
| `core/config/PlacementConfig.kt` | `PlacementFormat`, `PlacementConfig` (một placement), `AdKey` (placement + mặc định bộ full), `AdSet`, `AdPlacementsDocument`, `AdIdsDocument` |
| `core/config/RemoteConfig.kt` | Đọc 4 parameter (+ `ad_config`), cache, override debug; getter cho từng setting; `AdsProfileRule` |
| `core/ads/AdProfile.kt` | `AdProfile` (OFF / FULL / BASIC), `resolveAdProfile` |
| `core/ads/AdPolicy.kt` | `config(key)` / `format(key)` cho user này, `interDelay`, `splashFallback`, `nativeEnabled(placement)` |
| `core/ads/AdUnit.kt` | Ad unit (tên viết thường = `ad_ids` / cột `ad_unit`), `ids()` |
| `core/ads/FullScreenAdPlan.kt` | `format` → loại full-screen (AOA / inter / inter + native / chỉ native) |
| `core/ads/BannerAds.kt` | `format` của `banner_*` → banner / collapsible / native / native collapsible |
| `core/ads/NativeAds.kt` | `NativePlacement` → `AdUnit`; bật theo `enable` / `pages`; mẫu theo `template` (`templateFor` → `NativeTemplateSpec`) |
| `core/ads/template/NativeStyle.kt`, `NativeStyleApplier.kt` | `ad_templates`: `NativeStyle`, `AdTemplatesDocument.resolve`; áp màu / bo góc / viền / kích thước lên view native |
| `debug/NativePreviewActivity.kt` (chỉ bản debug) | Xem trước mọi mẫu + style: `adb shell am start -n com.namstd.androidskillhub/.debug.NativePreviewActivity [--es name big5]` |
| `core/ads/CollapClickGate.kt`, `NativeCollapseAd.kt` | Native collapsible theo bộ Collap1/2/3 (dạng lớn) → Tiny1/2 (dải), `NativeAdTemplate.collapsed`; `open_ad_on_clicks`: đếm mọi click trên native collapsible |

## Chưa làm

- `onboarding_next_position = right`: chưa có giao diện "Next bên phải", hiện vẫn đặt Next dưới cụm chấm như `bottom`.

## Lịch sử

- **26/10/2026** — bộ key theo spec PDF cũ (key gộp `enable`, ID theo loại quảng cáo).
- **04–05/11/2026** — theo sheet "Template Info": `ads_enable` + kịch bản A/B, mọi thời gian sang ms, sửa 5 key theo ghi chú sheet, Intro 4 trang với `intro1…4_setup`, `native_intro_full`, `splash_fallback`, bỏ `release_log` / `taller_banner_enabled`.
- **05/11/2026** — chuyển hẳn sang cấu hình **theo từng vị trí** như sheet, gói trong **một key `ad_config`** đúng mẫu JSON của team (ID theo ad unit qua `name1`/`id1`–`id3`), xoá bộ key cũ; thêm `native_splash`, `native_intro` theo trang, `step_count` / `wait_time` / `native_template` / `refresh_rate` / `collap_config` riêng từng vị trí.
- **06/11/2026** — tên template đúng như tài liệu (Big1…, Tiny1…; bỏ Small5–7); `native_language` chọn layout theo `enable`, `NATIVE_LANGUAGE_2` cho màn Language mở từ Cài đặt; thêm `native_language_small` (dải Tiny1 dưới tiêu đề màn Language); bỏ khỏi base các vị trí không có màn: theme / icon / widget / collection / result / wallpaper, `native_get_start`, `inter_start`, `inter_back`, `inter_welcome_back`. Thêm mua Premium thật qua Google Play Billing (xem `billing.md`).
- **06/11/2026** — đổi tên theo tab "Kịch bản A new" của sheet: Intro → Onboarding (`native_onboarding`, `native_full_onboarding`, `inter_onboarding`, `onboarding1…4_setup`, `onboarding_swipe`, `native_onboarding_use_icon_next`, ad unit `NATIVE_ONBOARDING_1…4`, `NATIVE_FULL_ONBOARDING`, `INTER_ONBOARDING`, `NATIVE_INTER_ONBOARDING`); `name1` không phân biệt hoa thường. Thêm mua Premium thật qua Google Play Billing (xem `billing.md`).
- **06/11/2026** — app chuyển sang **format mới**: 3 parameter `ad_settings`, `ad_placements` (`full` / `basic`), `ad_ids` (hoặc 1 key `ad_config` chứa cả 3); `enable` chỉ là bật/tắt, loại ở `format`, mẫu ở `template`, trang ở `pages`; bộ basic đọc từ config; thêm `native_home_feed`; `open_ad_on_clicks` đếm mọi click trên quảng cáo (bấm thẻ lại mở được quảng cáo); bấm icon khi app đang chạy nền không còn chạy lại Splash. Bỏ hẳn format `ad_config` cũ (`settings`, `name1`, `id1..3`).

- **06/11/2026** — thêm parameter **`ad_templates`** (tab `templates` của sheet): đổi màu / bo góc / viền / cỡ chữ / kích thước (CTA, ảnh, icon, đệm) của mọi native từ remote, đặt mẫu tự đặt (layout gốc `base` + style) cho cột `template`; màn debug `NativePreviewActivity` xem trước mọi mẫu. Sheet: `id_1..3`, `ads_profile` 0–3, cột `type` + dropdown `format` theo type (tab `types`).
