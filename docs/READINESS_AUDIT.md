# BeautyStore — kiểm tra khả năng chạy/demo

Ngày kiểm tra: 20/10/2026. Phạm vi: backend, Thymeleaf/JavaScript, phân quyền,
luồng thương mại, cấu hình mặc định và quy trình clone/build/run.

## Các lỗi đã sửa

| Nhóm | Lỗi/rủi ro và biện pháp |
| --- | --- |
| Khởi động | Default trước đây cần MySQL và JWT riêng. Thêm profile demo H2 file, chỉ loopback; tách dev/prod; không tự đọc `.env` trong demo/prod. |
| Email | Template gửi nền gọi bean bằng web context, làm OTP không gửi được. Dùng biến `store` trong mail context và `th:href` cho liên kết; test email render thật. |
| Tích hợp demo | SMTP chuyển vào hộp thư RAM có TTL; upload kiểm tra bytes và lưu cục bộ; chatbot fallback catalog thật; không tạo URL VNPay trong demo. |
| OTP | Verify/consume và invalidate khi gửi lỗi phải atomic theo challenge; cleanup guest OTP sau commit dùng transaction mới. |
| Tài khoản | Tách `account_locked` khỏi chưa kích hoạt; OTP không thể mở khóa admin. Thêm optimistic version chống ghi đè thay đổi đồng thời. |
| Password/JWT | Hỗ trợ đầy đủ 128 ký tự/Unicode qua BCrypt có prehash/version, vẫn nhận hash cũ; JWT mới gắn user ID; role trả về có thứ tự ổn định, chặn gán role không hỗ trợ. |
| Commerce | Sửa DTO detached làm admin status/return trả 500 sau khi DB đã commit; chặn reject return đã restock; kiểm cấu hình VNPay trước mọi thay đổi giỏ/kho. |
| Gallery/review | Khóa theo product cho giới hạn gallery/primary/delete; duyệt review đồng thời không làm sai rating summary. |
| Frontend | Gắn guest order token đúng đơn; đánh giá trực tiếp từ receipt cho sản phẩm đã ẩn; chống submit trùng, upload lại và response admin đến sai thứ tự. |
| Assets | Self-host Bootstrap, Font Awesome, Chart.js và fonts kèm license; bỏ URL Chart CSS không tồn tại. |
| Secrets | Loại giá trị credential khỏi `.env.example`; kiểm prod JWT/HTTPS chặt hơn. Chủ sở hữu phải thu hồi/đổi khóa đã lộ trong lịch sử. |

Migration mới duy nhất: **V18** (`account_locked`, `row_version`).
Không sửa V1–V17, không xóa database MySQL, không xóa đơn/người dùng lịch sử.

## Kiểm chứng tự động

- `mvn clean install`: thành công trên source snapshot sạch, không `.env`/database có sẵn.
- Backend: **265 tests**, 264 pass, 0 failure/error, 1 MySQL Testcontainers skip khi không có Docker.
- `npm run check`: 27 nguồn JavaScript và tham chiếu asset hợp lệ.
- `npm test`: **99/99 pass**.
- Integration tests dùng HTTP controller/security/service/database thực, tắt Open Session in View:
  - đăng ký → email OTP thật → kích hoạt → login → reset mật khẩu;
  - guest/member cart, merge một lần, checkout COD, kiểm role/ownership;
  - admin xác nhận → shipper nhận/giao → review → moderation → return → restock/refund;
  - giao thất bại/restock một lần, cancel/reservation release, voucher usage, ngưỡng ship;
  - VNPay không cấu hình không đổi giỏ/kho; các test chữ ký/IPN/idempotency hiện có;
  - concurrency gallery, review aggregate, shipper và support; khóa tài khoản và stale write;
  - sandbox mailbox, chặn client/Host không loopback, prod/demo isolation, ảnh giả/path traversal.

CI chạy Docker trước full verify và kiểm tra XML để **không chấp nhận migration test bị skip**.
CI còn khởi động JAR với profile mặc định, kiểm catalog 40 sản phẩm, inbox và static assets.

## Kiểm chứng chạy thực

- `mvn spring-boot:run` không profile, không `.env`, không MySQL: HTTP 200,
  8 nhóm/40 sản phẩm được seed.
- Upload ảnh admin → GET ảnh PNG → xóa ảnh thử: HTTP 200/200/204.
- Trình duyệt: guest chọn sản phẩm → giỏ → COD → yêu cầu OTP → hộp thư sandbox → xem đơn.
- Hộp thư dùng iframe `sandbox` + CSP, không nới `X-Frame-Options: DENY` của ứng dụng.

Kết quả clone cuối và phần browser đa role được bổ sung sau vòng kiểm tra cuối.

## Giới hạn và checklist trước triển khai thật

1. **Rotate secrets đã xuất hiện trong lịch sử Git.** Commit mới không thu hồi credential
   và không xóa các commit cũ. Không tự force-push/rewrite history trong đợt này.
2. MySQL/Flyway phải chạy trên Docker/CI có MySQL 8.4 trước khi nâng DB thật.
   Sao lưu và thử restore; giữ DB cũ để rollback.
3. VNPay sandbox thực, SMTP thật và Cloudinary thật cần credential/hạ tầng của chủ repo.
   Demo không giả lập kết quả thanh toán. Chưa tuyên bố đã xác minh end-to-end các nhà cung cấp.
4. V18 không thể suy ra tài khoản inactive cũ là chưa verify hay đã bị ban.
   Admin phải rà soát/reapply ban cần giữ khi rollout.
5. JWT cũ chưa có claim user ID tiếp tục tương thích trong hạn token. Muốn vô hiệu toàn bộ
   token legacy ngay phải chủ động rotate JWT secret (tất cả tài khoản sẽ đăng nhập lại).
6. OTP/rate limit là cache một instance. Triển khai nhiều instance cần shared store và
   kiểm thử tải; demo không phải benchmark SLA/uptime.
7. H2 demo không thay cho MySQL production. Dữ liệu demo có thể backup bằng cách dừng app
   rồi sao chép toàn bộ `.beautystore`; tuyệt đối không dùng khóa/tài khoản demo trên production.
8. Yêu cầu JDK 24+ và Maven 3.9+, Internet lần tải dependencies đầu. Docker chỉ cần cho
   migration integration test; Node chỉ cần bộ test frontend.

Không có cam kết “không thể phát sinh lỗi”. Kết luận phải dựa trên những luồng đã chạy,
test regression và các giới hạn nêu rõ ở trên.
