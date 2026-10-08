package com.wroxtaaar.newfinance.capture

object AllowList {
    // Derived from the user's real SMS corpus. These cover the observed
    // transaction sender families while avoiding unrelated payment/marketing
    // senders that also appear in the inbox.
    private val bankCodes = setOf(
        "HDFCBK",
        "ICICIT",
        "ICICIB",
        "AXISBK",
        "SBICRD",
        "MYSBIC",
        "INDUSB",
        "INDUSA",
        "HSBCIN",
        "HSBCIM",
        "SCBANK"
    )

    private val packages = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "com.dreamplug.androidapp",
        "in.org.npci.upi",
        "com.amazon.mShop.android.shopping"
    )

    fun allowedSender(sender: String): Boolean {
        val normalized = sender.trim().uppercase().replace(Regex("\\s+"), " ")
        val core = normalized
            .replace(Regex("^[A-Z]{1,3}-"), "")
            .replace(Regex("-[A-Z]{1,3}$"), "")

        return bankCodes.any { core.contains(it) }
    }

    fun allowedPackage(pkg: String) = pkg in packages
}
