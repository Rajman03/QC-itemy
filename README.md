# QC-Itemy

Projekt jest udostępniony na licencji [MIT](LICENSE).

Plugin na Paper 1.21.4. Dodaje specjalny spawner Striderów z menu, poziomami i dochodem wypłacanym przez Vault.

## Wymagania

- Paper 1.21.4 i Java 21
- Vault
- Plugin ekonomii obsługujący Vault, np. EssentialsX Economy

Plugin wyłączy się przy starcie, jeśli Vault lub dostawca ekonomii nie jest dostępny. Vault jest mostem API; sam nie dodaje ekonomii.

## Budowanie

Projekt używa Maven. Uruchom `mvn package`; gotowy plik będzie w `target/QC-Itemy-1.0.0.jar`. Paper API i Vault API są zależnościami `provided` i nie są dołączane do JAR-a.

## Użycie

- `/ss give <gracz> [typ] [poziom]` — nadaje spawner, np. `/ss give Steve PIG 2` (uprawnienie `striderspawner.admin`, domyślnie OP).
- `/itemy` — otwiera wspólny katalog itemów (`striderspawner.admin`). Kliknięcie pozycji daje jej kopię; miecz Heart jest darmowy, a wbudowane typy spawnerów są chronione przed usunięciem.
- `/item boski-dirt give <gracz>` — nadaje Boski Dirt online'emu graczowi (`striderspawner.admin`).
- `/item <id> set` — dodaje lub aktualizuje item w katalogu na podstawie dokładnie jednej sztuki trzymanej w głównej ręce.
- `/item <id> remove` — usuwa własny item z katalogu.
- `/item heart set` — ustawia item, który może wypaść jako serce przy kopaniu stone Kilofem N1; trzymasz dokładnie jedną sztukę w głównej ręce.

## Struktura projektu

- `StriderSpawnerPlugin` uruchamia plugin, rejestruje komendy oraz obsługuje wspólne menu i logikę spawnerów.
- `SpawnerProgression` zawiera reguły poziomów spawnerów niezależnie od Bukkitowych zdarzeń.
- `items/spawners/`, `items/kilofy/`, `items/n1mc/` i `items/boskidirt/` to osobne moduły przedmiotów.
- Mechaniki przedmiotów znajdują się w ich modułach. Obsługa zdarzeń części istniejących przedmiotów jest jeszcze przekazywana przez główną klasę pluginu; Boski Dirt sam rejestruje swoje zdarzenia.
- W module Boskiego Dirta lore, losowanie buffa i X-Ray są osobnymi klasami. Dzięki temu tekst itemu i te moce można edytować niezależnie od walki, furii, totemu oraz klatki.
- Konfiguracje modułów znajdują się w `src/main/resources/items/<kategoria>/` i są kopiowane do `plugins/QC-Itemy/items/<kategoria>/`.
- `items/custom/` przechowuje kod i dane katalogu własnych przedmiotów.

Nowy typ itemu dodaj jako osobny pakiet w `items/`, z własną klasą obsługi, konfiguracją w `src/main/resources/items/<kategoria>/` i rejestracją modułu podczas `onEnable`.

Własne pozycje katalogu zapisują się osobno: `plugins/QC-Itemy/items/custom/<id>/config.yml`. Każdy item ma swój folder i konfigurację; stare pliki `item.yml` są migrowane automatycznie. ID może mieć 1–32 małych liter, cyfr, myślników lub podkreśleń; prefiks `default_` jest zarezerwowany dla pozycji pluginu. Dotychczasowy `items.yml` zostanie przeniesiony do folderów przy uruchomieniu.

Ustawienia efektu i atrybutów miecza znajdują się w `plugins/QC-Itemy/items/n1mc/config.yml`.

Kilofy konfiguruje się w `plugins/QC-Itemy/items/kilofy/config.yml`. Domyślnie dostępny jest Kilof N1 oraz kilofy poziomów 3–6. Aby dodać następny poziom, dopisz sekcję pod `levels`, np. `7`, ustaw `name`, `area-size`, `uses` i poziomy zaklęć. `area-size: 7` niszczy obszar 7×7, a `uses: 0` daje nieograniczoną liczbę użyć. `max-area-size` domyślnie wynosi 15 i ogranicza maksymalną szerokość obszaru.
- Postaw spawner i kliknij go prawym przyciskiem, aby otworzyć menu.
- Połącz trzy spawnery tego samego typu i poziomu w stole rzemieślniczym. Ich poziomy się sumują (trzy spawner'y poziomu 1 dają poziom 3).
- Typ moba spawnera jest stały i nie można go zmienić jajkiem przywołania.
- Kliknij szmaragd, aby wypłacić zgromadzony dochód na konto Vault.
- Saldo i wypłata spawnerów są w pełnych dolarach; ułamek naliczonego czasu zostaje zachowany do uzbierania kolejnego dolara.

W `plugins/QC-Itemy/items/spawners/config.yml` możesz dodawać typy pod `spawners`, ustawiać ich dochód i maksymalny poziom oraz szansę (`drop.chance`) i liczbę wypadających sztuk. Typy są powiązane: spawner `PIG` może wypaść tylko ze świni, `COW` tylko z krowy itd. Szansa jest ułamkiem od `0.0` do `1.0` (np. `0.05` to 5%). Stary `config.yml` jest nadal odczytywany podczas pierwszego uruchomienia jako źródło migracji ustawień.
