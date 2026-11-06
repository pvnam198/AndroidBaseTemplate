# Remote Config

Toàn bộ cấu hình quảng cáo và luồng mở app điều khiển từ xa qua **Firebase Remote Config**, theo sheet
**"Template Info" → tab "Ad Script version 100 A"** (gọi tắt là *sheet*).

**Trên Firebase chỉ có một key: `ad_config`**, giá trị là một khối JSON gồm `settings` (cấu hình chung) và mỗi vị trí
quảng cáo một object. Đúng theo mẫu JSON của team.

- **File nguồn:** [`core/config/RemoteConfig.kt`](../app/src/main/java/com/namstd/androidskillhub/core/config/RemoteConfig.kt) (đọc `ad_config`, cấu hình chung) và [`core/config/PlacementConfig.kt`](../app/src/main/java/com/namstd/androidskillhub/core/config/PlacementConfig.kt) (`AdConfigDocument`, `PlacementConfig`, `AdKey`).
- **Mẫu JSON dùng cho unit test:** [`app/src/test/resources/ad_config_sample.json`](../app/src/test/resources/ad_config_sample.json).
- **Khởi tạo:** `RemoteConfig.initialize(this)` gọi 1 lần trong `AndroidSkillHubApp.onCreate()`.
- **Tài liệu cho team sản phẩm** (lời thường, không kỹ thuật): [`ads-config.html`](ads-config.html).

---

## Cơ chế hoạt động

1. **Seed từ cache** — khi `initialize()`, `ad_config` được nạp bản của **lần fetch thành công gần nhất** (lưu trong `AppPreferences`, tên `cache_ad_config`). Nhờ vậy ngay khi app vừa mở, cấu hình đã là bản tốt gần nhất.
2. **Mặc định** — lần mở app đầu tiên (chưa có cache) hoặc `ad_config` thiếu / hỏng: cấu hình chung dùng mặc định trong bảng dưới, mỗi vị trí dùng `enable` mặc định.
3. **Fetch + activate** — `fetchAndActivate()` kéo `ad_config` mới; xong thì lưu cache cho lần mở sau.
4. **Readiness gate** — `RemoteConfig.isReady` lật `true` sau khi fetch xong. `SplashActivity` chờ cờ này (tối đa 8s) trước khi điều hướng.
5. **Tần suất fetch** — DEBUG: mỗi lần mở. RELEASE: 3600 giây.
6. **Debug override** (chỉ bản debug) — màn *Cài đặt → Debug config* có dòng `ad_config` (sửa cả khối), từng giá trị trong `settings`, và từng vị trí. Dòng nào sửa thì thắng `ad_config`; chỉ dòng đã sửa mới được lưu.

> **Đơn vị thời gian: mọi giá trị thời gian đều là mili-giây (ms).** Mẫu JSON của team đang ghi giây (`"wait_time": 5`, `"refresh_rate": 20`, `time=10`): khi nhập lên Firebase phải **nhân 1000** (`5000`, `20000`, `time=10000`). Riêng `language_delay_millis` vốn đã là ms (`800`).

---

## Cấu trúc `ad_config`

```json
{
  "settings": {
    "ads_enable": 2,
    "language_delay_millis": 800,
    "splash_fallback": "inter_language",
    "language_setup": "next=1, loading=true",
    "intro1_setup": "next=1,ds=1,delay=0,dot=1",
    "intro_swipe": true,
    "inter_delay_time": "time=10000,ignore=ads_splash|inter_language|inter_intro",
    "native_intro_use_icon_next": false,
    "on_resume": 2,
    "on_resume_id": "ca-app-pub-…"
  },
  "ads_splash": {
    "enable": 3,
    "wait_time": 5000,
    "app_open":     { "name1": "AOA_SPLASH",          "id1": "ca-app-pub-…", "id2": "…", "id3": "…" },
    "inter":        { "name1": "INTER_SPLASH",        "id1": "ca-app-pub-…", "id2": "…", "id3": "…" },
    "native_inter": { "name1": "NATIVE_INTER_SPLASH", "id1": "ca-app-pub-…", "id2": "…", "id3": "…" }
  },
  "native_splash": {
    "enable": 1,
    "native_template": "Big5",
    "native": { "name1": "NATIVE_SPLASH", "id1": "ca-app-pub-…" }
  }
}
```

- **Mỗi object con có `name1` là một ad unit** (một dòng `name1` của sheet). Tên object con (`inter`, `native_inter`, `native_language1`…) **không quan trọng**: app nhận ra ad unit qua `name1`. `name1` phải viết đúng tên app định nghĩa (xem `AdUnit`).
- **`id1`, `id2`, `id3`** của ad unit: thứ tự waterfall highfloor → medium → all_price. Không có ID nào → dùng **test ID** của Google. Kịch bản B dùng ID của kịch bản A.
- **Cột thiếu** dùng mặc định: `step_count` 1, `refresh_rate` 0, `collap_config` trống, `wait_time` 0, `native_template` trống (→ `Big1`), `enable` theo bảng dưới. Vị trí không có trong `ad_config` cũng dùng `enable` mặc định.
- **`on_resume`** nằm trong `settings` (`on_resume` = enable, `on_resume_id` = ID App Open khi quay lại app), không phải object riêng.
- `release_log`, `taller_banner_enabled` trong mẫu: app bỏ qua (đã bỏ).
- **JSON hỏng** → coi như `ad_config` rỗng (mọi thứ về mặc định).

---

## 1. Cấu hình chung (`settings`)

| Tên | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `ads_enable` | Int | `1` | `0` tắt hết · `1` kịch bản A (REMOTE) · `2` user từ marketing → A, còn lại → B (BASIC) · `3` máy nhận test ad → B, còn lại → A · giá trị khác → A. Premium luôn tắt. |
| `language_delay_millis` | ms | `800` | Chờ trước khi hiện nút xác nhận màn Language. `0` = hiện ngay. |
| `splash_fallback` | String | trống | Splash không kịp hiện quảng cáo thì inter của vị trí này (vd `inter_language`) hiện bù **1 lần** ở lần chạy kế tiếp của nó (Language: lúc bấm Apply), bất kể `enable` / `step_count` / gap của nó. Không có màn Language thì thôi. Chỉ kịch bản A. |
| `language_setup` | String | `next=2,loading=false` | `next`: `1` icon dấu check trên thanh tiêu đề, `2` nút chữ. `loading`: hiện vòng loading trong lúc chờ `language_delay_millis`. |
| `intro1_setup` … `intro4_setup` | String | intro1 `next=1,ds=1,delay=0,dot=1`; intro2-4 không có | Trang nội dung thứ N của Intro dùng `introN_setup` (không có → dùng intro1). `next`: `1` nút button, `2` chữ "Next" không nền. `ds`: `1` chấm tròn trên nút Next, `0` dưới. `delay`: ms chờ trước khi hiện Next. `dot`: `1` hiện chấm, `0` ẩn. |
| `intro_swipe` | Boolean | `true` | Cho vuốt chuyển trang ở Intro. |
| `inter_delay_time` | String | `time=0,ignore=` | `time` = khoảng cách tối thiểu (**ms**) giữa 2 interstitial; `ignore` = các vị trí được miễn (`ads_splash\|inter_language\|inter_intro`…). |
| `native_intro_use_icon_next` | Boolean | `false` | Trang native toàn màn hình trong Intro: `true` nút X, `false` nút Next. Nhận cả `1`/`0`. |
| `on_resume` | Int | `1` | Quay lại app: `0` tắt · `1` App Open · `2` màn Welcome back. |
| `on_resume_id` | String | trống | ID App Open khi quay lại app (ad unit `ON_RESUME`). Trống → test ID. |

---

## 2. Vị trí quảng cáo (các object còn lại)

### Ý nghĩa các cột

| Cột | Ý nghĩa |
|---|---|
| `enable` | `0` = tắt. Các số khác theo bảng mã của từng vị trí (bảng dưới). Một số vị trí đọc **từng chữ số** (`native_intro`, `native_intro_full`). |
| `step_count` | Inter chỉ hiện ở lần yêu cầu chia hết cho N. Bộ đếm **riêng từng vị trí**, lưu qua các lần mở app. |
| `refresh_rate` | Ms tự tải lại banner/native của ô banner. `0` = không tự tải lại. |
| `collap_config` | Native collapsible: lần bấm **nút X** nào mở quảng cáo thay vì thu gọn (`357` = lần 3, 5, 7). Bộ đếm chung trong một lần mở app. Cần Google duyệt *custom click gesture* cho tài khoản. |
| `wait_time` | Ms đếm ngược trên màn native toàn màn hình (sau inter, trang native Intro, Welcome back) trước khi cho đóng/đi tiếp. |
| `native_template` | Tên mẫu native đúng như tài liệu: Big1…Big9 (Big6 = Big5), Small1…Small4, Tiny1, Tiny2, Collap1…Collap3, Full1…Full5. Không phân biệt hoa thường. Trống hoặc không khớp → `Big1`. Bộ mẫu dùng chung, vị trí nào cũng gọi được. **Ngoại lệ:** `native_language` chọn layout bằng `enable`, bỏ qua `native_template`. Small5–Small7 team đã bỏ, không dùng. |

### Các vị trí app đang dùng

| Key | `enable` mặc định | Bảng mã `enable` | Ad unit (`name1`) | Ở đâu trong app |
|---|---|---|---|---|
| `ads_splash` | `2` | 1 AOA · 2 Inter · 3 Inter + native sau inter | `AOA_SPLASH`, `INTER_SPLASH`, `NATIVE_INTER_SPLASH` | Cuối màn Splash |
| `native_splash` | `0` | 0 tắt · 1 bật | `NATIVE_SPLASH` | Native trên màn Splash |
| `native_language` | `1` | 0 tắt · 1 layout 1 (nút Install ở dưới = Big1) · 2 layout 2 (nút Install ở trên = Big5). **Layout chỉ theo `enable`, `native_template` bị bỏ qua** (ghi cả hai vẫn theo `enable`); mã lạ → layout 1 | `NATIVE_LANGUAGE`: màn Language **lần đầu cài app**; `NATIVE_LANGUAGE_2`: màn Language **mở từ Cài đặt** | Màn Language |
| `native_language_small` | `0` | 0 tắt · ≥1 bật. Template theo `native_template`, **mặc định Tiny1** khi trống / ghi sai | `NATIVE_LANGUAGE_SMALL` | Dải nhỏ ngay dưới tiêu đề màn Language (cả lần đầu lẫn mở từ Cài đặt), ngoài native `native_language` ở chân màn |
| `inter_language` | `1` | 1 Inter · 2 chỉ native · 3 Inter + native | `INTER_LANGUAGE`, `NATIVE_INTER_LANGUAGE` | Bấm Apply ở Language |
| `native_intro` | `1234` | Mỗi chữ số = trang Intro có native (`24` = trang 2 và 4) | `NATIVE_INTRO_1` … `NATIVE_INTRO_4` | Chân các trang nội dung Intro |
| `native_intro_full` | `0` | Mỗi chữ số = trang nội dung có trang native toàn màn hình ngay sau (`123` = sau trang 1, 2, 3); không chèn sau trang cuối | `NATIVE_INTRO_FULL` | Trang native toàn màn hình trong Intro (không có chấm tròn) |
| `inter_intro` | `1` | như `inter_language` | `INTER_INTRO`, `NATIVE_INTER_INTRO` | Kết thúc Intro |
| `on_resume` *(trong `settings`)* | `1` | 0 tắt · 1 App Open · 2 màn Welcome back | `ON_RESUME` (`on_resume_id`) | Quay lại app |
| `native_welcome_back` | `1` | 0 tắt · ≥1 bật | `NATIVE_WELCOME_BACK` | Màn Welcome back |
| `banner_home` | `2` | 1 Banner · 2 Banner collapsible · 3 Native trong ô banner · 4 Native collapsible | `BANNER_HOME`, `NATIVE_HOME`, `NATIVE_COLLAP_HOME` | Chân màn chính |
| `banner_setting` | `2` | 1 Banner · 2 Banner collapsible (3/4 → banner thường) | `BANNER_SETTING` | Chân màn Cài đặt |
| `inter_home` | `0` | như `inter_language` | `INTER_HOME`, `NATIVE_INTER_HOME` | App mẫu: bấm Cài đặt ở màn chính |

Native xen trong feed màn chính không có dòng riêng trong sheet: luôn bật (kịch bản A), dùng ID của `NATIVE_HOME`.

### Các vị trí không thuộc base app

Mọi vị trí còn trong `AdKey` đều đã có chỗ dùng trong base. Các vị trí theo tính năng riêng của từng app (`native_theme_setup`, `reward_theme_setup`, `inter_icon`, `reward_icon_setup`, `native_dialog_icon`, `native_icon_setup`, `reward_widget`, `inter_collection`, `banner_result`, `native_result`, `banner_wallpaper`) và `native_get_start`, `inter_start` (base không có màn Get start), `inter_back` (sheet không ghi bấm Back ở đâu), `inter_welcome_back` (chưa rõ hiện lúc nào ở màn Welcome back) đã bỏ khỏi code. Khối của chúng trong `ad_config` (nếu có) bị bỏ qua; app nào có màn đó thì tự thêm key vào `AdKey` / `AdUnit`.

---

## 3. Kịch bản A / B (`ads_enable`)

| Kịch bản | Trong code | Cấu hình |
|---|---|---|
| A | `AdProfile.REMOTE` | Đúng như Remote Config |
| B | `AdProfile.BASIC` | `enable` cố định (`BasicAdProfile`): `ads_splash` 1 (AOA), `banner_home` 1, `banner_setting` 1, `inter_home` 1, `on_resume` 1, các vị trí khác 0; `step_count` 1; gap inter 90s. ID và các cột khác vẫn lấy từ Remote Config |
| Tắt | `AdProfile.OFF` | Mọi vị trí `enable` = 0 (`ads_enable` = 0 hoặc Premium) |

Kiểm tra người dùng (`AdAudience`): nguồn marketing qua attribution Adjust; máy nhận test ad qua headline native bắt đầu bằng "Test Ad". Kết quả lưu trong `AppPreferences`.

---

## 4. Bản đồ code

| File | Vai trò |
|---|---|
| `core/config/PlacementConfig.kt` | `AdKey` (các vị trí + `enable` mặc định), `PlacementConfig` (cấu hình một vị trí), `AdConfigDocument` (đọc cả khối `ad_config`) |
| `core/ads/AdUnit.kt` | `AdUnit` (tên đúng cột `name1`, thuộc `AdKey` nào, loại quảng cáo), `AdFormat` (test ID) |
| `core/ads/AdPolicy.kt` | `config(key)` = cấu hình vị trí cho user này (áp kịch bản A/B/tắt), `interDelay`, `splashFallback`, `nativeEnabled(placement)` |
| `core/ads/InterstitialAds.kt` | `InterstitialPlacement` (SPLASH, LANGUAGE, INTRO, HOME → key, unit inter, chỗ native sau inter); cache 1 inter mỗi unit |
| `core/ads/AppOpenAds.kt` | Cache 1 AOA mỗi unit (`AOA_SPLASH`, `ON_RESUME`) |
| `core/ads/NativeAds.kt` | `NativePlacement` → `AdUnit`; ID và `native_template` lấy từ vị trí của unit |
| `core/ads/BannerAds.kt` | `BannerPlacement` (HOME, SETTING) → key + unit banner |
| `core/ads/FullScreenAdPlan.kt` | `resolveFullScreenPlan(placement, enable)` |

Code quảng cáo **luôn** đọc qua `AdPolicy.config(key)`, không đọc thẳng `RemoteConfig.placement(key)` (trừ ID trong `AdUnit.ids()`).

---

## 5. Đã bỏ (spec cũ, trước sheet)

| Key cũ | Thay bằng |
|---|---|
| `enable` (key gộp `ads_splash=…,inter=…,banner=…,onresume=…,inter_inapp=…`) | `enable` của từng vị trí trong `ad_config` |
| Mỗi cấu hình chung là một key Remote Config riêng (`ads_enable`, `language_setup`…) | `settings` trong `ad_config` |
| `id_banner`, `id_banner_collapsible`, `id_interstitial`, `id_native`, `id_app_open` | `id1`–`id3` trong object của từng ad unit |
| `wait_time`, `step_count`, `refresh_rate`, `collap_config`, `native_template` (dùng chung) | Cột cùng tên trong từng vị trí |
| `banner_slot_auto_reload` | `refresh_rate` > 0 là tự reload |
| `post_interstitial_native_ad_count` | Cố định 1 native sau mỗi inter (`NATIVE_INTER_*`) |
| `open_open_gap_millis`, `inter_open_gap_millis` | Bỏ (sheet không có) |
| `native_intro_full` dạng chuỗi số riêng | Vị trí `native_intro_full`, `enable` theo chữ số |
| `release_log`, `taller_banner_enabled` | Bỏ (sheet không ghi rõ / không cần) |
| Banner ở Splash, Language, Intro | Bỏ (sheet không có vị trí này) |

---

## Lịch sử

- **26/10/2026** — bộ key theo spec PDF cũ (key gộp `enable`, ID theo loại quảng cáo).
- **04–05/11/2026** — theo sheet "Template Info": `ads_enable` + kịch bản A/B, mọi thời gian sang ms, sửa 5 key theo ghi chú sheet, Intro 4 trang với `intro1…4_setup`, `native_intro_full`, `splash_fallback`, bỏ `release_log` / `taller_banner_enabled`.
- **05/11/2026** — chuyển hẳn sang cấu hình **theo từng vị trí** như sheet, gói trong **một key `ad_config`** đúng mẫu JSON của team (ID theo ad unit qua `name1`/`id1`–`id3`), xoá bộ key cũ; thêm `native_splash`, `native_intro` theo trang, `step_count` / `wait_time` / `native_template` / `refresh_rate` / `collap_config` riêng từng vị trí.
- **06/11/2026** — tên template đúng như tài liệu (Big1…, Tiny1…; bỏ Small5–7); `native_language` chọn layout theo `enable`, `NATIVE_LANGUAGE_2` cho màn Language mở từ Cài đặt; thêm `native_language_small` (dải Tiny1 dưới tiêu đề màn Language); bỏ khỏi base các vị trí không có màn: theme / icon / widget / collection / result / wallpaper, `native_get_start`, `inter_start`, `inter_back`, `inter_welcome_back`.
