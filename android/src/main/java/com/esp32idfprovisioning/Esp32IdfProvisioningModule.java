package com.esp32idfprovisioning;

import androidx.annotation.NonNull;
import android.util.Log;
import android.provider.Settings;
// import android.os.Handler;
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
  private static final String TAG = "FTLABS";

  // React Native variables
  private ReactApplicationContext reactContext;
  private ESPProvisionManager provisionManager;
  // private Handler handler;
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

  // Required for React Native Module
  public Esp32IdfProvisioningModule(ReactApplicationContext context) {
    super(context);

    // // Log.d(TAG, reactContext.toString());
    // Log.d(TAG, "Constructor");
    // Log.d(TAG, context.toString());
    reactContext = context;
    // Log.d(TAG, "Constructor finished");
    // Log.d(TAG, reactContext.toString());
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
      Log.d(TAG, "Session creation is failed", e);
      promiseNetworkProvision.reject("Error in provision listener", "Session creation is failed", e);
    }

    @Override
    public void wifiConfigSent() {
      Log.d(TAG, "Wi-Fi credentials successfully sent to the device");
    }

    @Override
    public void wifiConfigFailed(Exception e) {
      Log.d(TAG, "Wi-Fi credentials failed to send to the device", e);
      promiseNetworkProvision.reject("Error in provision listener", "Wi-Fi credentials failed to send to the device",
          e);
    }

    @Override
    public void wifiConfigApplied() {
      Log.d(TAG, "Wi-Fi credentials successfully applied to the device");
    }

    @Override
    public void wifiConfigApplyFailed(Exception e) {
      Log.d(TAG, "Wi-Fi credentials failed to apply to the device", e);
      promiseNetworkProvision.reject("Error in provision listener", "Wi-Fi credentials failed to apply to the device",
          e);
    }

    @Override
    public void provisioningFailedFromDevice(ESPConstants.ProvisionFailureReason failureReason) {
      Log.d(TAG, "provisioningFailedFromDevice: " + failureReason.toString());
      promiseNetworkProvision.reject("Error in provision listener",
          "provisioningFailedFromDevice: " + failureReason.toString(), new Exception());
    }

    @Override
    public void deviceProvisioningSuccess() {
      Log.d(TAG, "Device is provisioned successfully");
      WritableMap res = Arguments.createMap();
      res.putBoolean("success", true);
      promiseNetworkProvision.resolve(res);
    }

    @Override
    public void onProvisioningFailed(Exception e) {
      Log.d(TAG, "Provisioning is failed", e);
      promiseNetworkProvision.reject("Error in provision listener", "Provisioning is failed", e);
    }
  };

  // Response listener for custom data
  ResponseListener responseListener = new ResponseListener() {
    @Override
    public void onSuccess(byte[] data) {
      Log.d(TAG, "Custom data sent successfully");
      WritableMap res = Arguments.createMap();
      res.putBoolean("success", true);
      promiseCustomDataProvision.resolve(res);
    }

    @Override
    public void onFailure(Exception e) {
      Log.d(TAG, "Custom data sending failed", e);
      promiseCustomDataProvision.reject("Error in response listener", "Custom data sending failed", e);
    }
  };

  // Get this to see the status of the connection
  @ReactMethod
  public void doGetStatus(Promise promise) {
    WritableMap res = Arguments.createMap();
    res.putBoolean("deviceConnected", deviceConnected);
    promise.resolve(res);
  }

  // First method to be called before any other method
  @ReactMethod
  public void doCreateESPDevice(Promise promise) {
    try {
      // if (reactContext == null) {
      // Log.d(TAG, "React Context is null");
      // } else {
      // Log.d(TAG, "React Context is not null");
      // Log.d(TAG, reactContext.toString());
      // }
      provisionManager = ESPProvisionManager.getInstance(reactContext);
      provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP,
          ESPConstants.SecurityType.SECURITY_1);
      // EventBus.getDefault().register(this);
      Log.d(TAG, "Create method");
      // return available methods on the module
      promise.resolve("Provisioning Manager created successfully");
      if (!EventBus.getDefault().isRegistered(this)) {
        EventBus.getDefault().register(this);
        Log.d(TAG, "Event Bus Registered");
      } else {
        Log.d(TAG, "Event Bus Already Registered");
      }

    } catch (Exception e) {
      Log.d(TAG, "Error on Create method", e);
      promise.reject("Error on Create method", e);
    }

  }

  // if the QR code is scanned and we have an SSID and password, we will call this
  // method to first connect to Semi
  @ReactMethod
  public void doConnectWifiDevice(String ssid, String password, Promise promise) {
    Log.d(TAG, "Connecting to device using SoftAP" + "--" + ssid + "--" + password);

    // try {
    // if (provisionManager == null) {
    // Log.d(TAG, "Provision Manager is null");
    // provisionManager = ESPProvisionManager.getInstance(reactContext);
    // Log.d(TAG, "Connection successful");
    // }
    // } catch (Exception e) {
    // promise.reject("Error initializing provisionManager",
    // "An error has occurred in initializing provision manager",
    // e);
    // Log.d(TAG, "Connection failed");
    // Log.d(TAG, e.toString());
    // }

    try {
      // Create a WiFiAccessPoint object with the provided SSID and password
      // WiFiAccessPoint wifiAccessPoint = new WiFiAccessPoint(ssid, password);
      // Connect to the device using WiFi transport
      // provisionManager = ESPProvisionManager.getInstance(reactContext);
      provisionManager.getEspDevice().connectWiFiDevice(ssid, password);
      this.promiseConnection = promise;
      // Log.d(TAG, "Connection successful to device");
    } catch (Exception e) {
      promise.reject("Error connecting to device",
          "An error has occurred in connecting to the device",
          e);
      Log.d(TAG, "Connection failed");
      Log.d(TAG, e.toString());
    }
  }

  // Set POP to Semi
  @ReactMethod
  public void doSetProofOfPossession(String POP, Promise promise) {
    try {
      // provisionManager = ESPProvisionManager.getInstance(reactContext);
      provisionManager.getEspDevice().setProofOfPossession(POP);
      promise.resolve("Proof of possession set successfully");
    } catch (Exception e) {
      promise.reject("Error setting proof of possession", e);
    }
  }

  // Get method is also available not sure if we need it
  @ReactMethod
  public void doGetProofOfPossession(Promise promise) {
    promise.resolve(provisionManager.getEspDevice().getProofOfPossession());
  }

  // Will open the Network Settings panel so that the user can connect to Semi's
  // Wifi network [If the QR Code has the Wifi SSID and Password we will not have
  // to do this]
  @ReactMethod
  public void doOpenNetworkSettings() {
    try {
      // Create an intent to open the system network settings
      Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
      // Start the activity to open the network settings
      getCurrentActivity().startActivity(intent);
    } catch (Exception e) {
      Log.d(TAG, "Error opening network settings", e);
    }
  }

  // Once the device is connected we will call this method to get the list of wifi
  // networks visible to Semi
  @ReactMethod
  public void doScanNetworks(Promise promise) {
    // TODO: We need to reactive this check later
    // if (!deviceConnected) {
    // Log.d(TAG, "No Semi connected");
    // promise.reject("No Semi connected",
    // "Please connect a Semi first",
    // new Exception());
    // return;
    // }
    try {
      if (provisionManager == null) {
        Log.d(TAG, "Provision Manager is null");
        // provisionManager = ESPProvisionManager.getInstance(reactContext);
        provisionManager.getEspDevice().scanNetworks(wiFiScanListener);
        Log.d(TAG, "Wifi scan successful");
      } else {
        Log.d(TAG, "Provision Manager is not null");
        provisionManager.getEspDevice().scanNetworks(wiFiScanListener);
        Log.d(TAG, "Wifi scan successful");
      }
      this.promiseNetworkScan = promise;
    } catch (Exception e) {
      promise.reject("Networks scan init error",
          "An error has occurred in initialization of networks scan",
          e);
    }
  }

  // Once the wifi network is selected we will call this method for provisioning
  @ReactMethod
  public void doProvisioning(String ssid, String pass, Promise promise) {
    try {
      provisionManager = ESPProvisionManager.getInstance(reactContext);
      provisionManager.getEspDevice().provision(ssid, pass, provisionListener);
      this.promiseNetworkProvision = promise;
    } catch (Exception e) {
      promise.reject("Credentials provision init error",
          "An error has occurred in init of provisioning of credentials", e);
    }
  }

  // Events that we can watch to see if the device is connected or not
  // These events are fired by the ESP Provisioning SDK
  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(DeviceConnectionEvent event) {

    Log.d(TAG, "ON Device Prov Event RECEIVED : " + event.getEventType());
    switch (event.getEventType()) {
      case ESPConstants.EVENT_DEVICE_CONNECTED:
        Log.d(TAG, "Device Connected Event Received");
        deviceConnected = true;
        if (promiseConnectionFinished) {
          break;
        }
        // The following code to navigate back to the provisioning screen from the
        // network settings screen once the device is connected
        // Activity currentActivity = getCurrentActivity();
        // if (currentActivity != null) {
        // currentActivity.finish();
        // }
        // break;

        WritableMap res = Arguments.createMap();
        res.putBoolean("Success", true);
        promiseConnectionFinished = true;
        promiseConnection.resolve(res);
        break;

      case ESPConstants.EVENT_DEVICE_DISCONNECTED:
        Log.d(TAG, "Device disconnected");
        deviceConnected = false;
        break;

      case ESPConstants.EVENT_DEVICE_CONNECTION_FAILED:
        Log.d(TAG, "Device connection failed");
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
