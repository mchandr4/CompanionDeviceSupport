/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.connecteddevice.service;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MetaDataServiceTest {

  private static final String CORRECT_NAME = "CorrectMetaDataKey";

  private static final int RESOURCE_ID = 2;

  private static final String INVALID_NAME = "InvalidMetaDataKey";

  private static final String META_STRING = "Some meta-data string.";

  private static final boolean META_BOOLEAN = true;

  private static final int META_INT = 1;

  private static final float META_FLOAT = 2.0f;

  private static final String[] META_STRING_ARRAY = new String[] { "test1", "test2" };

  private final Context context = ApplicationProvider.getApplicationContext();

  private final MetaDataService metaDataService = new MetaDataService() {

    @NonNull
    @Override
    protected Bundle retrieveMetaDataBundle() {
      Bundle bundle = new Bundle();
      bundle.putInt(CORRECT_NAME, RESOURCE_ID);
      return bundle;
    }

    @Override
    public Resources getResources() {
      return new Resources(
          context.getResources().getAssets(),
          context.getResources().getDisplayMetrics(),
          context.getResources().getConfiguration()) {

        @NonNull
        @Override
        public String getString(int id) {
          if (id == RESOURCE_ID) {
            return META_STRING;
          }
          throw new NotFoundException();
        }

        @Override
        public int getInteger(int id) {
          if (id == RESOURCE_ID) {
            return META_INT;
          }
          throw new NotFoundException();
        }

        @Override
        public float getFloat(int id) {
          if (id == RESOURCE_ID) {
            return META_FLOAT;
          }
          throw new NotFoundException();
        }

        @Override
        public boolean getBoolean(int id) {
          if (id == RESOURCE_ID) {
            return META_BOOLEAN;
          }
          throw new NotFoundException();
        }

        @NonNull
        @Override
        public String[] getStringArray(int id) {
          if (id == RESOURCE_ID) {
            return META_STRING_ARRAY;
          }
          throw new NotFoundException();
        }
      };
    }
  };

  @Before
  public void setUp() {
    // Load the meta-data for the service.
    metaDataService.onCreate();
  }

  @Test
  public void getMetaString_returnsValueIfInBundle() {
    assertThat(metaDataService.getMetaString(CORRECT_NAME, /* defaultValue= */ null))
        .isEqualTo(META_STRING);
  }

  @Test
  public void getMetaString_returnsDefaultValueIfNameNotInBundle() {
    String defaultString = "A default string.";
    assertThat(metaDataService.getMetaString(INVALID_NAME, defaultString))
        .isEqualTo(defaultString);
  }

  @Test
  public void getMetaBoolean_returnsValueIfInBundle() {
    assertThat(metaDataService.getMetaBoolean(CORRECT_NAME, /* defaultValue= */ !META_BOOLEAN))
        .isEqualTo(META_BOOLEAN);
  }

  @Test
  public void getMetaBoolean_returnsDefaultValueIfNameNotInBundle() {
    assertThat(metaDataService.getMetaBoolean(INVALID_NAME, /* defaultValue= */ false)).isFalse();
  }

  @Test
  public void getMetaInt_returnsValueIfInBundle() {
    assertThat(metaDataService.getMetaInt(CORRECT_NAME, /* defaultValue= */ 0)).isEqualTo(META_INT);
  }

  @Test
  public void getMetaInt_returnsDefaultValueIfNameNotInBundle() {
    int defaultValue = -1;
    assertThat(metaDataService.getMetaInt(INVALID_NAME, defaultValue)).isEqualTo(defaultValue);
  }

  @Test
  public void getMetaFloat_returnsValueIfInBundle() {
    assertThat(metaDataService.getMetaFloat(CORRECT_NAME, /* defaultValue= */ 0.0f))
        .isEqualTo(META_FLOAT);
  }

  @Test
  public void getMetaFloat_returnsDefaultValueIfNameNotInBundle() {
    float defaultValue = -1.0f;
    assertThat(metaDataService.getMetaFloat(INVALID_NAME, defaultValue)).isEqualTo(defaultValue);
  }

  @Test
  public void getMetaStringArray_returnsValueIfInBundle() {
    assertThat(metaDataService.getMetaStringArray(CORRECT_NAME, null))
        .asList()
        .containsExactlyElementsIn(META_STRING_ARRAY);
  }

  @Test
  public void getMetaStringArray_returnsDefaultValueIfNameNotInBundle() {
    assertThat(metaDataService.getMetaStringArray(INVALID_NAME, null)).isNull();
  }

  @Test
  public void getMetaResourceId_returnsValueIfInBundle() {
    assertThat(metaDataService.getMetaResourceId(CORRECT_NAME, /* defaultValue= */ 0))
        .isEqualTo(RESOURCE_ID);
  }

  @Test
  public void getMetaResourceId_returnsDefaultValueIfNameNotInBundle() {
    int defaultValue = -1;
    assertThat(metaDataService.getMetaResourceId(INVALID_NAME, defaultValue))
        .isEqualTo(defaultValue);
  }

  @Test
  public void requireMetaString_returnsValueIfInBundle() {
    assertThat(metaDataService.requireMetaString(CORRECT_NAME)).isEqualTo(META_STRING);
  }

  @Test
  public void requireMetaBoolean_returnsValueIfInBundle() {
    assertThat(metaDataService.requireMetaBoolean(CORRECT_NAME)).isEqualTo(META_BOOLEAN);
  }

  @Test
  public void requireMetaInt_returnsValueIfInBundle() {
    assertThat(metaDataService.requireMetaInt(CORRECT_NAME)).isEqualTo(META_INT);
  }

  @Test
  public void requireMetaFloat_returnsValueIfInBundle() {
    assertThat(metaDataService.requireMetaFloat(CORRECT_NAME)).isEqualTo(META_FLOAT);
  }

  @Test
  public void requireMetaStringArray_returnsValueIfInBundle() {
    assertThat(metaDataService.requireMetaStringArray(CORRECT_NAME))
        .asList()
        .containsExactlyElementsIn(META_STRING_ARRAY);
  }

  @Test
  public void requireMetaResourceId_returnsValueIfInBundle() {
    assertThat(metaDataService.requireMetaResourceId(CORRECT_NAME)).isEqualTo(RESOURCE_ID);
  }

  @Test
  public void requireMetaString_throwsIfNameNotInBundle() {
    assertThrows(
        IllegalArgumentException.class,
        () -> metaDataService.requireMetaString(INVALID_NAME));
  }

  @Test
  public void requireMetaBoolean_throwsIfNameNotInBundle() {
    assertThrows(
        IllegalArgumentException.class,
        () -> metaDataService.requireMetaBoolean(INVALID_NAME));
  }

  @Test
  public void requireMetaInt_throwsIfNameNotInBundle() {
    assertThrows(
        IllegalArgumentException.class,
        () -> metaDataService.requireMetaInt(INVALID_NAME));
  }

  @Test
  public void requireMetaFloat_throwsIfNameNotInBundle() {
    assertThrows(
        IllegalArgumentException.class,
        () -> metaDataService.requireMetaFloat(INVALID_NAME));
  }

  @Test
  public void requireMetaStringArray_throwsIfNameNotInBundle() {
    assertThrows(
        IllegalArgumentException.class,
        () -> metaDataService.requireMetaStringArray(INVALID_NAME));
  }

  @Test
  public void requireMetaResourceId_throwsIfNameNotInBundle() {
    assertThrows(
        IllegalArgumentException.class, () -> metaDataService.requireMetaResourceId(INVALID_NAME));
  }

  @Test
  public void onBind_returnsNullByDefault() {
    assertThat(metaDataService.onBind(new Intent())).isNull();
  }
}
