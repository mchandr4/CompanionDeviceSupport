package com.google.android.connecteddevice.service;

import static com.google.common.truth.Truth.assertThat;

import android.content.ServiceConnection;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SppServiceTest {

  @Test
  public void onDestroy_unbindsFromService() {
    final boolean[] unbound = {false};
    SppService sppService = new SppService() {
      @Override
      public void unbindService(ServiceConnection conn) {
        unbound[0] = true;
      }
    };
    sppService.onDestroy();
    assertThat(unbound[0]).isTrue();
  }
}
