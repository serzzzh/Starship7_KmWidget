# Техническое задание и Архитектура (TECH_SPEC)

## Цель проекта
Создать Android-виджет для рабочего стола ГУ автомобиля Geely Galaxy Starship 7, который рассчитывает и отображает реальный остаток пробега (Range) в километрах, основываясь на недавней статистике вождения.

## Основная проблема
Штатный прогнозатор пробега в авто может отображать идеализированные цифры (например, по циклу CLTC). Виджет должен давать более честный прогноз, основываясь на том, как автомобиль расходовал энергию и топливо в последние минуты/километры.

## Логика расчетов (Текущая реализация)
Пользователь в настройках (`ConfigActivity`) задает "Окно анализа":
- Либо по времени (например, последние 30 минут)
- Либо по дистанции (например, последние 50 км)

Фоновый сервис `RangeUpdateService` раз в 1 минуту читает данные из шины (через `VehiclePropertyHelper`) и пишет в локальную БД (`RangeDatabaseHelper`):
- `timestamp` (время)
- `odometer` (пробег, км)
- `battery` (заряд, %)
- `fuel` (топливо, %)

**Формула расчета в `RangeWidgetProvider`**:
1. Выбираются исторические данные за заданное "Окно анализа".
2. Вычисляется дельта: $\Delta Odo$, $\Delta Bat$, $\Delta Fuel$.
3. Если $\Delta Bat > 1\%$, рассчитывается эффективность батареи: $E_{bat} = \Delta Odo / \Delta Bat$ (км на 1%).
4. Если $\Delta Fuel > 1\%$, рассчитывается эффективность топлива: $E_{fuel} = \Delta Odo / \Delta Fuel$ (км на 1%).
5. Расчетный запас хода EV = $E_{bat} * CurrentBat$.
6. Расчетный запас хода Fuel = $E_{fuel} * CurrentFuel$.
7. Итоговый запас хода = $Range_{EV} + Range_{Fuel}$.

*Примечание: Формулу расчета гибридного режима (когда тратятся и бензин, и батарея одновременно) в будущем можно улучшить или выводить на виджет раздельно (EV: XX км / Fuel: YY км).*

## Компоненты проекта

### Основные (актуальные)
1. **OverlayService** (`Service`) — плавающее окно поверх всех приложений (`SYSTEM_ALERT_WINDOW`).
   Обновляет данные каждые 30 сек. Поддерживает drag-and-drop. Сохраняет позицию.
   **Основной способ отображения** Range — т.к. App Widget недоступен в Flyme Auto.
2. **WidgetActivity** (`Activity`) — точка входа для пользователя. Кнопка включения оверлея,
   настройка конфигурации, статус.
3. **RangeUpdateService** (`Foreground Service`) — раз в минуту читает Car API и пишет в SQLite.
4. **RangeCalculator** — расчет остатка пробега по историческим данным из БД.
5. **RangeDatabaseHelper** (`SQLiteOpenHelper`) — локальная БД (`range_log`). Хранит историю 7 дней.
6. **BootReceiver** (`BroadcastReceiver`) — запускает RangeUpdateService и OverlayService (если включён)
   после перезагрузки устройства.
7. **VehiclePropertyHelper** — обёртка для `CarPropertyManager`. Только чтение (Read-Only).
   **Создаётся только в Main-потоке** (требование Looper/Handler).

### Вспомогательные
- **ConfigActivity** — настройка окна анализа (по времени или км).
- **WidgetPreferences** — хранение конфигурации в SharedPreferences.
- **RangeWidgetProvider** (`AppWidgetProvider`) — резервный App Widget
  (на Flyme Auto не используется из-за ограничений launcher).
- **FlymeWidgetHelper** — попытки открыть Aicy-галерею через broadcast (не работает без platform-подписи).

## Разрешения (Permissions)
Поскольку чтение Car API требует системных разрешений (`CAR_MILEAGE`, `CAR_ENERGY` и др.),
приложение **должно** быть установлено как системное:
- APK → `/system/priv-app/com.starship7.kmwidget/`
- XML → `/system/etc/permissions/privapp-permissions-com.starship7.kmwidget.xml`

Требуемые разрешения в XML:
```xml
<permission name="android.permission.SYSTEM_ALERT_WINDOW"/>
<permission name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<permission name="android.car.permission.CAR_MILEAGE"/>
<permission name="android.car.permission.CAR_ENERGY"/>
```

## Известные ограничения Flyme Auto

> Подробнее: [LESSONS_LEARNED.md](LESSONS_LEARNED.md)

- **App Widget не работает** без platform-подписи Geely. Лончер (`com.flyme.auto.launcher`)
  проверяет UID при добавлении виджета в Aicy-галерею.
- `android:sharedUserId="android.uid.system"` делает APK неустановимым через файловый менеджер.
- `Car.createCar()` требует Main Looper — не вызывать из `Dispatchers.IO`.