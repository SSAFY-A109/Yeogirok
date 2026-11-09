package com.yeogirokarpoc.geo

import android.os.Parcel
import android.os.ResultReceiver

/** Send the framework receiver proxy, never an app's anonymous subclass, across a process boundary. */
internal fun ResultReceiver.forIpc(): ResultReceiver {
    val parcel = Parcel.obtain()
    return try {
        writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        ResultReceiver.CREATOR.createFromParcel(parcel)
    } finally { parcel.recycle() }
}
