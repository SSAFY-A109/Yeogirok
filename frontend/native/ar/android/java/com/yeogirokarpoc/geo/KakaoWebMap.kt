package com.yeogirokarpoc.geo

import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class KakaoWebMap(context: Context, center: JSONObject?, points: JSONArray = JSONArray(),
                  private val onSelect: ((Double, Double) -> Unit)? = null) : FrameLayout(context) {
    private val web = WebView(context)
    private val status = TextView(context).apply {
        text = "지도 불러오는 중"; textSize = 11f; setTextColor(Color.DKGRAY)
        setBackgroundColor(Color.WHITE); setPadding(8, 8, 8, 8)
    }
    private var destroyed = false
    private var ready = false
    private var latestPosition: String? = null
    private var latestPoints = points.toString()
    private val timeout = Runnable { if (!ready && !destroyed) showError() }
    private val html: String

    init {
        val key = GeoRecord.key(context, "com.yeogirokarpoc.KAKAO_NATIVE_APP_KEY")
        val keyJson = JSONObject.quote(key).replace("<", "\\u003c")
        val latitude = center?.getDouble("latitude") ?: 37.5665
        val longitude = center?.getDouble("longitude") ?: 126.9780
        val theme = ArTheme(context)
        val pinColor = String.format("#%06X", 0xFFFFFF and theme.primary)
        html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
          <style>html,body,#map{margin:0;width:100%;height:100%;overflow:hidden}</style></head><body><div id="map"></div><script>
          var map,me,selected,pending,markers=[];
          window.setPoints=function(points){
            if(!map)return;
            markers.forEach(function(marker){marker.setMap(null);});
            markers=points.map(function(p){return new kakao.maps.Marker({map:map,position:new kakao.maps.LatLng(p.latitude,p.longitude)});});
          };
          window.setCurrent=function(lat,lng,heading){
            if(!map){pending=[lat,lng,heading];return;}
            var p=new kakao.maps.LatLng(lat,lng);
            if(me)me.setMap(null);
            var angle=heading===null?'': '<span style="position:absolute;left:0;top:-12px;transform:rotate('+heading+'deg);transform-origin:7px 19px">▲</span>';
            me=new kakao.maps.CustomOverlay({map:map,position:p,content:'<div style="position:relative;width:14px;height:14px;border:2px solid white;border-radius:50%;background:$pinColor;color:$pinColor">'+angle+'</div>'});
            ${if (onSelect == null) "map.panTo(p);" else "if(!window.positioned){map.panTo(p);window.positioned=true;}"}
          };
          window.hideCurrent=function(){if(me)me.setMap(null);};
          var s=document.createElement('script');s.src='https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey='+encodeURIComponent($keyJson);
          s.onerror=function(){ArMap.failed();};s.onload=function(){
            try{kakao.maps.load(function(){
              map=new kakao.maps.Map(document.getElementById('map'),{center:new kakao.maps.LatLng($latitude,$longitude),level:3});
              map.setDraggable(${onSelect != null});map.setZoomable(${onSelect != null});
              window.setPoints(${points.toString().replace("<", "\\u003c")});
              ${if (onSelect != null) "kakao.maps.event.addListener(map,'click',function(e){if(selected)selected.setMap(null);selected=new kakao.maps.Marker({map:map,position:e.latLng});ArMap.selected(e.latLng.getLat(),e.latLng.getLng());});" else ""}
              kakao.maps.event.addListener(map,'tilesloaded',function(){ArMap.ready();});
              if(pending)window.setCurrent.apply(null,pending);
            });}catch(e){ArMap.failed();}
          };document.head.appendChild(s);
          </script></body></html>""".trimIndent()
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
        }
        web.addJavascriptInterface(object {
            @JavascriptInterface fun ready() { post {
                if (!destroyed && !ready) {
                    ready = true; status.visibility = GONE; removeCallbacks(timeout)
                    web.evaluateJavascript("window.setPoints($latestPoints);", null)
                    latestPosition?.let { web.evaluateJavascript(it, null) }
                }
            } }
            @JavascriptInterface fun failed() { post { if (!destroyed) showError() } }
            @JavascriptInterface fun selected(lat: Double, lng: Double) { post {
                if (!destroyed && NavigationPolicy.coordinates(lat, lng)) onSelect?.invoke(lat, lng)
            } }
        }, "ArMap")
        addView(web, LayoutParams(-1, -1)); addView(status, LayoutParams(-1, -2))
        status.setOnClickListener { if (!destroyed) load() }
        load()
    }
    private fun showError() { status.visibility = VISIBLE; status.text = "지도를 불러오지 못했어요 · 눌러 재시도" }
    private fun load() {
        ready = false; status.visibility = VISIBLE; status.text = "지도 불러오는 중"
        removeCallbacks(timeout); postDelayed(timeout, 20_000)
        web.loadDataWithBaseURL("http://localhost", html, "text/html", "UTF-8", null)
    }
    fun updateLocation(lat: Double, lng: Double, heading: Double?) {
        if (destroyed || !NavigationPolicy.coordinates(lat, lng)) return
        val direction = heading?.takeIf { it.isFinite() }?.toString() ?: "null"
        latestPosition = "window.setCurrent && window.setCurrent($lat,$lng,$direction);"
        if (ready) web.evaluateJavascript(latestPosition!!, null)
    }
    fun hideLocation() { latestPosition = null; if (!destroyed && ready) web.evaluateJavascript("window.hideCurrent && window.hideCurrent();", null) }
    fun updatePoints(points: JSONArray) {
        val raw = points.toString().replace("<", "\\u003c")
        if (destroyed || raw == latestPoints) return
        latestPoints = raw
        if (ready) web.evaluateJavascript("window.setPoints($raw);", null)
    }
    fun resume() { if (!destroyed) web.onResume() }
    fun pause() { if (!destroyed) { hideLocation(); web.onPause() } }
    fun destroy() { destroyed = true; removeCallbacks(timeout); web.removeJavascriptInterface("ArMap"); web.stopLoading(); web.destroy() }
}
