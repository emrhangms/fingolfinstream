# FingolfinStream 

## 📁 Proje Yapısı


## 📦 Cloudstream'e Nasıl Eklenir?

1. Bu projeyi kendi GitHub hesabınızda bir repoya yükleyin.
2. GitHub deponuzun **Settings > Actions > General** sekmesinden:
   - **Workflow permissions**: "Read and write permissions" olarak ayarlayın.
3. Ana dala (`main` veya `master`) kod gönderdiğinizde GitHub Actions eklentiyi otomatik olarak derler ve `build` dalına yükler.
4. Cloudstream uygulamasında **Ayarlar > Eklentiler > Depo Ekle** kısmına deponuzun bağlantısını girin:
   ```text
   https://raw.githubusercontent.com/<KULLANICI_ADINIZ>/<REPO_ADINIZ>/build/plugins.json
   ```
5. Listeden **Çizgi ve Dizi** eklentisini indirip kullanmaya başlayabilirsiniz!
