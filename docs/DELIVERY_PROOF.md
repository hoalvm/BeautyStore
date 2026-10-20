# Bằng chứng giao hàng

## Luồng sử dụng

1. Admin chuyển đơn COD sang `PROCESSING` (tồn kho đã commit theo luồng hiện có).
2. Shipper nhận đơn → `SHIPPING`, bấm hoàn thành trên tab đang giao.
3. Chọn **một** ảnh JPEG/PNG/WebP, tối đa **5 MiB**. Xem trước rồi xác nhận đã giao
   và đã thu đủ tiền nếu COD. HEIC cần chuyển sang JPEG.
4. Server kiểm tra shipper được phân công và trạng thái dưới khóa ghi của đơn,
   kiểm tra bytes/MIME/decode ảnh, thu nhỏ tối đa 1600 px và chuyển thành JPEG.
   Metadata gốc (tên file, EXIF/GPS) không được lưu.
5. Ảnh, thời điểm giao, `DELIVERED` và `COD_PAID` được lưu **trong cùng transaction**.
   Không trừ tồn kho lần nữa. VNPay đã thanh toán không bị đổi trạng thái thanh toán.
6. Xem lại tại lịch sử shipper → “Chi tiết / ảnh giao”, chi tiết đơn admin,
   hoặc trang chi tiết đơn của khách → “Xem ảnh giao hàng”.

Ảnh nên thể hiện kiện hàng/nơi bàn giao, tránh chụp mặt, giấy tờ, nhãn có thông tin
riêng tư. Đây là bằng chứng do shipper cung cấp, không phải xác nhận độc lập của khách.

## Hợp đồng API

- `PUT /api/shipper/orders/{id}/complete`: Bearer JWT của shipper, multipart field
  `image` (file). **Không** tự đặt `Content-Type` khi gửi `FormData`; browser thêm boundary.
  Không nhận URL ảnh hoặc base64 trong JSON.
- Thiếu/giả ảnh hoặc quá 5 MiB: `400`; vượt giới hạn multipart chung: `413`;
  sai phân công: `403`; trạng thái không hợp lệ: `409`.
- Một request hợp lệ lưu đúng một ảnh. Request lặp từ cùng shipper cho đơn đã
  `DELIVERED` và có ảnh trả lại kết quả hiện tại, không ghi đè ảnh/thời gian/COD.
  Ảnh không được chỉnh sửa sau hoàn thành.
- `OrderResponse` thêm `hasDeliveryProof` và `deliveredAt`; không chứa ảnh bytes,
  public ID, URL storage hoặc token.
- `GET /api/delivery-proofs/{orderId}`: JPEG, `Cache-Control: private, no-store`,
  `X-Content-Type-Options: nosniff`. Cho admin, shipper được phân công, chủ đơn thành viên.
  Đơn guest yêu cầu **`X-Order-Token` còn hạn, đã xác minh OTP, đúng đơn**.
  Token không đưa vào URL. Không có quyền → `403`; có quyền nhưng chưa có ảnh → `404`.

Request ảnh guest được security filter cho đi qua, nhưng service luôn xác minh
quyền trước khi đọc bytes. Truy cập bằng mã/ID đơn thuần không đủ.
Client tải ảnh theo yêu cầu bằng fetch có auth rồi dùng blob URL; giải phóng khi đóng
modal/chuyển trang. Đơn cũ không có ảnh vẫn đọc được và có thông báo rõ ràng.

## Lưu trữ, triển khai và giới hạn

- Flyway **V19** tạo `delivery_proofs`, thêm FK nullable `orders.delivery_proof_id`.
  Không sửa V1–V18, không tự thêm ảnh giả vào lịch sử.
- Demo H2 tự cập nhật schema; không cần Cloudinary hay biến môi trường mới.
  `mvn clean install` / `mvn spring-boot:run` giữ nguyên cách sử dụng.
- Dev/prod MySQL chạy migration mới; sao lưu và kiểm tra restore trước triển khai.
  Deploy backend và frontend cùng phiên bản: client cũ không gửi ảnh sẽ không thể
  hoàn thành một đơn mới.
- Ảnh JPEG chuẩn hóa nằm trong `LONGBLOB` riêng, không load vào listing order.
  Transaction rollback cũng rollback ảnh, không có asset ngoài DB cần dọn.
  Đánh đổi: backup/database lớn dần theo số đơn. Theo dõi dung lượng; chưa có job tự
  xóa ảnh hay thời hạn lưu tự động. Cần chốt chính sách lưu giữ trước vận hành thật;
  mã hóa backup và hạn chế quyền truy cập DB.
- Không thêm upload ảnh cho giao thất bại, không thay quy trình restock/đổi trả.
- Đồng thời gửi hoàn thành/thất bại được serialize bằng khóa đơn. Gửi lại hoàn thành
  không thể ghi đè một trạng thái thất bại/đã hủy.

## Kiểm thử

```sh
mvn clean install
mvn -Dtest=DeliveryProofIntegrationTests,DeliveryProofImageProcessorTests,DeliveryProofAccessServiceTests test
npm run check
npm test
```

Test tự động bao phủ decode JPEG/PNG/WebP, kích thước/MIME giả, thiếu ảnh, resize,
quyền các vai trò/guest, token hết hạn, retry đồng thời, rollback, COD/VNPay, lịch sử
không có ảnh, multipart frontend, chống submit trùng, lỗi mạng và giải phóng blob.
Test MySQL 8.4 Testcontainers cần Docker; local không có Docker sẽ skip, CI phải chạy.

### Kết quả kiểm tra local ngày 20/10/2026

- `mvn clean install`: 277 test Java, 276 pass, 1 MySQL Testcontainers skip do
  chưa có Docker; không failure/error.
- `npm run check`: 28 JavaScript source và asset references hợp lệ.
- `npm test`: 112/112 pass.
- Chạy bằng `mvn spring-boot:run` với database H2 tạm riêng; tạo đơn COD qua API,
  admin chuyển `PROCESSING`; thao tác trình duyệt shipper nhận đơn, chọn WebP,
  xem trước, gửi và mở lại ảnh trong lịch sử. Ảnh JPEG thực đọc được qua API riêng tư.
- Khách thành viên và admin đăng nhập bằng hai browser session riêng, mở chi tiết đơn
  và xem được cùng ảnh; không có lỗi JavaScript từ ứng dụng trong các luồng này.
- Form upload kiểm tra ở 390 × 844, không tràn ngang. Ảnh và dữ liệu test không
  đưa vào repository; database demo có sẵn của người dùng không bị thay đổi.
- Guest OTP, quyền sai tài khoản, retry đồng thời và rollback xác minh bằng
  integration test. MySQL migration V19 chưa được chạy trên Docker tại máy này;
  không coi kiểm tra H2 là thay thế kiểm tra MySQL.
