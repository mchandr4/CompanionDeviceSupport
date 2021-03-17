package com.google.android.connecteddevice.trust;

import static com.google.common.truth.Truth.assertThat;

import android.content.ServiceConnection;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TrustedDeviceUiDelegateServiceTest {

  @Test
  public void onDestroy_unbindsFromService() {
    final boolean[] unbound = {false};
    TrustedDeviceUiDelegateService service = new TrustedDeviceUiDelegateService() {
      @Override
      public void unbindService(ServiceConnection conn) {
        unbound[0] = true;
      }
    };
    service.onDestroy();
    assertThat(unbound[0]).isTrue();
  }
}
