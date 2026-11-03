# Remote Config

Toàn bộ feature flag điều khiển từ xa qua **Firebase Remote Config**.

- **File nguồn (single source of truth):** [`app/src/main/java/com/namstd/androidskillhub/core/config/RemoteConfig.kt`](../app/src/main/java/com/namstd/androidskillhub/core/config/RemoteConfig.kt)
- **Khởi tạo:** `RemoteConfig.initialize(this)` gọi 1 lần trong `AndroidSkillHubApp.onCreate()`.
- **Không có file defaults XML** — defaults được seed bằng code từ cache `AppPreferences`.

---

## Cơ chế hoạt động

1. **Seed defaults từ cache** — khi `initialize()`, mỗi key được nạp giá trị **lần fetch thành công gần nhất** (lưu trong `AppPreferences`, file `app_preferences`) qua `setDefaultsAsync`. Nhờ vậy ngay khi app vừa mở (fetch còn đang chạy), đọc flag đã trả về giá trị tốt gần nhất thay vì fallback cứng.
2. **Fallback cứng** — tham số thứ 2 của `prefs.getCachedBoolean(key, <fallback>)` / `getCachedLong(...)` là giá trị dùng ở **lần mở app đầu tiên** khi chưa có gì trong cache.
3. **Fetch + activate** — `fetchAndActivate()` kéo giá trị mới từ Firebase. Khi xong, `persistToCache()` ghi tất cả giá trị hiện tại trở lại `AppPreferences` để làm seed cho lần mở sau.
4. **Readiness gate** — `RemoteConfig.isReady: StateFlow<Boolean>` lật `true` sau khi fetch hoàn tất. `SplashActivity` chờ cờ này trước khi điều hướng, nên các màn sau (Language, Onboarding, Main…) đọc flag là đã settle.
5. **Tần suất fetch** — DEBUG: `minimumFetchIntervalInSeconds = 0` (fetch mỗi lần mở, tiện test). RELEASE: `3600` giây.

### Thêm một key mới cần 4 touch-point trong `RemoteConfig.kt`

1. Khai báo `private const val KEY_... = "..."` (khối cuối file).
2. Thêm dòng seed trong `setDefaultsAsync(mapOf(...))` với fallback phù hợp.
3. Thêm dòng ghi cache trong `persistToCache()`.
4. Thêm getter `val ...` đọc `remoteConfig.getBoolean/getLong(KEY_...)`.

`AppPreferences` **không cần sửa** — dùng chung `getCachedBoolean/putCachedBoolean` và `getCachedLong/putCachedLong`.

---

## Danh sách key

### Quảng cáo — bật/tắt & chọn loại theo vị trí (`enable`)

Key `enable` là công tắc xương sống: mỗi vị trí 1 số = bật/tắt + chọn loại. Dạng `ads_splash=2,inter=1,banner=2,onresume=1`; `0` = tắt vị trí đó. Parse ra `RemoteConfig.enable` (`EnableConfig`). **Thay cho 2 key cũ `use_custom_interstitial` & `use_native_collapsible`.**

| Vị trí | Giá trị | Ý nghĩa | Nơi áp dụng |
|---|---|---|---|
| `ads_splash` | 0 off · 1 AOA · 2 Inter · 3 Inter+Native | Quảng cáo lúc mở app (màn Splash) | `SplashActivity` + `resolveFullScreenPlan` |
| `inter` | 0 off · 1 Inter · 2 Native · 3 Inter+Native | Interstitial xen (Language/Onboarding) | `BaseActivity.showInterstitial` + `resolveFullScreenPlan` |
| `banner` | 0 off · 1 Banner · 2 BannerCollap · 3 Native · 4 NativeCollap | Loại ô banner (mọi màn, hợp nhất) | `BannerAds.load` |
| `onresume` | 0 off · 1 app-open · 2 welcome back | Khi quay lại app | `AndroidSkillHubApp.onStart` |

**Default `ads_splash=2,inter=1,banner=2,onresume=1`** giữ hành vi cũ (Inter ở splash, Inter xen, banner collapsible, app-open khi quay lại). Lưu ý: hợp nhất banner → Splash/Onboarding đổi từ banner thường → collapsible theo giá trị chung. Loading native/welcome-back dùng `wait_time` làm đếm ngược.

### Quảng cáo — layout native

| Key | Kiểu | Fallback | Ý nghĩa |
|---|---|---|---|
| `native_template` | String | `native media` | Layout của native card render qua `NativeAds.renderDefaultCard`: `native media` (có ảnh), `native no media` (chỉ chữ), `native banner` (dải nhỏ), `native fullscreen` (card lớn), hoặc một mẫu Figma "Wavez - ADs / Mẫu v2": `big1`…`big9` (`big6` = `big5`), `small1`…`small4`, `tiny1`, `tiny2`, `collap1`…`collap3` (bản mở rộng; bản thu gọn là `tiny1`/`tiny2`), `full1`…`full5`, `lang1_a`, `lang1_b`, `lang2_a`, `lang2_b`. Giá trị lạ → `native media`. Áp cho các placement render mặc định (Language, Onboarding, Welcome back, banner-native). **Không** đổi feed / post-interstitial / native collapsible (layout cố định riêng). Parse ra `RemoteConfig.nativeTemplate` (`NativeAdTemplate`). Màn nào muốn dùng một mẫu cố định thì gọi `NativeAds.renderTemplate(activity, ad, NativeAdTemplate.X)`. |
| `collap_config` | Long → Int | `1` | Click-bait native collapsible (`NativeCollapseAd`): chỉ **tap thứ N** mới bắn click thật (mở trang QC); tap trước bị nuốt. `1` = click đi thẳng như thường. Dùng SDK `enableCustomClickGesture`/`recordCustomClickGesture`. Clamp tối thiểu 1. |

### Quảng cáo — banner slot auto reload

| Key | Kiểu | Fallback | Ý nghĩa |
|---|---|---|---|
| `banner_slot_auto_reload` | Boolean | `false` | Bật timer tự reload banner slot (ô banner của màn) sau mỗi `refresh_rate` khi màn đang hiển thị. Không ảnh hưởng native khác (feed, onboarding, post-interstitial). |
| `refresh_rate` | Long (giây) | `0` | Chu kỳ reload banner slot ("sau bao nhiêu giây load lại quảng cáo" trong spec). `0` = không có timer. Màn nào reload bằng tay thì bỏ qua giá trị này. Getter nhân ×1000 ra ms. **Đổi tên từ `banner_slot_reload_gap_seconds` cho khớp spec.** |

### Quảng cáo — khoảng cách (gap) giữa các lần hiển thị

Dùng bởi `AdGapGate`. Getter nhân ×1000 ra ms (trừ `inter_delay_time` — xem dưới).

| Key | Kiểu | Fallback | Ý nghĩa |
|---|---|---|---|
| `inter_delay_time` | String | `time=0,ignore=` | Gap giữa 2 Interstitial **+ danh sách loại inter được miễn gap**. Dạng `time=10,ignore=ads_splash\|inter_language\|inter_intro`: `time` = gap (giây), `ignore` = các inter (`InterstitialPlacement.key`: `ads_splash`, `inter_language`, `inter_intro`) luôn hiện bất kể gap. **Thay cho `inter_inter_gap_seconds` cũ.** Parse ra `RemoteConfig.interDelay` (`InterDelayConfig`). |
| `open_open_gap_seconds` | Long (giây) | `0` | Thời gian tối thiểu giữa 2 lần App Open. |
| `inter_open_gap_seconds` | Long (giây) | `0` | Thời gian tối thiểu giữa 1 Interstitial và 1 App Open (cả 2 chiều). Phần này **không** bị `ignore` của `inter_delay_time` bỏ qua. |

### Quảng cáo — tần suất interstitial theo lượt

Gate ở `BaseActivity.showInterstitial`. Khác với các key gap ở trên (chặn theo **thời gian**), key này chặn theo **số lần**.

| Key | Kiểu | Fallback | Ý nghĩa |
|---|---|---|---|
| `step_count` | Long → Int | `1` | Chỉ hiện interstitial ở lần yêu cầu **chia hết cho** giá trị này (`1` = mọi lần; `2` = cứ 2 lần/1 lần...). Bộ đếm **lưu qua các lần mở app** (`AppPreferences.interstitialStepCounter`), nên với trigger hiện tại (chỉ ở Splash) `step_count=2` nghĩa là "cứ 2 lần mở app hiện 1 lần". Clamp tối thiểu 1. |

### Quảng cáo — ID ads theo tầng (tiering)

Mỗi format có 3 tầng ID ưu tiên: **highfloor → medium → all_price**; set 1 trong 3 hoặc cả 3. Dựng thành `AdUnitIds` qua `AdUnitIds.fromConfig()`, nạp vào `Ads.initialize` ở Splash; waterfall (`WaterfallLoader`) thử lần lượt theo đúng thứ tự. Value dạng `highfloor=id1|id2,medium=id3,all_price=id4` (nhiều id/tầng ngăn bằng `|`). **Rỗng → test id mặc định.** Rewarded/RewardedInter (dormant) luôn test id.

| Key | Kiểu | Fallback | Ý nghĩa |
|---|---|---|---|
| `id_banner` | String (tầng) | test id | ID banner thường |
| `id_banner_collapsible` | String (tầng) | test id | ID banner collapsible |
| `id_interstitial` | String (tầng) | test id | ID interstitial |
| `id_native` | String (tầng) | test id | ID native — áp **chung mọi placement** |
| `id_app_open` | String (tầng) | test id | ID App Open |

> **Lưu ý:** ID chốt 1 lần tại Splash (`Ads.initialize`). Lần cài đầu (chưa có cache) dùng test id; từ lần mở sau mới áp ID thật. Debug override cũng ăn ở lần mở kế.

### Quảng cáo — break native sau interstitial

Dùng bởi `PostInterstitialNativeAdDialogFragment` (flow 2 stage).

| Key | Kiểu | Fallback | Ý nghĩa |
|---|---|---|---|
| `post_interstitial_native_ad_count` | Long → Int | `2` | Số native ad hiển thị liên tiếp trong break sau inter. **Clamp 1–2** (dialog chỉ hỗ trợ flow 2 stage). |
| `wait_time` | Long (giây) | `5` | Số giây đếm ngược trên màn native fullscreen sau interstitial trước khi icon X (đóng) hiện ra — cũng là thời gian mỗi stage đứng trước khi nút Next khả dụng. Getter nhân ×1000 ra ms. |

### UI luồng mở app

| Key | Kiểu | Fallback | Ý nghĩa |
|---|---|---|---|
| `intro_swipe` | Boolean | `true` | Cho phép vuốt (swipe) ở màn Onboarding (`OnboardingActivity`, `ViewPager2.isUserInputEnabled`). `true` = vuốt được (mặc định); `false` = chỉ chuyển trang bằng nút Next. |
| `language_delay_millis` | Long (**ms**) | `800` | Thời gian chờ trước khi nút xác nhận (`apply_button`) ở màn chọn ngôn ngữ (`LanguageActivity`) hiện ra. `0` = hiện ngay. **Đơn vị đã là mili-giây — getter KHÔNG nhân ×1000** (khác các key gap tính bằng giây). |
| `language_setup` | String | `next=1,loading=false` | Nút Next màn Language. Dạng `next=1,loading=true`: `next` = kiểu nút (`1` nút chữ, `2` icon mũi tên), `loading` = có hiện vòng loading trong lúc chờ `language_delay_millis` không. Parse ra `RemoteConfig.languageSetup` (`LanguageSetup`). |
| `intro1_setup` | String | `next=1,ds=1,delay=0,dot=0` | UI màn Onboarding. Dạng `next=1,ds=1,delay=0,dot=1`: `next` = kiểu nút (`1` chữ, `2` icon), `ds` = vị trí indicator (`1` trên nút Next, `0` dưới), `delay` = **ms** chờ trước khi hiện nút Next mỗi trang, `dot` = `1` hiện chấm tròn (TabLayout) / `0` hiện text "x/n". Parse ra `RemoteConfig.intro1Setup` (`Intro1Setup`). |
| `native_intro_use_icon_next` | String | `next=1,loading=false` | Control riêng cho trang native fullscreen trong Onboarding (`OnboardingAdFragment`). Dạng `next=1,loading=true`: `next` = `1` mũi tên Next / `2` icon X (cả hai bấm đều sang trang kế); `loading` = có hiện spinner chờ **`wait_time` giây** trước khi hiện control không. Khi ở trang này, nút Next chung ở đáy bị ẩn. Parse ra `RemoteConfig.nativeIntroUseIconNext` (dùng chung `LanguageSetup`). |

---

## Lịch sử triển khai (so với spec PDF "Remote Config")

Spec gốc (file `Remote Config.pdf`) mô tả bộ key legacy khác tên. App **không** dùng tên legacy đó. Tiến độ theo tính năng:

- [x] `intro_swipe` — cho/không cho swipe Onboarding *(26/10/2026)*
- [x] `wait_time` — đếm ngược icon X native fullscreen (sau interstitial; đổi tên từ `post_interstitial_native_ad_duration_seconds`) *(26/10/2026)*
- [x] `language_delay_millis` — delay hiện nút xác nhận màn Language *(26/10/2026)*
- [x] `step_count` — inter chỉ hiện ở lần chia hết cho N (bộ đếm persist) *(26/10/2026)*
- [x] `inter_delay_time` (time + `ignore`) — gap giữa inter + miễn gap cho một số loại inter (thay `inter_inter_gap_seconds`) *(26/10/2026)*
- [x] `language_setup` — kiểu nút Next + loading màn Language *(26/10/2026)*
- [x] `intro1_setup` — kiểu nút Next + vị trí indicator + delay + chấm tròn màn Onboarding *(26/10/2026)*
- [x] `native_intro_use_icon_next` — nút Next/icon X cho native fullscreen onboarding (loading gate bằng `wait_time`) *(26/10/2026)*
- [x] `enable` — bật/tắt + chọn loại QC theo vị trí (gồm AOA splash, native-inter, native-banner, welcome back) *(26/10/2026)*
- [x] `native_template` — chọn layout native động (media/no media/banner/fullscreen) *(26/10/2026)*
- [x] `collap_config` — click-bait native collap (tap thứ N mới click) *(26/10/2026)*
- [x] Tiering ID `highfloor / medium / all_price` (id_banner/_collapsible/_interstitial/_native/_app_open) *(26/10/2026)*

> Khi làm thêm key mới, cập nhật bảng "Danh sách key" và tick mục tương ứng ở đây.
