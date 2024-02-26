# react-native-esp32-idf-provisioning

This is an unpublished React Native package that bridges the powerful esp-idf-provisioning-* library to your React Native application, enabling you to seamlessly provision ESP32 devices in a react native app.

### Android Support:
- [ ] Search for available BLE devices.
- [x] Scan device QR code to provide reference to ESP device.
- [x] Create reference of ESPDevice manually.
- [x] Data Encryption
- [x] Data transmission through BLE.
- [x] Data transmission through SoftAP.
- [x] Scan for available Wi-Fi networks.
- [x] Provision device.
- [x] Scan for available Wi-Fi networks.
- [ ] Support for exchanging custom data.
- [ ] Support for security version 2.

### IOS Support:
- [ ] Search for available BLE devices.
- [ ] Scan device QR code to provide reference to ESP device.
- [ ] Create reference of ESPDevice manually.
- [ ] Data Encryption
- [ ] Data transmission through SoftAP.
- [ ] Data transmission through BLE.
- [ ] Scan for available Wi-Fi networks.
- [ ] Provision device.
- [ ] Scan for available Wi-Fi networks.
- [ ] Support for exchanging custom data.
- [ ] Support for security version 2.


### Native Android Library
https://github.com/espressif/esp-idf-provisioning-android

### Native IOS Library
https://github.com/espressif/esp-idf-provisioning-ios


These are the native apps available by Espressif to provision ESP32 devices:

PlayStore:
SOFTAP: https://play.google.com/store/apps/details?id=com.espressif.provsoftap
BLE: https://play.google.com/store/apps/details?id=com.espressif.provble

AppStore:
SOFTAP: https://apps.apple.com/in/app/esp-softap-provisioning/id1474040630
BLE: https://apps.apple.com/in/app/esp-ble-provisioning/id1473590141

Version 1:
- We will focus on feature completeness with only SoftAP 

Version 2:
- Support BLE


## Sequence for ESP32 Provisioning (BLE)
This sequence diagram illustrates the provisioning process for an ESP32 device using Bluetooth Low Energy (BLE):

### Participants:

1. **Mobile App**: The React Native application on the user's device(ios/android).
2. **ESP32 Device**: The ESP32 device in provisioning mode.
3. **Provisioning Service**: A service running on the ESP32 device responsible for handling the provisioning process.

#### Messages:
1. **Mobile App -> ESP32 Device (Broadcast)**:
Message: Service discovery request for provisioning service.
2. **ESP32 Device -> Mobile App (Response)**:
Message: Service information advertisement (including UUID for provisioning service).
3. **Mobile App -> ESP32 Device:**
Message: Connection request to provisioning service.
4. **ESP32 Device -> Mobile App**:
Message: Connection confirmation.
5. **Mobile App -> ESP32 Device**:
Message: Security handshake initiation (e.g., key exchange).
6. **ESP32 Device -> Mobile App**:
Message: Security handshake response (e.g., encrypted keys).
7. **Mobile App -> ESP32 Device**:
Message: Provisioning data (e.g., WiFi SSID, password, other settings).
8. **ESP32 Device -> Mobile App**:
Message: Provisioning confirmation or error message.
9. **Mobile App -> ESP32 Device (Optional)**:
Message: Disconnect request.
10. **ESP32 Device -> Mobile App (Optional)**:
Message: Disconnect confirmation.


### Installation

Currently, this package is not published on npm and needs to be included manually.

1. Clone this repository into your React Native project's node_modules folder.
2. Update your package.json file:

```json
{
  "dependencies": {
    "react-native-esp-idf-provisioning": "file:node_modules/react-native-esp-idf-provisioning"
  }
}
```

### Usage:

Import the module in your React Native component:

```javascript
import EspIdfProvisioning from 'react-native-esp-idf-provisioning';
```

### License
```plaintext
Copyright © 2021 Futuristic Labs Private Limited. All rights reserved. 
We will make it open source soon, once we have a stable version.
```



