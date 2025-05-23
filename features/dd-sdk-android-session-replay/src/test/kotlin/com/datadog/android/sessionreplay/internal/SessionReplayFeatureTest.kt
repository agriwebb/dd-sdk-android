/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Application
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.sessionreplay.NoOpSessionReplayInternalCallback
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.net.SegmentRequestFactory
import com.datadog.android.sessionreplay.internal.recorder.NoOpRecorder
import com.datadog.android.sessionreplay.internal.recorder.Recorder
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayRecorder
import com.datadog.android.sessionreplay.internal.storage.NoOpRecordWriter
import com.datadog.android.sessionreplay.internal.storage.SessionReplayRecordWriter
import com.datadog.android.sessionreplay.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.sessionreplay.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayFeatureTest {

    private lateinit var testedFeature: SessionReplayFeature

    @Forgery
    lateinit var fakeConfiguration: SessionReplayConfiguration

    @Mock
    lateinit var mockRecorder: Recorder

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTouchPrivacyManager: TouchPrivacyManager

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockSampler: Sampler<Unit>

    private lateinit var fakeSessionId: String

    private var fakeSampleRate: Float? = null

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSampleRate = forge.aNullable { aFloat() }
        whenever(mockSampler.getSampleRate()).thenReturn(fakeSampleRate)
        fakeSessionId = UUID.randomUUID().toString()
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.createSingleThreadExecutorService(any())) doReturn mockExecutorService

        testedFeature = SessionReplayFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeConfiguration.customEndpointUrl,
            privacy = fakeConfiguration.privacy,
            textAndInputPrivacy = fakeConfiguration.textAndInputPrivacy,
            imagePrivacy = fakeConfiguration.imagePrivacy,
            startRecordingImmediately = true,
            touchPrivacy = fakeConfiguration.touchPrivacy,
            rateBasedSampler = mockSampler
        ) { _, _, _, _ -> mockRecorder }
    }

    @Test
    fun `M initialize writer W initialize()`() {
        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(SessionReplayRecordWriter::class.java)
    }

    @Test
    fun `M initialize session replay recorder W initialize()`() {
        // Given
        testedFeature = SessionReplayFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeConfiguration.customEndpointUrl,
            privacy = fakeConfiguration.privacy,
            textAndInputPrivacy = fakeConfiguration.textAndInputPrivacy,
            imagePrivacy = fakeConfiguration.imagePrivacy,
            touchPrivacy = fakeConfiguration.touchPrivacy,
            touchPrivacyManager = mockTouchPrivacyManager,
            customMappers = emptyList(),
            customOptionSelectorDetectors = emptyList(),
            customDrawableMappers = emptyList(),
            startRecordingImmediately = true,
            sampleRate = fakeConfiguration.sampleRate,
            dynamicOptimizationEnabled = fakeConfiguration.dynamicOptimizationEnabled,
            internalCallback = NoOpSessionReplayInternalCallback()
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.sessionReplayRecorder)
            .isInstanceOf(SessionReplayRecorder::class.java)
    }

    @Test
    fun `M update feature context for telemetry W initialize()`() {
        // Given
        testedFeature = SessionReplayFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeConfiguration.customEndpointUrl,
            privacy = fakeConfiguration.privacy,
            textAndInputPrivacy = fakeConfiguration.textAndInputPrivacy,
            imagePrivacy = fakeConfiguration.imagePrivacy,
            touchPrivacy = fakeConfiguration.touchPrivacy,
            touchPrivacyManager = mockTouchPrivacyManager,
            customMappers = emptyList(),
            customOptionSelectorDetectors = emptyList(),
            customDrawableMappers = emptyList(),
            sampleRate = fakeConfiguration.sampleRate,
            startRecordingImmediately = true,
            dynamicOptimizationEnabled = fakeConfiguration.dynamicOptimizationEnabled,
            internalCallback = NoOpSessionReplayInternalCallback()
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        argumentCaptor<(context: MutableMap<String, Any?>) -> Unit> {
            val updatedContext = mutableMapOf<String, Any?>()
            verify(mockSdkCore).updateFeatureContext(
                eq(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME),
                capture()
            )
            firstValue.invoke(updatedContext)
            assertThat(updatedContext[SessionReplayFeature.SESSION_REPLAY_SAMPLE_RATE_KEY])
                .isEqualTo(fakeConfiguration.sampleRate.toLong())
            assertThat(updatedContext[SessionReplayFeature.SESSION_REPLAY_START_IMMEDIATE_RECORDING_KEY])
                .isEqualTo(true)
            assertThat(updatedContext[SessionReplayFeature.SESSION_REPLAY_IMAGE_PRIVACY_KEY])
                .isEqualTo(fakeConfiguration.imagePrivacy.toString().lowercase(Locale.US))
            assertThat(updatedContext[SessionReplayFeature.SESSION_REPLAY_TOUCH_PRIVACY_KEY])
                .isEqualTo(fakeConfiguration.touchPrivacy.toString().lowercase(Locale.US))
            assertThat(updatedContext[SessionReplayFeature.SESSION_REPLAY_TEXT_AND_INPUT_PRIVACY_KEY])
                .isEqualTo(fakeConfiguration.textAndInputPrivacy.toString().lowercase(Locale.US))
        }
    }

    @Test
    fun `M set the feature event receiver W initialize()`() {
        // Given
        testedFeature = SessionReplayFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeConfiguration.customEndpointUrl,
            privacy = fakeConfiguration.privacy,
            textAndInputPrivacy = fakeConfiguration.textAndInputPrivacy,
            imagePrivacy = fakeConfiguration.imagePrivacy,
            startRecordingImmediately = true,
            touchPrivacy = fakeConfiguration.touchPrivacy,
            rateBasedSampler = mockSampler
        ) { _, _, _, _ -> mockRecorder }

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        verify(mockSdkCore).setEventReceiver(
            SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME,
            testedFeature
        )
    }

    @Test
    fun `M register the Session Replay lifecycle callback W initialize()`() {
        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        verify(mockRecorder).registerCallbacks()
    }

    @Test
    fun `M unregister the Session Replay lifecycle callback W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        verify(mockRecorder).unregisterCallbacks()
    }

    @Test
    fun `M stop processing records in the recorder W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        verify(mockRecorder).stopProcessingRecords()
    }

    @Test
    fun `M invalidate the feature components W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpRecordWriter::class.java)
        assertThat(testedFeature.sessionReplayRecorder).isInstanceOf(NoOpRecorder::class.java)
        assertThat(testedFeature.initialized.get()).isFalse
    }

    @Test
    fun `M stop all the recorders in the recorder W stopRecording()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.startRecording()

        // When
        testedFeature.stopRecording()

        // Then
        verify(mockRecorder).stopRecorders()
    }

    @Test
    fun `M update the isEnabled flag into the context W stopRecording()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.startRecording()

        // When
        testedFeature.stopRecording()

        // Then
        argumentCaptor<(context: MutableMap<String, Any?>) -> Unit> {
            val updatedContext = mutableMapOf<String, Any?>()
            verify(mockSdkCore, times(3)).updateFeatureContext(
                eq(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME),
                capture()
            )
            allValues.forEach { it.invoke(updatedContext) }
            assertThat(updatedContext[SessionReplayFeature.SESSION_REPLAY_ENABLED_KEY])
                .isEqualTo(false)
        }
    }

    @Test
    fun `M do nothing W stopRecording() { was already stopped }`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.startRecording()
        testedFeature.stopRecording()

        // When
        testedFeature.stopRecording()

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
            verify(mockRecorder).stopRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M do nothing W stopRecording() { initialize without Application context }`() {
        // Given
        testedFeature.onInitialize(mock())

        // When
        testedFeature.stopRecording()

        // Then
        verifyNoInteractions(mockRecorder)
    }

    @Test
    fun `M resume recorders W startRecording() { was stopped before }`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.startRecording()
        testedFeature.stopRecording()

        // When
        testedFeature.startRecording()

        // Then
        verify(mockRecorder, times(2))
            .resumeRecorders()
    }

    @Test
    fun `M update the isEnabled flag into the context W startRecording()`() {
        // When
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.startRecording()

        // Then
        argumentCaptor<(context: MutableMap<String, Any?>) -> Unit> {
            val updatedContext = mutableMapOf<String, Any?>()
            verify(mockSdkCore, times(2)).updateFeatureContext(
                eq(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME),
                capture()
            )
            allValues.forEach { it.invoke(updatedContext) }
            assertThat(updatedContext[SessionReplayFeature.SESSION_REPLAY_ENABLED_KEY])
                .isEqualTo(true)
        }
    }

    @Test
    fun `M resume recorders only once W startRecording() { multi threads }`() {
        // Given
        val countDownLatch = CountDownLatch(3)
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        repeat(3) {
            Thread {
                testedFeature.startRecording()
                countDownLatch.countDown()
            }.start()
        }

        // Then
        countDownLatch.await(5, TimeUnit.SECONDS)
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M do nothing W startRecording() { was already started before }`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.startRecording()

        // When
        testedFeature.startRecording()

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M do nothing W startRecording() { initialize without Application context }`() {
        // Given
        testedFeature.onInitialize(mock())

        // When
        testedFeature.startRecording()

        // Then
        verifyNoInteractions(mockRecorder)
    }

    @Test
    fun `M log warning and do nothing W startRecording() { feature is not initialized }`() {
        // When
        testedFeature.startRecording()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            SessionReplayFeature.CANNOT_START_RECORDING_NOT_INITIALIZED
        )
        verifyNoInteractions(mockRecorder)
    }

    @Test
    fun `M log warning and do nothing W onInitialize() { context is not Application }`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            SessionReplayFeature.REQUIRES_APPLICATION_CONTEXT_WARN_MESSAGE
        )
        verifyNoInteractions(mockRecorder)
    }

    @Test
    fun `M startRecording W rum session updated { keep, sampled in }`() {
        // Given
        whenever(mockSampler.sample(any())).thenReturn(true)
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.stopRecording()
        val rumSessionUpdateBusMessage = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage)

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M doNothing W rum session updated { keep, sessionId is null }`() {
        // Given
        whenever(mockSampler.sample(any())).thenReturn(true)
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.stopRecording()
        val rumSessionUpdateBusMessage = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage)

        // Then
        verify(mockRecorder).registerCallbacks()
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M do nothing W rum session updated { keep false, sampled in }`() {
        // Given
        whenever(mockSampler.sample(any())).thenReturn(true)
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.stopRecording()
        val rumSessionUpdateBusMessage = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                false,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage)

        // Then
        verify(mockRecorder).registerCallbacks()
        verifyNoMoreInteractions(mockRecorder)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            SessionReplayFeature.SESSION_NOT_KEPT_MESSAGE
        )
    }

    @Test
    fun `M only startRecording once W rum session updated { same session Id }`() {
        // Given
        whenever(mockSampler.sample(any())).thenReturn(true)
        testedFeature.onInitialize(appContext.mockInstance)
        val rumSessionUpdateBusMessage1 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId
        )
        val rumSessionUpdateBusMessage2 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                false,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage1)
        testedFeature.onReceive(rumSessionUpdateBusMessage2)

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M do nothing W rum session updated { keep true, sampled out }`() {
        // Given
        whenever(mockSampler.sample(any())).thenReturn(false)
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.stopRecording()
        val rumSessionUpdateBusMessage = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage)

        // Then
        verify(mockRecorder).registerCallbacks()
        verifyNoMoreInteractions(mockRecorder)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            SessionReplayFeature.SESSION_SAMPLED_OUT_MESSAGE
        )
    }

    @Test
    fun `M stopRecording W rum session updated { keep false, sample in }`() {
        // Given
        val fakeSessionId2 = UUID.randomUUID().toString()
        whenever(mockSampler.sample(any())).thenReturn(true)
        testedFeature.onInitialize(appContext.mockInstance)
        val rumSessionUpdateBusMessage1 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId
        )
        val rumSessionUpdateBusMessage2 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                false,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId2
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage1)
        testedFeature.onReceive(rumSessionUpdateBusMessage2)

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
            verify(mockRecorder).stopRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            SessionReplayFeature.SESSION_NOT_KEPT_MESSAGE
        )
    }

    @Test
    fun `M stopRecording W rum session updated { keep true, sample out }`() {
        // Given
        val fakeSessionId2 = UUID.randomUUID().toString()
        whenever(mockSampler.sample(any())).thenReturn(true).thenReturn(false)
        testedFeature.onInitialize(appContext.mockInstance)
        val rumSessionUpdateBusMessage1 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )
        val rumSessionUpdateBusMessage2 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId2
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage1)
        testedFeature.onReceive(rumSessionUpdateBusMessage2)

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
            verify(mockRecorder).stopRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            SessionReplayFeature.SESSION_SAMPLED_OUT_MESSAGE
        )
    }

    @Test
    fun `M stopRecording W rum session updated { keep false, sample out }`() {
        // Given
        val fakeSessionId2 = UUID.randomUUID().toString()
        whenever(mockSampler.sample(any())).thenReturn(true).thenReturn(false)
        testedFeature.onInitialize(appContext.mockInstance)
        val rumSessionUpdateBusMessage1 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId
        )
        val rumSessionUpdateBusMessage2 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                false,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId2
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage1)
        testedFeature.onReceive(rumSessionUpdateBusMessage2)

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
            verify(mockRecorder).stopRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            SessionReplayFeature.SESSION_NOT_KEPT_MESSAGE
        )
    }

    @Test
    fun `M log warning W rum session updated before initialization { keep true, sample in }`() {
        // Given
        whenever(mockSampler.sample(any())).thenReturn(true)
        val rumSessionUpdateBusMessage = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            SessionReplayFeature.CANNOT_START_RECORDING_NOT_INITIALIZED
        )
        verifyNoInteractions(mockRecorder)
    }

    @Test
    fun `M log warning W rum session updated before initialization { keep true, sample out }`() {
        // Given
        whenever(mockSampler.sample(any())).thenReturn(false)
        val rumSessionUpdateBusMessage = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            SessionReplayFeature.CANNOT_START_RECORDING_NOT_INITIALIZED
        )
        verifyNoInteractions(mockRecorder)
    }

    @Test
    fun `M log warning W rum session updated before initialization { keep false, sample in }`() {
        // Given
        whenever(mockSampler.sample(any())).thenReturn(true)
        val rumSessionUpdateBusMessage = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                false,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            SessionReplayFeature.CANNOT_START_RECORDING_NOT_INITIALIZED
        )
        verifyNoInteractions(mockRecorder)
    }

    @Test
    fun `M log warning W rum session updated before initialization { keep false, sample out }`() {
        // Given
        whenever(mockSampler.sample(any())).thenReturn(false)
        val rumSessionUpdateBusMessage = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                false,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            SessionReplayFeature.CANNOT_START_RECORDING_NOT_INITIALIZED
        )
        verifyNoInteractions(mockRecorder)
    }

    @Test
    fun `M not resume recording in new session W user call stopRecording {startRecordingImmediately = true}`(
        @Forgery fakeUUID2: UUID
    ) {
        // Given
        val fakeSessionId2 = fakeUUID2.toString()
        whenever(mockSampler.sample(any())).thenReturn(true)
        testedFeature = SessionReplayFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeConfiguration.customEndpointUrl,
            privacy = fakeConfiguration.privacy,
            textAndInputPrivacy = fakeConfiguration.textAndInputPrivacy,
            imagePrivacy = fakeConfiguration.imagePrivacy,
            touchPrivacy = fakeConfiguration.touchPrivacy,
            startRecordingImmediately = true,
            rateBasedSampler = mockSampler
        ) { _, _, _, _ -> mockRecorder }
        testedFeature.onInitialize(appContext.mockInstance)
        val rumSessionUpdateBusMessage1 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId
        )
        val rumSessionUpdateBusMessage2 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId2
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage1)
        testedFeature.manuallyStopRecording()
        testedFeature.onReceive(rumSessionUpdateBusMessage2)

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
            verify(mockRecorder).stopRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M resume recording in new session W user call startRecording {startRecordingImmediately = false}`(
        @Forgery fakeUUID3: UUID,
        @Forgery fakeUUID4: UUID
    ) {
        // Given
        val fakeSessionId3 = fakeUUID3.toString()
        val fakeSessionId4 = fakeUUID4.toString()
        whenever(mockSampler.sample(any())).thenReturn(true)
        testedFeature = SessionReplayFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeConfiguration.customEndpointUrl,
            privacy = fakeConfiguration.privacy,
            textAndInputPrivacy = fakeConfiguration.textAndInputPrivacy,
            imagePrivacy = fakeConfiguration.imagePrivacy,
            touchPrivacy = fakeConfiguration.touchPrivacy,
            startRecordingImmediately = false,
            rateBasedSampler = mockSampler
        ) { _, _, _, _ -> mockRecorder }
        testedFeature.onInitialize(appContext.mockInstance)
        val rumSessionUpdateBusMessage1 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId
        )
        val rumSessionUpdateBusMessage2 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId
        )
        val rumSessionUpdateBusMessage3 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                false,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId3
        )
        val rumSessionUpdateBusMessage4 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId4
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage1)
        testedFeature.manuallyStartRecording()
        // send an event with same session id to process manual start recording.
        testedFeature.onReceive(rumSessionUpdateBusMessage2)
        testedFeature.onReceive(rumSessionUpdateBusMessage3)
        testedFeature.onReceive(rumSessionUpdateBusMessage4)

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
            verify(mockRecorder).stopRecorders()
            verify(mockRecorder).resumeRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M start recording W rum session is initialized after first message`() {
        // Given
        whenever(mockSampler.sample(any())).thenReturn(true)
        val rumSessionUpdateBusMessage1 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )
        val rumSessionUpdateBusMessage2 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage1)
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.onReceive(rumSessionUpdateBusMessage2)

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M log warning and do nothing W onReceive() { unknown event type }`() {
        // When
        testedFeature.onReceive(Any())

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            SessionReplayFeature.UNSUPPORTED_EVENT_TYPE.format(
                Locale.US,
                Any()::class.java.canonicalName
            )
        )

        verifyNoInteractions(mockRecorder)
    }

    @Test
    fun `M log warning and do nothing W onReceive() { unknown type property value }`(
        forge: Forge,
        @Mock fakeContext: Application
    ) {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                forge.anAlphabeticalString()
        )

        // When
        testedFeature.onInitialize(fakeContext)
        testedFeature.onReceive(event)

        // Then
        val expectedMessage = SessionReplayFeature.UNKNOWN_EVENT_TYPE_PROPERTY_VALUE
            .format(Locale.US, event[SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY])
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            expectedMessage
        )

        verify(mockRecorder, never()).resumeRecorders()
    }

    @Test
    fun `M log warning and do nothing W onReceive() { missing mandatory fields }`(
        @Mock fakeContext: Application
    ) {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE
        )

        // When
        testedFeature.onInitialize(fakeContext)
        testedFeature.onReceive(event)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            SessionReplayFeature.EVENT_MISSING_MANDATORY_FIELDS
        )

        verify(mockRecorder, never()).resumeRecorders()
    }

    @Test
    fun `M log warning and do nothing W onReceive() { missing keep state field }`(
        @Mock fakeContext: Application
    ) {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )

        // When
        testedFeature.onInitialize(fakeContext)
        testedFeature.onReceive(event)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            SessionReplayFeature.EVENT_MISSING_MANDATORY_FIELDS
        )

        verify(mockRecorder, never()).resumeRecorders()
    }

    @Test
    fun `M log warning and do nothing W onReceive() { missing session id field }`(
        @BoolForgery fakeKeep: Boolean,
        @Mock fakeContext: Application
    ) {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to fakeKeep
        )

        // When
        testedFeature.onInitialize(fakeContext)
        testedFeature.onReceive(event)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            SessionReplayFeature.EVENT_MISSING_MANDATORY_FIELDS
        )

        verify(mockRecorder, never()).resumeRecorders()
    }

    @Test
    fun `M log warning and do nothing W onReceive() { mandatory fields have wrong format }`(
        forge: Forge,
        @Mock fakeContext: Application
    ) {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                forge.anAlphabeticalString(),
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                fakeSessionId
        )

        // When
        testedFeature.onInitialize(fakeContext)
        testedFeature.onReceive(event)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            SessionReplayFeature.EVENT_MISSING_MANDATORY_FIELDS
        )

        verify(mockRecorder, never()).resumeRecorders()
    }

    @Test
    fun `M provide session replay feature name W name()`() {
        // When+Then
        assertThat(testedFeature.name)
            .isEqualTo(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME)
    }

    @Test
    fun `M provide Session Replay request factory W requestFactory()`() {
        // When+Then
        assertThat(testedFeature.requestFactory)
            .isInstanceOf(SegmentRequestFactory::class.java)
    }

    @Test
    fun `M provide custom storage configuration W storageConfiguration()`() {
        // When+Then
        assertThat(testedFeature.storageConfiguration)
            .isEqualTo(SessionReplayFeature.STORAGE_CONFIGURATION)
    }

    // region startRecordingImmediately

    @ParameterizedTest
    @MethodSource("recordingScenarioProvider")
    fun `M start recording W startRecordingImmediately`(
        scenario: SessionRecordingScenario
    ) {
        // Given
        val fakeContext = mock<Application>()
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to scenario.keepSession,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )

        whenever(mockSampler.sample(any())).thenReturn(scenario.sampleInSession)

        // When
        testedFeature = SessionReplayFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeConfiguration.customEndpointUrl,
            privacy = fakeConfiguration.privacy,
            textAndInputPrivacy = fakeConfiguration.textAndInputPrivacy,
            imagePrivacy = fakeConfiguration.imagePrivacy,
            touchPrivacy = fakeConfiguration.touchPrivacy,
            startRecordingImmediately = scenario.startRecordingImmediately,
            rateBasedSampler = mockSampler
        ) { _, _, _, _ -> mockRecorder }
        testedFeature.onInitialize(fakeContext)
        testedFeature.onReceive(event)

        // Then
        if (scenario.expectedResult) {
            verify(mockRecorder).resumeRecorders()
        } else {
            verify(mockRecorder, never()).resumeRecorders()
        }
    }

    // endregion

    // region manual stop/start

    @Test
    fun `M start recorders only once W onReceive { sessionId is the same and recordingState did not change }`(
        @Mock fakeContext: Application
    ) {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )
        whenever(mockSampler.sample(any())).thenReturn(true)

        // When
        testedFeature = SessionReplayFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeConfiguration.customEndpointUrl,
            privacy = fakeConfiguration.privacy,
            textAndInputPrivacy = fakeConfiguration.textAndInputPrivacy,
            imagePrivacy = fakeConfiguration.imagePrivacy,
            touchPrivacy = fakeConfiguration.touchPrivacy,
            startRecordingImmediately = true,
            rateBasedSampler = mockSampler
        ) { _, _, _, _ -> mockRecorder }
        testedFeature.onInitialize(fakeContext)
        testedFeature.onReceive(event)
        testedFeature.onReceive(event)

        // Then
        verify(mockRecorder, times(1)).resumeRecorders()
    }

    @Test
    fun `M call resumeRecorders W manuallyStartRecording`(
        @Mock fakeContext: Application
    ) {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )
        whenever(mockSampler.sample(any())).thenReturn(true)

        // When
        testedFeature = SessionReplayFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeConfiguration.customEndpointUrl,
            privacy = fakeConfiguration.privacy,
            textAndInputPrivacy = fakeConfiguration.textAndInputPrivacy,
            imagePrivacy = fakeConfiguration.imagePrivacy,
            touchPrivacy = fakeConfiguration.touchPrivacy,
            startRecordingImmediately = false,
            rateBasedSampler = mockSampler
        ) { _, _, _, _ -> mockRecorder }
        testedFeature.onInitialize(fakeContext)
        testedFeature.manuallyStartRecording()
        testedFeature.onReceive(event)

        // Then
        verify(mockRecorder).resumeRecorders()
    }

    @Test
    fun `M call stopRecorders W manuallyStopRecording { if already recording }`(
        @Mock fakeContext: Application,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeSessionId
        )

        whenever(mockSampler.sample(any())).thenReturn(true)

        // When
        testedFeature = SessionReplayFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeConfiguration.customEndpointUrl,
            privacy = fakeConfiguration.privacy,
            textAndInputPrivacy = fakeConfiguration.textAndInputPrivacy,
            imagePrivacy = fakeConfiguration.imagePrivacy,
            touchPrivacy = fakeConfiguration.touchPrivacy,
            startRecordingImmediately = true,
            rateBasedSampler = mockSampler
        ) { _, _, _, _ -> mockRecorder }
        testedFeature.onInitialize(fakeContext)
        testedFeature.onReceive(event)
        testedFeature.manuallyStopRecording()
        testedFeature.onReceive(event)

        // Then
        verify(mockRecorder).stopRecorders()
    }

    @Test
    fun `M resume recordings W keepSession changes from false to true`(
        @Mock fakeContext: Application,
        @Forgery fakeUUID1: UUID,
        @Forgery fakeUUID2: UUID,
        @Forgery fakeUUID3: UUID
    ) {
        // Given
        val event1 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeUUID1.toString()
        )

        whenever(mockSampler.sample(any())).thenReturn(true)

        // When
        testedFeature = SessionReplayFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeConfiguration.customEndpointUrl,
            privacy = fakeConfiguration.privacy,
            textAndInputPrivacy = fakeConfiguration.textAndInputPrivacy,
            imagePrivacy = fakeConfiguration.imagePrivacy,
            touchPrivacy = fakeConfiguration.touchPrivacy,
            startRecordingImmediately = true,
            rateBasedSampler = mockSampler
        ) { _, _, _, _ -> mockRecorder }
        testedFeature.onInitialize(fakeContext)
        testedFeature.onReceive(event1)

        // When
        val event2 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeUUID2.toString()
        )
        testedFeature.onReceive(event2)

        // When
        val event3 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
            SessionReplayFeature.RUM_SESSION_ID_BUS_MESSAGE_KEY to fakeUUID3.toString()
        )
        testedFeature.onReceive(event3)

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).resumeRecorders()
            verify(mockRecorder).stopRecorders()
            verify(mockRecorder).resumeRecorders()
        }
    }

    // endregion

    internal data class SessionRecordingScenario(
        val startRecordingImmediately: Boolean,
        val keepSession: Boolean,
        val sampleInSession: Boolean,
        val expectedResult: Boolean
    )

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        @Suppress("unused") // this is actually used
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }

        @JvmStatic
        fun recordingScenarioProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    SessionRecordingScenario(
                        startRecordingImmediately = true,
                        keepSession = true,
                        sampleInSession = true,
                        expectedResult = true
                    )
                ),
                Arguments.of(
                    SessionRecordingScenario(
                        startRecordingImmediately = true,
                        keepSession = false,
                        sampleInSession = true,
                        expectedResult = false
                    )
                ),
                Arguments.of(
                    SessionRecordingScenario(
                        startRecordingImmediately = true,
                        keepSession = true,
                        sampleInSession = false,
                        expectedResult = false
                    )
                ),
                Arguments.of(
                    SessionRecordingScenario(
                        startRecordingImmediately = true,
                        keepSession = false,
                        sampleInSession = false,
                        expectedResult = false
                    )
                ),
                Arguments.of(
                    SessionRecordingScenario(
                        startRecordingImmediately = false,
                        keepSession = true,
                        sampleInSession = true,
                        expectedResult = false
                    )
                ),
                Arguments.of(
                    SessionRecordingScenario(
                        startRecordingImmediately = false,
                        keepSession = false,
                        sampleInSession = true,
                        expectedResult = false
                    )
                ),
                Arguments.of(
                    SessionRecordingScenario(
                        startRecordingImmediately = false,
                        keepSession = true,
                        sampleInSession = false,
                        expectedResult = false
                    )
                ),
                Arguments.of(
                    SessionRecordingScenario(
                        startRecordingImmediately = false,
                        keepSession = false,
                        sampleInSession = false,
                        expectedResult = false
                    )
                )
            )
        }
    }
}
