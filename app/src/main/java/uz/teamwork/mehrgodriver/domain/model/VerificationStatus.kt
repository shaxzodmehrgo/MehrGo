package uz.teamwork.mehrgodriver.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Snapshot of the driver's pre-shift verification checks, returned by
 * GET driver/verification-status. The whole feature renders generically off
 * [checks] keyed by [VerificationCheck.key] / [VerificationCheck.action], so new
 * server-side checks (e.g. medical, car branding) appear without app changes.
 */
data class VerificationStatus(
    @SerializedName("can_go_online")
    val canGoOnline: Boolean = false,

    @SerializedName("all_passed")
    val allPassed: Boolean = false,

    @SerializedName("checks")
    val checks: List<VerificationCheck> = emptyList()
) {
    // `checks.orEmpty()` guards against Gson leaving the field null when the server
    // omits "checks" (Gson bypasses the Kotlin constructor default via Unsafe).

    /** Applicable checks only — the rows we actually render. Gson (via Unsafe) can also leave a
     *  null ELEMENT inside the list, so filter those out too, not just a null list. */
    val visibleChecks: List<VerificationCheck>
        get() = checks.orEmpty().filterNotNull().filter { it.applicable }

    /** A mandatory check is failing — the driver is hard-gated from going online (incl. under review). */
    val hasBlocking: Boolean
        get() = checks.orEmpty().filterNotNull().any { it.isBlocking }

    /** A mandatory check the driver can still act on (not yet under review) — drives the red chip/card. */
    val hasActionableBlocking: Boolean
        get() = checks.orEmpty().filterNotNull().any { it.isActionableBlocking }

    /** Only optional checks are failing — surface them as suggestions, don't block. */
    val hasSuggestion: Boolean
        get() = checks.orEmpty().filterNotNull().any { it.isSuggestion }

    /** An applicable doc is uploaded and awaiting moderation — shown as an amber "under review" chip
     *  (it still BLOCKS the gate via [hasBlocking], but there's nothing for the driver to do). */
    val hasPending: Boolean
        get() = checks.orEmpty().filterNotNull().any { it.applicable && !it.ok && it.isPending }

    /** Client-authoritative go-online decision: allowed unless a mandatory check is blocking. The
     *  server's can_go_online is advisory (enforcement switch is OFF) and is intentionally NOT
     *  required here — a missing/false can_go_online (Gson default) must never strand a driver whose
     *  checks all pass. The server still gates driver/start when enforcement is turned on. */
    val canDriverGoOnline: Boolean
        get() = !hasBlocking
}

data class VerificationCheck(
    @SerializedName("key")
    val key: String = "",

    @SerializedName("title")
    val title: String = "",

    @SerializedName("applicable")
    val applicable: Boolean = false,

    @SerializedName("ok")
    val ok: Boolean = false,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("required")
    val required: Boolean = false,

    @SerializedName("can_skip")
    val canSkip: Boolean = false,

    @SerializedName("video_url")
    val videoUrl: String = "",

    @SerializedName("action")
    val action: String = ACTION_NONE
) {
    /** A failing check that BLOCKS going online: not ok AND (required OR not skippable). Per the
     *  owner's rule a pending (under-review) required doc STILL blocks until it is approved (ok=true). */
    val isBlocking: Boolean
        get() = applicable && !ok && (required || !canSkip)

    /** A blocker the driver can still ACT on (upload/fix) — blocking but NOT yet under review. Drives
     *  the red "Required" card/chip; a pending blocker shows as an amber "under review" row instead. */
    val isActionableBlocking: Boolean
        get() = isBlocking && !isPending

    /** A failing but skippable check — shown as a suggestion, never blocks (warning / amber). */
    val isSuggestion: Boolean
        get() = applicable && !ok && !(required || !canSkip)

    val hasVideo: Boolean
        get() = !videoUrl.isNullOrBlank()

    val isPending: Boolean
        get() = action == ACTION_PENDING

    val canUploadLicense: Boolean
        get() = action == ACTION_UPLOAD_LICENSE

    companion object {
        const val ACTION_NONE = "none"
        const val ACTION_UPLOAD_LICENSE = "upload_license"
        const val ACTION_PENDING = "pending"

        const val KEY_SELF_EMPLOYMENT = "self_employment"
        const val KEY_COMMITENT = "commitent"
        const val KEY_LICENSE = "license"
    }
}

/** Result of POST driver/upload-license (`{ id, status }`). */
data class LicenseUploadResult(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("status")
    val status: Int? = null
)
