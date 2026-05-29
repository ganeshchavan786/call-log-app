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
import timber.log.Timber

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

    /**
     * Call Log मधील PHONE_ACCOUNT_ID ला वास्तविक SIM Slot (0 किंवा 1) मध्ये मॅप करतो.
     */
    fun getSimSlotFromAccountId(context: Context, accountId: String?): Int {
        if (accountId.isNullOrEmpty()) return 0

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                        as? SubscriptionManager
                val activeSubscriptions = subscriptionManager?.activeSubscriptionInfoList
                if (!activeSubscriptions.isNullOrEmpty()) {
                    // Pass 1: Exact matches (subId, iccId, or slotIndex)
                    for (sub in activeSubscriptions) {
                        val subIdStr = sub.subscriptionId.toString()
                        val iccId = sub.iccId ?: ""
                        if (accountId == subIdStr || (iccId.isNotEmpty() && accountId == iccId)) {
                            Timber.d("Exact match for accountId $accountId: Slot ${sub.simSlotIndex} (SubId: $subIdStr, IccId: $iccId)")
                            return sub.simSlotIndex
                        }
                    }

                    // Pass 2: Partial matches
                    for (sub in activeSubscriptions) {
                        val subIdStr = sub.subscriptionId.toString()
                        val iccId = sub.iccId ?: ""
                        if (accountId.contains(subIdStr) || (iccId.isNotEmpty() && accountId.contains(iccId))) {
                            Timber.d("Partial match for accountId $accountId: Slot ${sub.simSlotIndex} (SubId: $subIdStr, IccId: $iccId)")
                            return sub.simSlotIndex
                        }
                    }

                    // Pass 3: Check if accountId matches slot index directly
                    for (sub in activeSubscriptions) {
                        if (accountId == sub.simSlotIndex.toString()) {
                            Timber.d("Slot index match for accountId $accountId: Slot ${sub.simSlotIndex}")
                            return sub.simSlotIndex
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error resolving SIM slot for accountId $accountId")
        }

        // True fallback checks if SubscriptionManager matching failed or returned no match
        val accountIdLower = accountId.lowercase()

        // Check for common slot indices in name/id
        if (accountIdLower.contains("sim2") || accountIdLower.contains("slot2") || accountIdLower.contains("sub2") || accountIdLower.contains("slot_1")) {
            Timber.d("Fallback match for accountId $accountId -> Slot 1 (SIM 2)")
            return 1
        }
        if (accountIdLower.contains("sim1") || accountIdLower.contains("slot1") || accountIdLower.contains("sub1") || accountIdLower.contains("slot_0")) {
            Timber.d("Fallback match for accountId $accountId -> Slot 0 (SIM 1)")
            return 0
        }

        // Check for numeric fallback
        if (accountId == "2") {
            Timber.d("Fallback match for numeric accountId 2 -> Slot 1")
            return 1
        }
        if (accountId == "1" || accountId == "0") {
            Timber.d("Fallback match for numeric accountId $accountId -> Slot 0")
            return 0
        }

        Timber.d("Default fallback for accountId $accountId -> Slot 0 (SIM 1)")
        return 0
    }
}
