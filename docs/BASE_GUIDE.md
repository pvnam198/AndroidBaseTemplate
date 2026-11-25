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
Quay lại app (on_resume theo ad_type): aoa → App Open ad · ad_type khác → WelcomeBackActivity (Tiếp tục → quảng cáo theo ad_type)
Settings → Language (mở lại, không inter) · Premium · Privacy (khi UMP yêu cầu) · Reset onboarding · Debug config (debug)
```

| Màn | File | Làm gì / quảng cáo |
|---|---|---|
| App | `AndroidSkillHubApp.kt` | Khởi tạo theo thứ tự: Adjust → `AdAudience`, `AdPolicy` → locale → `Ads.adsEnabled = !premium` → `Premium.initialize` → `RemoteConfig.initialize`. Theo dõi app ra / vào nền để chạy `on_resume` (App Open / Welcome back), xuống nền thì tải sẵn quảng cáo cho lần quay lại. |
| Splash | `feature/splash/SplashActivity.kt` | Chờ `splash_min_ms`–`splash_timeout_ms` (mặc định 3–8 s) sau consent (UMP), chờ Remote Config. Mạng lỗi thì không chặn: consent / Remote Config thử lại ngầm (mục 4.4). Native `native_splash`, quảng cáo cuối Splash theo `ads_splash` (AOA / inter / inter + native full). Quá `splash_timeout_ms` chưa có ad → bật `splash_fallback`. Đã setup → Main, chưa → Language. |
| Language | `feature/language/LanguageActivity.kt` | Native `native_language` (lần đầu) / `native_language_2` (mở từ Settings), dải `native_language_small`. Lần đầu: nút đi tiếp (icon ✓ / nút chữ theo `language_next_style`) ẩn tới khi user chọn ngôn ngữ, rồi hiện sau `language_next_delay_ms` (vòng loading nếu `language_show_loading`; 0 = hiện ngay). Bấm → `inter_language` đã sẵn thì dialog loading nhỏ (`dialog_simple_loading.xml`) ~1 s rồi mới hiện inter → đổi locale → Onboarding. |
| Onboarding | `feature/onboarding/` | 4 trang nội dung (`OnboardingPageFragment`), chân trang N có native `native_onboarding_N` (1–4, mỗi trang enable / ID / template riêng); trang native toàn màn (`OnboardingAdFragment`) chèn sau các trang trong `native_full_onboarding.pages`, không có ad thì tự bỏ qua (cả khi vuốt lùi). Vuốt (`onboarding_swipe`) chỉ được khi nút đi tiếp của trang đang xem đã hiện (hết `next_delay_ms`, trang native full đã hiện X / Next). Next kiểu `text`: chấm trái, Next phải trên cùng một hàng. Xong → `setup_completed = true` → `inter_after_onboarding` → Main. |
| Main (Home) | `feature/main/` | 3 tab feed (`MainFeedPageFragment`), native trong list `native_home_feed`, ô banner `banner_home`. Nút Settings → `inter_home`. **Màn tính năng của app con đặt từ đây.** |
| Settings | `feature/settings/SettingsActivity.kt` | Mục chung + banner `banner_setting`. |
| Welcome back | `feature/welcome/WelcomeBackActivity.kt` | `on_resume` ad_type khác `aoa`. Native chân màn `native_welcome_back` (tối đa 60% chiều cao, `AdFitFrame` không để native đẩy nút ra khỏi màn). Nút Tiếp tục đếm ngược `wait_ms` của `native_welcome_back` rồi bấm được → quảng cáo theo `ad_type` của `on_resume`: `inter` · `native` / `native_as_inter` (native toàn màn, `ad_ids.native` của `on_resume`) · `inter_then_native` → đóng màn. Không có native lẫn quảng cáo cho Tiếp tục thì không mở. Chịu `open_gap_ms` / `open_inter_gap_ms` như App Open. |
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
                                                              // ignoreGap = true: bỏ qua inter_gap_ms / open_inter_gap_ms
preloadReward(RewardPlacement.X)                              // tải sẵn khi mở màn có nút "Xem quảng cáo để nhận…"
showReward(RewardPlacement.X) { result -> … }                 // REWARDED: cho thưởng · SKIPPED: không · UNAVAILABLE: app tự quyết
```

Quảng cáo thưởng (`core/ads/RewardAds.kt`) có 2 type (`type` / `ad_type` cùng tên):
- `reward`: user tự bấm xem → `showReward` hiện quảng cáo ngay.
- `reward_inter` (rewarded interstitial): tự hiện khi chuyển màn → `showReward` hiện trước một màn đếm ngược `wait_ms`
  (mặc định 5 s) có nút Bỏ qua, như chính sách AdMob yêu cầu; hết giờ mới hiện quảng cáo.
- `UNAVAILABLE` gồm cả user Premium (không có quảng cáo): app tự quyết vẫn cho thưởng hay báo thử lại.
- Base có 2 placement mẫu `reward_sample`, `reward_inter_sample` (thử ở Debug config); app con thêm placement thật:
  `AdKey` + `AdUnit` (`AdFormat.REWARDED` / `REWARDED_INTERSTITIAL`) + dòng trong `RewardPlacement`.

- `onPremiumUser(premium)` chạy mỗi `onResume`: mặc định ẩn và huỷ banner / native. Màn có quảng cáo riêng (vd. list) thì
  override và gọi `super`.
- Không gọi SDK AdMob trực tiếp trong màn; không tự đọc Remote Config cho quảng cáo — luôn qua `AdPolicy`.

---

## 4. Quảng cáo

### 4.1 Khái niệm

| Khái niệm | Là gì | Ở đâu |
|---|---|---|
| **Placement** (`AdKey`) | Một vị trí quảng cáo trong app, đơn vị cấu hình (vd. `inter_home`). Không có trong bộ = tắt (không có mặc định trong app). | `core/config/PlacementConfig.kt` |
| **Ad unit** (`AdUnit`) | Một loại ID trong `ad_ids` của placement (vd. `INTER_SPLASH` = `ads_splash.ad_ids.inter`). Không có ID → không có quảng cáo (app không giữ ID dự phòng). Bản debug gửi request bằng test ID của Google thay mọi ID đã cấu hình (`ids()` / `requestId()`); release không bao giờ dùng test ID. | `core/ads/AdUnit.kt` |
| **NativePlacement** / **InterstitialPlacement** / **BannerPlacement** | Cách màn gọi quảng cáo, mỗi cái trỏ tới AdUnit / AdKey. | `NativeAds.kt`, `InterstitialAds.kt`, `BannerAds.kt` |
| **Ad type** | Loại quảng cáo của placement (`ad_type`): `aoa`, `inter`, `inter_then_native`, `native_as_inter`, `native`, `banner`, `banner_collapsible`, `native_as_banner`, `native_as_banner_collap`, `reward`, `reward_inter`. | `AdType` |
| **Template** | Mẫu vẽ native (Big1…Full5 hoặc mẫu tự đặt). | `core/ads/template/` |
| **Bộ full / basic** | Hai bộ cấu hình `profiles.full` / `profiles.basic`, mỗi bộ có `settings` + `placements` đầy đủ (web đã gộp sẵn). | `AdConfigDocument` |
| **ads_profile** | Ai dùng bộ nào: 0 tắt hết · 1 mọi user full · 2 user marketing (Adjust) full, còn lại basic · 3 mọi user full trừ máy test ad dùng basic (nhận diện bằng native `native_check`, xem `docs/remote-config.md`). Premium luôn không có quảng cáo. | `AdProfile.kt`, `AdPolicy.kt` |

Mọi code quảng cáo đọc cấu hình qua `AdPolicy.config(key)` / `AdPolicy.adType(key)` — đã tính profile, Premium, debug override.

### 4.2 Thêm một placement mới

Ví dụ native ở màn mới "Detail" (app con):

1. `PlacementConfig.kt`: thêm `AdKey.NATIVE_DETAIL("native_detail")`.
2. `AdUnit.kt`: thêm `NATIVE_DETAIL(AdKey.NATIVE_DETAIL, AdFormat.NATIVE)`.
3. `NativeAds.kt`: thêm `NativePlacement.DETAIL(AdUnit.NATIVE_DETAIL)` **và** đưa vào nhánh `config.enable` của `isEnabled`
   (không thì placement bỏ qua `enable`).
4. Màn: `loadNative(binding.nativeContainer, NativePlacement.DETAIL)`; có thể `NativeAds.preload(...)` ở màn trước.
5. Template mặc định khác Big1: thêm vào `DEFAULT_BY_KEY` trong `NativeAdTemplate.kt`.
6. Quảng cáo thưởng: thêm vào `RewardPlacement` (mục 3), type `reward` / `reward_inter` trên web.
7. Inter: thêm `InterstitialPlacement` + unit `INTER_*`, `NATIVE_INTER_*` + `NativePlacement.AFTER_INTER_*` (test `AdStateTest` kiểm tra cho khớp). Banner: thêm `BannerPlacement`.
8. Web cấu hình: thêm placement cùng tên ở cả 2 bộ; thêm vào `docs/ad_config_sample.json` (+ bản trong `app/src/test/resources/`) và danh sách placement trong `docs/remote-config.md`.
9. Chạy unit test.

Màn Debug config tự có dòng cho placement mới.

### 4.3 Cache quảng cáo

Native, inter, App Open và reward cache theo **ID thật trong `ad_ids`** (`core/ads/AdIdPool.kt`; full-screen qua
`FullScreenAdCache.kt`), dùng chung giữa các placement: ad tải cho chỗ này mà chưa dùng thì chỗ khác có cùng ID lấy được;
placement chỉ quyết định bật/tắt, template và thứ tự ID (waterfall).

- Mỗi ID giữ tối đa **1 ad chờ**; hai ad cùng ID thì giữ ad mới hơn (sống lâu hơn), huỷ ad cũ.
- Lấy ad: ID đầu tiên trong chuỗi đang có ad chờ (có sẵn ID sau thì dùng luôn, không chờ ID đầu).
- Preload không tải thêm khi đã có ad chờ hoặc đang có lượt tải chạm tới một ID của chuỗi — đợi lượt đó.
- Native có **hàng người chờ** (màn đang shimmer), FIFO: ad tải xong của ID X giao cho người chờ cũ nhất có X trong
  chuỗi (kể cả placement khác); không ai chờ thì cất vào pool. Mỗi người chờ có lượt tải riêng nên hai màn cùng ID hiện
  cùng lúc không giành ad của nhau.
- Hạn ad: native / inter / reward **1 giờ**, App Open **4 giờ** (quá hạn thì huỷ, tải lại).
- Banner không cache (gắn với từng ô).
- Bản debug vẫn khoá cache theo ID thật, chỉ gửi request bằng test ID của Google (`AdUnit.requestId`).
- Log debug: `adb logcat -s AdsNative AdsInter AdsAppOpen AdsReward AdsGap AdsInit` (`AdsGap`: quảng cáo quay lại app
  hiện / bị chặn vì `open_gap_ms` / `open_inter_gap_ms`; `AdsInit`: các mốc khởi động quảng cáo, mục 4.4).

### 4.4 Mạng lỗi / chập chờn lúc mở app

Hai bước cần mạng lúc mở app được **thử lại ngầm**, Splash không bị chặn chờ:

- **Xin đồng ý (UMP)** — `Ads.initialize`. Lỗi mà máy đã có trạng thái đồng ý từ trước (`canRequestAds()` = true) thì
  khởi tạo SDK như bình thường. Chưa có (cài mới / xoá dữ liệu): lần lỗi đầu báo `false` cho Splash đi tiếp (không
  quảng cáo), rồi thử lại sau **2 s / 4 s / 8 s**; hết lượt thì chờ **mạng có lại** (`NetworkRegain`: mạng mặc định
  được hệ thống xác nhận ra internet) và chạy lại lịch từ đầu. Lần thử lại cần Activity: dùng màn đang hiện, không có
  thì chờ màn kế tiếp `onResume`. Không bao giờ bỏ qua bước đồng ý (user EU vẫn thấy form); dừng khi SDK đã sẵn sàng,
  quảng cáo bị tắt (Premium) hoặc user từ chối.
- **Remote Config** — fetch lỗi thì `isReady` vẫn lật `true` ngay (Splash dùng cache / mặc định), rồi thử lại cùng lịch
  2 s / 4 s / 8 s + chờ mạng có lại. Fetch được mà file cấu hình đổi → báo `AdsAvailability`. **Không có config mặc
  định nhúng trong app**: lần cài đầu chỉ có quảng cáo khi fetch được.

`core/ads/AdsAvailability.kt`: quảng cáo **dùng được** = SDK sẵn sàng **và** `RemoteConfig.hasAdConfig()` (có file cấu
hình, kể cả cache lần trước hoặc override ở Debug config). Khi chưa dùng được:

- `NativeAds.preload` / `InterstitialAds.preload` / `RewardAds.preload` được hoãn (gộp theo placement, chạy một lần khi
  dùng được).
- `loadNative` / `loadBanner` của `BaseActivity` / `BaseFragment` (user không Premium): container bị ẩn, `onUnavailable`
  chạy ngay (màn đi tiếp như không có ad), lời gọi được nhớ theo container; khi dùng được mà màn đang ở trạng thái
  STARTED thì gọi lại đúng lời gọi đó (trả container về độ hiện cũ) — màn đang mở có quảng cáo mà không phải mở lại.
  Fragment bỏ các lời gọi chờ khi view bị huỷ.
- `adb logcat -s AdsInit`: `ump lỗi lần N…`, `rc fetch lỗi lần N…`, `…: chờ mạng có lại`, `sdk sẵn sàng`,
  `quảng cáo dùng được`.

---

## 5. Remote Config

### 5.1 Parameter

**Một parameter theo bản app** — `VERSION_KEY` trong `core/config/RemoteConfig.kt`, hiện là `ad_ver10` (versionName
1.0) — kiểu JSON = nguyên file xuất từ web "Cấu hình quảng cáo" (mục 5.3):

| Phần | Nội dung |
|---|---|
| `ads_profile` | Ai dùng bộ nào (mục 4.1). |
| `settings` | Dùng chung: `splash_timeout_ms`, `splash_fallback`, nút Language / Onboarding (`onboarding_pages` đè từng trang). |
| `profiles.full` / `profiles.basic` | `settings` (`inter_gap_ms`, `inter_gap_ignore`, `open_gap_ms`, `open_inter_gap_ms`) + `placements` (mỗi placement có `ad_type`, `ad_ids`, `template`…). |
| `templates` | Mẫu native tự đặt / style đè lên mẫu có sẵn (mục 6). |

- JSON hỏng / thiếu → mọi placement tắt, setting dùng mặc định trong code; trường lạ → bỏ qua. Thời gian luôn là mili-giây.
- Lần fetch thành công gần nhất được cache theo tên key (`cache_v4_ad_ver10`) để lần mở sau dùng ngay; bản mới đổi key
  nên không dùng cache của bản trước.
- Chi tiết từng key, mặc định, bản đồ code: `docs/remote-config.md`. Mẫu: `docs/ad_config_sample.json`.

### 5.2 Web → Firebase

1. Trên web "Cấu hình quảng cáo" chỉnh cấu hình của bản app, tải file JSON.
2. Dán nguyên file vào parameter của bản đó (vd. `ad_ver10`) trên Firebase (kiểu JSON) → Publish.

### 5.3 Cấu hình khác nhau theo bản app — QUY TẮC

- **Mỗi bản release đọc key riêng**: `VERSION_KEY` trong `core/config/RemoteConfig.kt` = `ad_ver` + các chữ số của
  versionName (1.0 → `ad_ver10`, 1.0.0 → `ad_ver100`, 1.1.2 → `ad_ver112`). Là hằng chuỗi dev tự đổi.
- **Mỗi lần ra bản**: đổi `versionName` (+ tăng `versionCode`) → đổi `VERSION_KEY` → trên Firebase thêm parameter
  `ad_ver…` mới với file JSON của bản đó **trước khi** phát hành. Key của bản cũ giữ nguyên cho user chưa cập nhật.
- Không có key của bản trên Firebase = không có cấu hình (mọi placement tắt) — quên thêm key là bản mới không có quảng cáo.
- Cache lần fetch trước lưu theo tên key, nên bản mới không đọc nhầm cấu hình của bản cũ.

### 5.4 Debug override (bản debug)

Settings → **Debug config**: sửa cả file (dòng `ad_ver…`), `ads_profile`, một key trong `settings` (vd. `splash_timeout_ms`),
`full.settings` / `basic.settings` (JSON), một placement bộ full (`inter_home`) hoặc basic (`basic.inter_home`). Giá trị
placement: JSON object (thay cả placement), `true` / `false` / `off`, hoặc tên ad_type. Override thắng Firebase, lưu
trong prefs `debug_overrides`; nút Reset xoá hết. Bản release bỏ qua override.

---

## 6. Native templates

### 6.1 Mẫu có sẵn

| Nhóm | Mẫu | Ghi chú |
|---|---|---|
| Big | Big1–Big9 | Thẻ có ảnh. Big1 ảnh / info / CTA dưới · Big2 CTA nhỏ bên phải · Big5 CTA trên, info (tiêu đề / Ad + sao / mô tả) · Big6 CTA trên, info (tiêu đề / mô tả / Ad + sao)… |
| Small | Small1–4 | Thẻ nhỏ không ảnh lớn. |
| Tiny | Tiny1, Tiny2 | Dải mỏng (dưới tiêu đề Language, dải thu gọn của Collap). |
| Collap | Collap1–3 | Native collapsible (`banner_home` ad_type `native_as_banner_collap`) — mỗi mẫu là **một bộ**: dạng lớn + dải khi thu gọn: Collap1 → Tiny1, Collap2 → Tiny2, Collap3 → Tiny1. |
| Full | Full1–5 | Toàn màn hình; Full1–4 có nút X, Full5 nút Next. |

- Layout: `res/layout/ad_tpl_*.xml`; mảnh dùng chung `ad_tpl_part_*` (phần info). Mọi mẫu dùng **chung view id**
  (`ad_headline`, `ad_body`, `ad_call_to_action`, `ad_media`, `ad_icon`, `ad_stars`, `ad_advertiser`; nút ẩn `ad_close`,
  `ad_collapse`, `ad_next`) nên gắn quảng cáo và áp style chạy cho mọi mẫu.
- Mẫu mặc định khi `template` trống: Big1; `native_language_small` → Tiny1; native collapsible → Collap1.
- **Không hiện giá** ("FREE") dù Figma có vẽ — Google trả trường giá, base cố ý bỏ.
- Danh sách mẫu nằm ở enum `NativeAdTemplate` (key viết thường, không phân biệt hoa thường khi đọc config).
- Shimmer lúc load dựng từ chính mẫu + style sẽ hiện (`template/NativeSkeleton.kt`, chữ / ảnh thành khối xám) nên ad
  hiện ra không nhảy kích thước. Banner / dải collapsible vẫn dùng `ad_banner_shimmer.xml`.
- Biểu tượng AdChoices ("i") luôn ở **góc trên trái** (`setAdChoicesPlacement(TOP_LEFT)`), góc trên phải để cho nút X /
  Next / thu gọn; `template/AdChoicesCorner.kt` chừa góc cho icon ở mẫu card có padding (áp cả cho shimmer).
- Ô native **rộng hết màn / dialog** (không lề / padding hai bên), cách nội dung trên / dưới `@dimen/ad_slot_margin`
  (12dp). Native trong khung có padding `ad_slot_margin` thì dùng `ad_slot_bleed` + `clipToPadding="false"` để tràn sát mép.
- Native ở chân màn mà nút phía trên phải luôn thấy (vd. Welcome Back): dùng `template/AdFitFrame.kt` làm container
  (`maxHeightPx`). Quảng cáo cao hơn chỗ thì chỉ thu nhỏ media (`AdMediaFrame.heightCap`), không dưới 120dp (chính sách
  AdMob); tiêu đề / CTA giữ nguyên.
- **Native toàn màn** sau inter (`inter_then_native` / `native_as_inter`, `feature/ads/PostInterstitialNativeAdDialogFragment.kt`
  + `dialog_native_full.xml`) và trang native của Onboarding (`OnboardingAdFragment`) vẽ theo `template` của placement chứa
  native (vd. `ads_splash` → Full1) vào `native_container`, phủ cả status bar. Góc trên phải (cách mép `ad_card_padding`,
  nút tròn 32dp `bg_ad_action`) hiện **số giây đếm ngược** theo `wait_ms` của placement đó (5000 → 5,4,3,2,1;
  `core/ads/AdCountdown.kt`), hết giờ mới hiện X / Next; `wait_ms` = 0 → nút hiện ngay.

### 6.2 `templates` (gốc file cấu hình)

```json
{
  "cta_top_purple": { "base": "Big5", "cta_bg": "#7B2FA6", "cta_corner_dp": 12 },
  "Big2": { "cta_bg": "#E53935" }
}
```

- **Tên mới** = mẫu tự đặt: layout của `base` + style. Placement chọn tên này ở trường `template`.
- **Tên mẫu có sẵn** = chỉ đè style lên mẫu đó, áp cho **mọi** placement dùng nó (kể cả placement dùng nó làm mặc định).
  **`base` của dòng mẫu có sẵn bị bỏ qua** — tên mẫu luôn đúng layout của nó. Muốn layout khác:
  chọn mẫu khác, hoặc tạo mẫu mới với `base` khác.
- Mẫu tự đặt **không thừa hưởng** style của dòng mẫu `base`.
- Định nghĩa trong `templates` không tự áp dụng: phải ghi tên vào `template` của placement (**không phải** `ad_type`).
- Trường style: màu `cta_bg`, `cta_text`, `card_bg`, `card_stroke`, `title_color`, `body_color` (`#RRGGBB` / `#AARRGGBB`);
  hình `cta_corner_dp`, `card_corner_dp`, `card_stroke_dp`; kích thước `cta_height_dp` (28–80), `cta_text_sp`,
  `title_text_sp`, `body_text_sp`, `media_height_dp` (120–400, mẫu Full bỏ qua), `icon_size_dp`, `card_padding_dp`.
  Sai định dạng → bỏ qua; ngoài khoảng → kéo về giới hạn.
- Native trong feed Home dùng layout riêng (`ad_native.xml`): chỉ nhận style (màu…), không đổi mẫu.

### 6.3 Thêm mẫu mới (bố cục mới — cần ra bản app)

1. Tạo `res/layout/ad_tpl_xxx.xml`, gốc là `NativeAdView`, dùng lại `ad_tpl_part_*` nếu được, giữ đúng view id chung.
2. Thêm vào enum `NativeAdTemplate` (và `collapsed` nếu là bộ collapsible).
3. Báo team web thêm tên mẫu vào danh sách mẫu có sẵn.
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
| Debug config | Override Remote Config trên máy, xem version, nút thử reward / reward_inter | Settings → Debug config (bản debug) |
| Native preview | Xem từng mẫu native + style `templates`, mẫu Collap kèm dải thu gọn | `adb shell am start -n com.namstd.androidskillhub/.debug.NativePreviewActivity [--es name big5]` (đổi applicationId thì thay phần trước `/`) |
| AdbDriver | Chạm / vuốt / gõ / back qua adb trên máy chặn `adb shell input` (MIUI) | `adb shell am broadcast -a com.namstd.androidskillhub.debug.TAP --ei x 540 --ei y 1200` (cũng `SWIPE`, `TYPE`, `BACK`; tên action cố định trong `AdbDriver.kt`) |
| Premium giả | Bật / tắt Premium không cần mua | Màn Premium (bản debug) |

Công cụ chỉ dùng khi phát triển đặt trong `app/src/debug/` (không lọt vào bản release). Debug config nằm trong `main` nhưng chỉ hiện và chỉ có tác dụng ở bản debug (`BuildConfig.DEBUG`).

---

## 9. Bắt đầu một app mới từ base

1. Định danh: `applicationId` + `namespace` (`app/build.gradle.kts`), package Kotlin, `app_name`, icon.
2. Firebase: tạo app Android với applicationId mới, thay `app/google-services.json`.
3. AdMob: `admob_app_id` trong `res/values/strings.xml` (hiện là test ID của Google); ID từng ad unit đưa qua `ad_ids` của placement (web cấu hình).
4. Adjust: `ADJUST_APP_TOKEN`, `ADJUST_IAP_EVENT_TOKEN` (`app/build.gradle.kts`).
5. Billing: product ID trong `PremiumProducts.kt`.
6. `versionCode` / `versionName` + `VERSION_KEY` (`RemoteConfig.kt`) theo quy ước mục 5.3.
7. Nội dung: ngôn ngữ (`core/language/AppLanguage.kt` + `res/values-<code>/strings.xml`), nội dung Onboarding
   (`OnboardingPageFragment`), màn tính năng từ Main.
8. Placement riêng của app: mục 4.2. Web cấu hình: tạo cấu hình cho app mới.

---

## 10. Test và kiểm tra

- Unit test: `./gradlew :app:testDebugUnitTest` (`app/src/test/`: config, profile, inter plan, template / style, billing,
  router, onboarding pages…). Instrumented: `app/src/androidTest/` (lifecycle base class, luồng chính).
- Đổi gì về quảng cáo / template → chạy unit test + xem trên máy (preview, hoặc override bằng Debug config rồi mở màn).
- Dữ liệu mẫu cho test: `app/src/test/resources/ad_config_sample.json`.

---

## 11. Quy ước làm việc

- Comment code và tài liệu viết tiếng Việt, ngắn, nói "vì sao" chứ không lặp lại code.
- Đổi config / placement / template thì **cập nhật luôn** file JSON mẫu và docs liên quan.
- Không commit thư mục `.idea/`. Chỉ commit / push khi được yêu cầu.
- Không đổi cài đặt hệ thống / bảo mật của máy test; cần thì nhờ người dùng.
- Quảng cáo luôn có đường thoát an toàn: lỗi / thiếu config / không có fill → app vẫn đi tiếp, không treo, không crash.

---

## 12. Còn thiếu / đã biết

- `custom_ui` của `on_resume` chưa có tác dụng (base chỉ có 1 màn Welcome back, cả 2 giá trị đều dùng nó).
- Quảng cáo thưởng mới có 2 placement mẫu, chưa màn nào của base gọi (app con gọi `showReward`).
- `docs/ads-config.html`, `docs/remote-config-testcases.*` viết cho format cũ (`ad_config` với `name1`, `id1..3`, Intro…)
  — **đã lỗi thời**, tin file này và `docs/remote-config.md`.

---

## Bản đồ tài liệu

| File | Nội dung |
|---|---|
| `docs/BASE_GUIDE.md` | File này — tổng quan, quy tắc. |
| `docs/remote-config.md` | Format file cấu hình (mọi key, mặc định), cơ chế đọc, bản đồ code, lịch sử thay đổi. |
| `docs/ad_config_sample.json` | File cấu hình mẫu (test ID của Google). |
| `docs/billing.md` | Premium / Google Play Billing. |
| `CLAUDE.md` | Tóm tắt quy tắc cho agent. |
