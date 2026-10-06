# Checklist revisi LaukSatSet

Dokumen ini memetakan seluruh butir pada `LaukSatSet.md` dan revisi lanjutan ke implementasi aplikasi versi 0.17.0. Detail penggunaan serta diagram alur terdapat di [Dokumentasi Fitur dan Alur](DOKUMENTASI_FITUR_DAN_ALUR.md).

## Pelanggan

- [x] Halaman masuk dirapikan, memiliki loading/error, pemulihan sandi, registrasi, dan fallback demo offline.
- [x] Detail lauk menyediakan **Tambah** dan **Beli sekarang**; pilihan menu/varian dibawa ke keranjang.
- [x] Satu paket dapat mencampur menu serta varian siap masak dan siap makan.
- [x] Kapasitas slot divalidasi lagi oleh backend dengan penguncian transaksi agar slot penuh tidak dapat dipesan.
- [x] Rekomendasi memberi sampai tiga hasil yang berbeda, jumlah orang, preferensi, alergi, anggaran, waktu, aktivitas, serta fallback lokal bila Gemini tidak tersedia.
- [x] Porsi dapat dipilih mulai satu, sementara minimum dan maksimum jumlah pack tetap mengikuti paket.
- [x] Tombol profil diperjelas dan profil dipisahkan menjadi data akun, preferensi, alamat, masukan, dan keamanan.
- [x] Tombol kembali memakai ikon vektor.
- [x] Keranjang menjadi langkah tersendiri dan mendukung banyak menu, ubah jumlah/varian/porsi, serta hapus dengan konfirmasi.
- [x] Durasi paket dikalikan frekuensi makan 1–3 kali per hari; setiap Hari/Makan memiliki kartu pilihan menu dan varian yang terpisah.
- [x] Keranjang dapat dibuka sejak pilihan lauk pertama tanpa menunggu seluruh kolom selesai.
- [x] Jumlah porsi dapat diketik langsung atau diubah memakai tombol tambah/kurang.
- [x] Sisa jumlah pack otomatis membatasi penambahan item; jumlah awal menyesuaikan paket yang dipilih.
- [x] Setiap tanggal memiliki dua slot waktu dan paket besar dapat memakai dua atau tiga pengiriman.
- [x] Ringkasan checkout menampilkan subtotal, tambahan, diskon, ongkir, serta total.
- [x] WhatsApp bantuan memakai nomor yang dapat dikonfigurasi dari backend.
- [x] Komplain pengiriman dan masalah stok meminta keterangan; komplain pengiriman mendukung foto dan dapat dilihat admin.
- [x] Aksi masak, cara simpan, sudah dimasak, dan laporkan masalah dipisahkan. Laporan tetap tersedia setelah lauk dimasak.
- [x] Customer dapat mengirim masalah, saran, atau masukan umum dari profil.
- [x] Pickup dan pengantaran tersedia. Koordinat alamat dipakai untuk radius maksimum dan tambahan ongkir per kilometer.
- [x] Paket empat minggu dibagi sampai empat pengiriman mingguan; ongkir memasukkan tarif slot, jarak, dan pack tambahan.
- [x] Foto menu dapat dibuka lebih besar dari detail lauk; kartu menu dapat ditekan.
- [x] Susun Paket memakai wizard lima langkah dengan pilihan aktif Terracotta dan ringkasan yang terpisah.
- [x] Tanggal hari ini dan tanggal lampau tidak dapat dipilih untuk pengiriman.
- [x] Pengiriman ditampilkan eksplisit sebagai Pengiriman 1, Pengiriman 2, dan Pengiriman 3 sesuai paket.
- [x] Cara penyimpanan dan petunjuk kematangan memakai data per varian yang dapat diubah admin.
- [x] Harga per pack terlihat saat memilih varian dan estimasi total berubah mengikuti isi keranjang.
- [x] Checkout tersedia setelah paket, keranjang, alamat/pickup, alokasi lauk, dan slot valid.
- [x] Alamat dipisahkan menjadi jalan, kecamatan, kota, provinsi, dan kode pos; label/kota/provinsi berupa pilihan; nama dan kode pos divalidasi frontend/backend.
- [x] Nomor telepon pengantar tampil pada detail pesanan setelah pengantar ditugaskan.
- [x] Timer dapur customer berupa halaman horizontal per langkah dan otomatis maju setelah timer selesai.
- [x] Detail lauk memakai langkah persiapan yang rinci (mencairkan, memotong, memanaskan, mengaduk, dan tanda matang), timer per langkah, serta pager animasi.
- [x] **Beli sekarang** selalu membuka Langkah 1; **Tambah ke paket** membuka kisi Hari/Makan dengan pilihan yang baru ditambahkan sudah terlihat.
- [x] Paket empat minggu menerima sampai 84 pilihan dan menyediakan aksi isi semua serta salin susunan Hari 1.
- [x] Keranjang meminta konfirmasi saat lauk diganti dan saat lauk dihapus.
- [x] Add-on nasi, sambal, dan kerupuk dapat dipilih per waktu makan dan dihitung frontend/backend.
- [x] Pickup memakai rentang jam serta menampilkan alamat dapur pada ringkasan.
- [x] Beranda hanya menampilkan tiga favorit, sedangkan seluruh menu tersedia pada halaman katalog dan pencarian tersendiri.
- [x] Ringkasan pesanan menampilkan tujuan tiap jadwal, timeline, rincian lauk, dan status operasional.
- [x] Timer hanya tampil untuk langkah yang membutuhkan durasi; persiapan, menyiapkan bahan, membuka kemasan, dan memotong ditandai tanpa timer.
- [x] Tombol **Pakai pilihan pertama untuk semua** mengisi semua Hari/Makan melalui state keranjang terpusat.
- [x] **Tambah ke paket** dari detail lauk membuka Langkah 1 dan mempertahankan pilihan lauk saat paket diganti.
- [x] Langkah 1 menyediakan jumlah orang; porsi seluruh pilihan mengikuti angka ini dan tetap dapat disesuaikan per lauk.
- [x] Langkah 4 menetapkan satu jadwal per hari, bukan satu jadwal untuk setiap waktu makan; backend menerapkan aturan yang sama.
- [x] Tujuan utama di Langkah 4 tampil satu kali.
- [x] State langkah wizard disimpan saat membuka preferensi, sehingga tombol kembali mengembalikan customer ke Langkah 5.
- [x] Langkah 5 menampilkan paket, jumlah orang, frekuensi, seluruh lauk/add-on, jadwal, alamat atau lokasi pickup, preferensi, harga, serta tautan Google Maps untuk pickup.
- [x] Filter stok tetap satu baris dan dapat digeser horizontal pada layar sempit.
- [x] Mengganti tanggal/jam jadwal mengganti ID slot, alokasi lauk, dan pasangan alamat secara atomik sehingga tanggal lama tidak tertinggal.
- [x] Alamat tujuan menampilkan ringkasan alamat, dapat diganti per jadwal, serta dapat dikelola dari Langkah 4.
- [x] Rekomendasi online dan fallback lokal menyaring alergi/pantangan tersimpan, termasuk kecocokan nama bahan gabungan.
- [x] Lokasi pickup dapat dibuka lewat Google Maps dari Langkah 4 dan ringkasan; detail pengiriman menampilkan nomor serta tombol hubungi pengantar.
- [x] Langkah 4 dan 5 memakai tombol Ubah alamat yang jelas; setiap hari pengiriman dapat memilih alamat tersimpan secara terpisah.
- [x] Status dan perjalanan pesanan pelanggan memakai kalimat ramah tanpa istilah operasional internal admin/dapur.
- [x] Halaman Stok dan Pesanan memiliki tombol kembali pada app bar seperti halaman Saran.
- [x] Alamat disederhanakan menjadi satu tujuan utama untuk seluruh jadwal dalam satu pesanan; perubahan alamat per hari dihapus.
- [x] Keranjang Langkah 3 memakai kartu menu per hari yang konsisten dengan kartu pada alur awal.
- [x] Susunan Hari 1 dapat disimpan di perangkat dan ditempel kembali pada pesanan berikutnya, dengan validasi ketersediaan menu dan frekuensi makan.
- [x] Paket yang dipilih pada Beranda langsung menjadi paket aktif di Langkah 1.

## Admin

- [x] Data demo pesanan tersedia dalam mode offline.
- [x] Produk ditampilkan dua kolom dan memiliki kategori.
- [x] Laporan CSV harian, mingguan, bulanan, dan pengantar tersedia dengan filter periode yang benar.
- [x] Grafik jumlah penjualan dan pemasukan terkonfirmasi tersedia.
- [x] Profil admin menyediakan ubah kata sandi dan keluar akun.
- [x] Navigasi admin berada di bawah dan halaman Ringkasan, Pesanan, Produk, Pengiriman, Masukan, serta Profil dipisahkan.
- [x] Status pengantar, pesanan yang dibawa, telepon, jumlah aktif/selesai, riwayat, dan kelengkapan bukti foto tampil di admin.
- [x] Editor produk mencakup seluruh detail dan kedua varian: harga, foto, kategori, bahan, alergen, porsi, kalori, instruksi, timer, alat, penyimpanan, dan kematangan.
- [x] Tambah produk memakai validasi harga, validasi pasangan langkah/timer, formulir gulir besar, serta konfirmasi akhir.
- [x] Produk yang diarsipkan dapat diaktifkan kembali.
- [x] Kapasitas dan ongkir slot diedit langsung; slot kosong dapat dihapus.
- [x] Paket dapat ditambah, diedit, dan diarsipkan.
- [x] Inbox admin menampilkan komplain berfoto dan masukan customer.
- [x] Unduh laporan memakai kartu berikon dan dialog konfirmasi; grafik batang penjualan, pemasukan, serta perbandingan diantar/pickup memiliki data demo.
- [x] Admin dapat membuka detail order dan memantau timeline; keputusan produksi, QC, serta siap kirim tidak tersedia di panel admin.
- [x] Editor produk menjadi halaman penuh dengan pratinjau foto, validasi per bagian, langkah dinamis, timer opsional, serta daftar peralatan dinamis.
- [x] Data pelanggan dan pengantar dapat dilihat langsung dari Kelola → Pengguna.
- [x] Grafik admin memiliki filter Mingguan, Bulanan, dan Tahunan; perubahan pembayaran demo Sukses/Gagal meminta konfirmasi kedua.
- [x] Detail pesanan admin memakai istilah settlement, produksi, QC, serah terima, dan bukti pickup yang sesuai konteks operasional.
- [x] Ikon profil admin tersedia di kanan atas untuk membuka tab Profil.
- [x] Grafik ringkasan memakai batang vertikal, ikon unduh memakai aset vektor, dan daftar produk awal dibatasi enam item dengan tombol Lihat semua.
- [x] Foto wajib pada produk baru, harga varian ditempatkan sebelum editor langkah, dan diskon dibatasi maksimum 90% pada frontend/backend.

## Dapur dan pengantar

- [x] Rekap produksi memakai nama menu, tanggal/jam berurutan, jumlah pack, pedas, nasi, catatan, dan alergen.
- [x] Perubahan status produksi dan QC meminta konfirmasi kedua.
- [x] Dapur memperoleh ringkasan H-1 serta alarm 30 dan 10 menit sebelum pengiriman.
- [x] Status siap dikirim memperbarui ringkasan admin; aplikasi admin memuat ulang berkala dan membuat notifikasi lokal saat status baru terdeteksi.
- [x] Profil dapur menjadi halaman tersendiri.
- [x] Role pengantar memiliki daftar tugas serta wajib mengunggah foto sebelum berangkat dan foto saat sampai.
- [x] Rekap packing dapur mencantumkan nasi, sambal, dan kerupuk.
- [x] Pesanan baru langsung membuat antrean dapur `menunggu_pembayaran`; pembayaran berhasil mengaktifkan produksi tanpa klik admin.
- [x] Dapur memegang keputusan `diproduksi → lolos_qc → siap_dikirim`; status siap langsung mengaktifkan tugas pengantar tanpa persetujuan admin.
- [x] Pengiriman siap yang belum memiliki pengantar otomatis diberikan kepada pengantar dengan tugas aktif paling sedikit.
- [x] Timeline tahap tampil pada detail pesanan, kartu produksi dapur, panel pemantauan admin, dan tugas pengantar.
- [x] Mode demo offline menyimpan status dapur/pengiriman sehingga pergantian akun tetap memperlihatkan alur serah terima yang sama.
- [x] Navbar dapur memakai ikon vektor yang konsisten dan layar pengantar memiliki delapan contoh tugas dengan status/tanggal berbeda.
- [x] Profil dapur dapat dibuka dari ikon kanan atas; pengantar memiliki bottom navigation Tugas/Profil, halaman profil, statistik, keamanan akun, dan kartu tugas yang dirapikan.

## Verifikasi build

- [x] Migrasi Alembic bersih hingga revision `b5814f72c9da` pada SQLite uji.
- [x] Dua belas test backend lulus, termasuk paket 84 posisi, aturan satu jadwal per hari, penyaringan alergi gabungan, batas diskon, perhitungan add-on, dan pengujian end-to-end checkout → antrean dapur → pembayaran → QC dapur → pengantar langsung → penerimaan → stok customer.
- [x] Smoke test autentikasi, alamat GPS, inbox admin, dan data pengantar lulus.
- [x] Android `lintDebug` lulus tanpa error.
- [x] Android `lintDebug` dan `assembleDebug` versi 0.17.0 lulus setelah penambahan pemindai ML Kit.
# Revisi admin, dapur, dan pengantar (v0.10.0)

- [x] Tombol unduh laporan ditempatkan paling atas pada ringkasan admin.
- [x] Kartu pengaturan paket tampil dalam dua kolom.
- [x] Slot pengiriman dikelola melalui kalender bulanan; tanggal dapat dipilih sebelum menambah slot.
- [x] Riwayat pengantar menampilkan lima data terakhir, dapat difilter per tanggal, dan dapat diunduh sebagai CSV.
- [x] Bukti foto pengantaran lama dibersihkan otomatis sesuai masa retensi backend tanpa menghapus catatan riwayat.
- [x] Navigasi admin diringkas menjadi Ringkasan, Pesanan, Kelola, dan Profil.
- [x] Nama menu dapur selalu memakai nama produk, batch diurutkan dari waktu pengiriman paling awal, serta perubahan status tetap memakai konfirmasi ganda.
- [x] Status siap dari dapur dihitung per pengiriman, dicatat di audit, memberi notifikasi admin, dan muncul otomatis di aplikasi pengantar.
- [x] Layar pengantar memuat ulang tugas berkala, memberi notifikasi siap antar, dan menahan tombol mulai antar sampai dapur siap.
- [x] Backend menolak pengantar yang mencoba memulai perjalanan sebelum dapur menyatakan pesanan siap.
