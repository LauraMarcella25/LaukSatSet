# LaukSatSet

Versi aplikasi Android yang didokumentasikan saat ini: **0.16.0 (version code 17)**. Dokumentasi rinci mengenai fitur per peran, langkah penggunaan, diagram alur, status, API, dan konfigurasi tersedia di [Dokumentasi Fitur dan Alur](docs/DOKUMENTASI_FITUR_DAN_ALUR.md). Panel admin memakai empat navigasi utama, kalender slot, paket dua kolom, filter dan ekspor riwayat pengantar. Pesanan baru langsung tercatat di admin dan dapur; setelah pembayaran, dapur mengelola produksi, QC, dan pelepasan langsung ke pengantar sementara admin memantau.

Foto bukti pengantaran yang berusia lebih dari enam bulan dibersihkan otomatis saat backend dimulai. Ubah periode dengan `COURIER_PROOF_RETENTION_MONTHS`; data riwayat pengiriman tetap dipertahankan untuk laporan.

Aplikasi Android native untuk pemesanan lauk siap masak dan siap makan, dilengkapi backend FastAPI, PostgreSQL, role pelanggan/admin/dapur/pengantar, transaksi checkout yang aman terhadap overbooking, dan integrasi Midtrans Snap Sandbox.

Palet antarmuka: Terracotta `#C9684B`, Beige `#F3E4D0`, Brown `#4A332B`, Olive `#7D8B62`, dan Warm White `#FFFDF8`.

## Yang sudah berjalan

- Login JWT dan otorisasi backend untuk pelanggan, admin, dapur, dan pengantar; tersedia pemulihan serta perubahan kata sandi.
- Katalog menu/varian, bahan, alergen, instruksi, paket 1/3/5/20 pack, slot dan ongkir.
- Penyusunan paket, validasi jumlah pack, porsi, pedas, nasi, catatan, alamat, dan jadwal.
- Kalkulasi ulang server untuk subtotal, add-on, diskon, ongkir per pengiriman, dan total.
- Reservasi kapasitas dengan row lock PostgreSQL, snapshot harga, order, delivery, payment, subscription, dan audit trail.
- Snap redirect Sandbox dan webhook SHA-512 yang memeriksa order, nominal, transisi, serta duplikat.
- Pesanan pelanggan, instruksi Masak Sekarang dan timer.
- Ringkasan admin dari pembayaran terkonfirmasi serta mode dapur tanpa akses laporan keuangan.
- State loading, kosong, error, sesi kedaluwarsa, dan navigasi dari notifikasi ke halaman terkait.
- Mode demo offline: akun demo tetap bisa login dan mencoba katalog, paket campuran, checkout, admin, dapur, rekomendasi, dan Stok Lauk Saya saat APK tidak dapat mencapai backend.
- Rekomendasi lauk berdasarkan anggaran, waktu, dan preferensi. Backend memakai Gemini bila `GEMINI_API_KEY` tersedia dan kembali ke aturan lokal bila tidak tersedia.
- Paket campuran serta pemilihan hingga tiga jadwal pengiriman sesuai ukuran paket.
- Stok Lauk Saya setelah pengiriman diterima, dengan aksi Masak Sekarang, Sudah dimasak, dan Ada masalah.
- State pilihan aktif berwarna Terracotta pada tab, chip, checkbox, switch, dan tombol proses agar pilihan mudah dikenali.
- Kalender Material 3 untuk tanggal pengiriman, riwayat pesanan, anjuran penggunaan stok, slot admin, dan jadwal produksi dapur.
- Validasi tanggal pengiriman pada aplikasi dan backend; tanggal sebelum hari ini tidak dapat digunakan untuk pesanan atau slot baru.
- Registrasi customer, profil, pencarian menu/bahan, keranjang sebelum checkout, dan pemberitahuan kiriman hari ini.
- Rekomendasi dengan pilihan cepat dua kolom serta jumlah orang; hasil aturan lokal memiliki pemecah seri agar tidak selalu memilih menu pertama.
- Konfirmasi dua langkah untuk perubahan status stok dan dialog cara penyimpanan.
- Panel admin terpisah untuk Ringkasan, Pesanan, Produk, dan Pengiriman, termasuk tambah/edit/arsip menu, pengaturan paket, serta tambah/hapus slot.
- Rekap dapur per hari dengan jumlah pack, target selesai, persiapan H-1, dan alarm 30/10 menit sebelum jadwal pengiriman setelah jadwal dimuat di aplikasi dapur.
- Sesi login dan keranjang disimpan di perangkat; customer kembali ke peran terakhir dan dapat melanjutkan susunan paket setelah aplikasi ditutup.
- Setiap kelompok lauk pada paket mingguan dapat ditentukan tanggal kirimnya, dengan validasi minimal satu lauk pada setiap jadwal.
- Customer dapat menambah, mengedit, menghapus, dan memilih alamat saat checkout.
- Rekomendasi menampilkan hingga tiga menu: pilihan utama Gemini bila API aktif dan alternatif aman dari mesin aturan katalog.
- Wizard Susun Paket lima langkah: paket, lauk campuran matang/mentah, keranjang, pengiriman, lalu checkout/pembayaran. Paket mingguan otomatis mengisi batas pack dan dapat dibagi antar tanggal.
- Pilihan diantar atau ambil sendiri, dua slot waktu per hari, pembatasan area layanan, rincian ongkir/subtotal, dan tanggal paling cepat besok.
- Detail pesanan dengan timeline, bayar ulang, batal sebelum pembayaran, ganti alamat/jadwal sebelum produksi, nomor pengantar, konfirmasi diterima, ulasan, dan komplain berfoto.
- Preferensi alergi, bahan yang dihindari, target kalori, frekuensi makan, aktivitas termasuk gym, serta tujuan kebugaran dipakai untuk menyaring rekomendasi.
- Produk memiliki kategori, foto, estimasi kalori, langkah memasak, dan timer per langkah yang dapat diatur admin.
- Admin memakai navigasi bawah, kartu produk dua kolom, arsip yang dapat dipulihkan, editor slot langsung, grafik penjualan, ekspor CSV, penetapan pengantar, serta profil keamanan.
- Dapur mendapat urutan produksi, konfirmasi ganda status/QC, ringkasan H-1, pengingat 30/10 menit, dan profil. Pengantar mengunggah foto barang sebelum berangkat dan foto saat sampai.
- Customer dapat mengirim masalah atau saran umum melalui profil dan membuka percakapan bantuan WhatsApp.
- Alamat dipecah menjadi jalan, kecamatan, kota, provinsi, dan kode pos; titik GPS dipakai backend untuk memvalidasi radius serta menghitung tambahan ongkir per kilometer.
- Detail lauk memiliki aksi tambah ke keranjang dan beli sekarang. Timer memasak dapat digeser per langkah dan otomatis berpindah saat hitung mundur selesai.
- Langkah persiapan seperti membuka kemasan, menyiapkan, dan memotong bahan ditampilkan tanpa timer; timer hanya muncul pada proses yang membutuhkan durasi seperti memanaskan atau memasak.
- Editor produk admin mengubah kedua varian, harga, instruksi, timer, alat, penyimpanan, kematangan, porsi, nutrisi, kategori, dan foto dalam satu formulir.
- Admin memiliki inbox komplain/saran, riwayat tiap pengantar, grafik pemasukan, laporan harian/mingguan/bulanan, serta laporan pengantar.
- Profil dapur menjadi halaman tersendiri. Panel admin memuat ulang data berkala dan mengirim notifikasi lokal saat dapur menandai pesanan siap dikirim.
- Paket sekarang memakai struktur durasi × frekuensi makan. Setiap Hari/Makan memiliki kartu menu sendiri sehingga seluruh lauk dapat berbeda; porsi dapat diketik atau diubah dengan tombol.
- Langkah pertama Susun Paket memuat jumlah orang dan frekuensi makan. Pilihan dari detail lauk tetap dibawa ketika customer memilih jenis paket.
- Paket empat minggu mendukung empat pengiriman mingguan. Ongkir bertambah berdasarkan jumlah pengiriman, jarak, dan pack tambahan pada setiap pengiriman.
- Paket empat minggu mendukung sampai 84 posisi lauk (28 hari × 3 kali makan) dan menyediakan pengisian cepat dengan menyalin satu menu atau susunan Hari 1.
- Setiap waktu makan dapat memiliki nasi, sambal, dan kerupuk sendiri; harga dihitung ulang backend dan jumlah add-on diteruskan ke rekap packing dapur.
- Satu pesanan menggunakan satu alamat tujuan utama untuk seluruh jadwal. Jadwal lauk ditentukan satu kali per hari; pickup menampilkan alamat dapur dan tautan Google Maps.
- Susunan Hari 1 dapat disimpan pada perangkat dan ditempel ke pesanan berikutnya; paket dari Beranda langsung dipilih di Langkah 1.
- Aplikasi versi 0.16.0 menyertakan profil dapur dan pengantar, bottom navigation Tugas/Profil untuk pengantar, filter grafik admin mingguan/bulanan/tahunan, dan konfirmasi simulasi pembayaran.
- Serah terima operasional memakai alur `pesanan dibuat → terlihat di admin & dapur → pembayaran mengaktifkan produksi → QC dapur → dapur menandai siap dikirim → tugas pengantar aktif`. Jika belum ditetapkan, backend memilih pengantar dengan beban aktif paling sedikit. Admin memantau tanpa tombol persetujuan operasional.
- Error registrasi menunjukkan penyebabnya pada kolom dan respons backend. Pesan sementara hilang otomatis setelah empat detik.
- Saat detail lauk dibuka dari Stok, tombol pembelian disembunyikan agar alur memasak tidak tercampur dengan pemesanan.

## Menjalankan backend

```bash
cp .env.example .env
docker compose up --build
curl http://localhost:8000/health
```

Untuk pengembangan ringan tanpa Docker:

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
DATABASE_URL=sqlite:///./lauksatset.db uvicorn app.main:app --reload
```

Jika memakai database lama, jalankan `alembic upgrade head` sebelum menyalakan API. Docker menjalankan migrasi ini otomatis.

PostgreSQL adalah database target. SQLite hanya disediakan untuk iterasi lokal dan test ringan; jaminan penguncian checkout bersamaan harus diuji pada PostgreSQL.

## Menjalankan Android

1. Buka folder `android` di Android Studio.
2. Gunakan JDK 17 dan biarkan Gradle melakukan sinkronisasi.
3. Jalankan backend pada komputer.
4. Emulator Android menggunakan default `http://10.0.2.2:8000/`.
5. Untuk perangkat fisik, tambahkan `API_BASE_URL=https://alamat-backend-anda/` ke `~/.gradle/gradle.properties` atau jalankan Gradle dengan `-PAPI_BASE_URL=...`. Gunakan HTTPS untuk alamat selain localhost.
6. Build APK: `./gradlew assembleDebug`. Hasilnya berada di `android/app/build/outputs/apk/debug/app-debug.apk`.

APK debug tetap dapat dipakai tanpa backend menggunakan akun demo. Aplikasi akan menampilkan tanda **Mode demo offline**. Checkout offline tidak membuat transaksi Midtrans dan tidak menyimpan data ke PostgreSQL.

## Rekomendasi Gemini

Tambahkan ke `.env` backend:

```bash
GEMINI_API_KEY=key-dari-google-ai-studio
GEMINI_MODEL=gemini-flash-latest
```

API key hanya dibaca backend dan tidak ditanam di APK. Rekomendasi Gemini dibatasi pada katalog aktif, divalidasi kembali oleh server, dan tidak digunakan untuk mengambil keputusan keamanan alergi. Tanpa key, endpoint tetap bekerja memakai aturan yang dapat dijelaskan.

Nomor bantuan, koordinat dapur, radius maksimum, dan tarif jarak diatur melalui `SUPPORT_WHATSAPP`, `KITCHEN_LATITUDE`, `KITCHEN_LONGITUDE`, `MAX_DELIVERY_KM`, dan `DELIVERY_FEE_PER_KM`. Nomor WhatsApp menggunakan format internasional tanpa tanda `+`.

## Akun demo

Semua akun memakai kata sandi `demo123`:

- `pelanggan@lauksatset.id`
- `admin@lauksatset.id`
- `dapur@lauksatset.id`
- `pengantar@lauksatset.id`

## Pengujian

```bash
cd backend
PYTHONPATH=. pytest -q
```

Pengujian otomatis saat ini memeriksa kalkulasi server, status pembayaran konservatif, dan kontrak signature Midtrans. Skenario concurrency slot terakhir dan webhook end-to-end dijelaskan di `docs/API.md` dan perlu dijalankan terhadap PostgreSQL serta akun Midtrans Sandbox yang sebenarnya.

## Monetisasi

Model data mendukung pembelian percobaan, diskon paket mingguan/4 minggu, porsi ekstra, nasi, menu dengan harga berbeda, dan ongkir per pengiriman. Contoh Rp799.000 serta target laba tidak ditampilkan sebagai klaim. Ringkasan admin hanya menghitung `settlement`; estimasi margin tetap diberi catatan sampai seluruh HPP, kemasan, payment fee, diskon, subsidi ongkir, waste, dan biaya tetap diisi.

Lihat [arsitektur](docs/ARCHITECTURE.md) dan [API/Midtrans](docs/API.md) untuk detail teknis.
