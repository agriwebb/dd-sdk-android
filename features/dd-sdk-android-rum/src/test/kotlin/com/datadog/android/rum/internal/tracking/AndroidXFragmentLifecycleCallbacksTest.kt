/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.app.Dialog
import android.content.Context
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.rum.internal.utils.resolveViewUrl
import com.datadog.android.rum.tracking.ComponentPredicate
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class AndroidXFragmentLifecycleCallbacksTest {

    lateinit var testedLifecycleCallbacks: AndroidXFragmentLifecycleCallbacks

    @Mock
    lateinit var mockFragment: Fragment

    @Mock
    lateinit var mockFragmentActivity: FragmentActivity

    @Mock
    lateinit var mockFragmentManager: FragmentManager

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockDialog: Dialog

    @Mock
    lateinit var mockUserActionTrackingStrategy: UserActionTrackingStrategy

    @Mock
    lateinit var mockGesturesTracker: GesturesTracker

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockScheduledExecutorService: ScheduledExecutorService

    @Mock
    lateinit var mockPredicate: ComponentPredicate<Fragment>

    lateinit var fakeAttributes: Map<String, Any?>

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        val mockRumFeature = mock<RumFeature>()
        whenever(mockRumFeature.actionTrackingStrategy) doReturn mockUserActionTrackingStrategy
        whenever(mockUserActionTrackingStrategy.getGesturesTracker()) doReturn mockGesturesTracker
        whenever(mockFragmentActivity.supportFragmentManager).thenReturn(mockFragmentManager)
        whenever(mockSdkCore.createScheduledExecutorService(any())) doReturn mockScheduledExecutorService
        whenever(
            mockScheduledExecutorService.schedule(
                any(),
                ArgumentMatchers.eq(AndroidXFragmentLifecycleCallbacks.STOP_VIEW_DELAY_MS),
                ArgumentMatchers.eq(TimeUnit.MILLISECONDS)
            )
        ) doAnswer { invocationOnMock ->
            (invocationOnMock.arguments[0] as Runnable).run()
            null
        }

        fakeAttributes = forge.aMap { forge.aString() to forge.aString() }
        testedLifecycleCallbacks = AndroidXFragmentLifecycleCallbacks(
            { fakeAttributes },
            mockPredicate,
            rumMonitor = mockRumMonitor,
            rumFeature = mockRumFeature
        )
    }

    // region Track RUM View

    @Test
    fun `M start a RUM View event W onFragmentResumed()`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verify(mockRumMonitor).startView(
            mockFragment,
            mockFragment.resolveViewUrl(),
            fakeAttributes
        )
    }

    @Test
    fun `M start a RUM View event W onFragmentResumed() {custom view name}`(
        @StringForgery fakeName: String
    ) {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true
        whenever(mockPredicate.getViewName(mockFragment)) doReturn fakeName

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verify(mockRumMonitor).startView(
            mockFragment,
            fakeName,
            fakeAttributes
        )
    }

    @Test
    fun `M start a RUM View event W onFragmentResumed() {custom blank view name}`(
        @StringForgery(StringForgeryType.WHITESPACE) fakeName: String
    ) {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true
        whenever(mockPredicate.getViewName(mockFragment)) doReturn fakeName

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verify(mockRumMonitor).startView(
            mockFragment,
            mockFragment.resolveViewUrl(),
            fakeAttributes
        )
    }

    @Test
    fun `M start RUM View W onFragmentResumed() { first display }`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verify(mockRumMonitor).startView(
            mockFragment,
            mockFragment.resolveViewUrl(),
            fakeAttributes
        )
    }

    @Test
    fun `M start RUM View W onFragmentResumed() { redisplay }`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verify(mockRumMonitor).startView(
            mockFragment,
            mockFragment.resolveViewUrl(),
            fakeAttributes
        )
    }

    @Test
    fun `M not stop RUM View W onFragmentPaused()`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentPaused(mockFragmentManager, mockFragment)
        Thread.sleep(250)

        // Then
        verify(mockRumMonitor, never()).stopView(mockFragment, emptyMap())
    }

    @Test
    fun `M stop RUM View W onFragmentStopped()`() {
        // Given
        testedLifecycleCallbacks.register(mockFragmentActivity, mockSdkCore)
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentStopped(mockFragmentManager, mockFragment)
        Thread.sleep(250)

        // Then
        verify(mockRumMonitor).stopView(mockFragment, emptyMap())
    }

    // endregion

    // region Track RUM View (not tracked)

    @Test
    fun `M start a RUM View event W onFragmentResumed() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `M start RUM View W onFragmentResumed() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `M stop RUM View W onActivityPaused() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentPaused(mockFragmentManager, mockFragment)

        // Then
        verifyNoInteractions(mockRumMonitor)
    }

    // endregion

    @Test
    fun `when fragment view created on DialogFragment, it will register a Window Callback`() {
        val mockDialogFragment: DialogFragment = mock()
        whenever(mockDialogFragment.context) doReturn mockContext
        whenever(mockDialogFragment.dialog) doReturn mockDialog
        whenever(mockDialog.window) doReturn mockWindow
        testedLifecycleCallbacks.register(mockFragmentActivity, mockSdkCore)

        testedLifecycleCallbacks.onFragmentViewCreated(mock(), mockDialogFragment, mock(), null)

        verify(mockGesturesTracker).startTracking(mockWindow, mockContext, mockSdkCore)
    }

    @Test
    fun `when fragment view created on Fragment, registers nothing`() {
        whenever(mockFragment.context) doReturn mockContext

        testedLifecycleCallbacks.onFragmentViewCreated(mock(), mockFragment, mock(), null)

        verifyNoInteractions(mockGesturesTracker)
    }

    @Test
    fun `will register the callback to fragment manager when required`() {
        // When
        testedLifecycleCallbacks.register(mockFragmentActivity, mockSdkCore)

        // Then
        verify(mockFragmentManager)
            .registerFragmentLifecycleCallbacks(testedLifecycleCallbacks, true)
    }

    @Test
    fun `will unregister the callback from the fragment manager when required`() {
        // When
        testedLifecycleCallbacks.unregister(mockFragmentActivity)

        // Then
        verify(mockFragmentManager).unregisterFragmentLifecycleCallbacks(testedLifecycleCallbacks)
    }
}
