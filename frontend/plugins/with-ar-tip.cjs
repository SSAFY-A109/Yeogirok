const fs = require("node:fs");
const path = require("node:path");
const { withAndroidManifest, withAppBuildGradle, withProjectBuildGradle, withMainApplication, withDangerousMod } = require("expo/config-plugins");

const nativePackage = "com.yeogirokarpoc.geo";
const gradleBlock = `
// BEGIN YEOGIROK AR
def arProperties = new Properties()
def arPropertiesFile = rootProject.file('local.properties')
if (arPropertiesFile.exists()) arPropertiesFile.withInputStream { arProperties.load(it) }
def arKey = { name -> (System.getenv(name) ?: arProperties.getProperty(name, '')).trim() }
android {
    defaultConfig {
        manifestPlaceholders += [kakaoNativeAppKey: arKey('KAKAO_NATIVE_APP_KEY')]
    }
    sourceSets.main.java.srcDirs += rootProject.file('../native/ar/android/java')
    sourceSets.main.assets.srcDirs += rootProject.file('../native/ar/android/assets')
}
dependencies {
    implementation 'com.google.ar:core:1.57.0'
    implementation 'com.google.android.gms:play-services-auth:22.0.0'
    implementation 'com.google.android.gms:play-services-location:21.0.1'
}
// END YEOGIROK AR
`;

module.exports = function withArTip(config) {
  config = withProjectBuildGradle(config, (mod) => {
    const marker = "// BEGIN YEOGIROK WINDOWS CMAKE";
    const block = `
${marker}
if (System.getProperty('os.name').toLowerCase().contains('windows')) {
    subprojects { child ->
        ['com.android.library', 'com.android.application'].each { pluginId ->
            child.plugins.withId(pluginId) {
                child.android.externalNativeBuild.cmake.buildStagingDirectory = rootProject.file('../.expo/cxx/' + child.name)
                child.android.defaultConfig.externalNativeBuild.cmake.arguments += '-DCMAKE_OBJECT_PATH_MAX=240'
                def ninja = new File(child.android.sdkDirectory, 'cmake/3.31.6/bin/ninja.exe')
                if (ninja.exists()) {
                    child.android.defaultConfig.externalNativeBuild.cmake.arguments += '-DCMAKE_MAKE_PROGRAM=' + ninja.absolutePath.replace('\\\\', '/')
                }
            }
        }
    }
}
// END YEOGIROK WINDOWS CMAKE
`;
    const source = mod.modResults.contents.replace(/\n\/\/ BEGIN YEOGIROK WINDOWS CMAKE[\s\S]*?\/\/ END YEOGIROK WINDOWS CMAKE\n/g, "");
    const rootPlugin = /apply plugin:\s*["']expo-root-project["']/;

    if (!rootPlugin.test(source)) {
      throw new Error("Cannot locate Expo root plugin for Windows CMake configuration");
    }

    mod.modResults.contents = source.replace(rootPlugin, (match) => {
      return block + match;
    });

    return mod;
  });

  config = withAndroidManifest(config, (mod) => {
    const manifest = mod.modResults.manifest;
    manifest["uses-permission"] ??= [];

    for (const name of ["CAMERA", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"]) {
      const full = `android.permission.${name}`;

      if (!manifest["uses-permission"].some((item) => {
        return item.$["android:name"] === full;
      })) {
        manifest["uses-permission"].push({ $: { "android:name": full } });
      }
    }

    manifest["uses-feature"] ??= [];
    const feature = "android.hardware.camera.ar";

    manifest["uses-feature"] = manifest["uses-feature"].filter((item) => {
      return item.$["android:name"] !== feature;
    });

    manifest["uses-feature"].push({ $: { "android:name": feature, "android:required": "false" } });
    const application = manifest.application[0];
    application.$["android:allowBackup"] = "false";
    application["meta-data"] ??= [];

    // Remove the previous API-key entry on every prebuild so ARCore selects Keyless auth.
    application["meta-data"] = application["meta-data"].filter((item) => {
      return item.$["android:name"] !== "com.google.android.ar.API_KEY";
    });

    for (const [name, value] of Object.entries({
      "com.google.ar.core": "optional",
      "com.yeogirokarpoc.KAKAO_NATIVE_APP_KEY": "${kakaoNativeAppKey}",
    })) {
      application["meta-data"] = application["meta-data"].filter((item) => {
        return item.$["android:name"] !== name;
      });

      application["meta-data"].push({ $: { "android:name": name, "android:value": value } });
    }

    for (const name of ["GeoArActivity", "GeoMapActivity"]) {
      application.activity = application.activity.filter((item) => {
        return item.$["android:name"] !== `${nativePackage}.${name}`;
      });

      application.activity.push({ $: {
        "android:name": `${nativePackage}.${name}`, "android:exported": "false", "android:screenOrientation": "portrait",
        ...(name === "GeoArActivity" ? { "android:process": ":geospatial" } : {}),
      } });
    }

    application.provider ??= [];
    const authority = "${applicationId}.ar-tip.photos";

    application.provider = application.provider.filter((item) => {
      return item.$["android:authorities"] !== authority;
    });

    application.provider.push({ $: {
      "android:name": "androidx.core.content.FileProvider", "android:authorities": authority,
      "android:exported": "false", "android:grantUriPermissions": "true",
    }, "meta-data": [{ $: { "android:name": "android.support.FILE_PROVIDER_PATHS", "android:resource": "@xml/ar_tip_photo_paths" } }] });
    return mod;
  });

  config = withAppBuildGradle(config, (mod) => {
    if (mod.modResults.language !== "groovy") {
      throw new Error("AR plugin requires a Groovy app build.gradle");
    }

    mod.modResults.contents = mod.modResults.contents.replace(/\n\/\/ BEGIN YEOGIROK AR[\s\S]*?\/\/ END YEOGIROK AR\n/g, "") + gradleBlock;
    return mod;
  });

  config = withMainApplication(config, (mod) => {
    if (mod.modResults.language !== "kt") {
      throw new Error("AR plugin requires Kotlin MainApplication");
    }

    let source = mod.modResults.contents;

    if (!source.includes(`add(${nativePackage}.GeoValidationPackage())`)) {
      if (!source.includes("PackageList(this).packages.apply {")) {
        throw new Error("Cannot locate package registration for AR");
      }

      source = source.replace("PackageList(this).packages.apply {", `PackageList(this).packages.apply {\n          add(${nativePackage}.GeoValidationPackage())`);
    }

    const isArProcess = 'android.app.Application.getProcessName() == "$packageName:geospatial"';
    const guard = `if (${isArProcess}) return`;
    // AR 프로세스도 카카오맵 WebView를 쓰므로, 메인 프로세스와 WebView 데이터 디렉터리를 나눠야 동시에 열어도 죽지 않는다.
    const createGuard = `if (${isArProcess}) {\n      android.webkit.WebView.setDataDirectorySuffix("geospatial")\n      return\n    }`;

    for (const [hook, line] of [["super.onCreate()", createGuard], ["super.onConfigurationChanged(newConfig)", guard]]) {
      if (!source.includes(hook)) {
        throw new Error(`Cannot locate ${hook} for AR process isolation`);
      }

      if (!source.includes(`${hook}\n    ${line}`)) {
        source = source.replace(`${hook}\n    ${guard}`, hook).replace(hook, `${hook}\n    ${line}`);
      }
    }

    mod.modResults.contents = source;
    return mod;
  });

  return withDangerousMod(config, ["android", async (mod) => {
    const root = mod.modRequest.projectRoot;
    const tokens = JSON.parse(fs.readFileSync(path.join(root, "design/tokens.json"), "utf8"));

    function value(token) {
      const raw = token.$value;

      return raw.startsWith("{") ? value(raw.slice(1, -1).split(".").reduce((item, key) => {
        return item[key];
      }, tokens)) : raw;
    }

    const palette = Object.fromEntries(["primary", "foreground", "card", "muted", "map-route", "map-footprint", "muted-foreground", "foreground-secondary", "border", "border-strong", "accent", "brand-muted", "positive", "destructive", "highlight-muted"].map((key) => {
      return [key, value(tokens.semantic.color[key])];
    }));

    const assets = path.join(mod.modRequest.platformProjectRoot, "app/src/main/assets");
    fs.mkdirSync(assets, { recursive: true });
    fs.writeFileSync(path.join(assets, "ar-theme.json"), JSON.stringify(palette));
    const xml = path.join(mod.modRequest.platformProjectRoot, "app/src/main/res/xml");
    fs.mkdirSync(xml, { recursive: true });
    fs.writeFileSync(path.join(xml, "ar_tip_photo_paths.xml"), '<?xml version="1.0" encoding="utf-8"?><paths><cache-path name="ar-tip-capture" path="ar-tip-capture/" /></paths>');
    const proguardPath = path.join(mod.modRequest.platformProjectRoot, "app/proguard-rules.pro");
    const proguard = fs.existsSync(proguardPath) ? fs.readFileSync(proguardPath, "utf8") : "";
    fs.writeFileSync(proguardPath, proguard.replace(/\n# BEGIN YEOGIROK KEYLESS[\s\S]*?# END YEOGIROK KEYLESS\n/g, "") + `
# BEGIN YEOGIROK KEYLESS
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
# END YEOGIROK KEYLESS
`);
    const propertiesPath = path.join(mod.modRequest.platformProjectRoot, "local.properties");
    let properties = fs.existsSync(propertiesPath) ? fs.readFileSync(propertiesPath, "utf8") : "";
    properties = properties.replace(/^ARCORE_API_KEY=.*(?:\r?\n|$)/gm, "");

    for (const [key, raw] of Object.entries({
      KAKAO_NATIVE_APP_KEY: process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY || process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY,
    })) {
      if (!raw?.trim()) {
        continue;
      }

      if (/[\r\n]/.test(raw)) {
        throw new Error(`Invalid ${key} format`);
      }

      properties = properties.replace(new RegExp(`^${key}=.*$`, "gm"), "");
      properties += `\n${key}=${raw.trim()}\n`;
    }

    fs.writeFileSync(propertiesPath, properties);
    return mod;
  }]);
};
