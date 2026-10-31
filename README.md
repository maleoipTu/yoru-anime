<div align="center">

<img src="docs/icon.png" width="112" alt="Иконка Yoru">

# Yoru

**Аниме-клиент YummyAnime для Windows и Android — без рекламы, с аккаунтом, очками и профилем, который хочется украсить.**

[![Сборка](https://github.com/maleoipTu/yoru-anime/actions/workflows/build.yml/badge.svg)](https://github.com/maleoipTu/yoru-anime/actions/workflows/build.yml)
[![Последняя сборка](https://img.shields.io/github/release-date-pre/maleoipTu/yoru-anime?label=%D0%BF%D0%BE%D1%81%D0%BB%D0%B5%D0%B4%D0%BD%D1%8F%D1%8F%20%D1%81%D0%B1%D0%BE%D1%80%D0%BA%D0%B0&color=ff4d67)](https://github.com/maleoipTu/yoru-anime/releases/tag/nightly)
![Windows](https://img.shields.io/badge/Windows-10%20%7C%2011-1f1f26?logo=windows)
![Android](https://img.shields.io/badge/Android-8.0%2B-1f1f26?logo=android)

[**⬇ Установщик для Windows**](https://github.com/maleoipTu/yoru-anime/releases/download/nightly/Yoru-Setup.exe) · [**⬇ APK для Android**](https://github.com/maleoipTu/yoru-anime/releases/download/nightly/Yoru-Android.apk) · [Все сборки](https://github.com/maleoipTu/yoru-anime/releases)

</div>

---

## Что умеет

| | |
|---|---|
| 🎬 **Плеер без рекламы** | Kodik открывается в собственном плеере Yoru: качество, скорость, перемотка, пропуск опенинга (+85 с), автопереход к следующей серии, позиция сохраняется. |
| 🔎 **Удобный поиск** | Подсказки прямо при вводе названия, фильтры по жанрам, годам и возрастному рейтингу, сортировки и случайная подборка. |
| 👤 **Профиль как в Discord** | Баннер и аватар (в том числе анимированные GIF и WebM), «Обо мне», местоимения, статус, цвета темы профиля, витрина любимого аниме. |
| ◆ **Очки и украшения** | Очки за реальный просмотр, уровни, дни подряд, 15 достижений. Украшения как в Discord: ушки, нимб, корона и венок сакуры на аватаре, анимированные эффекты профиля (искры, лепестки, светлячки, звездопад), рамки профиля (неон, пламя, созвездие), значки и цвета ника, сезонные коллекции. |
| 📚 **Списки и друзья** | «Смотрю / Запланировано / Просмотрено / Брошено» синхронно на ПК и телефоне, друзья, лента их просмотров, оценки и отзывы. |
| 👥 **Смотреть вместе** | Комната по коду: общая пауза, перемотка и выбор серии, чат и реакции поверх видео. |
| 💬 **Обсуждение серии** | Комментарии с таймкодами (нажми — и видео перемотается к моменту), спойлеры и эмодзи-реакции. |
| ⏭ **Точный пропуск заставок** | Зрители отмечают начало и конец опенинга и эндинга — кнопка пропуска ведёт ровно на конец. |
| 🎁 **Подарки и рейтинг** | Очки и украшения в подарок друзьям, рейтинг недели среди друзей и всех зрителей. |
| ✨ **Год в Yoru** | Сводка за год историями, как Spotify Wrapped: часы, аниме года, жанры, привычки, топ-% зрителей. |
| 📊 **Статистика и «Для тебя»** | Часы, серии, средняя оценка, любимые жанры и дни недели в профиле; рекомендации по жанрам и от друзей; рулетка случайного аниме. |
| 🔔 **Новые серии и Discord** | Уведомление о новой серии из «Смотрю» и избранного; на Windows — статус «Смотрит …» в Discord. |
| ⇩ **Перенос с YummyAnime** | Списки и оценки со старого сайта (old.yummyani.me) переносятся по ссылке на профиль. |
| ⟳ **Автообновление** | Установщик для Windows и проверка обновлений на Android: новая версия ставится в пару кликов. |

## Установка

**Windows.** Скачайте [Yoru-Setup.exe](https://github.com/maleoipTu/yoru-anime/releases/download/nightly/Yoru-Setup.exe) и запустите — права администратора не нужны, Yoru ставится в профиль пользователя и дальше обновляется сам. Нужен Microsoft Edge WebView2 Runtime (в Windows 11 уже есть). Переносная версия без установки — `Yoru-Windows.zip` в [той же сборке](https://github.com/maleoipTu/yoru-anime/releases/tag/nightly).

**Android.** Скачайте [Yoru-Android.apk](https://github.com/maleoipTu/yoru-anime/releases/download/nightly/Yoru-Android.apk) на телефон и откройте; если Android спросит, разрешите установку из этого источника. Приложение само сообщит о новой версии.

При первом запуске Yoru попросит войти или зарегистрироваться — аккаунт нужен для профиля, очков, списков и друзей.

### Discord

Чтобы друзья в Discord видели «Смотрит …» с названием и серией, создайте приложение на [discord.com/developers/applications](https://discord.com/developers/applications) (имя — «Yoru»), скопируйте **Application ID** и вставьте его в Yoru → Настройки → «Уведомления и Discord». Discord должен быть запущен на этом же ПК.

## Как устроено

- **Windows** — C# / WPF (.NET 10), одно окно со страницами; плеер — HTML5 + hls.js внутри WebView2, видео идёт через встроенный прокси приложения.
- **Android** — Java, нативные элементы интерфейса, плеер на Media3 ExoPlayer.
- **Сервер** — Supabase: аккаунты, Postgres с правилами доступа (RLS) и серверными функциями для очков, покупок и модерации, хранилище аватаров и баннеров. Схема — в [`supabase/migrations`](supabase/migrations), проверки — в [`supabase/tests`](supabase/tests).
- **CI** — GitHub Actions собирает и тестирует обе платформы и базу, а каждая сборка из `main` публикуется как pre-release [nightly](https://github.com/maleoipTu/yoru-anime/releases/tag/nightly).

Каталог и видео — через официальный API YummyAnime (`api.yani.tv`); API заявлен владельцем для личного использования. Yoru — независимый клиент; доступность серий и видео зависит от YummyAnime и поставщиков плееров.

## Данные и приватность

- Аккаунт Yoru хранит профиль, очки, украшения, списки, оценки и отзывы. Списки, оценки и отзывы публичные: их видят другие пользователи. Заявки в друзья видят только их участники.
- Сессия входа хранится на устройстве: на Windows она зашифрована средствами системы, на Android лежит в приватных данных приложения.
- Серии не скачиваются. Наличие видео, озвучек и качества зависит от YummyAnime и поставщиков плееров. Если серия не открывается, выберите другую озвучку или источник.

## Сборка из исходников

Установите стабильный .NET SDK 10 или более новый совместимый SDK.

```powershell
dotnet publish Yoru/Yoru.csproj -c Release -r win-x64 --self-contained true -p:DebugType=None -p:DebugSymbols=false -o outputs/Yoru
dotnet run --project Tests/Tests.csproj -c Release -r win-x64 --self-contained true
```

Для первого восстановления зависимостей нужен интернет. NuGet-зависимость: Microsoft.Web.WebView2. Постеры и данные каталога принадлежат YummyAnime и правообладателям.

Для Android нужны JDK 17 или 21, Android SDK Platform 35 и Build Tools 35.0.0. Gradle Wrapper включён в исходники.

```powershell
$env:ANDROID_HOME='C:\Android\Sdk'
.\android\gradlew.bat -p android assembleDebug testDebugUnitTest lintDebug
node Tests/player-host.test.mjs
```
