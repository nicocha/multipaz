package org.multipaz.presentment.model

import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.mdoc.request.DocRequest
import org.multipaz.sdjwt.SdJwt
import org.multipaz.sdjwt.credential.KeyBoundSdJwtVcCredential
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import org.multipaz.util.zlibDeflate

private const val TAG = "SdJwtProximityPresentment"

/**
 * Computes the `st_hash` claim value for Key-Binding JWT session binding.
 *
 * Value = `BASE64URL(SHA-256(SessionTranscriptCBOR))` as per the proposed
 * ISO 18013-5 SD-JWT proximity extension.
 *
 * @param sessionTranscript the `SessionTranscript` [DataItem].
 * @return the base64url-encoded SHA-256 digest of the CBOR-encoded SessionTranscript.
 */
suspend fun computeSessionTranscriptHash(sessionTranscript: DataItem): String {
    val encodedSessionTranscript = Cbor.encode(sessionTranscript)
    val hash = Crypto.digest(Algorithm.SHA256, encodedSessionTranscript)
    return hash.toBase64Url()
}

/**
 * Builds the `aud` claim for the KB-JWT.
 *
 * Uses the subject DN of the first reader-auth certificate if present; otherwise falls
 * back to a synthetic `urn:iso18013:st:<stHash>` URI.
 */
fun deriveAudience(docRequest: DocRequest, stHash: String): String {
    val certChain = docRequest.readerAuthCertChain
    return if (certChain != null) {
        certChain.certificates.first().subject.name
    } else {
        "urn:iso18013:st:$stHash"
    }
}

/**
 * Hook point for optional compression of the SD-JWT+KB compact-serialization bytes.
 *
 * Default uses DEFLATE (RFC 1951) with ZLIB wrapper (RFC 1950).
 */
typealias SdJwtCompressor = suspend (ByteArray) -> ByteArray

/** Default SD-JWT document compressor (DEFLATE with ZLIB wrapper). */
val SdJwtZlibCompressor: SdJwtCompressor = { bytes -> bytes.zlibDeflate() }

/**
 * Compresses compact SD-JWT+KB serialization for `DeviceResponse.sdjwtDocuments`.
 */
suspend fun compressSdJwtDocument(
    compactSerialization: String,
    compressor: SdJwtCompressor = SdJwtZlibCompressor
): ByteArray {
    return compressor(compactSerialization.encodeToByteArray())
}

/**
 * Produces a compact SD-JWT+KB presentation for an [KeyBoundSdJwtVcCredential] over
 * ISO 18013-5 proximity.
 *
 * Session binding:
 *   `st_hash = BASE64URL(SHA-256(SessionTranscriptCBOR))` is added to the KB-JWT alongside
 *   the standard `nonce`, `aud`, and `sd_hash` claims.
 *
 * Disclosure selection:
 *   The `nameSpaces` map of [docRequest] maps each data-element name to a top-level JSON claim
 *   path.  For example `{"eu.europa.ec.eudi.pid.1": {"given_name": true}}` requests the path
 *   `["given_name"]`.  When [docRequest].nameSpaces is empty, all disclosures are included.
 *
 * @param docRequest the document request with nameSpaces and docFormat.
 * @param credential the [KeyBoundSdJwtVcCredential] to present.
 * @param sessionTranscript the ISO 18013-5 session transcript DataItem.
 * @param nonce presentation nonce (for proximity this is typically equal to stHash).
 * @param audience optional KB-JWT audience override.
 * @param compressor optional compressor used for validation of final payload encoding.
 * @return the compact SD-JWT+KB serialization string.
 */
suspend fun buildSdJwtKbPresentation(
    docRequest: DocRequest,
    credential: KeyBoundSdJwtVcCredential,
    sessionTranscript: DataItem,
    nonce: String,
    audience: String? = null,
    compressor: SdJwtCompressor = SdJwtZlibCompressor,
): String {
    // 1. Parse issuer-provided SD-JWT.
    val sdJwtString = credential.issuerProvidedData.decodeToString()
    val sdJwt = SdJwt.fromCompactSerialization(sdJwtString)

    // 2. Session-transcript hash.
    val stHash = computeSessionTranscriptHash(sessionTranscript)

    // 3. Audience from request if present, otherwise from reader identity or synthetic URI.
    val audienceToUse = audience ?: deriveAudience(docRequest, stHash)

    // 4. Map requested nameSpaces data-element names to SD-JWT claim paths.
    val requestedPaths = docRequest.nameSpaces
        .flatMap { (_, dataElements) ->
            dataElements.keys.map { elementName -> buildJsonArray { add(elementName) } }
        }

    Logger.i(TAG, "Requested SD-JWT paths: ${requestedPaths.map { it.toString() }}")

    // 5. Filter to only the requested disclosures.
    val filteredSdJwt = if (requestedPaths.isEmpty()) sdJwt else sdJwt.filter(requestedPaths)

    // 6. Retrieve holder signing key from SecureArea.
    val secureAreaBound = credential as SecureAreaBoundCredential
    val keyInfo = secureAreaBound.secureArea.getKeyInfo(secureAreaBound.alias)
    val signingKey = AsymmetricKey.NamedSecureAreaBased(
        keyId = secureAreaBound.alias,
        alias = secureAreaBound.alias,
        secureArea = secureAreaBound.secureArea,
        keyInfo = keyInfo,
    )

    require(signingKey.publicKey == filteredSdJwt.kbKey) {
        "Holder key (alias=${secureAreaBound.alias}) does not match `cnf` key in SD-JWT"
    }

    // 7. Build KB-JWT with st_hash for ISO 18013-5 session binding.
    val compactSerialization = filteredSdJwt.present(
        signingKey = signingKey,
        nonce = nonce,
        audience = audienceToUse,
        additionalClaimBuilderAction = {
            put("st_hash", stHash)
        }
    ).compactSerialization

    Logger.i(
        TAG,
        "SD-JWT+KB built: len=${compactSerialization.length}, " +
            "stHash=${stHash.take(8)}…, aud=${audienceToUse.take(40)}"
    )

    val compressed = compressSdJwtDocument(compactSerialization, compressor)
    check(compressed.isNotEmpty()) { "Compressed SD-JWT document must not be empty" }
    return compactSerialization
}
