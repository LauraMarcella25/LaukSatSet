# API dan webhook

Dokumentasi interaktif tersedia di `http://localhost:8000/docs` setelah backend berjalan.

Endpoint utama:

- `POST /auth/login` — token JWT dan peran.
- `POST /auth/forgot-password`, `POST /auth/reset-password`, `POST /me/password` — pemulihan dan perubahan kata sandi.
- `GET /catalog`, `GET /delivery-slots` — katalog dan kapasitas aktif.
- `GET /settings/public` — nomor bantuan serta konfigurasi radius dan tarif jarak yang aman ditampilkan ke aplikasi.
- `GET/POST/PATCH/DELETE /me/addresses` — alamat terstruktur dengan koordinat, validasi radius, dan jarak dari dapur.
- `POST /checkout` — memvalidasi sampai 100 posisi `meal_day` × `meal_sequence`, satu jadwal untuk seluruh waktu makan pada hari yang sama, add-on nasi/sambal/kerupuk, alamat pelanggan, ongkir jumlah pack/jarak, cut-off, dan kapasitas; membuat Snap transaction. UI Android saat ini memakai satu alamat utama untuk seluruh jadwal.
- `GET /orders`, `GET /orders/{id}` — akses dibatasi pemilik/peran.
- `POST /payments/webhook` — memverifikasi SHA-512 dari `order_id + status_code + gross_amount + server_key`, nominal, dan idempotensi status.
- `POST /orders/{id}/status` — koreksi administratif terbatas; status operasional ditolak karena berasal otomatis dari pembayaran, dapur, dan pengantar.
- `GET /admin/summary`, `GET /kitchen/production` — tampilan berbasis peran.
- `POST /recommendations` — memilih menu aktif berdasarkan anggaran, waktu, dan preferensi; memakai Gemini melalui backend bila dikonfigurasi, atau aturan lokal sebagai fallback.
- `POST /kitchen/production/{id}/status` — transisi produksi milik role dapur; setelah seluruh batch pengiriman lolos QC dan siap, pengiriman langsung menjadi `siap_dikirim`.
- `POST /deliveries/{id}/status` — koreksi masalah oleh admin; admin tidak dapat menetapkan `siap_dikirim`.
- `GET /me/pantry`, `POST /me/pantry/{id}/status` — membaca stok lauk dan menandai sudah dimasak/bermasalah.
- `POST /orders/{id}/cancel`, `PATCH /orders/{id}/address`, `PATCH /deliveries/{id}/reschedule` — perubahan customer sebelum produksi.
- `POST /me/deliveries/{id}/receive`, `POST /deliveries/{id}/problem`, `POST /orders/{id}/review` — penerimaan, komplain berfoto, dan ulasan.
- `GET /admin/couriers`, `GET /admin/users`, `POST /admin/deliveries/{id}/assign` — data pelanggan/pengantar dan penetapan pengantar.
- `GET /admin/messages` — gabungan komplain pengiriman berfoto dan saran customer.
- `PUT /admin/menus/{id}` — mengganti seluruh detail menu dan semua varian, termasuk timer, alat, penyimpanan, dan kematangan.
- `GET /courier/deliveries`, `POST /courier/deliveries/{id}/{before|arrival}` — daftar tugas dan bukti foto pengantar.
- `POST /me/feedback` — masalah atau saran umum customer.
- `POST /demo/payments/{order_id}/{sukses|gagal|kedaluwarsa}` — hanya tersedia jika Server Key kosong, hanya untuk admin, dan selalu dilabeli simulasi lokal.

## Konfigurasi Midtrans Sandbox

1. Buat akun Sandbox di Merchant Administration Portal Midtrans.
2. Isi `MIDTRANS_SERVER_KEY` hanya pada environment backend dan `MIDTRANS_CLIENT_KEY` bila nanti beralih ke SDK UI Kit.
3. Publikasikan backend melalui HTTPS dan arahkan Payment Notification URL ke `https://domain-anda/payments/webhook`.
4. Atur Finish, Unfinish, dan Error Redirect URL di dashboard Midtrans.
5. Android membuka `redirect_url` resmi yang diterima dari backend. Setelah kembali, aplikasi mengambil ulang status order; callback UI tidak mengubah status pembayaran.

Implementasi mengikuti alur resmi Snap: token dibuat backend, halaman pembayaran menggunakan URL hosted Midtrans, lalu status final berasal dari notification/webhook. Dokumentasi resmi: https://docs.midtrans.com/docs/snap-snap-integration-guide

## Skenario demo

- Pending: buat checkout dan jangan selesaikan pembayaran.
- Sukses: gunakan simulator/metode pembayaran Sandbox, tunggu webhook, lalu muat ulang Pesanan Saya.
- Gagal/kedaluwarsa: pilih skenario gagal di simulator atau tunggu expiration yang dikonfigurasi.

Tanpa kredensial, backend menghasilkan transaksi demo lokal dan tombol Midtrans di Android dinonaktifkan. Endpoint demo admin dapat mengubah status untuk presentasi logika aplikasi, tetapi hasilnya bukan bukti transaksi Midtrans.

## Serah terima dapur ke pengantar

Checkout langsung membuat batch berstatus `menunggu_pembayaran`, sehingga pesanan terlihat di admin dan dapur sejak dibuat. Webhook pembayaran yang berhasil mengaktifkannya menjadi `menunggu_produksi`. Dapur sendiri mengubah batch melalui `diproduksi → lolos_qc → siap_dikirim`; setelah semua batch untuk satu pengiriman siap, tugas pengantar langsung aktif tanpa persetujuan admin. Jika admin belum menetapkan pengantar, backend memilih akun pengantar dengan beban tugas aktif paling sedikit. Endpoint pengantar tetap menolak foto sebelum berangkat selama dapur belum menetapkan status `siap_dikirim`.
