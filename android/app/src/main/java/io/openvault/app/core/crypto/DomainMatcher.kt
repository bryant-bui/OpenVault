package io.openvault.app.core.crypto

import java.net.URI
import java.util.Locale

/**
 * Smart domain + Android package URI matcher shared by Vault recommendations and AutofillService.
 */
object DomainMatcher {

    private val MULTI_PART_TLDS = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk",
        "com.au", "net.au", "org.au",
        "com.vn", "net.vn", "org.vn", "edu.vn", "gov.vn",
        "co.jp", "ne.jp", "or.jp",
        "com.sg", "com.br", "com.cn", "co.kr", "co.in", "com.tw", "com.hk",
    )

    private val IPV4_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")

    fun parseHostname(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("androidapp://", ignoreCase = true)) return null
        return runCatching {
            val withProto = if (SCHEME_REGEX.matches(trimmed)) trimmed else "https://$trimmed"
            val uri = URI(withProto)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") return null
            uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun getBaseDomain(hostname: String): String {
        val clean = hostname.lowercase(Locale.ROOT).removePrefix("www.")
        if (clean == "localhost" || IPV4_REGEX.matches(clean) || clean.contains(':')) {
            return clean
        }
        val parts = clean.split('.').filter { it.isNotEmpty() }
        if (parts.size <= 2) return clean
        val lastTwo = parts.takeLast(2).joinToString(".")
        return if (lastTwo in MULTI_PART_TLDS && parts.size >= 3) {
            parts.takeLast(3).joinToString(".")
        } else {
            lastTwo
        }
    }

    fun extractDisplayDomain(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        if (rawUrl.startsWith("androidapp://", ignoreCase = true)) {
            return rawUrl.removePrefix("androidapp://")
        }
        return parseHostname(rawUrl)?.removePrefix("www.") ?: ""
    }

    /**
     * Scores how well a stored URI matches a requested web domain and/or Android packageName.
     * Returns 0 if no match; up to 100 for exact match.
     */
    fun scoreMatch(
        storedUri: String?,
        webDomain: String?,
        packageName: String?,
    ): Int {
        if (storedUri.isNullOrBlank()) return 0
        val trimmed = storedUri.trim()

        // 1. Exact androidapp:// package match
        if (!packageName.isNullOrBlank() && trimmed.startsWith("androidapp://", ignoreCase = true)) {
            val storedPkg = trimmed.removePrefix("androidapp://").trim().lowercase(Locale.ROOT)
            if (storedPkg == packageName.lowercase(Locale.ROOT)) return 100
        }

        // 2. Web domain matching
        if (!webDomain.isNullOrBlank()) {
            val targetHost = parseHostname(webDomain)
            val storedHost = parseHostname(trimmed)
            if (targetHost != null && storedHost != null) {
                if (targetHost == storedHost) return 100
                if (targetHost.removePrefix("www.") == storedHost.removePrefix("www.")) return 95
                if (getBaseDomain(targetHost) == getBaseDomain(storedHost)) return 75
            }
        }

        // 3. Heuristic reverse-DNS Android package matching (e.g. com.github.android -> github.com)
        if (!packageName.isNullOrBlank() && webDomain.isNullOrBlank()) {
            val storedHost = parseHostname(trimmed) ?: return 0
            val storedBase = getBaseDomain(storedHost)
            val brandToken = storedBase.substringBefore('.')
            if (brandToken.length >= 3) {
                val pkgParts = packageName.lowercase(Locale.ROOT).split('.')
                if (pkgParts.size >= 2 && pkgParts[1] == brandToken) {
                    return 70
                }
            }
        }

        return 0
    }
}
