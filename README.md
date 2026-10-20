<h1 align="center">BeautyStore</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Java-24-ED8B00?style=flat-square&amp;logo=openjdk&amp;logoColor=white" alt="Java 24">
  <img src="https://img.shields.io/badge/Spring_Boot-3.5.5-6DB33F?style=flat-square&amp;logo=springboot&amp;logoColor=white" alt="Spring Boot 3.5.5">
  <img src="https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&amp;logo=springsecurity&amp;logoColor=white" alt="Spring Security">
  <img src="https://img.shields.io/badge/Thymeleaf-005F0F?style=flat-square&amp;logo=thymeleaf&amp;logoColor=white" alt="Thymeleaf">
  <img src="https://img.shields.io/badge/Bootstrap-5-7952B3?style=flat-square&amp;logo=bootstrap&amp;logoColor=white" alt="Bootstrap 5">
  <img src="https://img.shields.io/badge/JavaScript-F7DF1E?style=flat-square&amp;logo=javascript&amp;logoColor=black" alt="JavaScript">
  <img src="https://img.shields.io/badge/MySQL-4479A1?style=flat-square&amp;logo=mysql&amp;logoColor=white" alt="MySQL">
  <img src="https://img.shields.io/badge/Flyway-CC0200?style=flat-square&amp;logo=flyway&amp;logoColor=white" alt="Flyway">
  <img src="https://img.shields.io/badge/Maven-C71A36?style=flat-square&amp;logo=apachemaven&amp;logoColor=white" alt="Maven">
</p>

## Giới Thiệu

BeautyStore hỗ trợ quy trình bán mỹ phẩm từ xem sản phẩm, chọn màu hoặc dung tích, đặt hàng đến giao hàng và chăm sóc sau mua.

Hệ thống BeautyStore được xây dựng dựa trên ba vai trò: **Customer, Shipper và Administrator**.

## Công Nghệ

| Thành phần | Công nghệ |
| --- | --- |
| Back-end | Java 24, Spring Boot 3.5.5, Spring Web MVC |
| Front-end | Thymeleaf, Bootstrap 5, JavaScript, Chart.js |
| Database | Spring Data JPA, MySQL, Flyway |
| Service | VNPay, Cloudinary, Gemini |
| Email | Spring Mail, Gmail SMTP |
| Authen | Spring Security, JWT |

## Chức Năng

### Customer

- Tìm kiếm và lọc mỹ phẩm theo danh mục và giá.
- Xem ảnh, thông tin sản phẩm; chọn màu hoặc dung tích.
- Quản lý giỏ hàng, sản phẩm yêu thích và sử dụng voucher.
- Đánh giá sản phẩm sau khi giao thành công, gửi yêu cầu đổi trả và liên hệ hỗ trợ.
- Theo dõi đơn hàng; khách chưa đăng nhập xác minh email bằng OTP để truy cập đơn.
- Đặt hàng bằng tài khoản hoặc với tư cách khách; thanh toán COD hoặc VNPay khi đã cấu hình.

### Shipper

- Nhận đơn được phép giao và theo dõi đơn đang phụ trách.
- Tải ảnh bằng chứng khi giao thành công; xem lịch sử giao hàng.
- Ảnh giao hàng được giới hạn quyền xem, không công khai như ảnh sản phẩm.

### Administrator

- Quản lý thương hiệu, danh mục, sản phẩm, biến thể và ảnh.
- Quản lý tồn kho theo lô, hạn sử dụng; xem hàng sắp hết.
- Xử lý đơn hàng, voucher, đánh giá, đổi trả và tài khoản.
- Xem báo cáo bán hàng và hỗ trợ, chăm sóc khách hàng.
- Ẩn sản phẩm ngừng bán mà vẫn giữ thông tin đơn hàng.

## Run Project

### 1. Kiểm tra

```sh
java -version
mvn -version
```

### 2. Tạo cấu hình

Clone repository và mở terminal tại thư mục có `pom.xml`. Sao chép `.env.example` thành `.env`, rồi điền thông tin Gmail.

```sh
git clone https://github.com/hoalvm/BeautyStore.git
```

### 3. Khởi chạy

```sh
mvn clean install
mvn spring-boot:run
```

Mở **http://localhost:8080**. Dừng ứng dụng bằng `Ctrl + C`.

## Cấu Hình Gmail

1. Bật Xác minh 2 bước cho tài khoản Google.
2. Tạo [Mật khẩu ứng dụng Google](https://support.google.com/accounts/answer/185833?hl=vi).
3. Điền vào `.env`:

```dotenv
MAIL_USERNAME=your-account@gmail.com
MAIL_PASSWORD=your-google-app-password
```

Dùng **App Password 16 ký tự, bỏ khoảng trắng**, không dùng mật khẩu đăng nhập Gmail.

## Tài Khoản Mặc Định

| Vai trò | Tài khoản | Mật khẩu | Truy cập |
| --- | --- | --- | --- |
| Customer | `user@beautystore.vn` | `BeautyUser123!` | `/` |
| Shipper | `shipper@beautystore.vn` | `BeautyShipper123!` | `/shipper` |
| Admin | `admin@beautystore.vn` | `BeautyAdmin123!` | `/admin` |

## Cấu Trúc Dự Án

```text
src/
├── main/
│   ├── java/t4m/beauty_store/
│   │   ├── auth/                 # Tài khoản
│   │   ├── product/              # Sản phẩm
│   │   ├── cart/                 # Giỏ hàng
│   │   ├── order/                # Đơn hàng
│   │   ├── payment/              # Thanh toán
│   │   ├── admin/                # Quản trị
│   │   ├── shipper/              # Giao hàng
│   │   ├── review/               # Đánh giá
│   │   ├── returns/              # Đổi trả
│   │   └── ...                   # Voucher
│   └── resources/
│       ├── templates/            # Thymeleaf
│       ├── static/               # CSS, JavaScript
│       └── db/migration/         # Migration Database
└── test/
    ├── java/                     # Test Backend
    └── frontend/                 # Test JavaScript
```