# 🚨 Pedal - Emergency Alert App

Pedal is an **Android emergency response app** designed to work in tandem with a hardware fall detection device (e.g., ESP32). It ensures that in the event of a fall or accident, help is summoned automatically and immediately.

---

## 🔥 Features

✅ **Bluetooth Integration:** Seamlessly connects to external fall detection hardware.
✅ **Automated Fall Detection:** Listens for "IMPACT_DETECTED" signals from the connected device.
✅ **30-Second Grace Period:** A countdown timer allows users to cancel false alarms.
✅ **Visual & Audio Alerts:** Triggers a loud siren and a visual wave animation to alert bystanders.
✅ **Smart Location Sharing:** Fetches GPS/Network location and sends a Google Maps link via SMS.
✅ **Auto-Dialing:** Automatically calls the pre-selected emergency contact after the countdown.

---

## 🚀 How It Works

### 1. Setup & Connection
1.  **Select Contact**: Upon launching, the user must select a trusted emergency contact from their phone book.
2.  **Connect Device**: The user pairs and connects to their Bluetooth fall detection device (e.g., ESP32).
    *   The app uses the standard Serial Port Profile (SPP) UUID: `00001101-0000-1000-8000-00805F9B34FB`.
    *   Once connected, the app enters a listening mode, waiting for data.

### 2. Fall Detection Trigger
The app continuously monitors the Bluetooth stream. If it receives the specific string **`"IMPACT_DETECTED"`**, the emergency protocol is immediately triggered.

### 3. The Emergency Sequence
Once a fall is detected:
*   **Visual Alert**: A red expanding wave animation appears on the screen to indicate an active emergency.
*   **Audio Alert**: The phone's media volume is set to **100%**, and a loud siren alarm starts playing.
*   **Countdown**: A **30-second countdown** begins.
    *   *If the user is conscious and okay, they can press the **STOP** button to cancel the alarm and reset the system.*

### 4. Emergency Response (Post-Countdown)
If the countdown reaches zero (no cancellation):
1.  **Stop Alarm**: The siren stops to allow for clear communication.
2.  **Send SMS**:
    *   The app constructs an SOS message: *"🚨 SOS ALERT! I need help immediately! A fall has been detected..."*
    *   It attempts to retrieve the device's current location (via GPS or Network).
    *   If a location is found, a second SMS is sent with a direct **Google Maps link**.
3.  **Make Call**:
    *   The app automatically initiates a phone call to the saved emergency contact, putting the user in voice contact with their helper.

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **Minimum SDK:** Android 10 (API 29)
- **Target SDK:** Android 15 (API 35)

### Key Permissions
| Permission | Reason |
| :--- | :--- |
| `CALL_PHONE` | Required to automatically dial the emergency contact without user intervention. |
| `SEND_SMS` | Required to send the automated SOS text message. |
| `ACCESS_FINE_LOCATION` | Required to fetch precise coordinates for the Google Maps link. |
| `BLUETOOTH_CONNECT` | Required to communicate with the fall detection hardware. |

---

## 📱 Hardware Requirements
*   An Android device running Android 10 or higher.
*   A Bluetooth-enabled microcontroller (like ESP32) programmed to send `"IMPACT_DETECTED"` over Serial Bluetooth upon detecting a fall.
