package com.calllog.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.calllog.app.data.model.SimInfo

/**
 * SimDetailsManager — Device मधून SIM information fetch करतो
 */
object SimDetailsManager {

    @SuppressLint("MissingPermission", "HardwareIds")
    fun getSimDetails(context: Context): List<SimInfo> {
        val simList = mutableListOf<SimInfo>()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            return simList
        }

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as SubscriptionManager

            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                ?: return simList

            for (sub in activeSubscriptions) {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE)
                        as TelephonyManager

                val tm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    telephonyManager.createForSubscriptionId(sub.subscriptionId)
                } else {
                    telephonyManager
                }

                val networkType = when (tm.networkType) {
                    TelephonyManager.NETWORK_TYPE_LTE  -> "4G LTE"
                    TelephonyManager.NETWORK_TYPE_NR   -> "5G"
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                    else -> "Unknown"
                }

                simList.add(
                    SimInfo(
                        slotIndex    = sub.simSlotIndex,
                        operatorName = sub.carrierName?.toString() ?: "",
                        phoneNumber  = sub.number ?: "",
                        countryIso   = sub.countryIso ?: "",
                        networkType  = networkType,
                        isActive     = true
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return simList
    }
}
