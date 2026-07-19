package com.homeinventory.app.data.prefilter

/**
 * Outcome of the on-device pre-filter for one photo (see CLAUDE.md → On-Device Pre-Filter).
 *
 * Only [SEND] photos are eligible to reach Gemini. The two non-send outcomes are distinguished
 * so the UI/telemetry can report *why* a photo was held back.
 */
enum class PreFilterDecision {
    /** No person detected and a home/fashion object was found (or the outcome was ambiguous). */
    SEND,

    /**
     * A face or body part was detected. Hard privacy block — final, overrides everything else.
     * No photo with a person ever reaches the network.
     */
    BLOCK_PERSON,

    /** No person, but high confidence the photo contains no home/fashion object worth a call. */
    SKIP_NO_OBJECT,
}
