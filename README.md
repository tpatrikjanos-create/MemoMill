# MemoMill

Egyfájlos tanuló- és jegyzetapp (Memo). GitHub Pages-en fut.

## Beállítás
1. A `index.html` elején a `FIREBASE_CONFIG` objektumban a Firebase web-config értékei vannak.
2. Firebase konzol → **Authentication** → Sign-in method → **Email/Password** bekapcsolása.
3. Firebase konzol → Authentication → Templates: a sablonok nyelve legyen magyar (a jelszó-visszaállító levélhez).
4. Firebase konzol → Realtime Database → **Rules**: a `database.rules.json` tartalma. Minden fiók csak a saját `users/<uid>/` ágát látja; a régi `memo/` ágat belépve csak olvasni lehet (az átköltöztetéshez), később törölhető.
5. Az appban: belépés vagy regisztráció e-mail címmel és jelszóval. Az első belépéskor a helyi adatok és a régi szinkronkulcsos adatok átkerülnek a fiókba. Az Anthropic API-kulcsot a Beállításokban kell megadni (csak az adott eszközön tárolódik).

## Tanulás és ismétlés
- **Tanulás** (1. fázis): 3 szint, 7 fokozat lakatos útvonalon. Felfedés → Kezdőbetű (könnyű: minden 3. szó rejtett, közepes: minden 2., nehéz: mind) → Gépelés (könnyű, közepes, nehéz).
  Bekezdésenként az első hiba figyelmeztet, a második egy szívbe kerül és a bekezdés újraindul. 3 szív fogyása után egy fokozattal vissza, és az a fokozat újra zárolódik.
- **Szöveg ismétlése** (2. fázis): vak gépelés 2 élettel; ha mindkettő elfogy, vissza a tanulásba (Gépelés – közepes).
- **Mesterfázis** (3.): ha a szöveg 21 napja stabil, vagy a vizsga 7 napon belül van: 1 élet; bukás után aznap még egyszer előjön.
- Gépelésnél az elírás (egy betű eltérés vagy felcserélés) elfogadott; a hossztól és a fokozattól függ. Számoknál nincs tűrés.
- **Kártyák:** Sikerült / Nem sikerült. Új kártya mindig másnap jön újra, az elrontott a kör végén és másnap is.
- **Hibatérkép:** a gyakran elrontott szavak színesek a dokumentumban, és a kezdőbetűs fokozatokban mindig rejtettek.

## Android-app

Minden `main`-re feltöltés után a GitHub Actions lefordítja az APK-t. Telefonon ezt a linket nyisd meg:

https://github.com/tpatrikjanos-create/MemoMill/releases/latest/download/MemoMill.apk

Az APK a webes appot beágyazva tartalmazza, így net nélkül is indul (a szinkronhoz és az AI-hoz net kell).
Az aláíró kulcs (`android/memomill.jks`) csak az oldaltelepítéshez van, Play Store-hoz újat kell készíteni.

### Alkalmazászár
Beállítások → Alkalmazászár. Kapcsold be a „MemoMill alkalmazászár” szolgáltatást a Kisegítő lehetőségekben,
jelöld ki a zárolt appokat (a TikTok, Instagram, YouTube stb. alapból ki van jelölve).
