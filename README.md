# BeautyStore

Website mỹ phẩm bằng Spring Boot, Thymeleaf và Java. Có catalog/biến thể, giỏ hàng
khách/thành viên, COD/VNPay, quản trị, giao hàng, đánh giá, đổi trả và hỗ trợ trực tiếp.

## Chạy ngay sau khi clone

Điều kiện: **JDK 24 trở lên**, **Maven 3.9 trở lên**, cổng `8080` còn trống.
Lần build đầu cần Internet để Maven tải dependencies. Không cần MySQL, Docker,
Node.js, `.env`, SMTP hay API key cho demo.

```sh
mvn clean install
mvn spring-boot:run
```

Mở **http://localhost:8080**. Dừng bằng `Ctrl+C`.
Nếu chưa cài Maven, dùng `.\mvnw.cmd` trên Windows hoặc `./mvnw` trên macOS/Linux
thay cho `mvn` (vẫn cần JDK).

Mặc định chạy profile **demo**, chỉ lắng nghe `127.0.0.1`.
Không mở bản demo ra Internet, không dùng thông tin/mật khẩu cá nhân thật.

| Vai trò | Email | Mật khẩu ban đầu |
| --- | --- | --- |
| Khách hàng | `user@beautystore.vn` | `BeautyUser123!` |
| Quản trị | `admin@beautystore.vn` | `BeautyAdmin123!` |
| Giao hàng | `shipper@beautystore.vn` | `BeautyShipper123!` |

Tài khoản chỉ được tạo khi chưa có vai trò tương ứng; không ghi đè mật khẩu đã đổi.
Đăng nhập trên `/login`; hệ thống điều hướng theo vai trò.

### Phạm vi demo và dữ liệu

- H2 lưu dữ liệu tại `.beautystore/demo.mv.db`; ảnh upload lưu tại `.beautystore/uploads/`.
  Thư mục này không vào Git, không bị `mvn clean` xóa. Khởi động lại giữ nguyên đơn/tài khoản.
- Seed 8 nhóm / 40 sản phẩm hư cấu. Seeder không khôi phục sản phẩm admin đã ẩn.
- Email được render thật và chuyển vào [hộp thư demo](http://localhost:8080/demo/inbox),
  không gửi ra Internet. Thư giữ trong RAM tối đa 10 phút; OTP hết hạn sau 5 phút.
  Mở hộp thư ở tab khác và bấm “Làm mới” sau khi đăng ký hoặc yêu cầu OTP.
- COD hoạt động. VNPay không hiện khi chưa cấu hình; không có thanh toán giả.
- Ảnh JPEG/PNG/WebP được kiểm tra nội dung rồi lưu cục bộ; không cần Cloudinary.
- Khi chưa có Gemini, chatbot dùng gợi ý catalog/chính sách xác định trước, **không phải AI**.
- Demo không chạy migration MySQL. H2 dùng Hibernate tạo/cập nhật schema riêng;
  không dùng dữ liệu H2 làm database production.

Để làm demo lại từ đầu: dừng app, **đổi tên** thư mục `.beautystore` thành
`.beautystore.backup-YYYYMMDD` rồi chạy lại. Giữ bản cũ nếu cần đơn/ảnh trước đó.
Không xóa database MySQL đang có.

### Kịch bản trình diễn theo vai trò

1. Khách mở sản phẩm, chọn biến thể → giỏ hàng → nhập địa chỉ → đặt COD.
2. Khách tra cứu đơn bằng email/OTP trong hộp thư demo; không thể xem đơn chỉ bằng mã đơn.
3. Admin `/admin` xác nhận đơn, kiểm tra tồn kho, lô, biến thể, voucher và tài khoản.
4. Shipper `/shipper` nhận đơn khả dụng → giao hàng → xác nhận đã giao.
5. Khách vào đơn đã giao để viết đánh giá hoặc yêu cầu đổi trả.
6. Admin duyệt đánh giá; xử lý yêu cầu trả, xác nhận hàng đủ điều kiện mới nhập lại lô,
   ghi nhận hoàn tiền thủ công. Luồng hỗ trợ trực tiếp ở widget khách và `/admin/support`.

Dùng cửa sổ riêng tư hoặc browser profile riêng cho mỗi vai trò để không ghi đè phiên đăng nhập.
Các giới hạn chống spam vẫn hoạt động trong demo (OTP, checkout, chatbot); đừng bấm gửi liên tục.
Phí ship 30.000đ, miễn ship khi tiền hàng sau giá sale đạt 500.000đ.

## MySQL và tích hợp thật

Chọn profile rõ ràng; lệnh mặc định không kết nối database MySQL cũ.

### Phát triển với MySQL

Tạo database `beauty_store` trên MySQL 8.4 và tài khoản DB phù hợp.
Sao chép `.env.example` sang `.env`, điền `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`JWT_SECRET` (ít nhất 32 byte ngẫu nhiên), rồi các tích hợp cần dùng.
Chỉ profile `dev` đọc `.env`; file này bị Git bỏ qua.

```sh
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Profile `dev` dùng Flyway, Hibernate `validate`, vẫn có catalog/tài khoản demo.
**Không trỏ `dev` vào production**. Trước khi nâng database hiện hữu, sao lưu và
kiểm tra restore. Không sửa migration đã chạy; các migration mới được nối tiếp.

### Production

Chọn `SPRING_PROFILES_ACTIVE=prod`; truyền secrets bằng biến môi trường, không commit.
Prod không seed demo, không có hộp thư/ảnh cục bộ, không tự đọc `.env`.
JWT, VNPay và thông tin pháp lý bắt buộc được kiểm tra lúc khởi động.
Cấu hình SMTP, Cloudinary, HTTPS, domain và CORS đúng môi trường.
VNPay cần merchant/sandbox hợp lệ và IPN công khai qua HTTPS.
Không dùng khóa JWT demo, tài khoản demo, hoặc H2 cho production.

Profile demo chỉ dành cho trình diễn cục bộ; để vận hành thực tế còn cần backup/restore,
giám sát email/payment, TLS, quản lý secrets và kiểm thử tích hợp trên hạ tầng triển khai.
OTP/rate-limit hiện là bộ nhớ một instance. Chưa hỗ trợ triển khai nhiều instance.

## Kiểm thử

```sh
mvn clean verify
```

Nếu có Docker chạy, MySQL 8.4 Testcontainers kiểm tra Flyway trên schema mới và legacy.
Không có Docker: test MySQL bị skip, các test còn lại vẫn chạy. CI có Docker kiểm tra phần này.

Frontend có bộ test riêng (cần Node.js 24, không cần `npm install`):

```sh
npm run check
npm test
```

Chi tiết phạm vi và giới hạn kiểm chứng: [docs/READINESS_AUDIT.md](docs/READINESS_AUDIT.md).

## Lỗi khởi động thường gặp

- `JAVA_HOME`/`java -version` không phải JDK 24+: cài/chọn JDK rồi mở terminal mới.
- Cổng 8080 đã dùng: dừng instance cũ của chính bạn hoặc chọn
  `mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`, mở cổng tương ứng.
- H2 báo file bị khóa: không chạy hai instance demo trên cùng thư mục dữ liệu.
- Maven không tải được thư viện: kiểm tra Internet/proxy và Maven settings, không tắt SSL.
- App yêu cầu MySQL/JWT khi mong đợi demo: kiểm tra biến `SPRING_PROFILES_ACTIVE`,
  tham số `--spring.profiles.active` và cấu hình IDE có ép `dev/prod` không.
- Tài khoản demo không còn mật khẩu như bảng: dữ liệu từ lần chạy trước được giữ nguyên;
  dùng reset OTP hoặc sao lưu/đổi tên thư mục demo khi app đã dừng.
