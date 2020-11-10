package com.google.android.connecteddevice.storage;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.app.ActivityManager;
import androidx.room.Room;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.model.AssociatedDevice;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Storage for connected devices in a car. */
public class ConnectedDeviceStorage {
  private static final String TAG = "CompanionStorage";

  private static final String SHARED_PREFS_NAME = "com.google.android.connecteddevice";
  private static final String UNIQUE_ID_KEY = "CTABM_unique_id";
  private static final String BT_NAME_KEY = "CTABM_bt_name";
  private static final String DATABASE_NAME = "connected-device-database";

  private static final String CHALLENGE_HASHING_ALGORITHM = "HmacSHA256";

  @VisibleForTesting static final int CHALLENGE_SECRET_BYTES = 32;

  private final Context context;

  private final AssociatedDeviceDao associatedDeviceDatabase;

  private final CryptoHelper cryptoHelper;

  private SharedPreferences sharedPreferences;

  private UUID uniqueId;

  private AssociatedDeviceCallback associatedDeviceCallback;

  public ConnectedDeviceStorage(@NonNull Context context) {
    this(
        context,
        new KeyStoreCryptoHelper(),
        Room.databaseBuilder(context, ConnectedDeviceDatabase.class, DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()
            .associatedDeviceDao());
  }

  @VisibleForTesting
  ConnectedDeviceStorage(
      @NonNull Context context,
      @NonNull CryptoHelper cryptoHelper,
      @NonNull AssociatedDeviceDao associatedDeviceDatabase) {
    this.context = context;
    this.cryptoHelper = cryptoHelper;
    this.associatedDeviceDatabase = associatedDeviceDatabase;
  }

  /**
   * Set a callback for associated device updates.
   *
   * @param callback {@link AssociatedDeviceCallback} to set.
   */
  public void setAssociatedDeviceCallback(@NonNull AssociatedDeviceCallback callback) {
    associatedDeviceCallback = callback;
  }

  /** Clear the callback for association device callback updates. */
  public void clearAssociationDeviceCallback() {
    associatedDeviceCallback = null;
  }

  /**
   * Get communication encryption key for the given device.
   *
   * @param deviceId id of trusted device
   * @return encryption key, null if device id is not recognized
   */
  @Nullable
  public byte[] getEncryptionKey(@NonNull String deviceId) {
    AssociatedDeviceKeyEntity entity = associatedDeviceDatabase.getAssociatedDeviceKey(deviceId);
    if (entity == null) {
      logd(TAG, "Encryption key not found!");
      return null;
    }

    return cryptoHelper.decrypt(entity.encryptedKey);
  }

  /**
   * Save encryption key for the given device.
   *
   * @param deviceId id of the device
   * @param encryptionKey encryption key
   */
  public void saveEncryptionKey(@NonNull String deviceId, @NonNull byte[] encryptionKey) {
    String encryptedKey = cryptoHelper.encrypt(encryptionKey);
    AssociatedDeviceKeyEntity entity = new AssociatedDeviceKeyEntity(deviceId, encryptedKey);
    associatedDeviceDatabase.addOrReplaceAssociatedDeviceKey(entity);
    logd(TAG, "Successfully wrote encryption key.");
  }

  /**
   * Save challenge secret for the given device.
   *
   * @param deviceId id of the device
   * @param secret Secret associated with this device. Note: must be {@value CHALLENGE_SECRET_BYTES}
   *     bytes in length or an {@link InvalidParameterException} will be thrown.
   */
  public void saveChallengeSecret(@NonNull String deviceId, @NonNull byte[] secret) {
    if (secret.length != CHALLENGE_SECRET_BYTES) {
      throw new InvalidParameterException(
          "Secrets must be " + CHALLENGE_SECRET_BYTES + " bytes in length.");
    }

    String encryptedKey = cryptoHelper.encrypt(secret);
    AssociatedDeviceChallengeSecretEntity entity =
        new AssociatedDeviceChallengeSecretEntity(deviceId, encryptedKey);
    associatedDeviceDatabase.addOrReplaceAssociatedDeviceChallengeSecret(entity);
    logd(TAG, "Successfully wrote challenge secret.");
  }

  /** Get the challenge secret associated with a device. */
  public byte[] getChallengeSecret(@NonNull String deviceId) {
    AssociatedDeviceChallengeSecretEntity entity =
        associatedDeviceDatabase.getAssociatedDeviceChallengeSecret(deviceId);
    if (entity == null) {
      logd(TAG, "Challenge secret not found!");
      return null;
    }

    return cryptoHelper.decrypt(entity.encryptedChallengeSecret);
  }

  /**
   * Hash provided value with device's challenge secret and return result. Returns {@code null} if
   * unsuccessful.
   */
  @Nullable
  public byte[] hashWithChallengeSecret(@NonNull String deviceId, @NonNull byte[] value) {
    byte[] challengeSecret = getChallengeSecret(deviceId);
    if (challengeSecret == null) {
      loge(TAG, "Unable to find challenge secret for device " + deviceId + ".");
      return null;
    }

    Mac mac;
    try {
      mac = Mac.getInstance(CHALLENGE_HASHING_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      loge(TAG, "Unable to find hashing algorithm " + CHALLENGE_HASHING_ALGORITHM + ".", e);
      return null;
    }

    SecretKeySpec keySpec = new SecretKeySpec(challengeSecret, CHALLENGE_HASHING_ALGORITHM);
    try {
      mac.init(keySpec);
    } catch (InvalidKeyException e) {
      loge(TAG, "Exception while initializing HMAC.", e);
      return null;
    }

    return mac.doFinal(value);
  }

  @NonNull
  private SharedPreferences getSharedPrefs() {
    // This should be called only after user 0 is unlocked.
    if (sharedPreferences != null) {
      return sharedPreferences;
    }
    sharedPreferences =
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    return sharedPreferences;
  }

  /**
   * Get the unique id for head unit. Persists on device until factory reset. This should be called
   * only after user 0 is unlocked.
   *
   * @return unique id
   */
  @NonNull
  public UUID getUniqueId() {
    if (uniqueId != null) {
      return uniqueId;
    }

    SharedPreferences prefs = getSharedPrefs();
    if (prefs.contains(UNIQUE_ID_KEY)) {
      uniqueId = UUID.fromString(prefs.getString(UNIQUE_ID_KEY, null));
      logd(TAG, "Found existing trusted unique id: " + prefs.getString(UNIQUE_ID_KEY, ""));
    }

    if (uniqueId == null) {
      uniqueId = UUID.randomUUID();
      prefs.edit().putString(UNIQUE_ID_KEY, uniqueId.toString()).apply();
      logd(TAG, "Generated new trusted unique id: " + prefs.getString(UNIQUE_ID_KEY, ""));
    }

    return uniqueId;
  }

  /** Store the current bluetooth adapter name. */
  public void storeBluetoothName(@NonNull String name) {
    getSharedPrefs().edit().putString(BT_NAME_KEY, name).apply();
  }

  /** Get the previously stored bluetooth adapter name or {@code null} if not found. */
  @Nullable
  public String getStoredBluetoothName() {
    return getSharedPrefs().getString(BT_NAME_KEY, null);
  }

  /** Remove the previously stored bluetooth adapter name from storage. */
  public void removeStoredBluetoothName() {
    getSharedPrefs().edit().remove(BT_NAME_KEY).apply();
  }

  /**
   * Get a list of associated devices for the given user.
   *
   * @param userId The identifier of the user.
   * @return Associated device list.
   */
  @NonNull
  public List<AssociatedDevice> getAssociatedDevicesForUser(@NonNull int userId) {
    List<AssociatedDeviceEntity> entities =
        associatedDeviceDatabase.getAssociatedDevicesForUser(userId);

    if (entities == null) {
      return new ArrayList<>();
    }

    ArrayList<AssociatedDevice> userDevices = new ArrayList<>();
    for (AssociatedDeviceEntity entity : entities) {
      userDevices.add(entity.toAssociatedDevice());
    }

    return userDevices;
  }

  /**
   * Get a list of associated devices for the current user.
   *
   * @return Associated device list.
   */
  @NonNull
  public List<AssociatedDevice> getActiveUserAssociatedDevices() {
    return getAssociatedDevicesForUser(ActivityManager.getCurrentUser());
  }

  /**
   * Returns a list of device ids of associated devices for the given user.
   *
   * @param userId The user id for whom we want to know the device ids.
   * @return List of device ids.
   */
  @NonNull
  public List<String> getAssociatedDeviceIdsForUser(@NonNull int userId) {
    List<AssociatedDevice> userDevices = getAssociatedDevicesForUser(userId);
    ArrayList<String> userDeviceIds = new ArrayList<>();

    for (AssociatedDevice device : userDevices) {
      userDeviceIds.add(device.getDeviceId());
    }

    return userDeviceIds;
  }

  /**
   * Returns a list of device ids of associated devices for the current user.
   *
   * @return List of device ids.
   */
  @NonNull
  public List<String> getActiveUserAssociatedDeviceIds() {
    return getAssociatedDeviceIdsForUser(ActivityManager.getCurrentUser());
  }

  /**
   * Add the associated device of the given deviceId for the currently active user.
   *
   * @param device New associated device to be added.
   */
  public void addAssociatedDeviceForActiveUser(@NonNull AssociatedDevice device) {
    addAssociatedDeviceForUser(ActivityManager.getCurrentUser(), device);
    if (associatedDeviceCallback != null) {
      associatedDeviceCallback.onAssociatedDeviceAdded(device);
    }
  }

  /**
   * Add the associated device of the given deviceId for the given user.
   *
   * @param userId The identifier of the user.
   * @param device New associated device to be added.
   */
  public void addAssociatedDeviceForUser(int userId, @NonNull AssociatedDevice device) {
    AssociatedDeviceEntity entity =
        new AssociatedDeviceEntity(userId, device, /* isConnectionEnabled= */ true);
    associatedDeviceDatabase.addOrReplaceAssociatedDevice(entity);
  }

  /**
   * Update the name for an associated device.
   *
   * @param deviceId The id of the associated device.
   * @param name The name to replace with.
   */
  public void updateAssociatedDeviceName(@NonNull String deviceId, @NonNull String name) {
    AssociatedDeviceEntity entity = associatedDeviceDatabase.getAssociatedDevice(deviceId);
    if (entity == null) {
      logw(TAG, "Attempt to update name on an unrecognized device " + deviceId + ". Ignoring.");
      return;
    }
    entity.name = name;
    associatedDeviceDatabase.addOrReplaceAssociatedDevice(entity);
    if (associatedDeviceCallback != null) {
      associatedDeviceCallback.onAssociatedDeviceUpdated(
          new AssociatedDevice(deviceId, entity.address, name, entity.isConnectionEnabled));
    }
  }

  /**
   * Remove the associated device of the given deviceId for the given user.
   *
   * @param userId The identifier of the user.
   * @param deviceId The identifier of the device to be cleared.
   */
  public void removeAssociatedDevice(int userId, @NonNull String deviceId) {
    AssociatedDeviceEntity entity = associatedDeviceDatabase.getAssociatedDevice(deviceId);
    if (entity == null || entity.userId != userId) {
      return;
    }
    associatedDeviceDatabase.removeAssociatedDevice(entity);
    if (associatedDeviceCallback != null) {
      associatedDeviceCallback.onAssociatedDeviceRemoved(
          new AssociatedDevice(deviceId, entity.address, entity.name, entity.isConnectionEnabled));
    }
  }

  /**
   * Clear the associated device of the given deviceId for the current user.
   *
   * @param deviceId The identifier of the device to be cleared.
   */
  public void removeAssociatedDeviceForActiveUser(@NonNull String deviceId) {
    removeAssociatedDevice(ActivityManager.getCurrentUser(), deviceId);
  }

  /**
   * Set if connection is enabled for an associated device.
   *
   * @param deviceId The id of the associated device.
   * @param isConnectionEnabled If connection enabled for this device.
   */
  public void updateAssociatedDeviceConnectionEnabled(
      @NonNull String deviceId, boolean isConnectionEnabled) {
    AssociatedDeviceEntity entity = associatedDeviceDatabase.getAssociatedDevice(deviceId);
    if (entity == null) {
      logw(
          TAG,
          "Attempt to enable or disable connection on an unrecognized device "
              + deviceId
              + ". Ignoring.");
      return;
    }
    if (entity.isConnectionEnabled == isConnectionEnabled) {
      return;
    }
    entity.isConnectionEnabled = isConnectionEnabled;
    associatedDeviceDatabase.addOrReplaceAssociatedDevice(entity);
    if (associatedDeviceCallback != null) {
      associatedDeviceCallback.onAssociatedDeviceUpdated(
          new AssociatedDevice(deviceId, entity.address, entity.name, isConnectionEnabled));
    }
  }

  /** Callback for association device related events. */
  public interface AssociatedDeviceCallback {
    /** Triggered when an associated device has been added. */
    void onAssociatedDeviceAdded(@NonNull AssociatedDevice device);

    /** Triggered when an associated device has been removed. */
    void onAssociatedDeviceRemoved(@NonNull AssociatedDevice device);

    /** Triggered when an associated device has been updated. */
    void onAssociatedDeviceUpdated(@NonNull AssociatedDevice device);
  }
}
