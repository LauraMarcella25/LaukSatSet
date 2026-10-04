# Arsitektur LaukSatSet

Untuk panduan fitur dan alur lengkap per peran, lihat [Dokumentasi Fitur dan Alur](DOKUMENTASI_FITUR_DAN_ALUR.md).

```mermaid
flowchart LR
  A[Android Kotlin\nCompose + ViewModel] -->|HTTPS REST + JWT| B[FastAPI]
  B -->|Transaksi dan row lock| C[(PostgreSQL)]
  B -->|Server Key| D[Midtrans Snap Sandbox]
  D -->|Webhook bertanda tangan| B
  A -->|Buka redirect_url| D
```

Android tidak pernah mengakses PostgreSQL dan tidak menyimpan Server Key. FastAPI menghitung ulang harga, memeriksa cut-off, mengunci baris slot dengan `SELECT ... FOR UPDATE`, lalu menyimpan order, item, jadwal, reservasi kapasitas, dan pembayaran dalam satu transaksi. Status pembayaran hanya menjadi final lewat webhook terverifikasi atau rekonsiliasi server.

## Relasi inti

```mermaid
erDiagram
  USER ||--o{ ADDRESS : memiliki
  USER ||--o{ ORDER : membuat
  PACKAGE ||--o{ ORDER : dipilih
  ORDER ||--|{ ORDER_ITEM : berisi
  MENU ||--|{ MENU_VARIANT : memiliki
  MENU_VARIANT ||--o{ ORDER_ITEM : dipilih
  ORDER ||--o{ DELIVERY_SCHEDULE : dijadwalkan
  CAPACITY_SLOT ||--o{ DELIVERY_SCHEDULE : menampung
  DELIVERY_SCHEDULE ||--o{ DELIVERY_ITEM : membawa
  ORDER_ITEM ||--o{ DELIVERY_ITEM : dialokasikan
  ORDER ||--|| PAYMENT : dibayar
  ORDER ||--o| SUBSCRIPTION : membuat
  ORDER ||--o| REVIEW : diulas
  INVENTORY_ITEM ||--o{ INVENTORY_MOVEMENT : memiliki
  MENU ||--o{ PRODUCTION_BATCH : diproduksi
  USER ||--o{ AUDIT_EVENT : melakukan
```

Status `Payment`, `Order`, `DeliverySchedule`, dan `ProductionBatch` disimpan terpisah. `OrderItem` menyimpan snapshot nama, varian, dan harga agar histori tidak berubah ketika katalog diedit.

## Batas akses

- Pelanggan hanya dapat membaca order dan alamat miliknya.
- Admin dapat membaca seluruh order, ringkasan keuangan, dan melakukan transisi operasional.
- Tim dapur hanya mendapat daftar produksi dan transisi produksi/QC yang diizinkan.
- Semua pemeriksaan berada di backend melalui JWT dan dependency berbasis peran.
