package org.multipaz.mdoc.request

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborArray

/**
 * Document request info according to ISO 18013-5.
 *
 * @property docFormat requested response format. If absent in CBOR, `mdoc` is assumed.
 * @property alternativeDataElements list of alternative data elements.
 * @property sdJwtRequest optional SD-JWT request details.
 * @property alternativeSdJwtClaimsSet optional SD-JWT claim alternatives.
 * @property issuerIdentifiers list of issuer identifiers.
 * @property uniqueDocSetRequired whether a unique doc set is required or not or unspecified.
 * @property maximumResponseSize the maximum response size, if available.
 * @property zkRequest optional request for a Zero-Knowledge Proof.
 * @property docResponseEncryption optional request for encrypting the response.
 * @property otherInfo other request info.
 */
data class DocRequestInfo(
    val docFormat: String = DOC_FORMAT_MDOC,
    val alternativeDataElements: List<AlternativeDataElementSet> = emptyList(),
    val sdJwtRequest: SdJwtRequest? = null,
    val alternativeSdJwtClaimsSet: List<AlternativeSdJwtClaimsSet> = emptyList(),
    val issuerIdentifiers: List<ByteString> = emptyList(),
    val uniqueDocSetRequired: Boolean? = null,
    val maximumResponseSize: Long? = null,
    val zkRequest: ZkRequest? = null,
    val docResponseEncryption: EncryptionParameters? = null,
    val otherInfo: Map<String, DataItem> = emptyMap()
) {
    internal fun toDataItem() = buildCborMap {
        if (docFormat != DOC_FORMAT_MDOC) {
            put("docFormat", docFormat)
        }
        if (alternativeDataElements.isNotEmpty()) {
            putCborArray("alternativeDataElements") {
                alternativeDataElements.forEach {
                    add(it.toDataItem())
                }
            }
        }
        if (docFormat == DOC_FORMAT_SD_JWT_KB) {
            sdJwtRequest?.let {
                put("sdjwtRequest", it.toDataItem())
            }
            if (alternativeSdJwtClaimsSet.isNotEmpty()) {
                putCborArray("alternativeSDJwtClaimsSet") {
                    alternativeSdJwtClaimsSet.forEach {
                        add(it.toDataItem())
                    }
                }
            }
        }
        if (issuerIdentifiers.isNotEmpty()) {
            putCborArray("issuerIdentifiers") {
                issuerIdentifiers.forEach {
                    add(it.toByteArray())
                }
            }
        }
        uniqueDocSetRequired?.let {
            put("uniqueDocSetRequired", uniqueDocSetRequired)
        }
        maximumResponseSize?.let {
            put("maximumResponseSize", it)
        }
        zkRequest?.let {
            put("zkRequest", it.toDataItem())
        }
        docResponseEncryption?.let {
            put("docResponseEncryption", Tagged(
                tagNumber = Tagged.ENCODED_CBOR,
                taggedItem = Bstr(Cbor.encode(it.toDataItem()))
            ))
        }
        otherInfo.forEach { (key, value) ->
            put(key, value)
        }
    }


    internal fun isUsingSecondEditionFeature(): Boolean {
        return docFormat != DOC_FORMAT_MDOC ||
                alternativeDataElements.isNotEmpty() ||
                sdJwtRequest != null ||
                alternativeSdJwtClaimsSet.isNotEmpty() ||
                issuerIdentifiers.isNotEmpty() ||
                uniqueDocSetRequired != null ||
                maximumResponseSize != null ||
                zkRequest != null ||
                docResponseEncryption != null
    }

    companion object {
        const val DOC_FORMAT_MDOC = "mdoc"
        const val DOC_FORMAT_SD_JWT_KB = "sd-jwt+kb"

        internal fun fromDataItem(dataItem: DataItem): DocRequestInfo {
            val docFormat = dataItem.getOrNull("docFormat")?.asTstr ?: DOC_FORMAT_MDOC
            val alternativeDataElements = dataItem.getOrNull("alternativeDataElements")?.asArray?.map {
                AlternativeDataElementSet.fromDataItem(it)
            } ?: emptyList()
            val parseSdJwt = docFormat == DOC_FORMAT_SD_JWT_KB
            val sdJwtRequest = if (parseSdJwt) {
                dataItem.getOrNull("sdjwtRequest")?.let {
                    SdJwtRequest.fromDataItem(it)
                }
            } else {
                null
            }
            val alternativeSdJwtClaimsSet = if (parseSdJwt) {
                dataItem.getOrNull("alternativeSDJwtClaimsSet")?.asArray?.map {
                    AlternativeSdJwtClaimsSet.fromDataItem(it)
                } ?: emptyList()
            } else {
                emptyList()
            }
            val issuerIdentifiers = dataItem.getOrNull("issuerIdentifiers")?.asArray?.map {
                ByteString(it.asBstr)
            } ?: emptyList()
            val uniqueDocSetRequired = dataItem.getOrNull("uniqueDocSetRequired")?.asBoolean
            val maximumResponseSize = dataItem.getOrNull("maximumResponseSize")?.asNumber
            val zkRequest = dataItem.getOrNull("zkRequest")?.let {
                ZkRequest.fromDataItem(it)
            }
            val docResponseEncryption = dataItem.getOrNull("docResponseEncryption")?.let {
                EncryptionParameters.fromDataItem(it.asTaggedEncodedCbor)
            }
            val otherInfo = mutableMapOf<String, DataItem>()
            for ((otherKeyDataItem, otherValue) in dataItem.asMap) {
                val otherKey = otherKeyDataItem.asTstr
                when (otherKey) {
                    "docFormat",
                    "alternativeDataElements",
                    "sdjwtRequest",
                    "alternativeSDJwtClaimsSet",
                    "issuerIdentifiers",
                    "uniqueDocSetRequired",
                    "maximumResponseSize",
                    "zkRequest",
                    "docResponseEncryption" -> continue
                    else -> otherInfo[otherKey] = otherValue
                }
            }
            return DocRequestInfo(
                docFormat = docFormat,
                alternativeDataElements = alternativeDataElements,
                sdJwtRequest = sdJwtRequest,
                alternativeSdJwtClaimsSet = alternativeSdJwtClaimsSet,
                issuerIdentifiers = issuerIdentifiers,
                uniqueDocSetRequired = uniqueDocSetRequired,
                maximumResponseSize = maximumResponseSize,
                zkRequest = zkRequest,
                docResponseEncryption = docResponseEncryption,
                otherInfo = otherInfo
            )
        }
    }
}