package org.multipaz.presentment.model

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.multipaz.cbor.Simple
import org.multipaz.cbor.buildCborArray
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.mdoc.request.DocRequestInfo
import org.multipaz.mdoc.request.SdJwtClaimRequest
import org.multipaz.mdoc.request.SdJwtRequest
import org.multipaz.mdoc.request.buildDeviceRequest
import org.multipaz.presentment.DocumentStoreTestHarness
import org.multipaz.presentment.mdocPresentment
import org.multipaz.util.zlibInflate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Iso18013SdJwtProximityTest {

    @Test
    fun sdJwtDocFormatEmitsOnlySdjwtDocuments() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        harness.provisionStandardDocuments()

        val sessionTranscript = buildCborArray {
            add(Simple.NULL)
            add(Simple.NULL)
            add("test-handover")
        }

        val deviceRequest = buildDeviceRequest(
            sessionTranscript = sessionTranscript
        ) {
            addDocRequest(
                docType = EUPersonalID.EUPID_DOCTYPE,
                nameSpaces = mapOf(
                    EUPersonalID.EUPID_NAMESPACE to mapOf(
                        "given_name" to false
                    )
                ),
                docRequestInfo = DocRequestInfo(
                    docFormat = DocRequestInfo.DOC_FORMAT_SD_JWT_KB,
                    sdJwtRequest = SdJwtRequest(
                        vct = EUPersonalID.EUPID_VCT,
                        claims = listOf(
                            SdJwtClaimRequest(
                                path = listOf("given_name"),
                                intentToRetain = false
                            )
                        )
                    )
                )
            )
        }

        val response = mdocPresentment(
            deviceRequest = deviceRequest,
            eReaderKey = null,
            sessionTranscript = sessionTranscript,
            source = harness.presentmentSource,
            keyAgreementPossible = emptyList(),
            requesterAppId = null,
            requesterOrigin = null,
            onDocumentsInFocus = {}
        ).deviceResponse

        assertEquals("1.1", response.version)
        assertTrue(response.sdJwtDocuments.isNotEmpty())

        val compactPresentation = response.sdJwtDocuments.first().zlibInflate().decodeToString()
        assertTrue(compactPresentation.isNotBlank())
        assertTrue(compactPresentation.contains("~"))

        // verify() is required before accessing documents; in sd-jwt mode it should be empty.
        response.verify(sessionTranscript = sessionTranscript)
        assertTrue(response.documents.isEmpty())
    }

    @Test
    fun sdJwtDocFormatWorksWithSdJwtOnlyDocument() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        harness.provisionSdJwtVc(
            displayName = "EU PID SD-JWT",
            vct = EUPersonalID.EUPID_VCT,
            data = listOf(
                "given_name" to JsonPrimitive("Erika"),
                "family_name" to JsonPrimitive("Mustermann")
            )
        )

        val sessionTranscript = buildCborArray {
            add(Simple.NULL)
            add(Simple.NULL)
            add("test-handover")
        }

        val deviceRequest = buildDeviceRequest(
            sessionTranscript = sessionTranscript
        ) {
            addDocRequest(
                docType = EUPersonalID.EUPID_DOCTYPE,
                nameSpaces = mapOf(
                    EUPersonalID.EUPID_NAMESPACE to mapOf(
                        "family_name" to false,
                        "given_name" to false
                    )
                ),
                docRequestInfo = DocRequestInfo(
                    docFormat = DocRequestInfo.DOC_FORMAT_SD_JWT_KB,
                    sdJwtRequest = SdJwtRequest(
                        vct = EUPersonalID.EUPID_VCT,
                        claims = listOf(
                            SdJwtClaimRequest(path = listOf("family_name"), intentToRetain = false),
                            SdJwtClaimRequest(path = listOf("given_name"), intentToRetain = false)
                        )
                    )
                )
            )
        }

        val response = mdocPresentment(
            deviceRequest = deviceRequest,
            eReaderKey = null,
            sessionTranscript = sessionTranscript,
            source = harness.presentmentSource,
            keyAgreementPossible = emptyList(),
            requesterAppId = null,
            requesterOrigin = null,
            onDocumentsInFocus = {}
        ).deviceResponse

        assertEquals("1.1", response.version)
        assertTrue(response.sdJwtDocuments.isNotEmpty())
        response.verify(sessionTranscript = sessionTranscript)
        assertTrue(response.documents.isEmpty())
    }
}

