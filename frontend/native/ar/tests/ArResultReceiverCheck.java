package com.yeogirokarpoc.geo;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.ResultReceiver;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Device-only check: an IPC bundle must decode using the framework/boot class loader. */
public final class ArResultReceiverCheck extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            CountDownLatch delivered = new CountDownLatch(1);
            ResultReceiver local = new ResultReceiver(new Handler(Looper.getMainLooper())) {
                @Override protected void onReceiveResult(int code, Bundle data) {
                    if (code == 42 && "round-trip".equals(data.getString("event"))) delivered.countDown();
                }
            };
            ResultReceiver proxy = ArResultReceiverKt.forIpc(local);
            if (proxy.getClass() != ResultReceiver.class) throw new AssertionError("App subclass leaked into IPC");
            Bundle outgoing = new Bundle();
            outgoing.putParcelable("reply", proxy);
            outgoing.putString("event", "round-trip");
            Parcel parcel = Parcel.obtain();
            try {
                parcel.writeBundle(outgoing);
                parcel.setDataPosition(0);
                Bundle incoming = parcel.readBundle(null);
                ResultReceiver reply = incoming.getParcelable("reply");
                reply.send(42, incoming);
            } finally { parcel.recycle(); }
            if (!delivered.await(5, TimeUnit.SECONDS)) throw new AssertionError("Reply was not delivered");
            result.putString("stream", "AR ResultReceiver boot-loader Bundle round trip passed\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
