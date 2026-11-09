# Hướng dẫn dùng Base App

Tài liệu gốc cho người mới và agent nhận task trên base này hoặc app tạo từ base. Đọc hết file này trước khi sửa code;
các file khác trong `docs/` là chi tiết của từng phần (bảng ở cuối).

Đường dẫn code dưới đây tính từ `app/src/main/java/com/namstd/androidskillhub/` nếu không ghi khác.

---

## 1. Base này là gì

- Một app Android **chạy đủ luồng từ Splash tới Settings**, có sẵn: quảng cáo AdMob (Next-Gen SDK), Firebase Remote Config,
  Adjust, mua Premium qua Google Play Billing, chọn ngôn ngữ, Onboarding, Welcome back.
- App mới = **copy base, đổi định danh, sửa / thêm màn tính năng**. Không dựng lại luồng, không viết lại phần quảng cáo.
- Base **chỉ chứa màn chung** cho mọi app. Màn theo tính năng riêng (theme, icon, widget, wallpaper…) và placement của
  chúng thuộc app con, không thêm vào base.
- Kotlin, một module `app`, ViewBinding (không Compose), không DI framework: các phần dùng chung là `object`
  (`Ads`, `RemoteConfig`, `Premium`, `NativeAds`…).

---

## 2. Luồng màn

```
SplashActivity ──(lần đầu)──▶ LanguageActivity ──▶ OnboardingActivity ──▶ MainActivity ──▶ SettingsActivity
       └──(đã setup)──────────────────────────────────────────────────────▶ MainActivity
Quay lại app (on_resume): App Open ad, hoặc WelcomeBackActivity (format welcome_back)
Settings → Language (mở lại, không inter) · Premium · Privacy (khi UMP yêu cầu) · Reset onboarding · Debug config (debug)
```

| Màn | File | Làm gì / quảng cáo |
|---|---|---|
| App | `AndroidSkillHubApp.kt` | Khởi tạo theo thứ tự: Adjust → `AdAudience`, `AdPolicy` → locale → `Ads.adsEnabled = !premium` → `Premium.initialize` → `RemoteConfig.initialize`. Theo dõi app ra / vào nền để hiện App Open hoặc Welcome back. |
| Splash | `feature/splash/SplashActivity.kt` | Chờ `splash_min_ms`–`splash_max_ms` (mặc định 3–8 s) sau consent (UMP), chờ Remote Config. Native `native_splash`, quảng cáo cuối Splash theo `ads_splash` (AOA / inter / inter + native full). Quá `splash_max_ms` chưa có ad → bật `splash_fallback`. Đã setup → Main, chưa → Language. |
| Language | `feature/language/LanguageActivity.kt` | Native `native_language` (lần đầu) / `native_language` unit 2 (mở từ Settings), dải `native_language_small`. Lần đầu: bấm xác nhận → `inter_language` → Onboarding. |
| Onboarding | `feature/onboarding/` | 4 trang nội dung (`OnboardingPageFragment`), native theo `native_onboarding.pages`; trang native toàn màn (`OnboardingAdFragment`) chèn sau các trang trong `native_full_onboarding.pages`. Xong → `setup_completed = true` → `inter_onboarding` → Main. |
| Main (Home) | `feature/main/` | 3 tab feed (`MainFeedPageFragment`), native trong list `native_home_feed`, ô banner `banner_home`. Nút Settings → `inter_home`. **Màn tính năng của app con đặt từ đây.** |
| Settings | `feature/settings/SettingsActivity.kt` | Mục chung + banner `banner_setting`. |
| Welcome back | `feature/welcome/WelcomeBackActivity.kt` | Native `native_welcome_back` + đếm ngược `wait_ms`. |
| Premium | `feature/premium/PremiumActivity.kt` | Danh sách gói, mua, khôi phục; công tắc Premium giả (debug). |

- Cờ "đã qua lần đầu": `AppPreferences.setupCompleted` (key `setup_completed`).
- Điều hướng: `startActivity(...)` + `finish()` trong từng màn; `core/navigation/AppRouter.kt` chỉ quyết định sau Language.

---

## 3. Viết một màn mới

Mọi màn kế thừa lớp base trong `core/ui/base/`:

| Lớp | Dùng cho | Có gì |
|---|---|---|
| `BaseActivity<VB>` | Activity | Inflate binding, padding system bar, hook theo thứ tự `initConfig` → `initViews` → `initListeners` → `observeData`, `releaseResources`. Helper quảng cáo (dưới). |
| `BaseFragment<VB>` | Fragment | Hook như trên, một native / fragment, `showInterstitial` qua Activity chủ. |
| `BaseDialogFragment<VB>` | Dialog | Hook + binding, không có helper quảng cáo. |
| `BaseRecyclerAdapter` / `BaseAdRecyclerAdapter<T>` | List / list có native | Trong list có quảng cáo: tự chèn `AdFeedItem.AdSlot` vào chỗ muốn, slot tự lấy ad (xem `MainFeedAdapter`). |

Helper quảng cáo (gọi trong `initViews` / khi bấm):

```kotlin
loadNative(binding.nativeContainer, NativePlacement.X)        // container là FrameLayout; tự ẩn khi tắt / Premium
loadBanner(binding.bannerContainer, BannerPlacement.HOME)     // banner / collapsible / native trong ô banner theo config
showInterstitial(InterstitialPlacement.HOME) { openNext() }   // luôn đi qua hàm này: step, khoảng cách, inter + native…
preloadReward(RewardPlacement.X)                              // tải sẵn khi mở màn có nút "Xem quảng cáo để nhận…"
showReward(RewardPlacement.X) { result -> … }                 // REWARDED: cho thưởng · SKIPPED: không · UNAVAILABLE: app tự quyết
```

Quảng cáo thưởng (`core/ads/RewardAds.kt`) có 2 type, đều **không có format**:
- `reward`: user tự bấm xem → `showReward` hiện quảng cáo ngay.
- `reward_inter` (rewarded interstitial): tự hiện khi chuyển màn → `showReward` hiện trước một màn đếm ngược `wait_ms`
  (mặc định 5 s) có nút Bỏ qua, như chính sách AdMob yêu cầu; hết giờ mới hiện quảng cáo.
- `UNAVAILABLE` gồm cả user Premium (không có quảng cáo): app tự quyết vẫn cho thưởng hay báo thử lại.
- Base có 2 placement mẫu `reward_sample`, `reward_inter_sample` (thử ở Debug config); app con thêm placement thật:
  `AdKey` + `AdUnit` (format `REWARDED` / `REWARDED_INTERSTITIAL`) + dòng trong `RewardPlacement`.

- `onPremiumUser(premium)` chạy mỗi `onResume`: mặc định ẩn và huỷ banner / native. Màn có quảng cáo riêng (vd. list) thì
  override và gọi `super`.
- Không gọi SDK AdMob trực tiếp trong màn; không tự đọc Remote Config cho quảng cáo — luôn qua `AdPolicy`.

---

## 4. Quảng cáo

### 4.1 Khái niệm

| Khái niệm | Là gì | Ở đâu |
|---|---|---|
| **Placement** (`AdKey`) | Một vị trí quảng cáo trong app, đơn vị cấu hình (vd. `inter_home`). Có cấu hình mặc định `fullDefault`. | `core/config/PlacementConfig.kt` |
| **Ad unit** (`AdUnit`) | Một ad unit AdMob mà placement dùng; tên viết thường = key trong `ad_ids`. Chưa có ID → test ID. | `core/ads/AdUnit.kt` |
| **NativePlacement** / **InterstitialPlacement** / **BannerPlacement** | Cách màn gọi quảng cáo, mỗi cái trỏ tới AdUnit / AdKey. | `NativeAds.kt`, `InterstitialAds.kt`, `BannerAds.kt` |
| **Format** | Loại quảng cáo của placement: `aoa`, `inter`, `native_full`, `inter_then_native_full`, `banner`, `banner_collapsible`, `native`, `native_collapsible`, `welcome_back`. Type `native`, `reward`, `reward_inter` không có format. | `PlacementFormat` |
| **Template** | Mẫu vẽ native (Big1…Full5 hoặc mẫu tự đặt). | `core/ads/template/` |
| **Bộ full / basic** | Hai bộ cấu hình: full = mọi placement (ô trống = mặc định), basic = chỉ placement được liệt kê mới chạy. | `ad_placements` |
| **ads_profile** | Ai dùng bộ nào: 0 tắt hết · 1 mọi user full · 2 user marketing (Adjust) full, còn lại basic · 3 mọi user full trừ máy test ad dùng basic. Premium luôn không có quảng cáo. | `AdProfile.kt`, `AdPolicy.kt` |

Mọi code quảng cáo đọc cấu hình qua `AdPolicy.config(key)` / `AdPolicy.format(key)` — đã tính profile, Premium, debug override.

### 4.2 Thêm một placement mới

Ví dụ native ở màn mới "Detail" (app con):

1. `PlacementConfig.kt`: thêm `AdKey.NATIVE_DETAIL("native_detail", PlacementConfig(enable = true))`.
2. `AdUnit.kt`: thêm `NATIVE_DETAIL(AdKey.NATIVE_DETAIL, AdFormat.NATIVE)`.
3. `NativeAds.kt`: thêm `NativePlacement.DETAIL(AdUnit.NATIVE_DETAIL)` **và** đưa vào nhánh `config.enable` của `isEnabled`
   (không thì placement bỏ qua `enable`).
4. Màn: `loadNative(binding.nativeContainer, NativePlacement.DETAIL)`; có thể `NativeAds.preload(...)` ở màn trước.
5. Template mặc định khác Big1: thêm vào `DEFAULT_BY_KEY` trong `NativeAdTemplate.kt`.
6. Quảng cáo thưởng: thêm vào `RewardPlacement` (mục 3), type `reward` / `reward_inter` trên sheet.
7. Inter: thêm `InterstitialPlacement` + unit `INTER_*`, `NATIVE_INTER_*` + `NativePlacement.AFTER_INTER_*` (test `AdStateTest` kiểm tra cho khớp). Banner: thêm `BannerPlacement`.
8. Sheet: thêm dòng ad unit / placement ở tab cấu hình; cập nhật bảng mặc định trong `docs/remote-config-v3/README.md`.
9. Chạy unit test.

Màn Debug config tự có dòng cho placement mới.

---

## 5. Remote Config

### 5.1 Các parameter

| Parameter | Nội dung |
|---|---|
| `ad_settings` | Cấu hình chung: `ads_profile`, `splash_fallback`, thời gian chờ Splash (`splash_min_ms` / `splash_max_ms`), nút Language / Onboarding, khoảng cách inter ↔ inter, App Open ↔ App Open, App Open ↔ inter. |
| `ad_placements` | `{ "full": {...}, "basic": {...} }` — mỗi placement một object, chỉ ghi chỗ khác mặc định. |
| `ad_ids` | `{ "ad_unit": ["id_1", "id_2", "id_3"] }` — app thử lần lượt. |
| `ad_templates` | Mẫu native tự đặt / style đè lên mẫu có sẵn (mục 6). |
| `ad_config` | Tuỳ chọn: một khối chứa cả 4 phần trên. Parameter riêng nào có giá trị thì thắng phần trong `ad_config`. |

- Thiếu / hỏng → app dùng mặc định trong code; trường lạ → bỏ qua. Thời gian luôn là mili-giây.
- Lần fetch thành công gần nhất được cache (`cache_v3_*`) để lần mở sau dùng ngay.
- Chi tiết từng key, cột, mặc định: `docs/remote-config-v3/README.md`; cơ chế + bản đồ code: `docs/remote-config.md`.

### 5.2 Sheet → JSON → Firebase

1. Google Sheet (mẫu: `docs/remote-config-v3/ad_config_v3_1sheet.xlsx`) gồm:
   - **tab cấu hình** (`ad_config`, hoặc mỗi bản app một tab: `version 109`, `version 201`…): hàng setting, bảng ad unit / placement với nhóm cột FULL / BASIC;
   - `templates`: mẫu native; `types`: type → format cho dropdown; `giai_thich`: giải thích.
2. Script `docs/remote-config-v3/export_ad_config.gs` (Extensions → Apps Script). Menu **Ad config → Export JSON**
   xuất **tab cấu hình đang mở** + tab `templates` ra 4 khối JSON, có cảnh báo khi ô sai.
3. Dán từng khối vào parameter cùng tên trên Firebase (kiểu JSON) → Publish.

Sửa sheet mẫu: chỉnh dữ liệu trong script sinh sheet rồi xuất lại xlsx, giữ đúng tên tab và các ô tiêu đề `setting`,
`ad_unit`, `FULL…`, `BASIC…` (script tìm theo chúng). Sửa script export thì chạy thử lại trên xlsx mẫu (kết quả phải
khớp các file JSON mẫu trong `docs/remote-config-v3/`).

### 5.3 Cấu hình khác nhau theo bản app — QUY TẮC

- **App luôn đọc cùng một bộ key** (`ad_settings`, `ad_placements`, `ad_ids`, `ad_templates`). **Không tạo key theo version**
  (`ad_config_109`, `ad_config_201`…), **không** thêm `app_version` vào setting để chọn cấu hình.
- Tách theo bản bằng **Conditions trên Firebase**:
  1. Remote Config → Conditions → tạo điều kiện, vd. `build_201_plus`: App = app Android **AND** **App build ≥ 201**.
  2. Parameter cần khác (thường `ad_placements`) → *Add new → Conditional value* → chọn điều kiện → dán JSON xuất từ tab
     của bản mới. Giá trị mặc định giữ cho bản cũ. Parameter không đổi thì không cần điều kiện.
  3. Nhiều điều kiện: bản mới nhất đặt **trên cùng** (Firebase xét từ trên xuống).
  4. Bản cũ gần hết user → chép giá trị điều kiện vào mặc định, xoá điều kiện.
- Vì vậy **`versionCode` phải tăng mỗi bản phát hành** (vd. 2.0.1 → 201) — điều kiện "App build" so với số này.
  Màn Debug config hiện "App build …" để kiểm tra máy test đang khớp điều kiện nào.
- Trên sheet: nhân bản tab cấu hình cho bản mới (`version 201`), sửa, đứng ở tab đó rồi Export.

### 5.4 Debug override (bản debug)

Settings → **Debug config**: sửa một parameter, một setting (vd. `ads_profile`), một placement bộ full (`inter_home`) hoặc
basic (`basic.inter_home`). Giá trị placement: JSON, `true` / `false` / `off`, hoặc tên format. Override thắng Firebase,
lưu trong prefs `debug_overrides`; nút Reset xoá hết. Bản release bỏ qua override.

---

## 6. Native templates

### 6.1 Mẫu có sẵn

| Nhóm | Mẫu | Ghi chú |
|---|---|---|
| Big | Big1–Big9 | Thẻ có ảnh. Big1 ảnh / info / CTA dưới · Big2 CTA nhỏ bên phải · Big5 CTA trên, info (tiêu đề / Ad + sao / mô tả) · Big6 CTA trên, info (tiêu đề / mô tả / Ad + sao)… |
| Small | Small1–4 | Thẻ nhỏ không ảnh lớn. |
| Tiny | Tiny1, Tiny2 | Dải mỏng (dưới tiêu đề Language, dải thu gọn của Collap). |
| Collap | Collap1–3 | Native collapsible (`banner_home` format `native_collapsible`) — mỗi mẫu là **một bộ**: dạng lớn + dải khi thu gọn: Collap1 → Tiny1, Collap2 → Tiny2, Collap3 → Tiny1. |
| Full | Full1–5 | Toàn màn hình; Full1–4 có nút X, Full5 nút Next. |

- Layout: `res/layout/ad_tpl_*.xml`; mảnh dùng chung `ad_tpl_part_*` (phần info). Mọi mẫu dùng **chung view id**
  (`ad_headline`, `ad_body`, `ad_call_to_action`, `ad_media`, `ad_icon`, `ad_stars`, `ad_advertiser`; nút ẩn `ad_close`,
  `ad_collapse`, `ad_next`) nên gắn quảng cáo và áp style chạy cho mọi mẫu.
- Mẫu mặc định khi `template` trống: Big1; `native_language_small` → Tiny1; native collapsible → Collap1.
- **Không hiện giá** ("FREE") dù Figma có vẽ — Google trả trường giá, base cố ý bỏ.
- Danh sách mẫu nằm ở enum `NativeAdTemplate` (key viết thường, không phân biệt hoa thường khi đọc config).

### 6.2 `ad_templates` (tab `templates` của sheet)

```json
{
  "cta_top_purple": { "base": "Big5", "cta_bg": "#7B2FA6", "cta_corner_dp": 12 },
  "Big2": { "cta_bg": "#E53935" }
}
```

- **Tên mới** = mẫu tự đặt: layout của `base` + style. Placement chọn tên này ở cột `template`.
- **Tên mẫu có sẵn** = chỉ đè style lên mẫu đó, áp cho **mọi** placement dùng nó (kể cả placement dùng nó làm mặc định).
  **`base` của dòng mẫu có sẵn bị bỏ qua** (script không xuất) — tên mẫu luôn đúng layout của nó. Muốn layout khác:
  chọn mẫu khác, hoặc tạo mẫu mới với `base` khác.
- Mẫu tự đặt **không thừa hưởng** style của dòng mẫu `base`.
- Định nghĩa trong `ad_templates` không tự áp dụng: phải ghi tên vào `template` của placement (**không phải** `format`).
- Trường style: màu `cta_bg`, `cta_text`, `card_bg`, `card_stroke`, `title_color`, `body_color` (`#RRGGBB` / `#AARRGGBB`);
  hình `cta_corner_dp`, `card_corner_dp`, `card_stroke_dp`; kích thước `cta_height_dp` (28–80), `cta_text_sp`,
  `title_text_sp`, `body_text_sp`, `media_height_dp` (120–400, mẫu Full bỏ qua), `icon_size_dp`, `card_padding_dp`.
  Sai định dạng → bỏ qua; ngoài khoảng → kéo về giới hạn.
- Native trong feed Home dùng layout riêng (`ad_native.xml`): chỉ nhận style (màu…), không đổi mẫu.

### 6.3 Thêm mẫu mới (bố cục mới — cần ra bản app)

1. Tạo `res/layout/ad_tpl_xxx.xml`, gốc là `NativeAdView`, dùng lại `ad_tpl_part_*` nếu được, giữ đúng view id chung.
2. Thêm vào enum `NativeAdTemplate` (và `collapsed` nếu là bộ collapsible).
3. Thêm tên vào danh sách mẫu có sẵn của sheet (`TEMPLATES` trong script sinh sheet, dropdown `base`) và
   `BUILT_IN_TEMPLATES` trong `export_ad_config.gs`.
4. Xem bằng màn preview (mục 8), chạy unit test.

Bản app cũ không biết tên mẫu mới sẽ dùng mẫu mặc định — chỉ chọn mẫu mới trên remote khi đa số user đã lên bản có nó.

### 6.4 Chính sách AdMob (không được phá)

Nhãn "Ad" luôn hiện; tiêu đề bắt buộc; ảnh / video ≥ 120 dp; không sửa nội dung quảng cáo; CTA bấm được. Style và mẫu mới
không được ẩn / che các phần này. Kiểm tra bằng popup "AdMob native ad validator" (hiện trên bản debug với test ad).

---

## 7. Premium (Billing)

`core/billing/` — chi tiết trong `docs/billing.md`. Premium = tắt toàn bộ quảng cáo. Mỗi app thay product ID trong
`PremiumProducts.kt`. Bản debug có công tắc Premium giả ở màn Premium.

---

## 8. Công cụ debug

| Công cụ | Dùng | Cách mở |
|---|---|---|
| Debug config | Override Remote Config trên máy, xem App build, nút thử reward / reward_inter | Settings → Debug config (bản debug) |
| Native preview | Xem từng mẫu native + style `ad_templates`, mẫu Collap kèm dải thu gọn | `adb shell am start -n com.namstd.androidskillhub/.debug.NativePreviewActivity [--es name big5]` (đổi applicationId thì thay phần trước `/`) |
| AdbDriver | Chạm / vuốt / gõ / back qua adb trên máy chặn `adb shell input` (MIUI) | `adb shell am broadcast -a com.namstd.androidskillhub.debug.TAP --ei x 540 --ei y 1200` (cũng `SWIPE`, `TYPE`, `BACK`; tên action cố định trong `AdbDriver.kt`) |
| Premium giả | Bật / tắt Premium không cần mua | Màn Premium (bản debug) |

Công cụ chỉ dùng khi phát triển đặt trong `app/src/debug/` (không lọt vào bản release). Debug config nằm trong `main` nhưng chỉ hiện và chỉ có tác dụng ở bản debug (`BuildConfig.DEBUG`).

---

## 9. Bắt đầu một app mới từ base

1. Định danh: `applicationId` + `namespace` (`app/build.gradle.kts`), package Kotlin, `app_name`, icon.
2. Firebase: tạo app Android với applicationId mới, thay `app/google-services.json`.
3. AdMob: `admob_app_id` trong `res/values/strings.xml` (hiện là test ID của Google); ID từng ad unit đưa qua `ad_ids` (sheet).
4. Adjust: `ADJUST_APP_TOKEN`, `ADJUST_IAP_EVENT_TOKEN` (`app/build.gradle.kts`).
5. Billing: product ID trong `PremiumProducts.kt`.
6. `versionCode` / `versionName` theo quy ước mục 5.3.
7. Nội dung: ngôn ngữ (`core/language/AppLanguage.kt` + `res/values-<code>/strings.xml`), nội dung Onboarding
   (`OnboardingPageFragment`), màn tính năng từ Main.
8. Placement riêng của app: mục 4.2. Sheet: nhân bản sheet mẫu, sửa tab cấu hình.

---

## 10. Test và kiểm tra

- Unit test: `./gradlew :app:testDebugUnitTest` (`app/src/test/`: config, profile, inter plan, template / style, billing,
  router, onboarding pages…). Instrumented: `app/src/androidTest/` (lifecycle base class, luồng chính).
- Đổi gì về quảng cáo / template → chạy unit test + xem trên máy (preview, hoặc override bằng Debug config rồi mở màn).
- Dữ liệu mẫu cho test: `app/src/test/resources/ad_config_sample.json`.

---

## 11. Quy ước làm việc

- Comment code và tài liệu viết tiếng Việt, ngắn, nói "vì sao" chứ không lặp lại code.
- Đổi config / placement / template thì **cập nhật luôn** sheet mẫu, script export, file JSON mẫu và docs liên quan.
- Không commit thư mục `.idea/`. Chỉ commit / push khi được yêu cầu.
- Không đổi cài đặt hệ thống / bảo mật của máy test; cần thì nhờ người dùng.
- Quảng cáo luôn có đường thoát an toàn: lỗi / thiếu config / không có fill → app vẫn đi tiếp, không treo, không crash.

---

## 12. Còn thiếu / đã biết

- Màn native toàn màn hình (sau inter, trang native của Onboarding, Welcome back) **chưa dùng nút X / Next của mẫu Full**:
  sau inter dùng layout riêng `dialog_native_full.xml`; Onboarding / Welcome back vẽ theo `template` (mặc định Big1) với
  nút + đếm ngược của màn.
- `onboarding_next_position = right` chưa có giao diện (đang như `bottom`).
- Quảng cáo thưởng mới có 2 placement mẫu, chưa màn nào của base gọi (app con gọi `showReward`).
- `docs/ads-config.html`, `docs/remote-config-testcases.*` viết cho format cũ (`ad_config` với `name1`, `id1..3`, Intro…)
  — **đã lỗi thời**, tin file này và `docs/remote-config-v3/`.

---

## Bản đồ tài liệu

| File | Nội dung |
|---|---|
| `docs/BASE_GUIDE.md` | File này — tổng quan, quy tắc. |
| `docs/remote-config-v3/README.md` | Từng parameter, key, cột sheet, mặc định của mọi placement, `ad_templates`. |
| `docs/remote-config-v3/*.json`, `*.xlsx`, `export_ad_config.gs` | JSON mẫu, sheet mẫu, script export. |
| `docs/remote-config.md` | Cơ chế đọc Remote Config, bản đồ code, lịch sử thay đổi. |
| `docs/billing.md` | Premium / Google Play Billing. |
| `CLAUDE.md` | Tóm tắt quy tắc cho agent. |
