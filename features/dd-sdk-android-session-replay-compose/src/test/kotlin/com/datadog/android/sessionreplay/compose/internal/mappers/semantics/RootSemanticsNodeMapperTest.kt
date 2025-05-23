/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import android.view.View
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
class RootSemanticsNodeMapperTest {

    @Mock
    private lateinit var mockContainerSemanticsNodeMapper: ContainerSemanticsNodeMapper

    @Mock
    private lateinit var mockTextSemanticsNodeMapper: TextSemanticsNodeMapper

    @Mock
    private lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    private lateinit var mockSemanticsUtils: SemanticsUtils

    @Mock
    private lateinit var mockRadioButtonSemanticsNodeMapper: RadioButtonSemanticsNodeMapper

    @Mock
    private lateinit var mockTabSemanticsNodeMapper: TabSemanticsNodeMapper

    @Mock
    private lateinit var mockButtonSemanticsNodeMapper: ButtonSemanticsNodeMapper

    @Mock
    private lateinit var mockImageSemanticsNodeMapper: ImageSemanticsNodeMapper

    @Mock
    private lateinit var mockCheckboxSemanticsNodeMapper: CheckboxSemanticsNodeMapper

    @Mock
    private lateinit var mockSwitchSemanticsNodeMapper: SwitchSemanticsNodeMapper

    @Mock
    private lateinit var mockComposeHiddenMapper: ComposeHiddenMapper

    @Mock
    private lateinit var mockSliderSemanticsNodeMapper: SliderSemanticsNodeMapper

    @Mock
    private lateinit var mockSemanticsConfiguration: SemanticsConfiguration

    @Forgery
    private lateinit var fakeMappingContext: MappingContext

    private lateinit var testedRootSemanticsNodeMapper: RootSemanticsNodeMapper

    private lateinit var rolesToMappers: Map<Role, SemanticsNodeMapper>

    @BeforeEach
    fun `set up`() {
        rolesToMappers = mapOf(
            Role.RadioButton to mockRadioButtonSemanticsNodeMapper,
            Role.Tab to mockTabSemanticsNodeMapper,
            Role.Button to mockButtonSemanticsNodeMapper,
            Role.Image to mockImageSemanticsNodeMapper,
            Role.Checkbox to mockCheckboxSemanticsNodeMapper,
            Role.Switch to mockSwitchSemanticsNodeMapper,
            Role.DropdownList to mockContainerSemanticsNodeMapper
        )

        testedRootSemanticsNodeMapper = RootSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils,
            semanticsNodeMapper = rolesToMappers,
            textSemanticsNodeMapper = mockTextSemanticsNodeMapper,
            containerSemanticsNodeMapper = mockContainerSemanticsNodeMapper,
            composeHiddenMapper = mockComposeHiddenMapper,
            sliderSemanticsNodeMapper = mockSliderSemanticsNodeMapper
        )
    }

    @Test
    fun `M use ContainerSemanticsNodeMapper W createComposeWireframes { role is missing }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(null)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockContainerSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use ButtonSemanticsNodeMapper W createComposeWireframes { role is Button }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Button)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockButtonSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use RadioButtonSemanticsNodeMapper W createComposeWireframes { role is RadioButton }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.RadioButton)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockRadioButtonSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use SwitchSemanticsNodeMapper W createComposeWireframes { role is Switch }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Switch)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockSwitchSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use TabSemanticsNodeMapper W createComposeWireframes { role is Tab }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Tab)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockTabSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use ImageSemanticsNodeMapper W createComposeWireframes { role is Image }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Image)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockImageSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use CheckboxSemanticsNodeMapper W createComposeWireframes { role is Checkbox }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Checkbox)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockCheckboxSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use SliderSemanticsNodeMapper W map createComposeWireframes { isSliderNode }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(null)
        val mockProgressBarRangeInfo = mock<ProgressBarRangeInfo>()
        whenever(mockSemanticsUtils.getProgressBarRangeInfo(mockSemanticsNode)) doReturn mockProgressBarRangeInfo

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockSliderSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use ComposeHideMapper W node is hidden`(forge: Forge) {
        // Given
        val fakeRole = forge.anElementFrom(
            rolesToMappers.keys + null
        )
        val mockSemanticsNode = mockSemanticsNode(fakeRole)
        whenever(mockSemanticsUtils.isNodeHidden(mockSemanticsNode)) doReturn true

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockComposeHiddenMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M skip semanticsNode W position unavailable`() {
        // Given
        rolesToMappers.forEach { (role, mapper) ->
            val node = mockSemanticsNode(role)

            whenever(mockSemanticsUtils.isNodePositionUnavailable(node)).thenReturn(true)

            // When
            testedRootSemanticsNodeMapper.createComposeWireframes(
                node,
                fakeMappingContext.systemInformation.screenDensity,
                fakeMappingContext,
                mockAsyncJobStatusCallback
            )

            // Then
            verifyNoInteractions(mapper)
        }
    }

    @Test
    fun `M call interop callback W semantics node has interop view`() {
        val mockSemanticsNode = mockSemanticsNode(null)
        val mockView = mock<View>()
        whenever(mockSemanticsUtils.getInteropView(mockSemanticsNode)) doReturn mockView

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(fakeMappingContext.interopViewCallback).map(
            eq(mockView),
            eq(fakeMappingContext)
        )
    }

    private fun mockSemanticsNode(role: Role?): SemanticsNode {
        return mock {
            whenever(mockSemanticsConfiguration.getOrNull(SemanticsProperties.Role)) doReturn role
            whenever(it.config) doReturn mockSemanticsConfiguration
        }
    }
}
