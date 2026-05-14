package com.truongnx.speedmeter.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class NetType(val label: String) {
    WIFI("W"),
    MOBILE("M"),
    ETHERNET("E"),
    VPN("VPN"),
    NONE("NIL"),
    OTHER("Unknown")
}

fun getActiveNetType(context: Context): NetType {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return NetType.NONE
    val caps = cm.getNetworkCapabilities(network) ?: return NetType.NONE

    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetType.MOBILE
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetType.ETHERNET
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetType.VPN
        else -> NetType.OTHER
    }
}
