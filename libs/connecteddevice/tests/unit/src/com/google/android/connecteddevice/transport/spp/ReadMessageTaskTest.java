package com.google.android.connecteddevice.transport.spp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.bluetooth.BluetoothSocket;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.shadow.api.Shadow;

@RunWith(AndroidJUnit4.class)
public class ReadMessageTaskTest {
  private static final int TESTED_MESSAGE_SPLITS = 3;
  private final byte[] testData1 = "data1".getBytes(UTF_8);
  private final byte[] testData2 = "testData2".getBytes(UTF_8);
  private final byte[] completedEmptyTestMessage = new byte[18];
  private final int messageSplitLength = completedEmptyTestMessage.length / TESTED_MESSAGE_SPLITS;
  private ReadMessageTask readMessageTask;
  private Executor executor;
  private final Executor callbackExecutor = directExecutor();
  private final BluetoothSocket shadowBluetoothSocket = Shadow.newInstanceOf(BluetoothSocket.class);

  @Mock private ReadMessageTask.Callback mockCallback;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Before
  public void setUp() throws IOException {
    readMessageTask =
        new ReadMessageTask(shadowBluetoothSocket.getInputStream(), mockCallback, callbackExecutor);
    executor = Executors.newSingleThreadExecutor();
  }

  @Test
  public void testRun_oneMessage_informCallback() throws IOException {
    shadowOf(shadowBluetoothSocket)
        .getInputStreamFeeder()
        .write(SppManager.wrapWithArrayLength(testData1));

    executor.execute(readMessageTask);

    verify(mockCallback, timeout(1000)).onMessageReceived(testData1);
  }

  @Test
  public void testRun_twoCombinedMessage_informCallback() throws IOException {
    shadowOf(shadowBluetoothSocket)
        .getInputStreamFeeder()
        .write(SppManager.wrapWithArrayLength(testData1));
    shadowOf(shadowBluetoothSocket)
        .getInputStreamFeeder()
        .write(SppManager.wrapWithArrayLength(testData2));
    executor.execute(readMessageTask);

    verify(mockCallback, timeout(1000)).onMessageReceived(testData1);

    verify(mockCallback, timeout(1000)).onMessageReceived(testData2);
  }

  @Test
  public void testReadData_splittedMessages_readSuccessfully(){
    FakeInputStream fakeInputStream = new FakeInputStream(messageSplitLength);

    assertThat(readMessageTask.readData(fakeInputStream, completedEmptyTestMessage)).isTrue();

    assertThat(fakeInputStream.methodCalls).isEqualTo(TESTED_MESSAGE_SPLITS);
  }

  @Test
  public void testCancel_stopReadingMessage() throws IOException{
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(readMessageTask);
    readMessageTask.cancel();

    shadowOf(shadowBluetoothSocket).getInputStreamFeeder().write(testData1);

    verify(mockCallback, timeout(1000).times(0)).onMessageReceived(testData1);
  }

  @Test
  public void testRun_onMessageReadError() throws IOException{
    ExecutorService executor = Executors.newSingleThreadExecutor();
    shadowOf(shadowBluetoothSocket).getInputStreamFeeder().close();

    executor.execute(readMessageTask);

    verify(mockCallback, timeout(1000)).onMessageReadError();
  }

  /**
   * Fake input stream that can track the number of [read] method calls and should only be used in
   * test.
   */
  private static class FakeInputStream extends InputStream {
    int methodCalls = 0;
    int messageSplitLength = 0;

    public FakeInputStream(int messageSplitLength) {
      this.messageSplitLength = messageSplitLength;
    }

    @Override
    public int read(byte[] b, int off, int len) {
      methodCalls++;
      return messageSplitLength;
    }

    @Override
    public int read() {
      return 0;
    }
  }
}
