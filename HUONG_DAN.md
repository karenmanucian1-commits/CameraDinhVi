# Camera Định Vị – camera chụp ảnh / quay video có watermark tùy chỉnh

## Build ra APK (không cần cài gì)
1. Vào github.com → New repository → đặt tên `CameraDinhVi` → Create.
2. Bấm "uploading an existing file" → kéo TOÀN BỘ nội dung thư mục này (kể cả thư mục `.github`) vào → Commit.
   (Nếu web không cho kéo thư mục `.github`, tạo file mới tên `.github/workflows/build.yml` rồi dán nội dung file đó vào.)
3. Vào tab **Actions** → "Build APK" tự chạy (~5 phút).
4. Xong thì vào **Releases → latest** tải `CameraDinhVi.apk` về điện thoại và cài.

## Dùng
- Mở app, cho phép Camera, Micro, Vị trí.
- **ẢNH / VIDEO**: chọn chế độ ở dưới. Nút tròn trắng = chụp, nút đỏ = quay (bấm lần nữa để dừng).
- **✎ Watermark**: chỉnh nội dung
  - Tiêu đề (ô vàng), vd tên dự án
  - Thời gian: giờ thật / giờ tùy chỉnh (đồng hồ vẫn chạy) / giờ cố định
  - Địa chỉ, tọa độ: tự động theo GPS hoặc gõ tay
  - Ghi chú, cỡ chữ, vị trí góc trên/dưới
- **⟲ Đổi cam**: camera trước/sau.
- Ảnh lưu ở `Pictures/CameraDinhVi`, video ở `Movies/CameraDinhVi`. Bấm ô nhỏ góc trái để xem file vừa chụp.

Ghi chú: địa chỉ tự động cần có mạng. Không có mạng thì app vẫn hiện tọa độ.
