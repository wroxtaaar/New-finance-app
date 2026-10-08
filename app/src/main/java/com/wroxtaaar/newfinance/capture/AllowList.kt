package com.wroxtaaar.newfinance.capture

object AllowList {
 private val bankCodes=setOf("HDFCBK","SBIINB","SBIPSG","ATMSBI","SBICRD","ICICIB","ICICIT","AXISBK","KOTAKB","YESBNK","PNBSMS","JKBANK","JAKBNK","IDFCFB","INDUSB","HSBCIN","RBLBNK","FEDBNK","AUFB","BOBTXN")
 private val packages=setOf("com.google.android.apps.nbu.paisa.user","com.phonepe.app","net.one97.paytm","com.dreamplug.androidapp","in.org.npci.upi","com.amazon.mShop.android.shopping")
 fun allowedSender(sender:String):Boolean{val s=sender.trim().uppercase().replace(Regex("\\s+")," ");val core=s.replace(Regex("^[A-Z]{1,3}-"),"").replace(Regex("-[A-Z]{1,3}$"),"");return bankCodes.any{core.contains(it)}}
 fun allowedPackage(pkg:String)=pkg in packages
}
