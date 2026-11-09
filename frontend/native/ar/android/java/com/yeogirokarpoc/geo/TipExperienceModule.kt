package com.yeogirokarpoc.geo

import android.app.Activity
import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.facebook.react.bridge.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import android.Manifest
import android.content.pm.PackageManager
import android.util.Base64
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.time.Instant

class TipExperienceModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
    private val worker = Executors.newSingleThreadExecutor()
    private var picker: Promise? = null
    private val photoRequest = 9110
    private val captureRequest = 9111
    private var captureFile: File? = null
    private val listener = object : BaseActivityEventListener() {
        override fun onActivityResult(activity: Activity, request: Int, result: Int, data: Intent?) {
            if (request != photoRequest && request != captureRequest) return
            val promise = picker ?: return
            picker = null
            val source = if (request == captureRequest) captureFile.also { captureFile = null } else null
            val uri = source?.let { Uri.fromFile(it) } ?: data?.data
            if (result != Activity.RESULT_OK || uri == null) { source?.delete(); promise.resolve(null); return }
            worker.execute {
                try { promise.resolve(importPhoto(uri).toString()) }
                catch (_: Exception) { promise.reject("PHOTO_FAILED", "사진을 불러오지 못했어요. 20MB 이하의 다른 사진을 선택해 주세요.") }
                finally { source?.delete() }
            }
        }
    }

    init { context.addActivityEventListener(listener) }
    override fun getName() = "TipExperience"

    @ReactMethod fun getCurrentLocation(promise: Promise) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            promise.reject("LOCATION_PERMISSION", "현재 위치 권한이 필요해요."); return
        }
        val cancellation = CancellationTokenSource()
        try {
            val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0).setDurationMillis(15_000).build()
            LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { location ->
                    if (location == null || !location.hasAccuracy() || location.time <= 0) {
                        promise.reject("LOCATION_REQUIRED", "현재 위치를 측정하지 못했어요. 다시 시도해 주세요.")
                    } else promise.resolve(JSONObject().put("latitude", location.latitude).put("longitude", location.longitude)
                        .put("accuracyMeters", location.accuracy.toDouble()).put("capturedAt", Instant.ofEpochMilli(location.time).toString()).toString())
                }.addOnFailureListener { promise.reject("LOCATION_REQUIRED", "현재 위치를 측정하지 못했어요.") }
        } catch (_: SecurityException) { promise.reject("LOCATION_PERMISSION", "현재 위치 권한이 필요해요.") }
    }

    @ReactMethod fun readPhoto(name: String, promise: Promise) {
        worker.execute {
            try {
                val file = LocalTipStore.photoFile(context, name)
                require(file.isFile && file.length() > 0)
                promise.resolve(JSONObject().put("sizeBytes", file.length())
                    .put("base64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)).toString())
            } catch (_: Exception) { promise.reject("PHOTO_FAILED", "사진을 읽지 못했어요. 다시 선택해 주세요.") }
        }
    }

    @ReactMethod fun listTips(promise: Promise) {
        worker.execute {
            try { promise.resolve(LocalTipStore.list(context).toString()) }
            catch (_: Exception) { promise.reject("READ_FAILED", "저장한 팁을 읽지 못했어요. 기존 데이터는 그대로 보관돼 있어요.") }
        }
    }

    @ReactMethod fun getTipDetail(id: String, promise: Promise) {
        worker.execute {
            try {
                val tip = LocalTipStore.find(context, id)
                tip.put("reaction", LocalTipStore.reaction(context, id) ?: JSONObject.NULL)
                promise.resolve(tip.toString())
            } catch (_: Exception) { promise.reject("TIP_UNAVAILABLE", "팁을 불러오지 못했어요.") }
        }
    }

    @ReactMethod fun toggleTipReaction(id: String, reaction: String, promise: Promise) {
        worker.execute {
            try {
                LocalTipStore.find(context, id)
                promise.resolve(LocalTipStore.toggleReaction(context, id, reaction))
            } catch (_: Exception) { promise.reject("REACTION_FAILED", "반응을 저장하지 못했어요.") }
        }
    }

    @ReactMethod fun closeTipDetail(promise: Promise) {
        val activity = context.currentActivity as? TipDetailSheet
        if (activity == null) { promise.reject("NO_ACTIVITY", "상세 화면을 다시 열어 주세요."); return }
        activity.runOnUiThread { activity.finish(); promise.resolve(null) }
    }

    @ReactMethod fun saveTip(raw: String, promise: Promise) {
        worker.execute {
            try { promise.resolve(LocalTipStore.save(context, raw).toString()) }
            catch (error: Exception) { promise.reject("SAVE_FAILED", error.message ?: "팁을 저장하지 못했어요.") }
        }
    }

    @ReactMethod fun pickPhoto(promise: Promise) {
        val activity = context.currentActivity
        if (activity == null) { promise.reject("NO_ACTIVITY", "앱 화면을 다시 열어 주세요."); return }
        activity.runOnUiThread {
            if (picker != null) { promise.reject("BUSY", "사진을 선택하고 있어요."); return@runOnUiThread }
            picker = promise
            try {
                activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, photoRequest)
            } catch (_: Exception) {
                picker = null; promise.reject("PICK_FAILED", "사진 선택 화면을 열지 못했어요.")
            }
        }
    }

    @ReactMethod fun capturePhoto(promise: Promise) {
        val activity = context.currentActivity
        if (activity == null) { promise.reject("NO_ACTIVITY", "앱 화면을 다시 열어 주세요."); return }
        activity.runOnUiThread {
            if (picker != null) { promise.reject("BUSY", "사진을 선택하고 있어요."); return@runOnUiThread }
            picker = promise
            try {
                val directory = File(context.cacheDir, "ar-tip-capture").apply { check(isDirectory || mkdirs()) }
                val source = File.createTempFile("capture-", ".jpg", directory)
                captureFile = source
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.ar-tip.photos", source)
                activity.startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    clipData = ClipData.newRawUri("AR 팁 사진", uri)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, captureRequest)
            } catch (_: Exception) {
                captureFile?.delete(); captureFile = null; picker = null
                promise.reject("CAPTURE_FAILED", "카메라를 열지 못했어요. 사진 선택으로 다시 시도해 주세요.")
            }
        }
    }

    @ReactMethod fun openTipAR(id: String, promise: Promise) {
        val activity = context.currentActivity
        if (activity == null) { promise.reject("NO_ACTIVITY", "앱 화면을 다시 열어 주세요."); return }
        try {
            val tip = LocalTipStore.find(context, id)
            val record = CloudRecord.parse(tip.getJSONObject("cloudRecord").toString(), forResolve = true)
            activity.runOnUiThread {
                try {
                    activity.startActivity(Intent(activity, GeoArActivity::class.java).apply {
                        putExtra("cloudMode", true)
                        putExtra("navigationMode", record.has("discoveryLocation"))
                        putExtra("restoreRecord", record.toString())
                        putExtra("localTip", tip.toString())
                    })
                    promise.resolve(null)
                } catch (_: Exception) { promise.reject("OPEN_FAILED", "AR 화면을 열지 못했어요.") }
            }
        } catch (_: Exception) {
            promise.reject("TIP_UNAVAILABLE", "AR 앵커의 예상 수명이 지났거나 기록을 읽지 못했어요. 글과 사진은 홈에 남아 있어요.")
        }
    }

    /** Copy while the temporary SAF grant is valid; downsample and bake EXIF orientation into JPEG. */
    private fun importPhoto(uri: Uri): JSONObject {
        val source = File.createTempFile("tip-import-", ".image", context.cacheDir)
        val name = "${UUID.randomUUID()}.jpg"
        val output = LocalTipStore.photoFile(context, name)
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var completed = false
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                source.outputStream().use { stream ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 20L * 1024 * 1024)
                        stream.write(buffer, 0, count)
                    }
                }
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, options)
            require(options.outWidth > 0 && options.outHeight > 0)
            require(options.outWidth.toLong() * options.outHeight <= 100_000_000L)
            options.inJustDecodeBounds = false; options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
            decoded = requireNotNull(BitmapFactory.decodeFile(source.path, options))
            val orientation = runCatching { ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            check(output.parentFile!!.isDirectory || output.parentFile!!.mkdirs())
            output.outputStream().use { check(oriented.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            completed = true
            return JSONObject().put("photoName", name).put("photoUri", Uri.fromFile(output).toString())
        } finally {
            if (oriented !== decoded) oriented?.recycle()
            decoded?.recycle()
            source.delete()
            if (!completed) output.delete()
        }
    }

    override fun invalidate() {
        context.removeActivityEventListener(listener)
        picker?.reject("RELOADED", "앱이 다시 로드되어 사진 선택이 취소됐어요."); picker = null
        worker.shutdown()
        super.invalidate()
    }
}
