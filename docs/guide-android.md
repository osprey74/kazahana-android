# kazahana Android Supplement Guide

This guide covers features specific to the Android version of kazahana. For common features shared across all platforms (timeline, posts, search, notifications, DM, profile, settings, BSAF, etc.), see the [Desktop User Guide](https://github.com/osprey74/kazahana/blob/main/docs/en/guide/index.md).

---

## Table of Contents

- [Push Notifications](#push-notifications)
- [Sharing to kazahana](#sharing-to-kazahana)
- [Android-Specific Navigation](#android-specific-navigation)
- [Account Switcher](#account-switcher)
- [Deep Links](#deep-links)
- [Differences from Desktop](#differences-from-desktop)

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
| Supporter Badge (IAP) | Available | Not available |
| Image sharing via share sheet | Supported | Text/URL only |
| Push notification toggle | iOS Settings app only | In-app toggle in Settings |
| Account switcher | Settings only | Bottom sheet from Home + Settings |
