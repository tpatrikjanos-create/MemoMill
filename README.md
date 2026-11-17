# MemoMill

Egyfájlos tanuló- és jegyzetapp (Memo). GitHub Pages-en fut.

## Beállítás
1. A `index.html` elején a `FIREBASE_CONFIG` objektumban a Firebase web-config értékei vannak (a `databaseURL` a Realtime Database URL-je).
2. Firebase Realtime Database → Rules:
   `{"rules":{"memo":{"$key":{".read":true,".write":true}}}}`
3. Az app első indításakor a **⚙ Beállítások**-ban add meg az Anthropic API-kulcsot (csak az adott böngészőben tárolódik) és a szinkronkulcsot (másik eszközön ugyanazt add meg).
