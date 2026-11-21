# Privacy

PixelBuddy is a launcher. It shows your apps and starts them; it does not collect data about you.

- **No tracking, no analytics, no ads, no accounts.**
- **No telemetry.** Nothing is sent automatically, including crash reports. Crash reports stay on the phone
  until you choose to share them yourself from the settings.
- **Data stays on the device.** Settings, favorites, hidden apps and icon styles are stored in the app's private
  storage. Android's backup may include the settings (not caches) if you have device backup turned on.

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

Optional features that need more access (notification access, accessibility service, calendar) are opt-in, are
explained before you enable them, and are documented here when they are added.

## Questions

Open an issue, or for anything sensitive see [SECURITY.md](SECURITY.md).
