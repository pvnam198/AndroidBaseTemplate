# Mua bán Premium (Google Play Billing)

Premium = tắt toàn bộ quảng cáo. Logic lấy theo repo tham khảo `d31-pdf-reader-3` (nhánh `feature/phase-7`, thư viện `billing_library` + `PremiumRepository`), chỉnh cho base app. Code nằm ở `core/billing/`.

## Các phần

| File | Làm gì |
|---|---|
| `PremiumProducts.kt` | Danh sách sản phẩm app bán (product ID trên Play Console). **Mỗi app thay ID của mình.** Mẫu, mỗi loại một gói: `premium_yearly` (thuê bao), `premium_lifetime` (mua một lần dùng mãi), `coin_pack_100` (mua một lần tiêu hao). |
| `Premium.kt` | Điểm vào cho cả app: `initialize` (trong `Application`), `refresh`, `purchase`, `showPaymentIssueMessage`, `products` / `purchaseUpdates` cho màn mua. Đồng bộ `AppPreferences.isPremium` và `Ads.adsEnabled`. |
| `GooglePlayBilling.kt` | Bọc Google Play Billing 8.3.0: kết nối (dùng chung một lần kết nối khi gọi song song), query giá + giao dịch, mở luồng mua, acknowledge / consume, thông báo lỗi thanh toán của Google Play. |
| `BillingProductDetail.kt` | Giá, offer, dùng thử, giá chia theo tuần giữ đúng định dạng tiền của Store. |
| `AdjustIapTracker.kt` | Gửi doanh thu lần mua đầu và từng kỳ gia hạn lên Adjust, mỗi kỳ một lần. Cần điền `ADJUST_IAP_EVENT_TOKEN` trong `app/build.gradle.kts`; để trống thì không gửi. |
| `feature/premium/PremiumActivity.kt` | Màn mua: danh sách gói + giá, Tiếp tục (mua gói đang chọn), Khôi phục giao dịch, công tắc Premium giả (chỉ bản debug). |

## Ba loại sản phẩm

| Loại | Mẫu | Cho Premium? | Sau khi mua |
|---|---|---|---|
| Thuê bao (`SUBS`) | `premium_yearly` | Có, khi còn hạn | Acknowledge. Hết hạn / hoàn tiền thì mất Premium. |
| Mua một lần dùng mãi (`NON_CONSUMABLE`) | `premium_lifetime` | Có, vĩnh viễn | Acknowledge. Mỗi tài khoản chỉ mua được một lần. |
| Mua một lần tiêu hao (`CONSUMABLE`) | `coin_pack_100` | **Không** | Consume để mua lại được. App tự cộng vật phẩm (xu, lượt...) khi nhận `PurchaseUpdate.Succeeded` có sản phẩm này. |

Gói tiêu hao chỉ được báo `Succeeded` **sau khi consume thành công**, nên vật phẩm được cộng đúng một lần, kể cả khi app bị tắt giữa lúc mua và lúc consume (lần mở sau query thấy giao dịch còn dở, consume nó rồi báo). Đã có Premium thì vẫn mua được gói tiêu hao.

## Khi nào có / mất Premium

- **Mở app** (`Premium.initialize`), **mở màn Premium**, **bấm Khôi phục**: query giao dịch trên Google Play.
  - Có giao dịch **đã thanh toán** của một gói thuê bao / mua một lần dùng mãi trong `PremiumProducts` → Premium.
  - Không có → mất Premium (gói hết hạn, bị hoàn tiền) → quảng cáo bật lại.
  - Giao dịch đang chờ thanh toán (PENDING) chưa tính là đã mua.
  - Query giao dịch **lỗi** → giữ nguyên trạng thái cũ (không tước Premium oan).
- **Mua xong** một gói cho Premium → Premium ngay. Google báo **đã sở hữu** → query lại để biết chắc.
- Gói thuê bao mua theo offer tốt nhất: dùng thử miễn phí → giá giới thiệu → giá gốc.
- Giao dịch được acknowledge ngay (không acknowledge trong 3 ngày Google sẽ tự hoàn tiền); sản phẩm tiêu hao thì consume.
- **Gia hạn bị từ chối** (grace period / account hold): ở màn chính, mỗi phiên một lần, Google Play tự hiện thông báo cho user sửa thanh toán (chỉ hiện khi có lỗi thật); sửa xong thì query lại.

Màn hình đang mở lúc Premium thay đổi tự ẩn quảng cáo ở lần `onResume` kế tiếp (`BaseActivity.onPremiumUser`).

## Khác với repo tham khảo

- Không dùng Hilt: `Premium` là object, giống phần quảng cáo.
- Gói tiêu hao chỉ báo đã mua sau khi consume thành công, và giao dịch tiêu hao còn dở lúc mở app cũng được báo (repo tham khảo consume âm thầm, user mất vật phẩm). Mua gói tiêu hao không bật Premium.
- Chỉ tính giao dịch đã thanh toán, và query lỗi thì không tước Premium (repo tham khảo tính cả giao dịch đang chờ, và query lỗi thì mất VIP).
- Không lấy phần IAP recovery của repo tham khảo (push nhắc gia hạn lỗi theo ngày, WorkManager) - riêng của app đó; base dùng thông báo có sẵn của Google Play.

## Test

- Unit test: `BillingLogicTest` (chọn offer, giá, dùng thử, định dạng giá, quyết định Premium, gói tiêu hao không cho Premium).
- Trên máy chưa có gói thật: màn Premium báo "Chưa tải được các gói", nút Tiếp tục khoá.
- Bản debug: công tắc **Premium giả** ở màn Premium bật / tắt Premium mà không cần mua (đồng bộ Google Play không xoá nó). Đã thử 06/11: bật → không còn banner / native, mở lại app vẫn Premium; tắt → quảng cáo quay lại.
- Mua thật: cần app trên Play Console (ít nhất internal testing) với các product ID trong `PremiumProducts` và tài khoản license tester.
