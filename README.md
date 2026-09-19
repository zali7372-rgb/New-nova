# NOVA — Magyar nyelvű AI asszisztens Androidra

NOVA egy natív Android alkalmazás: egy moduláris, magyar nyelvű személyes AI
asszisztens, amely alkalmazásokat tud nyitni, emlékszik dolgokra, kontextusban
tud beszélgetni, internetes keresést tud indítani, és eszközfunkciókat tud
vezérelni — mindezt úgy, hogy soha ne omoljon össze, ha egy külső AI
szolgáltatás éppen nem elérhető.

Ez nem csak egy felület egy külső chatbothoz: NOVA saját intent-felismerő,
kontextuskezelő, memória- és skill-rendszerrel rendelkezik, és az AI
szolgáltatót (helyi/offline vagy távoli) futásidőben lehet cserélni anélkül,
hogy bármelyik más komponenshez hozzá kellene nyúlni.

---

## Tartalomjegyzék

1. [Funkciók](#funkciók)
2. [Architektúra](#architektúra)
3. [Követelmények](#követelmények)
4. [Klónozás és build](#klónozás-és-build)
5. [Gradle wrapper — fontos megjegyzés](#gradle-wrapper--fontos-megjegyzés)
6. [Az APK telepítése](#az-apk-telepítése)
7. [AI szolgáltatók konfigurálása](#ai-szolgáltatók-konfigurálása)
8. [Engedélyek](#engedélyek)
9. [Hangvezérlés / folyamatos beszélgetési mód](#hangvezérlés--folyamatos-beszélgetési-mód)
10. [Memória rendszer](#memória-rendszer)
11. [GitHub Actions](#github-actions)
12. [Tesztek](#tesztek)
13. [Ismert korlátok](#ismert-korlátok)
14. [Hibaelhárítás](#hibaelhárítás)

---

## Funkciók

- **Magyar nyelvű felismerés és felület** — a teljes UI és a beszédfelismerés
  is magyarul működik, és NOVA szinonimákat/ragozási variánsokat is felismer
  ("nyisd meg", "indítsd el", "discordot", "discordos" stb.), nem csak pontos
  string-egyezéseket.
- **Alkalmazás-indítás** — a ténylegesen telepített, indítható alkalmazások
  listáját olvassa ki futásidőben (nincs kemény kódolt lista), és
  alias-/fuzzy-egyezés alapján találja meg a helyeset.
- **Eszközfunkciók** — Wi-Fi, Bluetooth, kamera, naptár, óra, névjegyek,
  értesítési/alkalmazás-beállítások megnyitása szabványos Android intentekkel.
- **Helyi memória** — Room adatbázisban tárolt, kategorizált emlékek
  (preferencia, tény, alias, stb.), amiket a Beállításokban meg is lehet
  tekinteni és törölni.
- **Kontextuskezelés** — rövid követő kérdéseket ("És Szegeden?") az előző
  témához tud kötni.
- **Valódi internetes keresés** — a keresés ténylegesen megnyit egy
  keresőmotor-találati oldalt a böngészőben; NOVA soha nem állítja, hogy
  "megtalálta" a választ, ha valójában nem keresett.
- **AI szolgáltató-lánc** — Helyi (offline, szabály-alapú) → Távoli
  (konfigurálható HTTPS API) → Vészhelyzeti válaszadó. Az alkalmazás sosem
  omlik össze pusztán azért, mert egy API nem elérhető.
- **Hangvezérlés** — magyar beszédfelismerés, TTS, folyamatos beszélgetési
  mód (nem kell minden mondat előtt kimondani, hogy "Nova").
- **Skill-rendszer** — minden képesség (alkalmazásindítás, keresés, memória,
  időjárás stb.) egy önálló, tesztelhető `Skill` implementáció.

## Architektúra

```
UI (Activity/Fragment)
     ↓
ViewModel (MainViewModel)
     ↓
ConversationEngine  (core/ConversationEngine.kt)
     ↓
Planner  →  IntentEngine   (több lépéses parancsok szétbontása + osztályozás)
     ↓
CommandProcessor  (ContextManager-rel kontextust old fel)
     ↓
SkillManager → Skill (AppSkill, BrowserSkill, SearchSkill, MemorySkill,
                       DeviceSkill, TimeSkill, WeatherSkill, SettingsSkill,
                       VoiceSkill, ConversationSkill)
     ↓
Services / Android API-k (AppManager, DeviceActionManager, VoiceManager,
                           MemoryManager → Room, AIProviderManager)
```

Csomagstruktúra:

```
com.nova.assistant/
├── core/
│   ├── ai/            AIProvider, LocalAIProvider, RemoteAIProvider,
│   │                  FallbackAIProvider, AIProviderManager
│   ├── intent/        IntentEngine, NovaIntent, IntentResult
│   ├── context/       ContextManager
│   ├── memory/        MemoryManager
│   ├── personality/   PersonalityManager
│   ├── planner/       Planner
│   ├── response/      ResponseGenerator
│   ├── command/       CommandProcessor
│   └── skills/        Skill interface + minden konkrét skill
├── system/
│   ├── apps/          AppManager (telepített alkalmazások, aliasok)
│   ├── device/        DeviceActionManager (rendszerfunkciók)
│   ├── permissions/   PermissionManager
│   └── voice/         VoiceManager (SpeechRecognizer + TTS állapotgép)
├── data/
│   ├── db/            Room entitások, DAO-k, adatbázis
│   └── repository/    MemoryRepository, SettingsRepository
├── service/           NovaListeningService (előtér-szolgáltatás)
├── ui/
│   ├── main/          MainActivity, MainViewModel, chat lista
│   ├── settings/      SettingsActivity (PreferenceFragmentCompat)
│   ├── memory/        MemoryActivity (emlékek megtekintése/törlése)
│   └── aliases/       AliasManagerActivity
└── NovaApplication.kt Kézi, framework nélküli dependency injection
```

## Követelmények

- Android Studio (Koala vagy újabb ajánlott)
- JDK 17
- Android SDK, `compileSdk`/`targetSdk` 35, `minSdk` 26
- Internetkapcsolat az első Gradle-szinkronizáláshoz (függőségek letöltése)

## Klónozás és build

```bash
git clone <a te repository URL-ed>
cd NOVA
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

A kész debug APK itt jön létre:
`app/build/outputs/apk/debug/app-debug.apk`

## Gradle wrapper — fontos megjegyzés

Ez a repository tartalmazza a `gradlew`, `gradlew.bat` szkripteket és a
`gradle/wrapper/gradle-wrapper.properties` fájlt, de a
`gradle/wrapper/gradle-wrapper.jar` egy **bináris** fájl, amit a Gradle
szervereiről kell letölteni — ezt nem lehet forráskódként, szövegesen
legenerálni. Az első klónozás után **egyszer** futtasd le a következőt egy
géppel, amin telepítve van a Gradle (vagy nyisd meg a projektet Android
Studióban, ami ezt automatikusan megteszi):

```bash
gradle wrapper --gradle-version 8.9
git add gradle/wrapper/gradle-wrapper.jar
git commit -m "Add gradle-wrapper.jar"
```

A mellékelt `.github/workflows/android.yml` ezt a lépést automatikusan
elvégzi CI-ban is, ha a jar hiányzik, szóval a GitHub Actions build enélkül
is lefut.

## Az APK telepítése

1. Build után másold át az `app-debug.apk` fájlt a telefonodra, vagy
2. Csatlakoztasd a telefont USB-n, és futtasd: `./gradlew installDebug`

Az "Ismeretlen forrásból származó alkalmazások" engedélyt engedélyezned kell,
ha nem a Play Store-on keresztül telepíted.

## AI szolgáltatók konfigurálása

NOVA **teljesen működik konfiguráció nélkül** — ilyenkor a `LocalAIProvider`
(szabály-alapú, offline) válaszol. Ha szeretnéd bekapcsolni a távoli AI
szolgáltatót:

1. Másold le `secrets.properties.example` → `secrets.properties`
   (projekt gyökér, ugyanott mint a `build.gradle.kts`).
2. Töltsd ki:
   ```properties
   NOVA_REMOTE_AI_API_KEY=a-saját-kulcsod
   NOVA_REMOTE_AI_ENDPOINT=https://api.anthropic.com/v1/messages
   ```
3. Buildeld újra az alkalmazást.

A `secrets.properties` a `.gitignore`-ban szerepel — **soha nem kerül be a
git történetbe**. Alternatívaként a felhasználó közvetlenül az alkalmazásban,
a Beállítások képernyőn is megadhat egy saját API kulcsot; ez
`EncryptedSharedPreferences`-ben, titkosítva kerül tárolásra a készüléken, és
felülírja a build-time kulcsot.

Soha ne írj be valódi API kulcsot a `gradle.properties`, a forráskód, vagy
bármilyen git-be commitolt fájlba.

## Engedélyek

| Engedély | Miért kell |
|---|---|
| `RECORD_AUDIO` | Hangvezérléshez (beszédfelismerés) |
| `POST_NOTIFICATIONS` | A háttér-hallgatás állapotának mutatásához (Android 13+) |
| `INTERNET` | Távoli AI, időjárás, internetes keresés |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | A háttérben futó ébresztőszó-figyeléshez |
| `QUERY_ALL_PACKAGES` | A telepített alkalmazások listázásához (Android 11+) |

Minden engedélyt csak akkor kér az alkalmazás, amikor a felhasználó ténylegesen
használni akarja az adott funkciót (pl. a mikrofon gombra koppintáskor) — nem
induláskor, tömegesen.

## Hangvezérlés / folyamatos beszélgetési mód

```
Felhasználó: "Nova"
NOVA:        "Hallgatlak."
Felhasználó: "Nyisd meg a YouTube-ot."
NOVA:        "Rendben."
```

Ha a Beállításokban bekapcsolod a "Folyamatos beszélgetési mód"-ot, NOVA a
válasza után automatikusan újra hallgatni kezd egy rövid ideig, így nem kell
minden mondat előtt kimondanod, hogy "Nova".

**Fontos, őszinte korlátozás:** Android erősen korlátozza, mit tehet egy
alkalmazás a mikrofonnal, ha nem fut aktív előtér-szolgáltatás. A
`NovaListeningService` egy valódi, a felhasználó számára is látható
előtér-szolgáltatás (állandó értesítéssel), amíg fut — de ha az operációs
rendszer (Doze mód, gyártói akkumulátor-optimalizálás) mégis leállítja, az
alkalmazás ezt őszintén jelzi az állapotjelzőn (`NOVA OFFLINE` /
hibaállapot), és lehetőséget ad az újraindításra — nem tesz úgy, mintha
továbbra is hallgatna.

## Memória rendszer

A `Nova, jegyezd meg, hogy...` paranccsal elmentett tények egy helyi Room
adatbázisban tárolódnak (`nova_memory.db`), kategorizálva (preferencia, tény,
alias, stb.). Ezek:

- **soha nem kerülnek automatikus felhőmentésbe** (lásd
  `res/xml/data_extraction_rules.xml`),
- csak akkor kerülnek távoli AI szolgáltatóhoz, ha egy kérdés megválaszolásához
  ténylegesen releváns kontextusként szükségesek, és csak akkor, ha a távoli
  szolgáltató egyáltalán be van kapcsolva,
- a Beállítások → "Elmentett emlékek megtekintése" alatt egyenként vagy
  tömegesen törölhetők.

## GitHub Actions

A `.github/workflows/android.yml` minden push/PR eseményre lefut a `main`
ágon, és:

1. Checkoutolja a repót
2. Telepíti a JDK 17-et és az Android SDK-t
3. Szükség esetén legenerálja a hiányzó `gradle-wrapper.jar`-t
4. Lefuttatja az unit teszteket
5. Buildeli a debug APK-t
6. Feltölti az APK-t és a teszteredményeket build artifact-ként

## Tesztek

```bash
./gradlew testDebugUnitTest
```

Lefedett területek (`app/src/test/java/com/nova/assistant/`):

- **IntentEngineTest** — magyar parancsvariánsok helyes felismerése
- **AppManagerTest** — alias-egyezés, egyéni aliasok, elgépelés-tűrő (fuzzy)
  keresés, "nem található" eset kezelése
- **MemoryManagerTest** — mentés/felidézés/törlés, releváns tények keresése
- **AIProviderManagerTest** — a szolgáltató-lánc mindig ad választ, még akkor
  is, ha minden szolgáltató elérhetetlen vagy hibázik
- **SkillManagerAndCommandProcessorTest** — helyes intent → skill routing,
  többlépéses parancsok végrehajtása, kontextusfeloldás

## Ismert korlátok

- A háttérbeli, folyamatos ébresztőszó-figyelés Android OS-szintű
  korlátozások (Doze, gyártói optimalizálás) miatt nem garantálható 100%-ban
  — lásd fentebb.
- A `SearchSkill` egy valódi keresési találati oldalt nyit meg a
  böngészőben; nem dolgozza fel/olvassa be maga a találatok tartalmát (ehhez
  külön, a felhasználó saját kulcsával konfigurált keresés-API kellene).
- Az időjárás-lekérdezés az Open-Meteo ingyenes, kulcs nélküli API-ját
  használja; nagyon ritka/kis településeknél előfordulhat, hogy a
  geokódolás nem talál pontos találatot.

## Hibaelhárítás

**"Nem sikerül a Gradle sync / build"** — ellenőrizd, hogy létrehoztad-e a
`gradle-wrapper.jar`-t (lásd fentebb), és hogy JDK 17 van-e beállítva.

**"A mikrofon gomb nem reagál"** — ellenőrizd, hogy megadtad-e a
`RECORD_AUDIO` engedélyt (Beállítások → Alkalmazások → NOVA → Engedélyek).

**"NOVA nem ismeri fel az alkalmazásomat"** — győződj meg róla, hogy az
alkalmazás valóban telepítve van és rendelkezik indítható (LAUNCHER) intent
filterrel; adj hozzá egyéni aliast a Beállítások → Alkalmazás aliasok alatt.

**"A távoli AI nem válaszol"** — ellenőrizd, hogy be van-e állítva API kulcs
(build-time `secrets.properties` vagy a Beállítások képernyőn), és hogy van-e
internetkapcsolat. Ha nincs, NOVA automatikusan a helyi, offline válaszadóra
vált — ez a várt, biztonságos viselkedés, nem hiba.
