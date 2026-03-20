package org.multipaz.mdoc.request

import org.multipaz.cbor.DataItem
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborArray

/**
 * A single SD-JWT claim request entry.
 *
 * @property path claim path represented as an ordered list of path elements.
 * @property intentToRetain whether the verifier intends to retain the claim value.
 */
data class SdJwtClaimRequest(
    val path: List<String>,
    val intentToRetain: Boolean
) {
    internal fun toDataItem() = buildCborMap {
        put("path", path.toDataItem())
        put("intentToRetain", intentToRetain)
    }

    companion object {
        internal fun fromDataItem(dataItem: DataItem): SdJwtClaimRequest {
            return SdJwtClaimRequest(
                path = dataItem["path"].asPathElements,
                intentToRetain = dataItem["intentToRetain"].asBoolean
            )
        }
    }
}

/**
 * SD-JWT specific request parameters carried in `DocRequestInfo`.
 *
 * @property vct the requested SD-JWT credential type.
 * @property claims requested claims and retention intent.
 */
data class SdJwtRequest(
    val vct: String,
    val claims: List<SdJwtClaimRequest>
) {
    internal fun toDataItem() = buildCborMap {
        put("vct", vct)
        putCborArray("claims") {
            claims.forEach { add(it.toDataItem()) }
        }
    }

    companion object {
        internal fun fromDataItem(dataItem: DataItem): SdJwtRequest {
            return SdJwtRequest(
                vct = dataItem["vct"].asTstr,
                claims = dataItem["claims"].asArray.map { SdJwtClaimRequest.fromDataItem(it) }
            )
        }
    }
}

/**
 * Defines alternative SD-JWT claim paths that can satisfy one requested claim path.
 *
 * @property requestedClaim the requested claim path.
 * @property alternativeClaimSets sets of alternative claim paths.
 */
data class AlternativeSdJwtClaimsSet(
    val requestedClaim: List<String>,
    val alternativeClaimSets: List<List<String>>
) {
    internal fun toDataItem() = buildCborMap {
        put("requestedClaim", requestedClaim.toDataItem())
        putCborArray("alternativeClaimSets") {
            alternativeClaimSets.forEach { claimPath ->
                add(claimPath.toDataItem())
            }
        }
    }

    companion object {
        internal fun fromDataItem(dataItem: DataItem): AlternativeSdJwtClaimsSet {
            return AlternativeSdJwtClaimsSet(
                requestedClaim = dataItem["requestedClaim"].asPathElements,
                alternativeClaimSets = dataItem["alternativeClaimSets"].asArray.map { it.asPathElements }
            )
        }
    }
}

private val DataItem.asPathElements: List<String>
    get() = asArray.map { it.asTstr }

private fun List<String>.toDataItem() = buildCborArray {
    forEach { add(it) }
}


