# Poterx Mobile — warstwa Poterx w aplikacji mobilnej

Fork [OwnTV Mobile](https://github.com/ahXN00/OwnTV_Mobile) zamknięty na panel dostawcy, tak samo jak
Poterx TV (`zdunas82/poterx-iptv-app`) jest forkiem OwnTV na Android TV. Ten plik opisuje tylko to, co
różni się od telewizora; reszta (sealed blob, PIN serwisowy, telemetria, serwer aktualizacji) działa
identycznie i jest opisana w repozytorium TV (`docs/poterx/`).

## Co jest przeniesione z Poterx TV

| Obszar | Telewizor | Telefon |
|---|---|---|
| Wbudowane konto (`LockedSource`) | tak | tak — ten sam format blobu, applicationId `tv.poterx.mobile` |
| Logowanie pierwszego uruchomienia | `LockedOnboarding` (Compose for TV) | `LockedOnboarding` w ramce kreatora Material 3 |
| Drugi login = drugi profil | tak | tak (kafelek „Dodaj profil” w „Kto ogląda?” i Ustawienia › Profil) |
| Ustawienia › Konto | tak | Ustawienia › Źródła i przewodnik › Konto |
| Playlisty / EPG bez adresów | tak | tak (`LockedSourcesPage`, `LockedGuidePage`) |
| Kopia zapasowa, Synchronizacja lokalna, Dodaj źródło | zastąpione notatką | zastąpione notatką (`LockedInfoPage`) |
| Maskowanie hosta (info o strumieniu, dziennik błędów, eksport raportu, pasek synchronizacji) | tak | tak |
| Aktualizacje z serwera dostawcy | `update.json` / `update-test.json` | `update-mobile.json` / `update-mobile-test.json` |
| Telemetria | ping bez pola `app` | ping z polem `app: "mobile"` |
| Przypomnienie o końcu abonamentu | popup z QR | arkusz z QR i klikalnym linkiem do Telegrama |
| Wymuszona aktualizacja (`minVersionCode`) | tak | tak |
| Poprawka „lista skacze po dodaniu do ulubionych” (`PagingIndex`) | tak | tak |
| VPN (WireGuard / OpenVPN, blokada operatora) | tak | **nie** — telefon ma dane komórkowe i własne aplikacje VPN; komunikat o blokadzie operatora zostaje |
| Baner zmiany kanału, przewodnik pod długim OK, CH+/CH- | tak | nie dotyczy (dotyk) |

## Sekrety repozytorium (GitHub › Settings › Secrets and variables › Actions)

Te same nazwy co w repozytorium TV, więc można je skopiować 1:1:

| Sekret | Do czego |
|---|---|
| `POTERX_PLAYLIST_URL` **lub** `POTERX_SERVER` | linijka playlisty (konto wbudowane) lub sam serwer (klient wpisuje login i hasło) |
| `POTERX_SERVICE_PIN` | PIN serwisowy (wylogowanie, kanał testowy aktualizacji) |
| `POTERX_CONTACT` | kontakt do dostawcy na ekranie logowania i Konto |
| `POTERX_TMDB_KEY` | klucz TMDB na plakaty i opisy |
| `POTERX_UPDATE_URL` | serwer aktualizacji, np. `https://app.poter.live` |
| `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | **ten sam keystore co w TV** (certyfikat jest stały na applicationId, a te są różne) |
| `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `VPS_KNOWN_HOSTS` | rsync na serwer aktualizacji (ten sam użytkownik `poterx`) |

Zmienna (nie sekret): `POTERX_MOBILE_MIN_VERSION_CODE` — najniższy kod wersji jeszcze obsługiwany
(wymuszona aktualizacja); puste = 0.

Sekretów `POTERX_VPN_USER` / `POTERX_VPN_PASS` **nie** dodawać — telefon ich nie czyta.

## Wersje i wydania

- Tag `vMAJOR.MINOR.PATCH[.HOTFIX]`, kod wersji `MAJOR*1000000 + MINOR*10000 + PATCH*100 + HOTFIX`
  (0.1.0 = 10000). Numeracja telefonu jest niezależna od telewizora.
- Tag buduje APK, publikuje pre-release na GitHubie i wrzuca wersję na **kanał testowy**
  (`update-mobile-test.json`). Kanał testowy włącza się na telefonie w Ustawienia › Konto › Kanał testowy
  (PIN serwisowy).
- Workflow **Promote update** (ręczny, z numerem wersji) przepisuje ją do `update-mobile.json`,
  `apk/PoterxMobile.apk` i oznacza release jako latest.
- Notatki wydania: `docs/poterx/changelog/<wersja>.md` (po polsku, tylko punkty).

## Serwer aktualizacji (ten sam co TV)

Nowe ścieżki w katalogu www (`/var/www/poterx` lub co ustawiono w `VPS.md` w repo TV):

```
update-mobile.json            # kanał klientów
update-mobile-test.json       # kanał testowy
apk/poterx-mobile-<wersja>.apk
apk/PoterxMobile.apk          # zawsze wersja promowana
changelog-mobile/<wersja>.md, changelog-mobile/latest.md
```

Do Caddy dodać przy regule cache dla APK telewizora:

```
@apk path /apk/poterx-tv-*.apk /apk/poterx-mobile-*.apk
```

oraz `PoterxMobile.apk` obok `PoterxTV.apk` w regule „no-cache”. Telemetria: w `telemetry.py`
pole `app` (patrz repo TV, `docs/poterx/vps/`) — bez niego pingi z telefonu są liczone razem z
telewizorami.

## Budowa lokalna

`poterx.properties` (skopiowany z `poterx.properties.example`) w katalogu głównym, potem
`./gradlew :app:assembleStandardRelease`. Bez pliku i bez zmiennych środowiskowych buduje się zwykłe
OwnTV Mobile (`POTERX_LOCKED = false`).
