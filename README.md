# eBook Reader - Android Uygulaması

Dikkat dağınıklığı olmadan PDF/e-kitap okumak için tasarlanmış basit ve kullanışlı bir Android uygulaması.

## Özellikler

- **PDF Seçimi**: Kullanıcı cihazından PDF dosyası seçebilir
- **Onay Penceresi**: Dosya açılmadan önce onay istenir
- **Tam Ekran Okuma Modu**: Dikkat dağınıklığı olmadan sadece PDF içeriği
- **Dikey Sayfa Düzeni**: Sayfalar alt alta sıralanır
- **Akıcı Kaydırma**: Smooth scrolling deneyimi
- **Koyu/Açık Tema**: Ayarlar menüsünden tema değiştirilebilir
- **Yazı Boyutu Ayarı**: Zoom seviyesi ayarlanabilir (50% - 200%)

## Teknik Detaylar

- **Dil**: Kotlin
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **PDF Viewer**: AndroidPdfViewer 3.2.0-beta.1

## Proje Yapısı

```
eBook/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/ebook/
│   │   │   ├── MainActivity.kt      # Ana ekran, PDF seçimi
│   │   │   ├── ReaderActivity.kt    # PDF okuma ekranı (tam ekran)
│   │   │   └── SettingsActivity.kt  # Ayarlar ekranı
│   │   ├── res/
│   │   │   ├── layout/              # XML layout dosyaları
│   │   │   ├── values/              # Strings, colors, themes
│   │   │   └── menu/                # Menü tanımları
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Kurulum

1. Projeyi Android Studio'da açın
2. Gradle sync yapın
3. Uygulamayı çalıştırın

## Kullanım

1. Uygulamayı açın
2. "PDF Seç" butonuna tıklayın
3. Cihazınızdan bir PDF dosyası seçin
4. Onay penceresinde "Tamam" butonuna tıklayın
5. Tam ekran okuma modunda kitabınızı okuyun

## Ayarlar

Ana ekrandaki menüden Ayarlar'a erişebilirsiniz:
- **Tema Seçimi**: Koyu veya Açık tema
- **Zoom Seviyesi**: %50-%200 arası ayarlanabilir

## Lisans

Bu proje eğitim amaçlı geliştirilmiştir.
