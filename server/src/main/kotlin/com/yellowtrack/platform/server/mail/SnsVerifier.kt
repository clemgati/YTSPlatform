package com.yellowtrack.platform.server.mail

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Decides whether a posted SNS message really came from Amazon.
 *
 * This endpoint has to be reachable from the public internet — an SNS HTTPS subscription is
 * a POST from Amazon's network to a URL, and there is no shared secret and no signing key of
 * ours involved. Without this class the endpoint would be a public, unauthenticated way to
 * write rows into the deployment's operational record, and the first thing that record is
 * used for is deciding whether mail is healthy. Somebody who can forge bounces can make a
 * working deployment look broken, which is a cheap way to waste an afternoon.
 *
 * ## What is checked
 *
 * The signature, against a certificate fetched from the URL in the message — and, before
 * fetching anything, that the URL is one of Amazon's. That second check is the one that
 * matters most and is the easiest to leave out: the message names the certificate that
 * validates it, so an unconstrained fetch lets the sender supply both halves of the proof
 * and sign whatever they like. Worse, it is a fetch to an attacker-chosen URL made from
 * inside the instance, which is the shape of a server-side request forgery.
 *
 * Replay is deliberately not checked here. A captured, genuinely-signed notification can be
 * posted again, and the answer to that is idempotence rather than a timestamp window: rows
 * are keyed on the SNS message id, so a replay writes nothing new. A clock check would add a
 * way to fail on a slow queue in exchange for stopping something already harmless.
 */
class SnsVerifier(
    private val fetchCertificate: (String) -> String = ::fetchOverHttps,
) {
    private val certificates = ConcurrentHashMap<String, X509Certificate>()

    /**
     * True when [message] carries a signature that Amazon's certificate validates.
     *
     * Every failure answers false rather than throwing. A caller that has to distinguish
     * "malformed" from "forged" from "the certificate host was unreachable" would be a
     * caller with three ways to accidentally accept something, and all three end in the same
     * refusal.
     */
    fun isAuthentic(message: SnsMessage): Boolean {
        val signature = message.signature ?: return false
        val certificateUrl = message.signingCertUrl ?: return false
        if (!isAmazonCertificateUrl(certificateUrl)) return false

        val algorithm =
            when (message.signatureVersion) {
                "1" -> "SHA1withRSA"
                "2" -> "SHA256withRSA"
                // An unknown version is not a reason to guess. AWS introduced version 2 and
                // may introduce another; refusing is visible, and picking an algorithm that
                // happens to verify is not.
                else -> return false
            }

        return runCatching {
            val certificate =
                certificates.computeIfAbsent(
                    certificateUrl,
                ) { url -> parseCertificate(fetchCertificate(url)) }
            Signature.getInstance(algorithm).run {
                initVerify(certificate.publicKey)
                update(message.canonicalString().toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(signature))
            }
        }.getOrDefault(false)
    }

    private fun parseCertificate(pem: String): X509Certificate =
        CertificateFactory
            .getInstance("X.509")
            .generateCertificate(pem.byteInputStream())
            as X509Certificate

    companion object {
        /**
         * Whether a certificate URL is one worth fetching.
         *
         * `https`, a host under `amazonaws.com`, and a host that starts `sns.` — SNS serves
         * its signing certificates from `sns.<region>.amazonaws.com` and nowhere else.
         *
         * The leading dot in the suffix is doing real work: `endsWith("amazonaws.com")`
         * without it also accepts `notamazonaws.com`, which somebody can register.
         */
        fun isAmazonCertificateUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            val host = uri.host?.lowercase() ?: return false
            return host.endsWith(".amazonaws.com") && host.startsWith("sns.")
        }
    }
}

private val httpClient: HttpClient =
    HttpClient
        .newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        // Never follow a redirect. The host allowlist is checked on the URL in the message,
        // and a redirect is precisely how that check would be escaped.
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

private fun fetchOverHttps(url: String): String {
    val request =
        HttpRequest
            .newBuilder(URI(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "certificate fetch answered ${response.statusCode()}" }
    return response.body()
}

/**
 * Accepts the subscription by fetching the URL SNS supplied.
 *
 * SNS will not deliver anything until this is done, and the URL is single-use. Guarded by
 * the same allowlist as the certificate for the same reason — this is a fetch from inside
 * the instance to an address chosen by whoever posted the message.
 */
fun confirmSubscription(subscribeUrl: String): Boolean {
    if (!isAmazonSubscribeUrl(subscribeUrl)) return false
    return runCatching {
        val request =
            HttpRequest
                .newBuilder(URI(subscribeUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
        httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200
    }.getOrDefault(false)
}

/**
 * As [SnsVerifier.isAmazonCertificateUrl], but the subscribe URL is served from the regional
 * SNS API host rather than a certificate path, so only the domain is constrained.
 */
internal fun isAmazonSubscribeUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase() ?: return false
    return host.endsWith(".amazonaws.com")
}
