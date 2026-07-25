# LESSONS LEARNED — KmWidget / Flyme Auto

Этот файл фиксирует выводы, ограничения и подходы, полученные в ходе разработки
виджета для Geely Galaxy Starship 7 (Flyme Auto). Обновлять при каждой значимой сессии.

---

## 1. Ограничения Flyme Auto / launcher

### 1.1 App Widget не устанавливается через стандартный механизм

**Проблема**: На Flyme Auto (com.flyme.auto.launcher) долгое нажатие на рабочем столе
не открывает галерею виджетов в привычном Android-смысле. Стандартный метод
«долгое нажатие → Виджеты» не работает.

**Попытки**:
- `Intent.ACTION_CREATE_SHORTCUT` — launcher не реагирует.
- `AppWidgetHost` + `AppWidgetManager.bindAppWidgetId` — требует подпись `platform.p12`.
- Broadcast `com.flyme.auto.launcher.action.TOGGLE_AICY_WIDGET` — открывает Aicy-галерею,
  но только для приложений с `android.uid.system` (подпись platform).
- Прямая запись в `aicywidget.db` (SQLite базу лончера) — launcher все равно проверяет UID.

**Вывод**: Стандартные App Widgets на Flyme Auto **недоступны** для сторонних приложений
без подписи ключом Geely/platform. Обходной путь — **floating overlay** (см. раздел 3).

### 1.2 android:sharedUserId="android.uid.system"

Использование `android:sharedUserId="android.uid.system"` в AndroidManifest делает APK
**неустановимым** через файловый менеджер — система требует подпись platform-ключом.

**Решение**: Убрать из основного манифеста, оставить только в `src/systemUid/AndroidManifest.xml`
как отдельный flavor (не используется в production build).

---

## 2. Car API — требования к потокам (threading)

### 2.1 VehiclePropertyHelper должен создаваться в Main-потоке

**Проблема**: `Car.createCar()` / `CarPropertyManager` внутри используют `Handler`,
которому требуется `Looper`. При создании из фонового потока (`Dispatchers.IO`)
выбрасывается:
```
java.lang.RuntimeException: Can't create handler inside thread that has not called Looper.prepare()
```

**Решение**:
- Создавать `VehiclePropertyHelper` только в Main-потоке.
- В `RangeUpdateService` использовать `Dispatchers.Main` для корутины.
- Передавать уже созданный экземпляр `vehicleHelper` во все вспомогательные классы
  (`RangeCalculator`, `RangeWidgetProvider`), а не создавать внутри них.

### 2.2 Требования к установке для Car API

Для доступа к `CarPropertyManager` (`CAR_MILEAGE`, `CAR_ENERGY` и другим привилегированным
разрешениям) приложение **обязано** быть установлено как системное:

```
/system/priv-app/<package>/<package>.apk
/system/etc/permissions/privapp-permissions-<package>.xml
```

Без этого Car API возвращает `null` или выбрасывает `SecurityException`.

---

## 3. Floating Overlay как альтернатива виджету

Поскольку стандартные App Widgets недоступны на Flyme Auto, реализован **OverlayService** —
плавающее окно поверх всех приложений.

### Реализация

- `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` (SYSTEM_ALERT_WINDOW)
- Запуск через кнопку в `WidgetActivity`
- Автозапуск после перезагрузки через `BootReceiver` (если пользователь включил)
- Позиция сохраняется в `SharedPreferences`, поддерживается drag-and-drop
- Обновление данных каждые 30 секунд через корутины

### Необходимые разрешения (в privapp-permissions XML)

```xml
<permission name="android.permission.SYSTEM_ALERT_WINDOW"/>
<permission name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
```

### Ограничение

На некоторых сборках Flyme Auto `TYPE_APPLICATION_OVERLAY` может быть заблокирован
системным SELinux-политиками. Если overlay не показывается — проверить:
```bash
adb logcat | grep -i "selinux\|denied\|overlay"
```

---

## 4. Сборка и CI/CD

### 4.1 GitHub Actions — настройка ключей

**Проблема**: Actions не имеет `keys/platform.p12` в репозитории (ключи не коммитятся).

**Решение**: Скрипт `setup-keys.sh` загружает AOSP test keys и генерирует `platform.p12`
на лету в CI. Для production нужно загрузить реальный ключ через GitHub Secrets.

**Статус**: Сборка работает, APK доступен в Artifacts после каждого пуша в `main`.

### 4.2 Сборка локально

```powershell
# Из корня проекта (требует Android SDK + ADB)
./deploy.sh            # сборка + деплой обновления
./deploy.sh --fresh    # полная переустановка
```

Или вручную (если нет bash):
```powershell
$adb = ".\platform-tools\adb.exe"
& $adb root && & $adb remount
& $adb push app-privileged-release.apk /system/priv-app/com.starship7.kmwidget/com.starship7.kmwidget.apk
& $adb push privapp-permissions-com.starship7.kmwidget.xml /system/etc/permissions/
& $adb reboot
```

---

## 5. Установка на устройство

### Схема (ADB Root, рекомендуется)

1. `adb devices` — убедиться что устройство подключено
2. `adb root` + `adb remount` — получить права
3. Push APK → `/system/priv-app/<pkg>/`
4. Push permissions XML → `/system/etc/permissions/`
5. `adb reboot`

**Важно**: `adb uninstall` для system priv-app возвращает `DELETE_FAILED_INTERNAL_ERROR` —
это нормально. Старая версия перезаписывается push-ом.

### Запуск приложения вручную

```bash
adb shell am start -n com.starship7.kmwidget/.WidgetActivity
```

---

## 6. Архитектурные решения

| Решение | Причина |
|---|---|
| Floating overlay вместо App Widget | Flyme Auto не поддерживает сторонние виджеты |
| Данные в SQLite (не только в памяти) | Переживает рестарт сервиса, нужна история |
| `Dispatchers.Main` для Car API | Требование `Looper` в Car.createCar() |
| priv-app вместо обычной установки | Нет Car API без системных разрешений |
| Передача `vehicleHelper` экземпляра | Избегаем повторного createCar() из фона |
| `isLoadingState` флаг во фрагментах | Android пересоздаёт Activity при смене языка |

---

## 7. Что можно улучшить (backlog)

- [ ] **Раздельный вывод EV + Fuel** на оверлее (сейчас суммируется)
- [ ] **Настройки через оверлей** — долгое нажатие открывает конфиг
- [ ] **Подписать ключом Geely** — получить platform.p12 даёт доступ к App Widget в Aicy
- [ ] **Проверить SELinux** при первом запуске — логировать успех/ошибку overlay
- [ ] **Тёмная тема оверлея** — сейчас фиксированный стиль
- [ ] **Размер и прозрачность оверлея** — настраиваемые через UI
- [ ] **Виджет в Aicy-галерее** — если удастся получить platform-подпись

---

## 8. Окружение разработки

- **Устройство**: Geely Galaxy Starship 7 (P145), Android Automotive, Flyme Auto
- **ADB ID**: `AE1F2A00014045907`
- **Package**: `com.starship7.kmwidget`
- **priv-app path**: `/system/priv-app/com.starship7.kmwidget/`
- **Android SDK**: в `C:\IT\starship7_km\platform-tools\`
- **Репозиторий**: https://github.com/serzzzh/Starship7_KmWidget
