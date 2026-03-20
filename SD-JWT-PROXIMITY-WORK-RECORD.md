# SD-JWT Proximity Work Record

Date: 2026-03-18
Scope: Multipaz wallet and verifier support for SD-JWT+KB over ISO/IEC 18013-5 proximity and ISO/IEC 18013-7 Annex C transport model.

## Context

We are extending ISO 18013 proximity presentment so a verifier can explicitly request SD-JWT+Key Binding, while preserving existing mdoc behavior.

### Proposition baseline

- `DeviceRequest` keeps existing structure and adds SD-JWT signaling in `DocRequestInfo`.
- `docFormat` is optional and defaults to `"mdoc"` when absent.
- Supported values:
  - `"mdoc"`
  - `"sd-jwt+kb"`
- For SD-JWT requests, `DocRequestInfo` carries `sdjwtRequest` with:
  - `vct`
  - `claims[]` where each claim has `path: [PathElement]+` (array of strings)
- `DeviceResponse` adds optional `sdjwtDocuments: [+ bstr]`.
  - Each entry is compact SD-JWT+KB UTF-8 bytes.
  - Payload in `bstr` is compressed with DEFLATE (RFC 1951) with ZLIB wrapper (RFC 1950).

### Required behavior

- Backward compatibility is mandatory:
  - If `docFormat` is absent or `"mdoc"`, existing mdoc flow is unchanged.
  - Wallet sends `sdjwtDocuments` only when explicitly requested with `docFormat="sd-jwt+kb"`.
- Session binding in KB-JWT must include:
  - `st_hash = base64url(SHA-256(SessionTranscriptCBOR))`
  - `nonce`
  - `aud`
- Wallet consent UI must show requested format and keep mdoc as preferred default when both formats are possible.

## Current known implementation issues (from active branch)

- Overload ambiguity in `buildDeviceRequestFromDcql(...)` calls:
  - `multipaz/src/commonMain/kotlin/org/multipaz/verification/VerificationUtil.kt`
  - `samples/testapp/src/commonMain/kotlin/org/multipaz/testapp/TestAppUtils.kt`
- iOS unresolved symbol/import issue:
  - `multipaz/src/iosMain/kotlin/org/multipaz/mdoc/transport/request/Iso18013Request.kt`
  - Error reported: unresolved reference `execute`.

## Implementation checklist (phased)

### Phase 0 - Build stabilization

- [x] Disambiguate `buildDeviceRequestFromDcql(...)` call sites by using explicit named params and explicit builder lambda types.
- [x] Fix iOS `execute` unresolved reference/import mismatch in `Iso18013Request.kt`.
- [x] Verify clean compile baseline before protocol-level changes.

Acceptance criteria:

- No overload ambiguity errors for `buildDeviceRequestFromDcql`.
- No unresolved `execute` in iOS request flow.

### Phase 1 - Request model and CBOR encode/decode

- [x] Add/confirm `docFormat` support in `DocRequestInfo` with default semantics (`mdoc` when absent).
- [x] Add/confirm `sdjwtRequest` model (`vct`, `claims[].path[]`, `intentToRetain`) and `alternativeSDJwtClaimsSet` support.
- [x] Keep `ReaderAuth` handling unchanged.
- [x] Ensure parser ignores SD-JWT request fields when format is mdoc.

Acceptance criteria:

- Round-trip encode/decode preserves new `DocRequestInfo` fields.
- Existing mdoc requests serialize exactly as before when no SD-JWT options are present.

### Phase 2 - Response schema and SD-JWT payload channel

- [x] Extend `DeviceResponse` model with optional `sdjwtDocuments: List<ByteArray>` (CBOR `bstr` array).
- [x] Add compression strategy hook (default = DEFLATE + ZLIB for `sdjwtDocuments`).
- [x] Ensure response parsing remains tolerant of unknown/optional keys.

Acceptance criteria:

- Response can carry either `documents` (mdoc) or `sdjwtDocuments` (SD-JWT) depending on request.
- `sdjwtDocuments[0]` non-empty when SD-JWT mode is active.

### Phase 3 - Wallet presentment flow (core logic)

- [ ] In proximity presentment, branch on `docFormat`:
  - `mdoc` path -> unchanged.
  - `sd-jwt+kb` path -> do not build mdoc `Document`; build SD-JWT+KB output.
- [ ] Claim selection:
  - map requested path arrays (`["a","b","c"]`) to disclosure selection.
  - support alternatives (`alternativeSDJwtClaimsSet`) where applicable.
- [ ] Build KB-JWT signed by holder key referenced by `cnf`.
- [ ] Add session-bound claims (`st_hash`, `nonce`, `aud`) to KB-JWT.

Acceptance criteria:

- SD-JWT request yields `sdjwtDocuments` only.
- mdoc request yields existing mdoc response only.
- KB-JWT contains expected session-binding claims.

### Phase 4 - Consent UI and format preference

- [ ] Show requested format (`mdoc` / `sd-jwt+kb`) in consent screen.
- [ ] If multiple options can satisfy request, prefer mdoc unless claim satisfiability requires SD-JWT.

Acceptance criteria:

- UI clearly indicates selected/requested format.
- Default behavior remains mdoc-first unless not sufficient.

### Phase 5 - Verifier path, tests, and debug tooling

- [ ] Add local integration/debug test that sends `docFormat="sd-jwt+kb"` request and asserts `sdjwtDocuments` is present and non-empty UTF-8.
- [ ] Add verifier-side parsing/validation path for `sdjwtDocuments` and KB verification inputs.
- [ ] Add minimal logging policy:
  - log lengths, hashes, selected format.
  - never log full compact SD-JWT by default.

Acceptance criteria:

- New test passes locally.
- Legacy mdoc tests remain green.
- Logs do not expose full SD-JWT/KB token content.

## Likely touchpoints

- `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/request/DocRequestInfo.kt`
- `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/request/DocRequest.kt`
- `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/request/DeviceRequest.kt`
- `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/response/DeviceResponse.kt`
- `multipaz/src/commonMain/kotlin/org/multipaz/presentment/mdocPresentment.kt`
- `multipaz/src/commonMain/kotlin/org/multipaz/presentment/model/SdJwtProximityPresentment.kt`
- `multipaz/src/commonMain/kotlin/org/multipaz/verification/VerificationUtil.kt`
- `multipaz/src/iosMain/kotlin/org/multipaz/mdoc/transport/request/Iso18013Request.kt`
- `samples/testapp/src/commonMain/kotlin/org/multipaz/testapp/TestAppUtils.kt`
- `multipaz/src/commonTest/kotlin/org/multipaz/presentment/model/Iso18013SdJwtProximityTest.kt`

## Immediate next actions

1. Fix compile blockers (overload ambiguity + iOS unresolved `execute`).
2. Finalize `DocRequestInfo` and `DeviceResponse` model changes.
3. Complete SD-JWT presentment branch and add integration test for `sdjwtDocuments`.
4. Update `SD-JWT-PROXIMITY.md` with trigger instructions and path-to-disclosure mapping notes.

## Progress log

### 2026-03-18

- Created this working record and aligned it with the latest ISO proposition for `docFormat`, `sdjwtRequest`, and `sdjwtDocuments`.
- Captured active branch blockers:
  - `buildDeviceRequestFromDcql(...)` overload ambiguity in `multipaz/src/commonMain/kotlin/org/multipaz/verification/VerificationUtil.kt` and `samples/testapp/src/commonMain/kotlin/org/multipaz/testapp/TestAppUtils.kt`.
  - unresolved `execute` in `multipaz/src/iosMain/kotlin/org/multipaz/mdoc/transport/request/Iso18013Request.kt`.
- Locked phased implementation plan (Phase 0 to Phase 5) with acceptance criteria.
- Next planned step: complete Phase 0 compile stabilization before additional protocol/model changes.

### 2026-03-18 (Phase 0 kickoff)
- Scope: Start Phase 0 build stabilization.
- Changes:
  - Updated DCQL request builder call in `samples/testapp/src/commonMain/kotlin/org/multipaz/testapp/TestAppUtils.kt` to match current API (`otherInfo`-based signature) and removed obsolete `deviceRequestInfo` usage.
  - Switched `addReaderAuthAll(...)` call to explicit named argument form for clarity.
  - Removed stale import `org.multipaz.mdoc.request.execute` from `multipaz/src/iosMain/kotlin/org/multipaz/mdoc/transport/request/Iso18013Request.kt` (DeviceRequest now uses member `execute`).
- Files touched:
  - `samples/testapp/src/commonMain/kotlin/org/multipaz/testapp/TestAppUtils.kt`
  - `multipaz/src/iosMain/kotlin/org/multipaz/mdoc/transport/request/Iso18013Request.kt`
- Validation:
  - [x] `:samples:testapp:compileKotlinMetadata`
  - [x] `:multipaz:compileKotlinMetadata`
  - [ ] `:multipaz:compileKotlinIosSimulatorArm64` (blocked)
- Notes/Risks:
  - iOS compilation currently fails due to unresolved `presentWithSessionBinding` in `multipaz/src/commonMain/kotlin/org/multipaz/presentment/model/SdJwtProximityPresentment.kt:125`.
  - This blocker is outside Phase 0 call-site/import fixes and needs follow-up in SD-JWT presentment implementation.
- Next:
  - Resolve `presentWithSessionBinding` symbol mismatch, then re-run `:multipaz:compileKotlinIosSimulatorArm64`.

### 2026-03-18 (Phase 0 follow-up: symbol fix)
- Scope: Unblock iOS compile by fixing unresolved `presentWithSessionBinding`.
- Changes:
  - Replaced `filteredSdJwt.presentWithSessionBinding(...)` with `filteredSdJwt.present(...)` in `multipaz/src/commonMain/kotlin/org/multipaz/presentment/model/SdJwtProximityPresentment.kt`.
  - Added KB-JWT session binding claim via `additionalClaimBuilderAction { put("st_hash", stHash) }`.
  - Added missing JSON builder import for `put`.
- Files touched:
  - `multipaz/src/commonMain/kotlin/org/multipaz/presentment/model/SdJwtProximityPresentment.kt`
- Validation:
  - [x] `:multipaz:compileKotlinIosSimulatorArm64`
- Notes/Risks:
  - Build now passes this previously failing target; only warnings remain.
- Next:
  - Resume Phase 1 (`DocRequestInfo` and `docFormat` + SD-JWT request model alignment).

### 2026-03-18 (Phase 1: request model + parser/encoder)
- Scope: Implement Phase 1 `DocRequestInfo` request model alignment for SD-JWT extension fields.
- Changes:
  - Added `docFormat` support in `DocRequestInfo` with default `"mdoc"` semantics when absent.
  - Added SD-JWT request models in `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/request/SdJwtRequestInfo.kt`:
    - `SdJwtRequest` (`vct`, `claims`)
    - `SdJwtClaimRequest` (`path[]`, `intentToRetain`)
    - `AlternativeSdJwtClaimsSet` (`requestedClaim[]`, `alternativeClaimSets[][]`)
  - Extended `DocRequestInfo` encode/decode to support:
    - `docFormat`
    - `sdjwtRequest`
    - `alternativeSDJwtClaimsSet`
  - Added decode gating: SD-JWT fields are parsed only when `docFormat == "sd-jwt+kb"`; when absent/`"mdoc"`, SD-JWT fields are ignored.
  - Kept ReaderAuth behavior untouched.
- Files touched:
  - `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/request/DocRequestInfo.kt`
  - `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/request/SdJwtRequestInfo.kt`
  - `multipaz/src/commonTest/kotlin/org/multipaz/mdoc/request/DeviceRequestTest.kt`
- Validation:
  - [x] `:multipaz:jvmTest --tests "org.multipaz.mdoc.request.DeviceRequestTest"`
  - [x] `:multipaz:compileKotlinMetadata`
- Notes/Risks:
  - `docFormat` is emitted only when not equal to `"mdoc"`, preserving legacy mdoc wire format.
  - SD-JWT fields are treated as recognized keys and not copied into `otherInfo`, preventing accidental reuse in mdoc mode.
- Next:
  - Phase 2: extend `DeviceResponse` with optional `sdjwtDocuments` (`[bstr]`) and add compression hook defaulting to no compression.

### 2026-03-18 (Phase 2: DeviceResponse sdjwtDocuments + mandated compression)
- Scope: Add SD-JWT response channel and enforce DEFLATE + ZLIB payload encoding for `sdjwtDocuments`.
- Changes:
  - Extended `DeviceResponse` with `sdJwtDocuments: List<ByteArray>` and CBOR key `"sdjwtDocuments"`.
  - Added `DeviceResponse.Builder.addSdJwtDocument(...)` overloads:
    - low-level `ByteArray` input (pre-compressed)
    - compact-serialization `String` input with default compressor.
  - Implemented default SD-JWT compressor as DEFLATE (RFC 1951) with ZLIB wrapper (RFC 1950).
  - Updated response version auto-selection so `sdjwtDocuments` responses are emitted as `1.1`.
  - Fixed `zlibDeflate(compressionLevel)` to actually pass `compressionLevel` into `deflate(...)`.
  - Updated SD-JWT presentment compression hook defaults to ZLIB/DEFLATE and added `compressSdJwtDocument(...)` helper.
  - Added DeviceResponse tests validating:
    - `sdjwtDocuments` field is present in CBOR output.
    - stored payload is compressed and round-trips via `zlibInflate()`.
- Files touched:
  - `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/response/DeviceResponse.kt`
  - `multipaz/src/commonMain/kotlin/org/multipaz/util/Compression.kt`
  - `multipaz/src/commonMain/kotlin/org/multipaz/presentment/model/SdJwtProximityPresentment.kt`
  - `multipaz/src/commonTest/kotlin/org/multipaz/mdoc/response/DeviceResponseTest.kt`
- Validation:
  - [x] `:multipaz:compileKotlinMetadata`
  - [x] `:multipaz:jvmTest --tests "org.multipaz.mdoc.response.DeviceResponseTest.sdJwtDocumentRoundTripUsesZlibCompression" --tests "org.multipaz.mdoc.response.DeviceResponseTest.deviceResponseWithSdJwtHasSdjwtDocumentsField"`
- Notes/Risks:
  - Compression behavior now follows the required DEFLATE+ZLIB encoding for `SdJwtDocument = bstr`.
  - Existing mdoc `documents` behavior remains unchanged.
- Next:
  - Phase 3: wire `docFormat == "sd-jwt+kb"` branch in presentment flow to emit only `sdjwtDocuments`.

### 2026-03-19 (Phase 3.1: docFormat branch in mdoc presentment)
- Scope: Wire `docFormat == "sd-jwt+kb"` path in `mdocPresentment` so this path emits `sdjwtDocuments` and skips mdoc `documents`.
- Changes:
  - Added `docFormat` gate in `multipaz/src/commonMain/kotlin/org/multipaz/presentment/mdocPresentment.kt` using `DocRequestInfo` defaults.
  - For `sd-jwt+kb` requests:
    - validate selected credential is `KeyBoundSdJwtVcCredential`.
    - build SD-JWT+KB via `buildSdJwtKbPresentation(...)`.
    - add payload via `addSdJwtDocument(compactSerialization)`.
    - skip mdoc `MdocDocument` generation for that match.
  - Preserved existing mdoc/ZK branch behavior for non-SD-JWT requests.
- Files touched:
  - `multipaz/src/commonMain/kotlin/org/multipaz/presentment/mdocPresentment.kt`
- Validation:
  - [x] `:multipaz:compileKotlinMetadata`
  - [x] `:multipaz:jvmTest --tests "org.multipaz.mdoc.response.DeviceResponseTest.sdJwtDocumentRoundTripUsesZlibCompression" --tests "org.multipaz.mdoc.response.DeviceResponseTest.deviceResponseWithSdJwtHasSdjwtDocumentsField"`
- Notes/Risks:
  - This is Phase 3.1 only; no mixed-format policy enforcement added yet.
  - Existing unchecked cast warning in mdoc branch remains unchanged (`List<RequestedClaim>` to `List<MdocRequestedClaim>`).
- Next:
  - Add a dedicated integration test in `Iso18013SdJwtProximityTest.kt` to assert SD-JWT requests produce non-empty `sdjwtDocuments` and no `documents`.

### 2026-03-19 (Phase 3.2: integration test for sdjwtDocuments-only response)
- Scope: Add a focused integration-style test for SD-JWT proximity presentment behavior.
- Changes:
  - Added `multipaz/src/commonTest/kotlin/org/multipaz/presentment/model/Iso18013SdJwtProximityTest.kt`.
  - Test `sdJwtDocFormatEmitsOnlySdjwtDocuments` now verifies:
    - request with `docFormat = "sd-jwt+kb"` succeeds through `mdocPresentment`.
    - `deviceResponse.sdJwtDocuments` is non-empty.
    - first payload inflates via `zlibInflate()` to a non-empty compact SD-JWT+KB string.
    - after `verify(sessionTranscript)`, `deviceResponse.documents` is empty.
  - Updated SD-JWT branch in `mdocPresentment` to select `KeyBoundSdJwtVcCredential` from the matched document (when initial match was mdoc), enabling this mixed-domain presentment path.
- Files touched:
  - `multipaz/src/commonMain/kotlin/org/multipaz/presentment/mdocPresentment.kt`
  - `multipaz/src/commonTest/kotlin/org/multipaz/presentment/model/Iso18013SdJwtProximityTest.kt`
- Validation:
  - [x] `:multipaz:compileKotlinMetadata`
  - [x] `:multipaz:jvmTest --tests "org.multipaz.presentment.model.Iso18013SdJwtProximityTest"`
- Notes/Risks:
  - Existing mdoc warning remains in the legacy branch (`List<RequestedClaim>` -> `List<MdocRequestedClaim>` unchecked cast), unchanged by this phase.
- Next:
  - Continue Phase 3 claim-mapping refinement so SD-JWT disclosure selection uses `sdjwtRequest.claims.path[]` / alternatives instead of `nameSpaces` fallback.

### 2026-03-19 (Phase 3.3: fix PresentmentCannotSatisfyRequestException for SD-JWT requests)
- Scope: Fix SD-JWT proximity matching where `docFormat="sd-jwt+kb"` requests could fail with `PresentmentCannotSatisfyRequestException` in environments without a matching mdoc credential.
- Changes:
  - Updated `DeviceRequest.execute()` matching logic in `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/request/DeviceRequest.kt`:
    - SD-JWT doc requests now search candidate credentials using `KeyBoundSdJwtVcCredential` (and requested `vct` when present) instead of mdoc-only matching.
    - Added SD-JWT claim matching path that builds `JsonRequestedClaim` entries from `sdjwtRequest.claims.path[]` with fallback to `nameSpaces` when needed.
    - Removed unnecessary `MdocCredential` casts when constructing consent options.
  - Updated SD-JWT credential selection in `multipaz/src/commonMain/kotlin/org/multipaz/presentment/mdocPresentment.kt`:
    - Prefer selected credential when already SD-JWT.
    - When searching on document, constrain by requested `vct` if present.
  - Added regression test `sdJwtDocFormatWorksWithSdJwtOnlyDocument` in `multipaz/src/commonTest/kotlin/org/multipaz/presentment/model/Iso18013SdJwtProximityTest.kt`.
- Validation:
  - [x] `:multipaz:jvmTest --tests "org.multipaz.presentment.model.Iso18013SdJwtProximityTest"`
- Notes/Risks:
  - SD-JWT disclosure filtering in `buildSdJwtKbPresentation(...)` still uses `nameSpaces` as fallback behavior; full `sdjwtRequest.claims.path[]` filtering in presentment builder remains a follow-up item from Phase 3.

