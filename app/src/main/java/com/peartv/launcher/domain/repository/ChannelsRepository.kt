package com.peartv.launcher.domain.repository

import com.peartv.launcher.domain.model.AppChannel

/** PRODUCT_SPEC.md §2.4/§3.1.1 Tier 3 — reads real Home Screen Channels data, not curated/fetched art. */
interface ChannelsRepository {
    /** @return every channel the app has published (each with its own programs), or an empty list if it hasn't published any (or lookup fails — never thrown to the caller). A package can publish more than one distinct channel (e.g. "Continue Watching" alongside "Recently Released") — all are returned, in the order the platform reports them. */
    suspend fun fetchChannels(packageName: String): List<AppChannel>
}
