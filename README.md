# MemoMill

Egyfájlos tanuló- és jegyzetapp (Memo). GitHub Pages-en fut.

## Beállítás
1. A `index.html` elején a `FIREBASE_CONFIG` objektumban a Firebase web-config értékei vannak (a `databaseURL` a Realtime Database URL-je).
2. Firebase Realtime Database → Rules:
   `{"rules":{"memo":{"$key":{".read":true,".write":true}}}}`
3. Az app első indításakor a **⚙ Beállítások**-ban add meg az Anthropic API-kulcsot (csak az adott böngészőben tárolódik) és a szinkronkulcsot (másik eszközön ugyanazt add meg).

## Android-app

Minden `main`-re feltöltés után a GitHub Actions lefordítja az APK-t. Telefonon ezt a linket nyisd meg:

https://github.com/tpatrikjanos-create/MemoMill/releases/latest/download/MemoMill.apk

Az APK a webes appot beágyazva tartalmazza, így net nélkül is indul (a szinkronhoz és az AI-hoz net kell).
Az aláíró kulcs (`android/memomill.jks`) csak az oldaltelepítéshez van, Play Store-hoz újat kell készíteni.

### Alkalmazászár
Beállítások → Alkalmazászár. Kapcsold be a „MemoMill alkalmazászár” szolgáltatást a Kisegítő lehetőségekben,
jelöld ki a zárolt appokat (a TikTok, Instagram, YouTube stb. alapból ki van jelölve).
