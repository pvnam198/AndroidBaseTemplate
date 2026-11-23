# CLAUDE.md

Base app Android (Kotlin, ViewBinding, một module `app`) chạy đủ luồng Splash → Language → Onboarding → Main → Settings,
có sẵn AdMob Next-Gen, Firebase Remote Config, Adjust, Google Play Billing. App mới copy base rồi chỉ sửa / thêm màn.

**Đọc `docs/BASE_GUIDE.md` trước khi làm task.** Chi tiết config: `docs/remote-config.md`; billing: `docs/billing.md`.

## Quy tắc bắt buộc

- Trả lời và viết comment / docs bằng tiếng Việt.
- Base chỉ chứa màn chung. Không thêm màn / placement theo tính năng riêng của một app vào base.
- Màn mới kế thừa `BaseActivity` / `BaseFragment` / `BaseDialogFragment`; quảng cáo chỉ qua helper `loadNative`,
  `loadBanner`, `showInterstitial`, `showReward`; cấu hình quảng cáo chỉ đọc qua `AdPolicy`. Không gọi SDK AdMob trực tiếp trong màn.
- Remote Config: mỗi bản release đọc **key riêng của bản đó** — `VERSION_KEY` trong `core/config/RemoteConfig.kt`
  (`ad_ver` + chữ số versionName, vd 1.0.0 → `ad_ver100`) = nguyên file JSON xuất từ web "Cấu hình quảng cáo"
  (`ads_profile`, `settings`, `profiles.full/basic`, `templates`). **Mỗi bản release đổi `VERSION_KEY`** và thêm key đó
  trên Firebase. Đổi format thì viết lại parser, không thêm lớp chuyển đổi.
- ID quảng cáo chỉ lấy từ `ad_ids`: placement không có ID = không có quảng cáo (không có ID dự phòng). Bản debug gửi
  request bằng test ID của Google thay cho mọi ID đã cấu hình; bản release không bao giờ dùng test ID.
- Native template: placement chọn mẫu ở trường `template` (không phải `ad_type`). Dòng mẫu có sẵn trong `templates`
  chỉ đè style, `base` bị bỏ qua. Bố cục mới = thêm `ad_tpl_*.xml` + enum `NativeAdTemplate`. Giữ đúng view id chung; không vi phạm chính sách native AdMob (nhãn Ad, ảnh ≥ 120dp, CTA bấm được).
  Không hiện giá ("FREE").
- Thêm placement: `AdKey` + `AdUnit` + `NativePlacement`/`InterstitialPlacement`/`BannerPlacement` (+ `isEnabled`), thêm vào
  `docs/ad_config_sample.json` — theo `docs/BASE_GUIDE.md` mục 4.2.
- Đổi config / placement / template thì cập nhật cùng lúc: code, JSON mẫu (`docs/` + `app/src/test/resources/`), docs.
- Quảng cáo lỗi / thiếu config / không fill → app vẫn đi tiếp, không treo, không crash.
- Code chỉ cho phát triển đặt trong `app/src/debug/`.
- Không commit `.idea/`. Chỉ commit / push khi người dùng yêu cầu.
- Không đổi cài đặt hệ thống / bảo mật của máy test.

## Lệnh

```bash
./gradlew :app:testDebugUnitTest      # unit test
./gradlew :app:installDebug           # cài bản debug
adb shell am start -n com.namstd.androidskillhub/.debug.NativePreviewActivity   # xem các mẫu native
```

Máy MIUI chặn `adb shell input`: dùng broadcast `com.namstd.androidskillhub.debug.TAP|SWIPE|TYPE|BACK` (`AdbDriver.kt`).
Override Remote Config trên máy: Settings → Debug config (bản debug).
