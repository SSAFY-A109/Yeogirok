package com.yeogirokarpoc.geo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/** Author-only, device-local content. Never appended to the shareable Cloud Anchor JSON. */
object LocalTipStore {
    private const val PREFS = "yeogirok_local_tips_v1"
    private const val KEY = "tips"
    const val MAX_BODY = 2000
    private val tipTypes = setOf("포토", "맛집", "카페", "꿀팁", "TMI")

    fun photoFile(context: Context, name: String): File {
        require(Regex("[a-f0-9-]{36}\\.jpg").matches(name)) { "사진 파일 형식이 올바르지 않습니다." }
        return File(File(context.filesDir, "tip-photos"), name)
    }

    fun validate(context: Context, raw: String): JSONObject {
        require(raw.length <= 100_000)
        val tip = JSONObject(raw)
        require(tip.getInt("schemaVersion") == 1)
        UUID.fromString(tip.getString("id"))
        Instant.parse(tip.getString("createdAt"))
        require(tip.getString("title").length in 1..60)
        if (tip.optBoolean("draft")) {
            require(tip.getString("tipType") in tipTypes)
            require(tip.getString("body").length <= 120)
            require(!tip.isNull("photoName"))
        } else require(tip.getString("body").trim().length in 1..MAX_BODY)
        val cloud = CloudRecord.parse(tip.getJSONObject("cloudRecord").toString())
        tip.put("cloudRecord", cloud)
        if (!tip.isNull("photoName")) photoFile(context, tip.getString("photoName"))
        // URI is always derived here, never trusted from imported JSON.
        tip.remove("photoUri")
        if (!tip.isNull("photoName")) {
            val photo = photoFile(context, tip.getString("photoName"))
            if (photo.isFile) tip.put("photoUri", android.net.Uri.fromFile(photo).toString())
        }
        return tip
    }

    @Synchronized fun list(context: Context): JSONArray {
        val stored = JSONArray(context.getSharedPreferences(PREFS, 0).getString(KEY, "[]"))
        return JSONArray().also { out ->
            for (i in 0 until stored.length()) out.put(validate(context, stored.getJSONObject(i).toString()))
        }
    }

    @Synchronized fun save(context: Context, raw: String): JSONObject {
        require(raw.length <= 100_000)
        val draft = JSONObject(raw)
        val body = draft.getString("body").trim()
        val isDraft = draft.optBoolean("draft")
        if (isDraft) {
            require(draft.getString("tipType") in tipTypes) { "팁 종류를 선택해 주세요." }
            require(body.length <= 120) { "팁 내용은 120자까지 입력할 수 있어요." }
            require(!draft.isNull("photoName")) { "팁 사진을 첨부해 주세요." }
        } else require(body.length in 1..MAX_BODY) { "팁 내용을 1~2,000자로 입력해 주세요." }
        val title = draft.optString("title").trim().ifEmpty { body.lineSequence().first().take(40) }
        require(title.length <= 60) { "제목은 60자까지 입력할 수 있어요." }
        val cloud = CloudRecord.parse(draft.getJSONObject("cloudRecord").toString(), forResolve = true)
        val existing = list(context)
        val previous = (0 until existing.length()).map { existing.getJSONObject(it) }
            .firstOrNull { it.getJSONObject("cloudRecord").getString("anchorId") == cloud.getString("anchorId") }
        require(previous != null || existing.length() < 100) { "이번 기기 체험은 팁 100개까지 저장할 수 있어요." }
        val tip = JSONObject().put("schemaVersion", 1).put("id", previous?.getString("id") ?: UUID.randomUUID().toString())
            .put("title", title).put("body", body).put("createdAt", previous?.getString("createdAt") ?: Instant.now().toString())
            .put("cloudRecord", cloud)
        if (isDraft) tip.put("draft", true).put("tipType", draft.getString("tipType"))
        if (!draft.isNull("photoName")) {
            val name = draft.getString("photoName")
            require(photoFile(context, name).isFile) { "사진을 다시 선택해 주세요." }
            tip.put("photoName", name)
        } else if (!draft.has("photoName") && previous != null && !previous.isNull("photoName")) {
            tip.put("photoName", previous.getString("photoName"))
        }
        val all = JSONArray().put(tip)
        for (i in 0 until existing.length()) {
            val item = existing.getJSONObject(i)
            if (item.getJSONObject("cloudRecord").getString("anchorId") != cloud.getString("anchorId")) all.put(item)
        }
        check(context.getSharedPreferences(PREFS, 0).edit().putString(KEY, all.toString()).commit())
        return validate(context, tip.toString())
    }

    fun find(context: Context, id: String): JSONObject {
        val all = list(context)
        return (0 until all.length()).map { all.getJSONObject(it) }.firstOrNull { it.getString("id") == id }
            ?: error("저장한 팁을 찾을 수 없습니다.")
    }

    fun reaction(context: Context, id: String): String? {
        UUID.fromString(id)
        return context.getSharedPreferences("yeogirok_local_reactions_v1", 0).getString(id, null)
            ?.takeIf { it in listOf("LIKE", "DISLIKE") }
    }

    @Synchronized fun toggleReaction(context: Context, id: String, selected: String): String? {
        require(selected in listOf("LIKE", "DISLIKE"))
        val next = if (reaction(context, id) == selected) null else selected
        check(context.getSharedPreferences("yeogirok_local_reactions_v1", 0).edit().putString(id, next).commit())
        return next
    }
}
