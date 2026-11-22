# Privacy policy for Momentum

Version of 2026-11-22.

Momentum is an Android app made by Nicolas Mahn, a private developer in Germany.
Contact for privacy questions: momentum@nicolas-mahn.de

## In short

Momentum has no account, no ads, no analytics and no crash reporting. What you record is stored on your phone. I, the developer, never receive it.

The app uses the internet for one thing: the map under a route you recorded with GPS. Its map images come from my own server. Your route itself is never sent; see "The route map".

A copy leaves the app only in the three cases described under "When a copy leaves the app". Each of them is switched on or started by you or by your phone's settings.

## What Momentum stores on your phone

Momentum stores what you type and tap, and nothing else:

- what you track and how: names, goals, units, reminder times, your wake time and bedtime, the app language;
- your records: activities and workouts with their exercises, sets, weights and duration; measurements such as your body weight; ticks in checklists, for example medication, supplements or care; your answers to your own questions; minutes of timers such as reading; uses of substances such as alcohol or nicotine, with an amount if you enter one, or that there was none;
- routes you record with GPS: where you went and when, during an activity you started recording;
- holidays and sick days you enter;
- what the app works out from this: your streak, Tokens, Freezes and how Mo is doing.

Some of this is information about your health. Momentum reads your location only while you record a route, from the moment you tap Start recording until you pause, finish or discard it. It reads nothing else from your phone: no contacts, no sensors, no other apps' data, no advertising ID.

The data is kept in the app's private storage, which other apps cannot read. Momentum does not add encryption of its own. It relies on the storage encryption and the screen lock of your phone.

## When a copy leaves the app

**1. Backup files in a folder you choose.** Under Settings, Backups, you can choose a local folder on your phone. Momentum then writes copies of all your records and settings into it as `.momentum.json` files. These files are readable text and are not encrypted. Anyone and any app with access to that folder can read them. They stay on your phone when you uninstall Momentum. So that they do not pile up, Momentum removes backups it wrote earlier. It always keeps the two newest, the last one from each of the latest 7 days with a backup, and the last one from each of the latest 3 months with a backup. Days and months are counted in UTC. Other backup files can remain as well, for example ones written before Momentum had this rule, or from before you switched folders. Those written before the rule are removed by it only when you confirm "Tidy up" under Settings, Backups. The files are yours to copy, move or delete. Without a chosen folder, no such files exist.

**2. Android's own backup and the move to a new phone.** Android can back up app data to your Google account and can copy it to a new phone. Momentum takes part in both, like most apps. If backup is switched on in your phone's settings, Android copies Momentum's records and settings (the same content as above) to your Google account. On Android 12 and newer, Momentum allows this cloud copy only when Android can encrypt it with your screen lock. On Android 11 and older, the copy follows your phone's backup setting; it is end-to-end encrypted only from Android 9 on and only if you have set a screen lock. This copy is kept by Google in your account, under Google's terms. I cannot see it. You switch it off in your phone's settings under System, Backup, and you can delete it in your Google account.

**3. What you share yourself.** "Export statistics" writes your plan and every recorded day into one text file and opens Android's share menu. "Share streak" does the same with a picture of your streak. You choose the app that receives it. From then on, that app's rules apply. The latest exported file and picture stay in the app's temporary storage until the next one replaces them.

Nothing else leaves the app. Restoring a backup only reads the file you pick.

A route is part of your records, so it is in these copies too.

## The route map

When Momentum shows a route on a map, it loads the map images (tiles) for that area from momentum.nicolas-mahn.de, my server, rented from Hetzner Online GmbH in Finland. To deliver them, the server receives your phone's internet address and which tiles it asks for, which shows roughly the area the map shows. It does not receive the route, your records or anything that names you. The server keeps no log of these requests. The app keeps tiles it has shown for a while, so a map you saw before does not ask again. The map data is © OpenStreetMap contributors.

No map is loaded unless you record a route or open one you recorded. Without an internet connection the route still shows, without the map under it.

## Reminders

Reminders are created on your phone. A reminder can show the name of what it is about, for example a checklist item, also on your lock screen. You can hide notification content on the lock screen in your phone's settings.

## Permissions

- Notifications: to show reminders.
- Alarms and reminders (exact alarms): so a reminder arrives at the time you set.
- Start after the phone restarts: to set your reminders again.
- Do Not Disturb access: only if you allow it. Momentum can then switch Do Not Disturb on while a timer runs and put it back when you finish.
- Location, precise, while the app is in use: only if you allow it, to record a route. Momentum never asks for location in the background. While a recording runs, a notification shows it, which lets the recording go on with the screen off.
- Internet: to load the route map's images.

The backup folder is chosen through Android's own file picker, so the app needs no storage permission.

## What I receive

From the app: nothing but the map requests described under "The route map", which are not stored.

From Google Play: Google shows developers summary figures such as the number of installs, and crash reports from people who agreed to share diagnostics with Google. That is Google's processing, described in Google's privacy policy. These figures do not contain your records.

If you write to me, I use your message and your address only to answer you.

## Deleting your data

- In the app you can remove single records and delete anything you track.
- Uninstalling Momentum, or "Clear storage" in Android's app settings, deletes everything the app stores on your phone.
- Backup files in a folder you chose stay until you delete them.
- A copy in Android's backup is deleted through your phone's backup settings or your Google account.

There is no account, so there is nothing for me to delete. Your data is kept until you delete it.

## Your rights

Under the GDPR you have the right to access, correct and erase your personal data, to restrict or object to its processing, and to receive it in a portable form. I hold no data from the app, so I cannot look anything up or delete anything for you: all of it is on your phone, in your hands. The backup files and the statistics export give you your data in a readable form.

These rights apply to any message you have sent me. You also have the right to complain to a data protection authority, for example the one where you live.

## Children

Momentum is not directed at children under 13.

## Changes

If the app changes in a way that matters for your privacy, this page changes with it and gets a new date.
