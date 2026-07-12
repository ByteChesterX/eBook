eBook Reader, Android cihazlarınızda PDF ve e-kitapları okumak için tasarlanmış, tamamen Kotlin ile geliştirilmiş, hafif ve son derece hızlı bir açık kaynaklı uygulamadır. Harici kütüphanelere bağımlı kalmadan, Android'in kendi PdfRenderer API'sini kullanarak maksimum performans ve düşük bellek tüketimi sağlar.
✨ Özellikler
📖 Okuma Deneyimi
Tam Ekran Yatay Kaydırma: Sayfalar yan yana dizilir, sağa/sola kaydırarak kitap hissiyle okuyun.
Akıcı Performans: LRU (Least Recently Used) önbellek sistemi sayesinde sayfalar arasında geçiş yağ gibi akar.
Gelişmiş Zoom: Çift dokunarak yakınlaştırma veya iki parmakla (pinch-to-zoom) istediğiniz detaya inin.
Otomatik Bellek Yönetimi: Görünmeyen sayfaları otomatik temizleyerek cihazınızı yormaz.
Sayfa Çevirme Animasyonları: Yumuşak ve doğal sayfa geçiş efektleri.
🎨 Görünüm ve Arayüz
Material 3 Tasarım: Modern, şık ve kullanıcı dostu arayüz.
3 Farklı Tema: ☀️ Açık, 🌙 Koyu ve 📜 Sepya modları ile göz yormayan okuma.
Edge-to-Edge: Durum çubuğu ve navigasyon tuşlarını gizleyerek tam dikkatle okuma imkanı.
Yazı Boyutu ve Düzeni: İçeriği ihtiyacınıza göre ölçeklendirin.
🛠️ Araçlar ve Kolaylıklar
Akıllı Yer İmleri: 📑 Kitabın içindekiler kısmını otomatik algılar, manuel yer imi ekleyebilirsiniz.
Kaldığınız Yerden Devam: Uygulamayı kapatsanız bile son sayfayı hatırlar.
İçerik Arama: PDF içinde metin arama özelliği.
Okuma İstatistikleri: ⏱️ Ne kadar süredir okuduğunuzu ve ilerleme yüzdesini görün.
Ekranı Açık Tutma: Okurken ekranın kapanmasını engeller.
Favoriler ve Son Dosyalar: Sık okuduklarınıza hızlı erişim.
📸 Ekran Görüntüleri
(Buraya uygulamanın ekran görüntülerini ekleyebilirsiniz: Ana ekran, Okuma ekranı, Ayarlar menüsü)
🚀 Kurulum ve Kullanım
Gereksinimler
Android Studio Hedgehog veya üzeri
JDK 17
Min SDK: 24 (Android 7.0 Nougat)
Target SDK: 34 (Android 14)
Projeyi Çalıştırma
Depoyu klonlayın:
bash
1
Projeyi Android Studio'da açın.
Gradle senkronizasyonunun tamamlanmasını bekleyin.
Bir emülatör veya fiziksel cihaz seçip Run butonuna basın.
APK Oluşturma (Manuel)
Terminalde proje dizinindeyken:
bash
1
Oluşturulan APK: app/build/outputs/apk/release/app-release.apk
🤖 Otomatik Derleme (CI/CD)
Bu proje, GitHub Actions ile tam entegre çalışır. Her push işleminden sonra veya manuel olarak tetiklendiğinde otomatik olarak Release APK oluşturur ve GitHub Releases bölümüne yükler.
Nasıl Tetiklenir?
Deponuzda Actions sekmesine gidin.
"Build Android Release APK" iş akışını seçin.
Run workflow butonuna tıklayın.
Versiyon bilgilerini girin (örn: 1.0.0) ve çalıştırın.
İşlem bitince Releases kısmından APK'yı indirin.
🏗️ Teknik Detaylar
Dil: %100 Kotlin
Mimari: MVVM (Model-View-ViewModel)
PDF Motoru: Android PdfRenderer (Harici kütüphane yok!)
UI Toolkit: Android Views + Material Components 3
Veri Saklama: SharedPreferences & Jetpack DataStore (Ayarlar için)
Önbellekleme: Custom LRU Cache implementasyonu
Neden Harici Kütüphane Yok?
Pek çok PDF okuyucu AndroidPdfViewer gibi ağır kütüphaneler kullanır. Bu proje, Android'in kendi PdfRenderer sınıfını kullanarak:
Uygulama boyutunu ~5MB altında tutar.
Bellek sızıntısı (memory leak) riskini minimize eder.
Daha stabil ve güvenli bir deneyim sunar.
📂 Proje Yapısı
text
1234567891011121314
📝 Lisans
