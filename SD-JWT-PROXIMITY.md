# SD-JWT+KB over ISO 18013-5 Proximity — Implementation Notes

This document describes the proposed ISO 18013-5 extension that allows an SD-JWT VC to be
presented over the standard ISO 18013-5 NFC/BLE/WiFi-Aware proximity channel.

---

## Overview

The standard ISO 18013-5 `DeviceRequest` / `DeviceResponse` protocol has been extended with
two optional fields:

| Structure        | New field         | Type              | Description |
|------------------|-------------------|-------------------|-------------|
| `ItemsRequest`   | `docFormat`       | `tstr` (optional) | `"mdoc"` (default) or `"sd-jwt+kb"` |
| `DeviceResponse` | `sdjwtDocuments`  | `[+ bstr]` (optional) | Array of UTF-8-encoded compact SD-JWT+KB strings |

When `docFormat` is absent or `"mdoc"` the existing behaviour is **completely unchanged**.

---

## How to trigger SD-JWT proximity presentment

### On the verifier side

Use `DeviceRequest.Builder.addDocRequest(...)` with the `docFormat` parameter:

```kotlin
val deviceRequest = buildDeviceRequest(sessionTranscript = sessionTranscriptDataItem) {
    addDocRequest(
        // docType carries the SD-JWT VC type (vct), e.g. "urn:eudi:pid:1"
        docType = "urn:eudi:pid:1",
        nameSpaces = mapOf(
            // Namespace key is ignored for SD-JWT; data-element names map to JSON claim paths.
            "urn:eudi:pid:1" to mapOf(
                "given_name"  to true,
                "family_name" to true,
                "birth_date"  to false,
            )
        ),
        docRequestInfo = null,
        docFormat = DocRequest.DOC_FORMAT_SD_JWT_KB,   // <-- triggers SD-JWT path
        readerKey = myReaderKey,                        // optional — for ReaderAuth
    )
}
val encodedDeviceRequest = Cbor.encode(deviceRequest.toDataItem())
```

### On the wallet side

No code change is required. The `Iso18013Presentment` function already calls `mdocPresentment`,
which now detects `docFormat == "sd-jwt+kb"` and routes to the new SD-JWT presentation path.

The wallet must have a **`KeyBoundSdJwtVcCredential`** whose `vct` equals the `docType` in the
request. If found, the wallet:

1. Shows the standard consent prompt (using `showConsentPrompt`).
2. Builds a filtered SD-JWT from the credential's `issuerProvidedData`.
3. Signs a Key-Binding JWT (KB-JWT) containing:
   - `nonce` = `st_hash` (see Session Binding below).
   - `aud`   = subject DN of the first ReaderAuth certificate, or a synthetic URI.
   - `sd_hash` = `BASE64URL(digestAlg(SD-JWT compact serialisation))`.
   - `st_hash` = `BASE64URL(SHA-256(SessionTranscriptCBOR))` — session binding.
4. Returns the compact SD-JWT+KB string as a UTF-8 `bstr` under `sdjwtDocuments[0]`.

---

## How requested paths map to disclosures

`ItemsRequest.nameSpaces` in ISO 18013-5 is a map of

```
NamespaceName → { DataElementName → IntentToRetain }
```

For SD-JWT VCs the namespace name is **ignored** — it is only present because the `ItemsRequest`
CBOR structure requires it. Each **DataElementName** maps directly to a top-level JSON claim path
in the SD-JWT VC. For example:

```
nameSpaces = {
  "urn:eudi:pid:1": {
    "given_name":  true,
    "family_name": true,
    "birth_date":  false
  }
}
```

produces the disclosure-path list `[["given_name"], ["family_name"], ["birth_date"]]`, which is
passed to `SdJwt.filter(pathsToInclude)`. The filter retains any disclosure whose path
**starts with** one of the requested paths (enabling selective disclosure of nested objects).

If `nameSpaces` is empty, **all disclosures** are included.

---

## Session binding (`st_hash`)

The proximity channel has no separate nonce mechanism, so the session transcript itself acts as
the binding anchor.

```
st_hash = BASE64URL(SHA-256(CBOR(SessionTranscript)))
```

`st_hash` is added as an extra claim in the KB-JWT alongside the standard `nonce` (set equal to
`st_hash`), `aud`, and `sd_hash`. This ensures the SD-JWT+KB is cryptographically tied to the
specific ISO 18013-5 session and cannot be replayed in a different session.

The computation is in `SdJwtProximityPresentment.kt` → `computeSessionTranscriptHash()` and the
JWT construction is in `SdJwt.presentWithSessionBinding()`.

---

## Compression hook

`buildSdJwtKbPresentation()` accepts an optional `compressor: SdJwtCompressor` parameter.
The default `SdJwtNoCompression` is a no-op (identity function). Pass a custom implementation
to apply e.g. DEFLATE compression on the UTF-8 bytes before they are stored in the `bstr`.

> **Note:** If compression is applied the verifier must know to decompress before parsing.
> Agree on a compression flag out-of-band or via an extension to `DeviceRequest` if needed.

---

## Key files changed

| File | Change |
|------|--------|
| `multipaz/src/commonMain/…/mdoc/request/DocRequest.kt` | Added `docFormat` field + `isSdJwtKbRequested`; parse/serialize from `ItemsRequest` |
| `multipaz/src/commonMain/…/mdoc/request/DeviceRequest.kt` | Added `docFormat` parameter to both `addDocRequest()` overloads |
| `multipaz/src/commonMain/…/mdoc/response/DeviceResponse.kt` | Added `sdjwtDocuments` field; `toDataItem` / `fromDataItem`; `Builder.addSdJwtDocument()` |
| `multipaz/src/commonMain/…/sdjwt/SdJwt.kt` | Added `presentWithSessionBinding()` method |
| `multipaz/src/commonMain/…/presentment/model/SdJwtProximityPresentment.kt` | New — `computeSessionTranscriptHash`, `deriveAudience`, `buildSdJwtKbPresentation` |
| `multipaz/src/commonMain/…/presentment/model/mdocPresentment.kt` | SD-JWT+KB branch in `mdocPresentment`; `handleSdJwtKbDocRequest` |
| `multipaz/src/commonTest/…/presentment/model/Iso18013SdJwtProximityTest.kt` | New — 4 integration tests |

---

## Backwards compatibility

- Any `DocRequest` without `docFormat` (or with `docFormat = "mdoc"`) follows the **unchanged
  mdoc code path** in `mdocPresentment`.
- Any verifier that does not understand `sdjwtDocuments` will simply ignore the unknown CBOR key
  in `DeviceResponse` (CBOR maps allow unknown keys).
- The wallet only sends `sdjwtDocuments` when the verifier explicitly requests it via
  `docFormat = "sd-jwt+kb"`.

