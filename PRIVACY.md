# Privacy

PixelBuddy is a launcher. It shows your apps and starts them; it does not collect data about you.

- **No tracking, no analytics, no ads, no accounts.**
- **No telemetry.** Nothing is sent automatically, including crash reports. Crash reports stay on the phone
  until you choose to share them yourself from the settings.
- **Data stays on the device.** Settings, favorites, hidden apps and icon styles are stored in the app's private
  storage. Android's backup may include the settings and the pictures you chose as app icons (not caches) if you
  have device backup turned on.
- **Pictures you choose as app icons** come from the system photo picker, which needs no permission and shows
  PixelBuddy only the picture you pick. A small copy (256 × 256) is kept in the app's private storage and
  deleted when you reset that icon or remove the app.
- **Exported settings** (Settings → Back up & move) go only where you save them with the system file picker. The
  file holds all settings, including your weather place, and the pictures you chose as app icons. Importing a
  file replaces your settings only after you confirm.

## Network access

The only network traffic is the **optional weather** feature. It is off until you turn it on. When it is off,
PixelBuddy makes no network requests at all.

When weather is on, PixelBuddy talks only to [Open-Meteo](https://open-meteo.com/) over HTTPS:

| You chose | What is sent |
|---|---|
| City search | The city name you type, to find the place |
| A city | The city's coordinates, to get the forecast |
| Approximate location | Your coarse location, rounded to about 10 km |

Nothing else is sent: no device identifiers, no app list, no account. Open-Meteo's own privacy policy applies to
those requests: <https://open-meteo.com/en/terms>.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Weather requests only |
| `ACCESS_COARSE_LOCATION` | Only if you choose approximate location for weather; asked when you choose it |
| `REQUEST_DELETE_PACKAGES` | Lets you start an uninstall from the long-press menu (Android asks you to confirm) |
| `SET_WALLPAPER` | Lets you set the launcher gradient as wallpaper |
| `EXPAND_STATUS_BAR` | Swipe down on home opens the notification shade |

### Lock service (optional accessibility service)

Android lets an app lock the screen only through an accessibility service (Device Admin would turn off fingerprint
and face unlock). If you set a gesture to "Lock screen", PixelBuddy explains this and you can turn on its
**PixelBuddy lock** service in Android's accessibility settings. It is off unless you turn it on.

- It does one thing: lock the screen when you use the lock gesture on home.
- It subscribes to no accessibility events and can't retrieve window content, so it can't see the screen, what
  you type or other apps.
- It sends nothing anywhere and stores nothing.
- It turns itself off as soon as no gesture locks the screen. You can also turn it off in the accessibility
  settings at any time.

### Notification access (optional)

The notification dots and the media controls (Settings → Notifications & media) need Android's notification access,
which lets an app read every notification and see which media plays. It is off unless you turn one of them on and
then grant the access in Android's settings.

- PixelBuddy uses it to show a dot next to apps with notifications and, when you tap the dot, the newest one's title
  and text, which you can open or dismiss.
- With media controls on, home shows the playing track (title, artist, album art) while home is visible and sends
  play/pause/skip to that player when you tap them.
- Notifications and track details are held in memory only while they are shown: never stored, logged, put into crash reports or sent
  anywhere.
- When you turn both off, PixelBuddy lets go of the access (it disconnects). You can also revoke it in Android's
  settings at any time; then nothing is shown.

Optional features that need more access (calendar) are opt-in, are explained before you enable them, and are
documented here when they are added.

## Questions

PixelBuddy doesn't take outside issues (see [Contributing](README.md#contributing)). Privacy or security problems
can be reported privately, see [SECURITY.md](SECURITY.md).
