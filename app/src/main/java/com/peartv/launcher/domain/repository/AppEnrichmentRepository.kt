package com.peartv.launcher.domain.repository

import com.peartv.launcher.domain.model.AppEnrichment

/** PRODUCT_SPEC.md §3.2.1's curated metadata enrichment schema, looked up by package name. */
interface AppEnrichmentRepository {
    fun forPackage(packageName: String): AppEnrichment?
}
