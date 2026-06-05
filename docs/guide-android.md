# kazahana Android Supplement Guide

This guide covers features specific to the Android version of kazahana. For common features shared across all platforms (timeline, posts, search, notifications, DM, profile, settings, BSAF, etc.), see the [Desktop User Guide](https://github.com/osprey74/kazahana/blob/main/docs/en/guide/index.md).

---

## Table of Contents

- [Shelter Navigator (Evacuation Assist)](#shelter-navigator-evacuation-assist)
- [Push Notifications](#push-notifications)
- [Sharing to kazahana](#sharing-to-kazahana)
- [Android-Specific Navigation](#android-specific-navigation)
- [Account Switcher](#account-switcher)
- [Deep Links](#deep-links)
- [Differences from Desktop](#differences-from-desktop)

---

## Shelter Navigator (Evacuation Assist)

Added in v3.2.0. This Japan-specific feature detects weather hazard information from the Japan Meteorological Agency (via bsaf-kikikuru-bot) and guides you to the nearest designated evacuation shelter. Shelter data (from the Geospatial Information Authority of Japan) is bundled with the app, so **it works even without an internet connection**.

> **Important:** This feature provides supplementary information based on JMA hazard levels. It does not represent official municipal evacuation orders. Always check official evacuation instructions from your local municipality.

### Enabling the Feature

Shelter Navigator is off by default. To enable it:

1. Go to the **Profile** tab → tap the **Settings** icon.
2. Scroll to the **Evacuation Assist** section.
3. Turn on the **Enable Evacuation Assist** toggle.
4. If bsaf-kikikuru-bot is not yet registered, a confirmation dialog appears. Tap **Enable** to automatically register and follow bsaf-kikikuru-bot via BSAF.

You can optionally select your **Prefecture (manual)** to specify your area. Leaving it on "Auto (from location)" uses your current location. Manual selection is recommended for offline use.

### Warning Banner

When Evacuation Assist is enabled and a weather warning is received for your configured prefecture (or current location), a red banner appears at the bottom of the screen.

![Warning banner display](./images/Android_evaccuation_001.png)

- **Level 3 (yellow):** Weather warning level information issued
- **Level 4 (red):** Check evacuation information
- **Level 5 (pink):** Secure safety immediately

Tap **View shelters** on the banner to open the nearest shelters list. The banner clears automatically when the alert is cancelled or after 6 hours.

### Nearest Shelters List

Shelters are listed in order of distance from your current location. Each entry shows the straight-line distance.

![Nearest shelters list](./images/Android_evaccuation_002.png)

Use the **Hazard type** chips at the top (Flood, Landslide, Storm Surge, Earthquake, Tsunami, Large Fire, Inland Flood, Volcano) to filter shelters. The filter is automatically set based on the type of warning received.

Tap a shelter to view its details, where you can choose **Navigate with Maps** (walking directions in a maps app such as Google Maps) or **Simple Nav (Compass)**.

### Simple Nav (Compass)

A compass-based navigator that works without an internet connection. It uses the device's magnetic sensor to show an arrow pointing toward the selected shelter and displays the straight-line distance in real time.

![Simple Nav (Compass)](./images/Android_evaccuation_003.png)

- Walk in the direction of the arrow — the distance decreases as you get closer.
- If compass accuracy is low, move your device in a figure-8 pattern to calibrate.
- When offline, maps app navigation is unavailable, making this the primary navigation method.

### Offline Use

Shelter data is bundled with the app, so the following features work even in airplane mode:

| Feature | Offline |
|---------|---------|
| Nearest shelters list | Available |
| Simple Nav (Compass) | Available |
| Navigate with Maps | Unavailable (requires internet) |
| Auto prefecture detection | Unavailable (use manual setting) |

> **Note:** Shelter data is sourced from the Geospatial Information Authority of Japan (GSI) Designated Emergency Evacuation Sites. Data may not be fully up to date or may differ from actual sites. Check with your local municipality for the latest information.

### Demo Mode (Try It Out)

You can try the Shelter Navigator even when no weather warnings are active.

1. Open **Settings**.
2. **Tap the version number 5 times** at the bottom of the screen.
3. Demo buttons will appear in the Evacuation Assist section.
4. Tap a demo button to simulate a warning banner and test the shelter list and navigation flow.

> **Note:** Banners shown in demo mode are for testing purposes only and do not reflect actual weather warnings.
---

## Push Notifications

kazahana for Android supports push notifications via Firebase Cloud Messaging (FCM), integrated with the [kazahana-push-backend](https://github.com/osprey74/kazahana-push-backend).

### Enabling Push Notifications

1. Open **Settings** in kazahana.
2. Toggle **Push Notifications** on.
3. On Android 13 and above, a system permission dialog will appear — tap **Allow**.

When disabled, the toggle unregisters your device from the push notification server.

> **Note:** On Android 12 and below, notification permission is granted at install time and no additional dialog is shown.

You can also manage notification permissions later from the Android system Settings app (Apps → Notifications → App notifications):

![Android app notifications](./images/mobile_006.png)

### How It Works

- When enabled, your FCM token is automatically registered with the kazahana push notification server.
- Notifications are delivered for new activity on your account.
- Tapping a notification opens the Notifications tab. If the notification is for a different account, kazahana automatically switches to that account.
- Background polling via WorkManager checks for unread notifications approximately every 15 minutes.

---

## Sharing to kazahana

You can share text and URLs from other apps to kazahana.

### How to Share

1. In any app (e.g., Chrome, another social media app), tap the **Share** button.
2. Select **kazahana** from the share sheet.
3. The kazahana compose screen opens with the shared text/URL pre-filled.
4. Edit the text, add images if needed, and tap **Post**.

### Supported Content

| Content Type | Behavior |
|--------------|----------|
| **URL** | The URL is inserted into the composer. If the source app provides a page title, it is included. |
| **Text** | Plain text is inserted into the composer. |

> **Note:** Image sharing via the Android share sheet is not currently supported. To post images, use the photo picker within the kazahana compose screen.

---

## Android-Specific Navigation

### Bottom Navigation Bar

The bottom navigation bar uses Material 3 design with 5 tabs: Home, Search, Notifications, Messages, and Profile.

- **Re-tap a tab** to refresh and scroll to the top.
- The Notifications tab shows an unread count badge.

### Gestures

| Gesture | Action |
|---------|--------|
| **Pull down** | Refresh the current feed (pull-to-refresh) |
| **Pinch zoom** | Zoom in/out on full-screen images |
| **Double tap** | Zoom in on full-screen images |
| **Long press + drag** | Reorder feeds in Feed Management |

---

## Account Switcher

When you have 2 or more accounts saved, a dedicated account switcher becomes available.

### How to Use

1. On the Home screen, tap the **account handle** (`@yourhandle ▼`) in the top bar.
2. A bottom sheet appears showing all saved accounts.
3. Tap an account to switch. The active account is shown in bold with an "Active" label.
4. Tap **+ Add Account** to add a new account.

![Account switcher bottom sheet](./images/mobile_005.png)

You can also manage accounts in **Settings → Accounts**, where you can switch accounts or tap **Logout** to remove an account.

---

## Deep Links

kazahana for Android responds to `kazahana://` URLs and `https://bsky.app` links:

| URL Pattern | Action |
|-------------|--------|
| `kazahana://profile/{handle}` | Opens the user's profile |
| `kazahana://post/{at_uri}` | Opens the post thread |
| `kazahana://compose?text=...` | Opens the composer with pre-filled text |
| `kazahana://hashtag/{tag}` | Searches for the hashtag |
| `https://bsky.app/profile/{handle}` | Opens the user's profile |
| `https://bsky.app/profile/{handle}/post/{rkey}` | Opens the post thread |

---

## Differences from Desktop

### Features Available on Android Only

| Feature | Description |
|---------|-------------|
| Shelter Navigator | Guides to nearest evacuation shelters during weather warnings (works offline) |
| Push notifications | Real-time notifications via FCM |
| Share intent | Share text/URLs from other apps to kazahana |
| Account switcher bottom sheet | Quick account switching from the home screen |
| Pull-to-refresh | Swipe down to refresh on all screens |
| Background polling | Periodic notification check via WorkManager |

### Desktop Features Not Available on Android

| Feature | Reason |
|---------|--------|
| Bookmarklet | Not applicable on Android |
| Auto-launch on OS startup | Not applicable on Android |
| System tray minimize | Not applicable on Android |
| Window management | Android apps are full-screen |

### Differences from iOS

| Feature | iOS | Android |
|---------|-----|---------|
| Shelter Navigator | Available | Available |
| Supporter Badge (IAP) | Available | Not available |
| Simple Nav maps integration | Apple Maps | Maps app such as Google Maps |
| Image sharing via share sheet | Supported | Text/URL only |
| Push notification toggle | iOS Settings app only | In-app toggle in Settings |
| Account switcher | Settings only | Bottom sheet from Home + Settings |
