# Termux'suz çalışma seçenekleri

Soru: Uygulama Termux kurulu olmadan da çalışabilsin mi?

## Mevcut durum (kod incelemesi)

- `app/src/main/assets/bridge.js` (344 satır) bir node sunucusu; `127.0.0.1:8787` üzerinde dinler ve `CLAUDE_BIN` olarak `/usr/local/lib/node_modules/@anthropic-ai/claude-code/bin/claude.exe` dosyasını `spawn` eder. Bu yol Termux'taki proot Ubuntu rootfs'in içindedir.
- `Termux.kt`: bridge'i `proot-distro login ubuntu` ile başlatır (`/data/data/com.termux/files/usr/bin`), RUN_COMMAND izni ve `allow-external-apps` ister.
- `setupMode` zaten üç yol tanıyor: `termux` (varsayılan), `root` (`ClaudeModes.kt`/`Engine.rootChat`, `su` ile aynı `claude.exe`), `api` (`Engine.apiChat`, yalnızca metin, araç yok).
- `claude.exe` boyutu bu cihazda 256 MB (Bun ile derlenmiş tek ELF). Ubuntu rootfs ve node için boyutlar tahmin; ölçülmedi.

## Seçenekler

| Seçenek | Ne yapılır | Boyut | Risk | Not |
|---|---|---|---|---|
| A) proot + Ubuntu paketleme | `proot`, node ve Ubuntu rootfs uygulamanın içine (jniLibs veya ilk açılışta indirme) | APK'ya gömülürse ~350 MB+; ilk açılış indirmesiyle APK küçük kalır | Orta-yüksek: proot ve node jniLibs'ten çalışmalı, Android 10+ kısıtları; güncelleme ve bakım yükü | Claude Code'un tam davranışı (araçlar, dosya, abonelik girişi) korunur |
| B) Kotlin'e port | Claude Code'un ajan döngüsünü ve araçlarını (Read, Edit, Bash) Kotlin ile yeniden yaz | Tahmini 2.000–4.000 satır | Yüksek: davranış farkı, Android sh/toybox ile Bash kısıtlı, abonelik girişi üçüncü taraf uygulamada onaylı değil | Bakım maliyeti en yüksek |
| C) API anahtarı | Mevcut `apiChat` yolunu varsayılan yap | Ek boyut yok, kod hazır | Düşük | Metin sohbeti, dosya/araç yok; kullanım API faturası ile |
| D) Yalnızca root | Mevcut `rootChat` yolu | Ek boyut yok | Düşük-orta: root gerekir; `RootMode.BIN` Android host yolu olduğu için claude.exe'nin host'ta bulunduğu doğrulanmalı | Geçici root kullanıcıda güvenilmez |

## Öneri

1. Hemen: C (API) ve D (root) zaten kodda; Termux kurulu değilse kurulum sihirbazı bunları sunmalı.
2. Tam çözüm: A. claude.exe ve rootfs'i APK'ya gömmek yerine ilk açılışta indirmek, APK'yı makul tutar.
3. B'yi şimdilik yapma; bakım maliyeti A'dan çok daha yüksek.

## Kontrol edilmeyenler

- Telefonda proot/node'un jniLibs'ten çalıştığı test edilmedi.
- Ubuntu rootfs ve node boyutları ölçülmedi.
- 7 GB RAM'de 256 MB claude + node + proot aynı anda çalışınca bellek baskısı ölçülmedi (cihazda şu an ~135 MB boş).
