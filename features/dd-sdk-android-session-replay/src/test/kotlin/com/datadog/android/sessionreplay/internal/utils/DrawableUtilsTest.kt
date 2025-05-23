/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.ConstantState
import android.util.AndroidRuntimeException
import android.util.DisplayMetrics
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils.Companion.DRAWABLE_DRAW_FINISHED_WITH_RUNTIME_EXCEPTION
import com.datadog.android.sessionreplay.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.recorder.wrappers.CanvasWrapper
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService
import java.util.stream.Stream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DrawableUtilsTest {

    private lateinit var testedDrawableUtils: DrawableUtils

    @Mock
    private lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    private lateinit var mockBitmapCachesManager: BitmapCachesManager

    @Mock
    private lateinit var mockDrawable: Drawable

    @Mock
    private lateinit var mockCurrentDrawable: Drawable

    @Mock
    private lateinit var mockBitmapWrapper: BitmapWrapper

    @Mock
    private lateinit var mockCanvasWrapper: CanvasWrapper

    @Mock
    private lateinit var mockCanvas: Canvas

    @Mock
    private lateinit var mockBitmap: Bitmap

    @Mock
    private lateinit var mockConfig: Bitmap.Config

    @Mock
    private lateinit var mockExecutorService: ExecutorService

    @Mock
    private lateinit var mockBitmapCreationCallback: ResourceResolver.BitmapCreationCallback

    @Mock
    lateinit var mockConstantState: ConstantState

    @Mock
    lateinit var mockSecondDrawable: Drawable

    @Mock
    lateinit var mockResources: Resources

    @Mock
    private lateinit var mockLogger: InternalLogger

    @BeforeEach
    fun setup() {
        whenever(mockConstantState.newDrawable(mockResources)).thenReturn(mockSecondDrawable)
        whenever(mockDrawable.constantState).thenReturn(mockConstantState)
        whenever(mockCurrentDrawable.constantState).thenReturn(mockConstantState)
        whenever(mockDrawable.current).thenReturn(mockCurrentDrawable)
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), anyOrNull()))
            .thenReturn(mockBitmap)
        whenever(mockCanvasWrapper.createCanvas(any()))
            .thenReturn(mockCanvas)
        whenever(mockBitmap.config).thenReturn(mockConfig)
        whenever(mockBitmapCachesManager.getBitmapByProperties(any(), any(), any())).thenReturn(null)

        whenever(mockExecutorService.execute(any())) doAnswer {
            val runnable = it.getArgument<Runnable>(0)
            runnable.run()
        }

        testedDrawableUtils = DrawableUtils(
            bitmapWrapper = mockBitmapWrapper,
            canvasWrapper = mockCanvasWrapper,
            bitmapCachesManager = mockBitmapCachesManager,
            executorService = mockExecutorService,
            internalLogger = mockLogger
        )
    }

    // region createBitmap

    @Test
    fun `M set width to drawable intrinsic W createBitmapOfApproxSizeFromDrawable() { no resizing }`() {
        // Given
        val requestedSize = 1000
        val edge = 10
        whenever(mockDrawable.intrinsicWidth).thenReturn(edge)
        whenever(mockDrawable.intrinsicHeight).thenReturn(edge)

        val argumentCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            requestedSizeInBytes = requestedSize,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapWrapper).createBitmap(
            bitmapWidth = argumentCaptor.capture(),
            bitmapHeight = argumentCaptor.capture(),
            config = any(),
            displayMetrics = displayMetricsCaptor.capture()
        )

        val width = argumentCaptor.firstValue
        val height = argumentCaptor.secondValue
        assertThat(width).isEqualTo(edge)
        assertThat(height).isEqualTo(edge)
    }

    @Test
    fun `M set height higher W createBitmapOfApproxSizeFromDrawable() { when resizing }`(
        @IntForgery(min = 0, max = 500) viewWidth: Int,
        @IntForgery(min = 501, max = 1000) viewHeight: Int
    ) {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(viewWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(viewHeight)

        val argumentCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapWrapper).createBitmap(
            bitmapWidth = argumentCaptor.capture(),
            bitmapHeight = argumentCaptor.capture(),
            config = any(),
            displayMetrics = displayMetricsCaptor.capture()
        )

        val width = argumentCaptor.firstValue
        val height = argumentCaptor.secondValue
        assertThat(height).isGreaterThanOrEqualTo(width)
    }

    @Test
    fun `M set width higher W createBitmapFromDrawableOfApproxSize() { when resizing }`(
        @IntForgery(min = 501, max = 1000) viewWidth: Int,
        @IntForgery(min = 0, max = 500) viewHeight: Int
    ) {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(viewWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(viewHeight)

        val argumentCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapWrapper).createBitmap(
            bitmapWidth = argumentCaptor.capture(),
            bitmapHeight = argumentCaptor.capture(),
            config = any(),
            displayMetrics = displayMetricsCaptor.capture()
        )

        val width = argumentCaptor.firstValue
        val height = argumentCaptor.secondValue
        assertThat(width).isGreaterThanOrEqualTo(height)

        assertThat(displayMetricsCaptor.firstValue).isEqualTo(mockDisplayMetrics)
    }

    // endregion

    @Test
    fun `M use bitmap from pool W createBitmapOfApproxSizeFromDrawable() { exists in pool }`(
        @IntForgery(min = 1, max = 1000) viewWidth: Int,
        @IntForgery(min = 1, max = 1000) viewHeight: Int
    ) {
        // Given
        val mockBitmapFromPool: Bitmap = mock()
        whenever(mockDrawable.intrinsicWidth).thenReturn(viewWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(viewHeight)
        whenever(mockBitmapCachesManager.getBitmapByProperties(any(), any(), any()))
            .thenReturn(mockBitmapFromPool)

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapCreationCallback).onReady(mockBitmapFromPool)
    }

    @Test
    fun `M call onFailure W createBitmapOfApproxSizeFromDrawable { failed to create bitmap }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(1)
        whenever(mockDrawable.intrinsicHeight).thenReturn(1)
        whenever(mockBitmapCachesManager.getBitmapByProperties(any(), any(), any()))
            .thenReturn(null)
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), anyOrNull()))
            .thenReturn(null)

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapCreationCallback).onFailure()
    }

    @Test
    fun `M call onFailure W createBitmapOfApproxSizeFromDrawable { failed to create new drawable }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(1)
        whenever(mockDrawable.intrinsicHeight).thenReturn(1)
        whenever(mockBitmap.isRecycled).thenReturn(true)
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), any())).thenReturn(
            null
        )

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapCreationCallback).onFailure()
    }

    @Test
    fun `M call onFailure W createBitmapOfApproxSizeFromDrawable { failed to create canvas }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(1)
        whenever(mockDrawable.intrinsicHeight).thenReturn(1)
        whenever(mockCanvasWrapper.createCanvas(any()))
            .thenReturn(null)

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapCreationCallback).onFailure()
    }

    @Test
    fun `M resize image that is greater than limit W createBitmapOfApproxSizeFromDrawable { when resizing }`(
        @IntForgery(min = 501, max = 1000) fakeViewWidth: Int,
        @IntForgery(min = 501, max = 1000) fakeViewHeight: Int
    ) {
        // Given
        val requestedSize = 1000
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeViewWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeViewHeight)

        val argumentCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            requestedSizeInBytes = requestedSize,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapWrapper).createBitmap(
            bitmapWidth = argumentCaptor.capture(),
            bitmapHeight = argumentCaptor.capture(),
            config = any(),
            displayMetrics = displayMetricsCaptor.capture()
        )

        val width = argumentCaptor.firstValue
        val height = argumentCaptor.secondValue
        assertThat(width).isLessThanOrEqualTo(fakeViewWidth)
        assertThat(height).isLessThanOrEqualTo(fakeViewHeight)
        assertThat(displayMetricsCaptor.firstValue).isEqualTo(mockDisplayMetrics)
    }

    @Test
    fun `M return scaled bitmap W createScaledBitmap()`(
        @Mock mockScaledBitmap: Bitmap
    ) {
        // Given
        whenever(
            mockBitmapWrapper.createScaledBitmap(
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(mockScaledBitmap)

        // When
        val actualBitmap = testedDrawableUtils.createScaledBitmap(mockBitmap)

        // Then
        assertThat(actualBitmap).isEqualTo(mockScaledBitmap)
    }

    @ParameterizedTest
    @MethodSource("exceptionTypes")
    fun `M call onFailure W createBitmapOfApproxSizeFromDrawable { drawable draw throws runtime exception }`(
        exceptionType: RuntimeException
    ) {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(1)
        whenever(mockDrawable.intrinsicHeight).thenReturn(1)
        whenever(mockDrawable.draw(any())).thenThrow(exceptionType)

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapCreationCallback).onFailure()

        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            { it == "$DRAWABLE_DRAW_FINISHED_WITH_RUNTIME_EXCEPTION ${mockDrawable.javaClass.canonicalName}" },
            exceptionType
        )
    }

    companion object {
        @JvmStatic
        fun exceptionTypes(): Stream<RuntimeException> {
            return Stream.of(
                AndroidRuntimeException(),
                IndexOutOfBoundsException()
            )
        }
    }
}
