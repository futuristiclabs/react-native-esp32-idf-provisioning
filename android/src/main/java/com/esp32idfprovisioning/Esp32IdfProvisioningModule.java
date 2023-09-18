package com.esp32idfprovisioning;

import androidx.annotation.NonNull;
import android.util.Log;
import android.provider.Settings;
import android.os.Handler;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.app.Activity;

// React Native SDK Imports
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;

// ESP Provisioning SDK Import
import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.WiFiAccessPoint;
import com.espressif.provisioning.device_scanner.WiFiScanner;
import com.espressif.provisioning.listeners.BleScanListener;
import com.espressif.provisioning.listeners.ProvisionListener;
import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.provisioning.listeners.WiFiScanListener;

// Java Imports
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

// Pub/Sub Event Bus 
// https://github.com/greenrobot/EventBus
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

@ReactModule(name = Esp32IdfProvisioningModule.NAME)
public class Esp32IdfProvisioningModule extends ReactContextBaseJavaModule {
  public static final String NAME = "Esp32IdfProvisioning";
  private static final String TAG = "FTLABS ESP PROV::";

  // React Native variables
  private ReactApplicationContext reactContext;
  private ESPProvisionManager provisionManager;
  private Handler handler;
  private HashMap<String, BluetoothDevice> listDevicesByUuid;
  private WritableArray listDeviceNamesByUuid;

  private WiFiScanner wifiScanner;

  // Status variables
  private boolean deviceConnected = false;
  private boolean promiseConnectionFinished = false;

  private String deviceName;
  private Promise promiseScan;
  private Promise promiseConnection;
  private Promise promiseNetworkScan;
  private Promise promiseNetworkProvision;
  private Promise promiseCustomDataProvision;

  public Esp32IdfProvisioningModule(ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext = reactContext;
  }

  // This method is **required** to be implemented by React Native
  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  // Will scan for wifi networks and then return a list of them
  // The promise will be resolved with the list of networks
  WiFiScanListener wiFiScanListener = new WiFiScanListener() {
    // @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onWifiListReceived(ArrayList<WiFiAccessPoint> wifiList) {
      WritableArray listOfNetworks = Arguments.createArray();
      wifiList.forEach((wiFiAccessPoint) -> {
        WritableMap newElement = Arguments.createMap();
        newElement.putString("ssid", wiFiAccessPoint.getWifiName());
        newElement.putString("password", wiFiAccessPoint.getPassword());
        newElement.putInt("rssi", wiFiAccessPoint.getRssi());
        newElement.putInt("security", wiFiAccessPoint.getSecurity());
        listOfNetworks.pushMap(newElement);
      });
      promiseNetworkScan.resolve(listOfNetworks);
    }

    @Override
    public void onWiFiScanFailed(Exception e) {
      promiseNetworkScan.reject("Network Scan Error", "WiFi networks scan has failed", e);
    }
  };

  // Native method to provision using SoftAP and it's callbacks
  ProvisionListener provisionListener = new ProvisionListener() {
    @Override
    public void createSessionFailed(Exception e) {
      Log.e(TAG, "Session creation is failed", e);
      promiseNetworkProvision.reject("Error in provision listener", "Session creation is failed", e);
    }

    @Override
    public void wifiConfigSent() {
      Log.e(TAG, "Wi-Fi credentials successfully sent to the device");
    }

    @Override
    public void wifiConfigFailed(Exception e) {
      Log.e(TAG, "Wi-Fi credentials failed to send to the device", e);
      promiseNetworkProvision.reject("Error in provision listener", "Wi-Fi credentials failed to send to the device",
          e);
    }

    @Override
    public void wifiConfigApplied() {
      Log.e(TAG, "Wi-Fi credentials successfully applied to the device");
    }

    @Override
    public void wifiConfigApplyFailed(Exception e) {
      Log.e(TAG, "Wi-Fi credentials failed to apply to the device", e);
      promiseNetworkProvision.reject("Error in provision listener", "Wi-Fi credentials failed to apply to the device",
          e);
    }

    @Override
    public void provisioningFailedFromDevice(ESPConstants.ProvisionFailureReason failureReason) {
      Log.e(TAG, "provisioningFailedFromDevice: " + failureReason.toString());
      promiseNetworkProvision.reject("Error in provision listener",
          "provisioningFailedFromDevice: " + failureReason.toString(), new Exception());
    }

    @Override
    public void deviceProvisioningSuccess() {
      Log.e(TAG, "Device is provisioned successfully");
      WritableMap res = Arguments.createMap();
      res.putBoolean("success", true);
      promiseNetworkProvision.resolve(res);
    }

    @Override
    public void onProvisioningFailed(Exception e) {
      Log.e(TAG, "Provisioning is failed", e);
      promiseNetworkProvision.reject("Error in provision listener", "Provisioning is failed", e);
    }
  };

  // Response listener for custom data
  ResponseListener responseListener = new ResponseListener() {
    @Override
    public void onSuccess(byte[] data) {
      Log.e(TAG, "Custom data sent successfully");
      WritableMap res = Arguments.createMap();
      res.putBoolean("success", true);
      promiseCustomDataProvision.resolve(res);
    }

    @Override
    public void onFailure(Exception e) {
      Log.e(TAG, "Custom data sending failed", e);
      promiseCustomDataProvision.reject("Error in response listener", "Custom data sending failed", e);
    }
  };

  // First method to be called before any other method
  @ReactMethod
  public void create() {
    try {
      provisionManager = ESPProvisionManager.getInstance(reactContext);
      EventBus.getDefault().register(this);
      Log.e(TAG, "Create method");
    } catch (Exception e) {
      Log.e(TAG, "Error on Create method", e);
    }
  }

  // if the QR code is scanned and we have an SSID and password, we will call this
  // method to first connect to Semi
  @ReactMethod
  public void connectToDeviceUsingSoftAP(String ssid, String password, Promise promise) {
    try {
      // Create a WiFiAccessPoint object with the provided SSID and password
      // WiFiAccessPoint wifiAccessPoint = new WiFiAccessPoint(ssid, password);

      // Connect to the device using WiFi transport
      provisionManager.getEspDevice().connectWiFiDevice(ssid, password);
      this.promiseConnection = promise;
    } catch (Exception e) {
      promise.reject("Error connecting to device",
          "An error has occurred in connecting to the device",
          e);
    }
  }

  // Set POP to Semi
  @ReactMethod
  public void setProofOfPossession(String POP, Promise promise) {
    try {
      provisionManager.getEspDevice().setProofOfPossession(POP);
      promise.resolve("Proof of possession set successfully");
    } catch (Exception e) {
      promise.reject("Error setting proof of possession", e);
    }
  }

  // Get method is also available not sure if we need it
  @ReactMethod
  public void getProofOfPossession(Promise promise) {
    promise.resolve(provisionManager.getEspDevice().getProofOfPossession());
  }

  // Will open the Network Settings panel so that the user can connect to Semi's
  // Wifi network [If the QR Code has the Wifi SSID and Password we will not have
  // to do this]
  @ReactMethod
  public void openNetworkSettings() {
    try {
      // Create an intent to open the system network settings
      Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
      // Start the activity to open the network settings
      getCurrentActivity().startActivity(intent);
    } catch (Exception e) {
      Log.e(TAG, "Error opening network settings", e);
    }
  }

  // Once the device is connected we will call this method to get the list of wifi
  // networks visible to Semi
  @ReactMethod
  public void scanWifiNetworks(Promise promise) {
    if (!deviceConnected) {
      Log.e(TAG, "No Semi connected");
      promise.reject("No Semi connected",
          "Please connect a Semi first",
          new Exception());
      return;
    }
    try {
      provisionManager.getEspDevice().scanNetworks(wiFiScanListener);
      this.promiseNetworkScan = promise;
    } catch (Exception e) {
      promise.reject("Networks scan init error",
          "An error has occurred in initialization of networks scan",
          e);
    }
  }

  // Once the wifi network is selected we will call this method for provisioning
  @ReactMethod
  public void provisionNetwork(String ssid, String pass, Promise promise) {
    try {
      provisionManager.getEspDevice().provision(ssid, pass, provisionListener);
      this.promiseNetworkProvision = promise;
    } catch (Exception e) {
      promise.reject("Credentials provision init error",
          "An error has occurred in init of provisioning of credentials", e);
    }
  }

  // Test Method will be removed later, setting device name as of now
  @ReactMethod
  public void getPhoneID(Promise promise) {
    String phoneID = Settings.Secure.getString(reactContext.getContentResolver(), Settings.Secure.ANDROID_ID);
    deviceName = phoneID;
    promise.resolve(phoneID);
  }

  // Events that we can watch to see if the device is connected or not
  // These events are fired by the ESP Provisioning SDK
  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(DeviceConnectionEvent event) {

    Log.e(TAG, "ON Device Prov Event RECEIVED : " + event.getEventType());
    switch (event.getEventType()) {
      case ESPConstants.EVENT_DEVICE_CONNECTED:
        Log.e(TAG, "Device Connected Event Received");
        deviceConnected = true;
        if (promiseConnectionFinished) {
          break;
        }
        // The following code to navigate back to the provisioning screen from the
        // network settings screen once the device is connected
        Activity currentActivity = getCurrentActivity();
        if (currentActivity != null) {
          currentActivity.finish();
        }
        // break;

        WritableMap res = Arguments.createMap();
        res.putBoolean("Success", true);
        promiseConnectionFinished = true;
        promiseConnection.resolve(res);
        break;

      case ESPConstants.EVENT_DEVICE_DISCONNECTED:
        Log.e(TAG, "Device disconnected");
        deviceConnected = false;
        break;

      case ESPConstants.EVENT_DEVICE_CONNECTION_FAILED:
        Log.e(TAG, "Device connection failed");
        if (promiseConnectionFinished) {
          break;
        }
        promiseConnectionFinished = true;
        promiseConnection.reject("Device connection failed",
            "The device connection has failed",
            new Exception());
        break;
    }
  }

}
