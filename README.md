# TrekDeck Remote — Android-приложение

Подключается к программе TrekDeck на ПК (нужна новая версия `main.go` из проекта ПК — в ней добавлены
эндпоинты для приложения и кнопки). ПК и телефон — в одной Wi-Fi сети.

## Как собрать APK в Android Studio
1. Установите Android Studio: https://developer.android.com/studio (при первом запуске согласитесь со всем, что предложит — SDK скачается сам).
2. File → Open → выберите папку проекта (где лежит `settings.gradle.kts`). Дождитесь, пока внизу закончится «Gradle sync» (первый раз 3–10 минут).
   Если Studio спросит про версию Gradle — выберите 8.9 (или «Use Gradle wrapper / Create wrapper»).
3. Build → Build Bundle(s) / APK(s) → **Build APK(s)**.
4. Внизу появится «Locate» — это папка `app/build/outputs/apk/debug/app-debug.apk`.
5. Перекиньте `app-debug.apk` на телефон (кабель / Telegram / облако), откройте и установите
   (разрешите «Установку из неизвестных источников»).

## Без Android Studio (через GitHub)
1. **Сначала распакуйте ZIP на компьютере.** GitHub не распаковывает архивы: если загрузить сам .zip, сборка не запустится.
2. Создайте репозиторий на github.com → «uploading an existing file» → перетащите ВСЁ содержимое распакованной папки
   (внутри папки `trekdeck-android`: `app`, `.github`, `build.gradle.kts` и т.д.). Папка `.github` скрытая — если её не видно,
   включите «Показывать скрытые элементы» в Проводнике. Файл `.github/workflows/build-apk.yml` обязательно должен попасть в репозиторий.
   Файлы должны лежать в корне репозитория (рядом с `settings.gradle.kts`), а не в подпапке.
3. Вкладка Actions → «Build APK» → Run workflow. Через 3–5 минут внизу страницы запуска в Artifacts будет `TrekDeck-apk` (внутри APK).

## Зависимости (уже прописаны в app/build.gradle.kts)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
Включён ViewBinding (`buildFeatures { viewBinding = true }`).

## Как пользоваться
1. На ПК запустите TrekDeck, в окне будут IP-адрес и PIN.
2. В приложении введите IP (можно вставить целиком адрес вида `http://192.168.1.42:8080/remote`) и PIN → «Подключиться». Если ПК не активирован — введите ключ.
3. Данные сохраняются, при следующем запуске подключение автоматическое.
4. Кнопки на вкладке «Кнопки» берутся из файла `buttons.json` рядом с TrekDeck.exe на ПК
   (создаётся сам). Формат действия: `key:ctrl+c`, `text:Привет`, `boost:ram`, `open:https://...`.
   После правки перезапустите TrekDeck на ПК.

## Файлы
- `LoginActivity.kt` — подключение (IP + PIN) и активация ключа
- `MainActivity.kt` — 3 вкладки (BottomNavigationView)
- `TouchpadFragment.kt` / `ButtonsFragment.kt` / `SettingsFragment.kt` — вкладки
- `TrekDeckClient.kt` — WebSocket (OkHttp), автопереподключение каждые 5 сек
- `ApiService.kt` — REST (Retrofit): /api/status, /api/activate, /api/stats
- `Prefs.kt` — SharedPreferences (IP и PIN) и ссылки; ключ активации на телефоне не сохраняется
