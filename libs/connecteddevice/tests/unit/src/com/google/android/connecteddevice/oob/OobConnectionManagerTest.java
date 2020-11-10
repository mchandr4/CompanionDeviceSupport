package com.google.android.connecteddevice.oob;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import android.security.keystore.KeyProperties;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.model.OobEligibleDevice;
import com.google.common.primitives.Bytes;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OobConnectionManagerTest {
  private static final byte[] TEST_MESSAGE = "testMessage".getBytes(UTF_8);
  private static final byte[] TEST_ENCRYPTION_IV =
      new byte[OobConnectionManager.NONCE_LENGTH_BYTES];
  private static final byte[] TEST_DECRYPTION_IV =
      new byte[OobConnectionManager.NONCE_LENGTH_BYTES];

  private TestChannel testChannel;
  private SecretKey testKey;
  private byte[] testOobData;

  @Before
  public void setUp() throws Exception {
    testChannel = new TestChannel();
    testKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES).generateKey();

    SecureRandom secureRandom = new SecureRandom();
    secureRandom.nextBytes(TEST_ENCRYPTION_IV);
    secureRandom.nextBytes(TEST_DECRYPTION_IV);

    testOobData = Bytes.concat(TEST_DECRYPTION_IV, TEST_ENCRYPTION_IV, testKey.getEncoded());
  }

  @Test
  public void testInitAsServer_keyIsNull() {
    OobConnectionManager oobConnectionManager = new OobConnectionManager();
    assertThat(oobConnectionManager.encryptionKey).isNull();
  }

  @Test
  public void testServer_onSetOobData_setsKeyAndNonce() {
    OobConnectionManager oobConnectionManager = new OobConnectionManager();
    oobConnectionManager.setOobData(testOobData);
    assertThat(oobConnectionManager.encryptionKey).isEqualTo(testKey);
    // The decryption IV for the server is the encryption IV for the client and vice versa
    assertThat(oobConnectionManager.decryptionIv).isEqualTo(TEST_ENCRYPTION_IV);
    assertThat(oobConnectionManager.encryptionIv).isEqualTo(TEST_DECRYPTION_IV);
  }

  @Test
  public void testInitAsClient_keyAndNoncesAreNonNullAndSent() {
    OobConnectionManager oobConnectionManager = new OobConnectionManager();
    oobConnectionManager.startOobExchange(testChannel);
    assertThat(oobConnectionManager.encryptionKey).isNotNull();
    assertThat(oobConnectionManager.encryptionIv).isNotNull();
    assertThat(oobConnectionManager.decryptionIv).isNotNull();
    assertThat(testChannel.sentOobData)
        .isEqualTo(
            Bytes.concat(
                oobConnectionManager.decryptionIv,
                oobConnectionManager.encryptionIv,
                oobConnectionManager.encryptionKey.getEncoded()));
  }

  @Test
  public void testServerEncryptAndClientDecrypt() throws Exception {
    OobConnectionManager clientOobConnectionManager = new OobConnectionManager();
    clientOobConnectionManager.startOobExchange(testChannel);
    OobConnectionManager serverOobConnectionManager = new OobConnectionManager();
    serverOobConnectionManager.setOobData(testChannel.sentOobData);

    byte[] encryptedTestMessage = clientOobConnectionManager.encryptVerificationCode(TEST_MESSAGE);
    byte[] decryptedTestMessage =
        serverOobConnectionManager.decryptVerificationCode(encryptedTestMessage);

    assertThat(decryptedTestMessage).isEqualTo(TEST_MESSAGE);
  }

  @Test
  public void testClientEncryptAndServerDecrypt() throws Exception {
    OobConnectionManager clientOobConnectionManager = new OobConnectionManager();
    clientOobConnectionManager.startOobExchange(testChannel);
    OobConnectionManager serverOobConnectionManager = new OobConnectionManager();
    serverOobConnectionManager.setOobData(testChannel.sentOobData);

    byte[] encryptedTestMessage = serverOobConnectionManager.encryptVerificationCode(TEST_MESSAGE);
    byte[] decryptedTestMessage =
        clientOobConnectionManager.decryptVerificationCode(encryptedTestMessage);

    assertThat(decryptedTestMessage).isEqualTo(TEST_MESSAGE);
  }

  @Test
  public void testEncryptAndDecryptWithDifferentNonces_throwsAEADBadTagException()
      throws Exception {
    // The OobConnectionManager stores a different nonce for encryption and decryption, so it
    // can't decrypt messages that it encrypted itself. It can only send encrypted messages to
    // an OobConnectionManager on another device that share its nonces and encryption key.
    OobConnectionManager oobConnectionManager = new OobConnectionManager();
    oobConnectionManager.startOobExchange(testChannel);
    byte[] encryptedMessage = oobConnectionManager.encryptVerificationCode(TEST_MESSAGE);
    assertThrows(
        AEADBadTagException.class,
        () -> oobConnectionManager.decryptVerificationCode(encryptedMessage));
  }

  @Test
  public void testDecryptWithShortMessage_throwsAEADBadTagException() {
    OobConnectionManager oobConnectionManager = new OobConnectionManager();
    oobConnectionManager.startOobExchange(testChannel);
    assertThrows(
        AEADBadTagException.class,
        () -> oobConnectionManager.decryptVerificationCode("short".getBytes(UTF_8)));
  }

  @Test
  public void testEncryptWithNullKey_throwsInvalidKeyException() {
    OobConnectionManager oobConnectionManager = new OobConnectionManager();
    assertThrows(
        InvalidKeyException.class,
        () -> oobConnectionManager.encryptVerificationCode(TEST_MESSAGE));
  }

  @Test
  public void testDecryptWithNullKey_throwsInvalidKeyException() {
    OobConnectionManager oobConnectionManager = new OobConnectionManager();
    assertThrows(
        InvalidKeyException.class,
        () -> oobConnectionManager.decryptVerificationCode(TEST_MESSAGE));
  }

  private static class TestChannel implements OobChannel {
    byte[] sentOobData = null;

    @Override
    public void completeOobDataExchange(OobEligibleDevice device, Callback callback) {}

    @Override
    public void sendOobData(byte[] oobData) {
      sentOobData = oobData;
    }

    @Override
    public void interrupt() {}
  }
}
