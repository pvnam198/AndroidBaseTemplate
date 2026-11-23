# Remote Config (cho dev)

Cấu hình quảng cáo và luồng mở app điều khiển từ xa qua **Firebase Remote Config**. File cấu hình xuất từ web
"Cấu hình quảng cáo" (WaveZ Marketing → tab Tài liệu có giải thích đầy đủ từng key). App đọc **thẳng** file đó, không
qua bước chuyển đổi nào.

## Trên Firebase

**Một parameter theo bản app** (kiểu JSON) chứa nguyên file xuất từ web: `VERSION_KEY` trong
`core/config/RemoteConfig.kt` = `ad_ver` + chữ số versionName (hiện `ad_ver10`). Mẫu: [`ad_config_sample.json`](ad_config_sample.json)
(bản giống hệt ở `app/src/test/resources/` dùng cho unit test; ID là test ID của Google).

```json
{
  "config_version": "1", "version": 1,
  "ads_profile": 1,
  "settings": { "splash_timeout_ms": 10000, "splash_fallback": ["inter_language"], "onboarding_pages": { "2": { "next_delay_ms": 1000 } }, ... },
  "profiles": {
    "full":  { "settings": { "inter_gap_ms": 10000, "inter_gap_ignore": ["ads_splash"] }, "placements": { "ads_splash": {...}, ... } },
    "basic": { "settings": { "inter_gap_ms": 90000 }, "placements": { ... } }
  },
  "templates": { "LanguageDisplayFirst": { "base": "Big5", "cta_bg": "#32A852" } }
}
```

Mỗi bản release đổi `VERSION_KEY` và thêm key mới trên Firebase (xem `BASE_GUIDE.md` mục 5.3). Không có key = mọi placement tắt.

### Gốc file

| Key | Ý nghĩa |
|---|---|
| `config_version`, `version` | Tên / số phiên bản cấu hình trên web — chỉ để đối chiếu (log `AdsRC` ở bản debug). |
| `ads_profile` | Ai dùng bộ nào: `0` tắt hết · `1` mọi user full · `2` user marketing (Adjust) full, còn lại basic · `3` mọi user full trừ máy test ad (basic). Nhận cả tên (`off`, `full_for_all`, `full_for_paid_users`, `full_except_test_devices`). Premium luôn không có quảng cáo. |
| `settings` | Thiết lập dùng chung cho cả 2 bộ (bảng dưới). |
| `profiles.full` / `profiles.basic` | Mỗi bộ: `settings` riêng + `placements` (đã gộp sẵn trên web). **Placement không có trong bộ = tắt** — app không giữ mặc định nào. |
| `templates` | Mẫu native tự đặt / style đè lên mẫu có sẵn (`BASE_GUIDE.md` mục 6.2). |

### `settings` gốc

| Key | Mặc định | Ý nghĩa |
|---|---|---|
| `splash_timeout_ms` | 8000 | Splash chờ quảng cáo tối đa (giới hạn `splash_min_ms`–30000). |
| `splash_min_ms` | 3000 | Splash chờ tối thiểu (0–10000). Không có trên web, thêm tay nếu cần. |
| `splash_fallback` | – | Mảng tên placement inter: Splash không kịp hiện quảng cáo thì placement đầu tiên trong danh sách hiện bù 1 inter (kể cả khi đang tắt). Chỉ bộ full. |
| `language_next_style` | text | Nút đi tiếp màn Language: `icon` = dấu ✓ trên thanh tiêu đề, `text` = nút chữ. Lần đầu: ẩn tới khi user chọn ngôn ngữ. |
| `language_next_delay_ms` | 0 | Chờ trước khi hiện nút đi tiếp (lần đầu: tính từ lúc chọn ngôn ngữ). 0 = hiện ngay, không loading. |
| `language_show_loading` | false | Trong lúc chờ `language_next_delay_ms` hiện vòng loading ở chỗ nút. |
| `onboarding_swipe` | true | Cho vuốt trang Onboarding — chỉ khi nút đi tiếp của trang đang xem đã hiện (hết `next_delay_ms`; trang native full: đã hiện X / Next). |
| `onboarding_next_style` | icon | `icon` = nút rộng hết màn; `text` = chữ Next, cùng hàng với chấm (chấm trái, Next phải; tắt chấm thì Next vẫn bên phải). |
| `onboarding_show_dots` | true | Chấm tròn báo trang. |
| `onboarding_indicator_position` | above | Kiểu `icon`: `above` = chấm trên nút Next, `below` = dưới. |
| `onboarding_next_delay_ms` | 0 | Chờ trước khi hiện nút Next. |
| `onboarding_pages` | – | Đè riêng từng trang (đếm từ 1): `{ "2": { "next_delay_ms": 3000, "next_style": "text" } }`. |
| `onboarding_full_close_button` | next | Nút trang native full trong Onboarding: `x` / `next`. |

### `profiles.<bộ>.settings`

| Key | Mặc định full / basic | Ý nghĩa |
|---|---|---|
| `inter_gap_ms` | 0 / 90000 | Khoảng cách tối thiểu giữa 2 inter. |
| `inter_gap_ignore` | – | Mảng placement không bị `inter_gap_ms` chặn. |
| `open_gap_ms` | 30000 / 90000 | Giữa 2 lần quảng cáo quay lại app (App Open hoặc màn Welcome Back). Không có trên web, thêm tay nếu cần. |
| `open_inter_gap_ms` | 15000 / 30000 | Giữa quảng cáo quay lại app (App Open / Welcome Back) và inter, cả 2 chiều. Không có trên web, thêm tay nếu cần. Log debug: `adb logcat -s AdsGap`. |

### Một placement

`"ads_splash": { "type": "splash", "ad_type": "inter_then_native", "enable": true, "ad_ids": { "inter": [...], "native": [...] }, "template": "Full1", "fallback": "show_available" }`

| Trường | Ý nghĩa |
|---|---|
| `enable` | Bật / tắt. |
| `type` | Loại vị trí: splash, onboarding, inter, banner, resume, native, reward, reward_inter. |
| `ad_type` | Loại quảng cáo (bảng dưới). |
| `ad_ids` | `{ loại: [id_1, id_2, id_3] }` theo loại Ad Unit `aoa` / `inter` / `native` / `banner` / `reward` / `reward_inter`; app thử lần lượt (tối đa 3). Không có ID → không có quảng cáo. Bản debug gửi request bằng test ID của Google thay mọi ID đã cấu hình; release không bao giờ dùng test ID. |
| `template` | Mẫu native (Big1…, Tiny1…, Collap1…, Full1…, hoặc tên trong `templates`). Trống = mặc định của chỗ hiện. |
| `pages` | type onboarding (`native_full_onboarding`): chèn trang native full sau các trang này. |
| `step` | Inter: cứ `step` lần gọi mới hiện 1 lần. |
| `wait_ms` | Đếm ngược trước khi đóng được native toàn màn (sau inter, trang native Onboarding); `native_welcome_back`: trước khi bấm được Tiếp tục; reward_inter: màn đếm ngược trước quảng cáo. |
| `refresh_ms` | Ô banner tự tải lại, 0 = không. |
| `open_ad_on_clicks` | native_as_banner_collap: lần click thứ mấy trên quảng cáo thì mở trang quảng cáo. |
| `fallback` | inter_then_native: `show_available` (mặc định) hiện cái tải được; `skip` thiếu 1 trong 2 thì bỏ cả luồng. |
| `custom_ui` | type resume: màn Welcome Back riêng — đọc nhưng base chỉ có 1 màn Welcome Back (cả 2 giá trị đều dùng nó). |

| `ad_type` | Dùng ở | App làm gì |
|---|---|---|
| `aoa` | ads_splash, on_resume | App Open (on_resume: hiện ngay khi quay lại app). |
| `inter` | ads_splash, inter_\*, on_resume | Interstitial (on_resume: sau khi bấm Tiếp tục ở Welcome Back, như mọi ad_type khác aoa). |
| `inter_then_native` | ads_splash, inter_\*, on_resume | Inter, đóng xong hiện native toàn màn (`ad_ids.native` của cùng placement). |
| `native_as_inter` | inter_\*, on_resume | Chỉ native toàn màn (Splash chưa hỗ trợ → tắt). |
| `native` | native_\*, on_resume | Native trong màn; ở `on_resume` = Tiếp tục ở Welcome Back hiện native toàn màn (`ad_ids.native`). |
| `banner` / `banner_collapsible` | banner_\* | AdMob banner thường / collapsible. |
| `native_as_banner` / `native_as_banner_collap` | banner_home | Native trong ô banner / native collapsible (ô không hỗ trợ native → banner thường). |
| `reward` / `reward_inter` | reward_\* | Quảng cáo thưởng. |

### Placement của base

`ads_splash`, `native_splash`, `native_language`, `native_language_2` (màn Language mở từ Cài đặt),
`native_language_small`, `inter_language`, `native_onboarding_1`…`native_onboarding_4` (chân trang nội dung 1–4, mỗi
trang một placement), `native_full_onboarding`,
`inter_after_onboarding`, `on_resume` (quay lại app: aoa = App Open; ad_type khác = màn Welcome Back, Tiếp tục chạy
quảng cáo theo ad_type), `native_welcome_back` (native chân màn Welcome Back, `wait_ms` = đếm ngược nút Tiếp tục), `banner_home`,
`native_home_feed`, `inter_home`, `banner_setting`, `reward_sample`, `reward_inter_sample`. Tên trên web phải khớp
`AdKey`; placement app không biết thì bỏ qua.

## Cơ chế

1. **Seed từ cache** — `RemoteConfig.initialize()` nạp giá trị thô của `VERSION_KEY` ở lần fetch thành công gần nhất (`AppPreferences`, key `cache_v4_` + tên key, vd `cache_v4_ad_ver10`).
2. **Thiếu / hỏng** — JSON hỏng hoặc không có = mọi placement tắt; setting thiếu dùng mặc định trong getter của `RemoteConfig`.
3. **Fetch + activate** — xong thì lưu cache; `RemoteConfig.isReady` lật `true` (cả khi lỗi). Lỗi thì thử lại sau 2 s / 4 s / 8 s, hết lượt thì chờ mạng có lại; fetch được mà file đổi thì quảng cáo chạy ngay từ lúc đó (`BASE_GUIDE.md` mục 4.4). Không có config mặc định nhúng trong app. DEBUG fetch mỗi lần mở (lỗi fetch in ra log `AdsRC`, mốc thử lại ở `AdsInit`), RELEASE 3600 giây.
4. **Chọn bộ** — `AdPolicy.profile` từ `ads_profile` + `AdAudience` (nguồn user, máy test ad) + Premium → OFF / FULL / BASIC. Code quảng cáo luôn đọc qua `AdPolicy.config(key)` / `AdPolicy.adType(key)`.
5. **Debug override** (chỉ bản debug, *Cài đặt → Debug config*): cả file (dòng `ad_ver…`), `ads_profile`, một key trong `settings` gốc, `full.settings` / `basic.settings` (JSON), một placement bộ full (vd `inter_home`) hoặc basic (`basic.inter_home`): JSON object thay cả placement, `true` / `false`, hoặc tên ad_type. Override thắng Firebase.

Mọi thời gian tính bằng **mili-giây**.

## Bản đồ code

| File | Vai trò |
|---|---|
| `core/config/PlacementConfig.kt` | `AdType`, `PlacementType`, `AdFallback`, `PlacementConfig` (một placement), `AdKey` (tên placement), `AdSet`, `AdConfigDocument` (cả file) |
| `core/config/RemoteConfig.kt` | `VERSION_KEY`, đọc file cấu hình, cache theo key, override debug; getter cho từng setting; `AdsProfileRule` |
| `core/ads/AdsAvailability.kt`, `AdsRetry.kt` | Quảng cáo dùng được chưa (SDK + config), hoãn preload / slot; lịch thử lại 2 s / 4 s / 8 s, `NetworkRegain` |
| `core/ads/AdProfile.kt` | `AdProfile` (OFF / FULL / BASIC), `resolveAdProfile` |
| `core/ads/AdPolicy.kt` | `config(key)` / `adType(key)` cho user này, `interDelay`, `openGaps`, `splashFallback`, `nativeEnabled(placement)` |
| `core/ads/AdUnit.kt` | Ad unit = một loại trong `ad_ids` của một placement; `configIds()` (ID thật), `ids()` / `requestId()` (bản debug: test ID) |
| `core/ads/AdIdPool.kt`, `FullScreenAdCache.kt` | Cache quảng cáo theo ID thật trong `ad_ids`, dùng chung giữa placement (`BASE_GUIDE.md` mục 4.3) |
| `core/ads/FullScreenAdPlan.kt` | `ad_type` → loại full-screen (AOA / inter / inter + native / chỉ native) |
| `core/ads/BannerAds.kt` | `ad_type` của `banner_*` → banner / collapsible / native / native collapsible |
| `core/ads/RewardAds.kt` | Quảng cáo thưởng: `RewardPlacement`, `BaseActivity.showReward` |
| `core/ads/NativeAds.kt` | `NativePlacement` → `AdUnit`; bật theo `enable`; mẫu theo `template` (`templateFor` → `NativeTemplateSpec`) |
| `core/ads/template/NativeStyle.kt`, `NativeStyleApplier.kt` | `templates`: `NativeStyle`, `AdTemplatesDocument.resolve`; áp màu / bo góc / viền / kích thước |
| `AndroidSkillHubApp.kt`, `feature/welcome/WelcomeBackActivity.kt` | `on_resume` theo `ad_type`: aoa → App Open; khác → `WelcomeBackActivity` (native `native_welcome_back`), Tiếp tục → `showInterstitial(InterstitialPlacement.RESUME, ignoreGap = true)` (inter / native toàn màn / inter + native). Welcome Back chịu `open_gap_ms` / `open_inter_gap_ms` (`AdGapGate`). |

## Chưa làm

- `custom_ui` của `on_resume`: base chỉ có 1 màn Welcome Back.

## Lịch sử

- **26/10/2026** — bộ key theo spec PDF cũ (key gộp `enable`, ID theo loại quảng cáo).
- **04–05/11/2026** — theo sheet "Template Info": `ads_enable` + kịch bản A/B, mọi thời gian sang ms, sửa 5 key theo ghi chú sheet, Intro 4 trang với `intro1…4_setup`, `native_intro_full`, `splash_fallback`, bỏ `release_log` / `taller_banner_enabled`.
- **05/11/2026** — chuyển hẳn sang cấu hình **theo từng vị trí** như sheet, gói trong **một key `ad_config`** đúng mẫu JSON của team (ID theo ad unit qua `name1`/`id1`–`id3`), xoá bộ key cũ; thêm `native_splash`, `native_intro` theo trang, `step_count` / `wait_time` / `native_template` / `refresh_rate` / `collap_config` riêng từng vị trí.
- **06/11/2026** — tên template đúng như tài liệu (Big1…, Tiny1…; bỏ Small5–7); `native_language` chọn layout theo `enable`, `NATIVE_LANGUAGE_2` cho màn Language mở từ Cài đặt; thêm `native_language_small` (dải Tiny1 dưới tiêu đề màn Language); bỏ khỏi base các vị trí không có màn: theme / icon / widget / collection / result / wallpaper, `native_get_start`, `inter_start`, `inter_back`, `inter_welcome_back`. Thêm mua Premium thật qua Google Play Billing (xem `billing.md`).
- **06/11/2026** — đổi tên theo tab "Kịch bản A new" của sheet: Intro → Onboarding (`native_onboarding`, `native_full_onboarding`, `inter_onboarding`, `onboarding1…4_setup`, `onboarding_swipe`, `native_onboarding_use_icon_next`, ad unit `NATIVE_ONBOARDING_1…4`, `NATIVE_FULL_ONBOARDING`, `INTER_ONBOARDING`, `NATIVE_INTER_ONBOARDING`); `name1` không phân biệt hoa thường. Thêm mua Premium thật qua Google Play Billing (xem `billing.md`).
- **06/11/2026** — app chuyển sang **format mới**: 3 parameter `ad_settings`, `ad_placements` (`full` / `basic`), `ad_ids` (hoặc 1 key `ad_config` chứa cả 3); `enable` chỉ là bật/tắt, loại ở `format`, mẫu ở `template`, trang ở `pages`; bộ basic đọc từ config; thêm `native_home_feed`; `open_ad_on_clicks` đếm mọi click trên quảng cáo (bấm thẻ lại mở được quảng cáo); bấm icon khi app đang chạy nền không còn chạy lại Splash. Bỏ hẳn format `ad_config` cũ (`settings`, `name1`, `id1..3`).

- **06/11/2026** — thêm parameter **`ad_templates`** (tab `templates` của sheet): đổi màu / bo góc / viền / cỡ chữ / kích thước (CTA, ảnh, icon, đệm) của mọi native từ remote, đặt mẫu tự đặt (layout gốc `base` + style) cho cột `template`; màn debug `NativePreviewActivity` xem trước mọi mẫu. Sheet: `id_1..3`, `ads_profile` 0–3, cột `type` + dropdown `format` theo type (tab `types`).
- **09/11/2026** — thêm quảng cáo thưởng: type `reward` (user bấm xem) và `reward_inter` (rewarded interstitial, màn đếm ngược `wait_ms` có Bỏ qua), không có format; 2 placement mẫu `reward_sample`, `reward_inter_sample`; `showReward` / `preloadReward` trong lớp base; nút thử ở Debug config. Bỏ `RewardedAds` / `RewardedInterstitialAds` cũ (chỉ test ID).
- **09/11/2026** — thêm vào `ad_settings`: `splash_min_ms` / `splash_max_ms` (Splash chờ 3 s / 8 s, dùng giá trị lần fetch trước), `full_` / `basic_open_gap_ms` (App Open ↔ App Open), `full_` / `basic_open_inter_gap_ms` (App Open ↔ inter), theo bộ như khoảng cách inter; mặc định 30 s / 90 s và 15 s / 30 s (full / basic); trước đây cố định trong code (khoảng cách App Open = 0).
- **16/11/2026** — đọc thẳng **file JSON của web "Cấu hình quảng cáo"** trong một parameter `ad_config`: `ads_profile`, `settings`, `profiles.full/basic` (settings + placements, `ad_ids` trong từng placement), `templates`. Bỏ hẳn format sheet (`ad_settings`, `ad_placements`, `ad_ids`, `ad_templates`, mặc định `fullDefault`, thư mục `remote-config-v3/`). `format` → `ad_type` (`inter_then_native`, `native_as_inter`, `native_as_banner(_collap)`…), thêm `fallback`; `on_resume` quyết định khi quay lại app (bỏ `native_welcome_back`); `inter_onboarding` → `inter_after_onboarding`; thêm placement `native_language_2`; `native_onboarding` không còn `pages`; `splash_max_ms` → `splash_timeout_ms`; khoảng cách inter / App Open sang `profiles.<bộ>.settings`; `onboarding_indicator_position`. Trang native full Onboarding / Welcome Back không có ad thì tự bỏ qua / đóng.
- **23/11/2026** — mỗi bản release đọc **key riêng** `VERSION_KEY` (`ad_ver` + chữ số versionName, base: `ad_ver10`) thay cho
  `ad_config` + Conditions "App build"; cache theo tên key. Placement không có ID = không có quảng cáo (bỏ test ID dự
  phòng); bản debug luôn gửi request bằng test ID của Google thay ID đã cấu hình.
- **23/11/2026** — cache native / inter / App Open / reward theo **ID** trong `ad_ids` (dùng chung giữa placement, mỗi
  ID 1 ad chờ, hàng người chờ FIFO cho native) thay cho cache theo placement / ad unit.
- **23/11/2026** — `on_resume`: `aoa` = App Open, ad_type khác = màn Welcome Back; nút Tiếp tục chạy quảng cáo theo
  ad_type (`inter` · `native` / `native_as_inter` = native toàn màn · `inter_then_native`). Thêm placement
  **`native_welcome_back`** (native chân màn Welcome Back, `wait_ms` = đếm ngược nút Tiếp tục). Welcome Back chịu
  `open_gap_ms` / `open_inter_gap_ms` như App Open (log `AdsGap`).
- **23/11/2026** — tách `native_onboarding` thành **`native_onboarding_1`…`native_onboarding_4`** (mỗi trang nội dung
  Onboarding enable / ID / template riêng; bỏ key cũ). Onboarding: chỉ vuốt được khi nút đi tiếp của trang đã hiện,
  vuốt lùi bỏ qua trang native full không có ad; Next kiểu `text` cùng hàng với chấm. Language lần đầu: nút đi tiếp hiện
  sau khi chọn ngôn ngữ (`language_next_delay_ms`, `language_show_loading`); có `inter_language` sẵn thì dialog
  loading nhỏ ~1 s trước inter.
- **23/11/2026** — mạng lỗi / chập chờn lúc mở app: fetch Remote Config và xin đồng ý UMP **thử lại** sau 2 s / 4 s /
  8 s rồi chờ mạng có lại (Splash không bị chặn; không bỏ qua bước đồng ý, không thêm config mặc định).
  `RemoteConfig.hasAdConfig()` + `AdsAvailability`: preload và `loadNative` / `loadBanner` gọi lúc chưa có SDK / config
  được hoãn, có lúc nào thì chạy lúc đó. Log `AdsInit`.
