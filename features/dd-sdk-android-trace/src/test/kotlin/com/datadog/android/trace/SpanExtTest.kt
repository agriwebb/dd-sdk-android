/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import android.util.Log
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.log.Fields
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
class SpanExtTest {

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockSpanBuilder: Tracer.SpanBuilder

    @Mock
    lateinit var mockSpan: Span

    @Mock
    lateinit var mockParentSpan: Span

    @Mock
    lateinit var mockScope: Scope

    @StringForgery
    lateinit var fakeOperationName: String

    @BeforeEach
    fun `set up`() {
        GlobalTracer.registerIfAbsent(mockTracer)
        whenever(mockTracer.buildSpan(fakeOperationName)) doReturn mockSpanBuilder
        whenever(mockTracer.activateSpan(mockSpan)) doReturn mockScope
        whenever(mockSpanBuilder.asChildOf(mockParentSpan)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    @Test
    fun `M log throwable W setError(Throwable)`(
        @Forgery throwable: Throwable
    ) {
        val mockSpan: Span = mock()

        mockSpan.setError(throwable)

        argumentCaptor<Map<String, Any>>().apply {
            verify(mockSpan).log(capture())
            assertThat(firstValue)
                .containsEntry(Fields.ERROR_OBJECT, throwable)
                .containsOnlyKeys(Fields.ERROR_OBJECT)
        }
    }

    @Test
    fun `M log error message W setError(String)`(
        @StringForgery message: String
    ) {
        val mockSpan: Span = mock()

        mockSpan.setError(message)

        argumentCaptor<Map<String, Any>>().apply {
            verify(mockSpan).log(capture())
            assertThat(firstValue)
                .containsEntry(Fields.MESSAGE, message)
                .containsEntry(AndroidTracer.LOG_STATUS, Log.ERROR)
                .containsOnlyKeys(Fields.MESSAGE, AndroidTracer.LOG_STATUS)
        }
    }

    @Test
    fun `M create span around lambda W withinSpan(name){}`(
        @LongForgery result: Long
    ) {
        var lambdaCalled = false
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder

        val callResult = withinSpan(fakeOperationName) {
            lambdaCalled = true
            result
        }

        assertThat(lambdaCalled).isTrue()
        assertThat(callResult).isEqualTo(result)
        verify(mockSpanBuilder).asChildOf(null as Span?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span and scope around lambda W withinSpan(name, parent){}`(
        @LongForgery result: Long
    ) {
        var lambdaCalled = false

        val callResult = withinSpan(fakeOperationName, mockParentSpan) {
            lambdaCalled = true
            result
        }

        assertThat(lambdaCalled).isTrue()
        assertThat(callResult).isEqualTo(result)
        verify(mockSpanBuilder).asChildOf(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span and scope around lambda W withinSpan(name, parent){} throwing error`(
        @Forgery throwable: Throwable
    ) {
        var lambdaCalled = false

        val thrown = assertThrows<Throwable> {
            withinSpan(fakeOperationName, mockParentSpan) {
                lambdaCalled = true
                throw throwable
            }
        }

        assertThat(thrown).isEqualTo(throwable)
        assertThat(lambdaCalled).isTrue()
        verify(mockSpanBuilder).asChildOf(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            argumentCaptor<Map<String, Any>>().apply {
                verify(mockSpan).log(capture())
                assertThat(firstValue)
                    .containsEntry(Fields.ERROR_OBJECT, throwable)
                    .containsOnlyKeys(Fields.ERROR_OBJECT)
            }

            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span around lambda W withinSpan(name, parent, false){}`(
        @LongForgery result: Long
    ) {
        var lambdaCalled = false

        val callResult = withinSpan(fakeOperationName, mockParentSpan, false) {
            lambdaCalled = true
            result
        }

        assertThat(lambdaCalled).isTrue()
        assertThat(callResult).isEqualTo(result)
        verify(mockSpanBuilder).asChildOf(mockParentSpan)
        inOrder(mockSpan) {
            verify(mockSpan).finish()
        }
        verify(mockTracer, never()).activateSpan(mockSpan)
    }
}
