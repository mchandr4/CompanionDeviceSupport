/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.connecteddevice.util

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class MetaDataParserTest {
  private val mockPackageManager = mock<PackageManager>()
  private val context = FakeContext(mockPackageManager)
  private val serviceInfo = ServiceInfo()
  private lateinit var metaParser: MetaDataParser

  @Before
  fun setUp() {
    val testBundle = Bundle().apply { putInt(CORRECT_NAME, FakeContext.RESOURCE_ID) }
    whenever(
        mockPackageManager.getServiceInfo(any<ComponentName>(), eq(PackageManager.GET_META_DATA))
      )
      .thenReturn(serviceInfo)
    serviceInfo.metaData = testBundle
    metaParser = MetaDataParser(context)
  }

  @Test
  fun getMetaString_returnCorrectValueIfInBundle() {
    val defaultValue = "defaultString"
    val value = metaParser.getMetaString(CORRECT_NAME, defaultValue)
    assertThat(value).isEqualTo(FakeContext.META_STRING)
  }

  @Test
  fun getMetaString_returnDefaultValueIfNotInBundle() {
    val defaultValue = "defaultString"
    val value = metaParser.getMetaString(INVALID_NAME, defaultValue)
    assertThat(value).isEqualTo(defaultValue)
  }

  @Test
  fun getMetaInt_returnCorrectValueIfInBundle() {
    val defaultValue = FakeContext.META_INT + 1
    val value = metaParser.getMetaInt(CORRECT_NAME, defaultValue)
    assertThat(value).isEqualTo(FakeContext.META_INT)
  }

  @Test
  fun getMetaInt_returnDefaultValueIfNotInBundle() {
    val defaultValue = FakeContext.META_INT + 1
    val value = metaParser.getMetaInt(INVALID_NAME, defaultValue)
    assertThat(value).isEqualTo(defaultValue)
  }

  @Test
  fun getMetaStringArray_returnCorrectValueIfInBundle() {
    val defaultValue = emptyArray<String>()
    val value = metaParser.getMetaStringArray(CORRECT_NAME, defaultValue)
    assertThat(value).isEqualTo(FakeContext.META_STRING_ARRAY)
  }

  @Test
  fun getMetaStringArray_returnDefaultValueIfInBundle() {
    val defaultValue = emptyArray<String>()
    val value = metaParser.getMetaStringArray(INVALID_NAME, defaultValue)
    assertThat(value).isEqualTo(defaultValue)
  }

  @Test
  fun getMetaBoolean_returnCorrectValueIfInBundle() {
    val defaultValue = !FakeContext.META_BOOLEAN
    val value = metaParser.getMetaBoolean(CORRECT_NAME, defaultValue)
    assertThat(value).isEqualTo(FakeContext.META_BOOLEAN)
  }

  @Test
  fun getMetaBoolean_returnDefaultValueIfInBundle() {
    val defaultValue = !FakeContext.META_BOOLEAN
    val value = metaParser.getMetaBoolean(INVALID_NAME, defaultValue)
    assertThat(value).isEqualTo(defaultValue)
  }
  companion object {
    private const val CORRECT_NAME = "CorrectMetaDataKey"
    private const val INVALID_NAME = "InvalidMetaDataKey"
  }
}

private open class FakeContext(private val mockPackageManager: PackageManager) :
  ContextWrapper(ApplicationProvider.getApplicationContext()) {

  override fun getPackageManager(): PackageManager = mockPackageManager

  override fun getResources(): Resources {
    return object :
      Resources(
        applicationContext.resources.assets,
        applicationContext.resources.displayMetrics,
        applicationContext.resources.configuration
      ) {
      override fun getString(id: Int): String {
        if (id == RESOURCE_ID) {
          return META_STRING
        }
        throw NotFoundException()
      }

      override fun getInteger(id: Int): Int {
        if (id == RESOURCE_ID) {
          return META_INT
        }
        throw NotFoundException()
      }

      override fun getStringArray(id: Int): Array<String> {
        if (id == RESOURCE_ID) {
          return META_STRING_ARRAY
        }
        throw NotFoundException()
      }

      override fun getBoolean(id: Int): Boolean {
        if (id == RESOURCE_ID) {
          return META_BOOLEAN
        }
        throw NotFoundException()
      }
    }
  }
  companion object {
    const val RESOURCE_ID = 2
    const val META_STRING = "Some meta-data string."
    const val META_INT = 1
    const val META_BOOLEAN = true
    val META_STRING_ARRAY = arrayOf("test1", "test2")
  }
}
