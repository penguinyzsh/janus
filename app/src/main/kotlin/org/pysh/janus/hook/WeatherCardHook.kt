package org.pysh.janus.hook

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Adds a persistent weather card to the Smart Assistant panel.
 * The MAML template is an original design generated as a ZIP at runtime.
 * Weather data is fetched directly by the MAML ContentProviderBinder.
 */
object WeatherCardHook {

    private const val TAG = "Janus-Weather"
    private const val JANUS_PKG = "org.pysh.janus"
    private const val WEATHER_BUSINESS = "weather"
    private const val TEMPLATE_PATH =
        "/data/system/theme_magic/users/\$user_id/subscreencenter/smart_assistant/weather"
    private const val NOTIFICATION_ID = 19990
    private const val WEATHER_FLAG =
        "/data/system/theme_magic/users/0/subscreencenter/config/janus_weather"
    private const val UPDATE_INTERVAL_MS = 30 * 60 * 1000L

    private var manager: Any? = null
    private var mainHandler: Handler? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var initialized = false

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam, @Suppress("UNUSED_PARAMETER") prefs: XSharedPreferences) {
        patchConstants(lpparam)
        patchProtectedMap(lpparam)
        hookFilter(lpparam)
        hookAppList(lpparam)
        hookManagerInit(lpparam)
        XposedBridge.log("[$TAG] All hooks installed")
    }

    private fun patchConstants(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClass("p2.a", lpparam.classLoader)
            @Suppress("UNCHECKED_CAST")
            val origA = XposedHelpers.getStaticObjectField(cls, "a") as Map<String, String>
            XposedHelpers.setStaticObjectField(cls, "a",
                HashMap(origA).apply { put(JANUS_PKG, WEATHER_BUSINESS) })
            @Suppress("UNCHECKED_CAST")
            val origD = XposedHelpers.getStaticObjectField(cls, "d") as Map<String, String>
            XposedHelpers.setStaticObjectField(cls, "d",
                HashMap(origD).apply { put(WEATHER_BUSINESS, TEMPLATE_PATH) })
            XposedBridge.log("[$TAG] Constants patched")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] patchConstants: ${e.message}")
        }
    }

    private fun patchProtectedMap(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClass("p2.c", lpparam.classLoader)
            @Suppress("UNCHECKED_CAST")
            val map = XposedHelpers.getStaticObjectField(cls, "d") as HashMap<String, Any?>
            map[JANUS_PKG] = null
            XposedBridge.log("[$TAG] Protected map patched")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] patchProtectedMap: ${e.message}")
        }
    }

    private fun hookFilter(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "p2.c", lpparam.classLoader, "k",
                String::class.java, java.util.Set::class.java, java.util.Map::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] as? String == JANUS_PKG) param.result = true
                    }
                },
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookFilter: ${e.message}")
        }
    }

    private fun hookAppList(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val mgrCls = XposedHelpers.findClass("Z1.d0", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(mgrCls, "o", Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (XposedHelpers.getObjectField(param.thisObject, "r")
                                as ConcurrentHashMap.KeySetView<String, *>).add(JANUS_PKG)
                        (XposedHelpers.getObjectField(param.thisObject, "q")
                                as ConcurrentHashMap<String, Boolean>)[JANUS_PKG] = true
                    }
                },
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookAppList: ${e.message}")
        }
    }

    private fun hookManagerInit(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val mgrCls = XposedHelpers.findClass("Z1.d0", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(mgrCls, "l", Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (initialized) return
                        initialized = true
                        manager = param.thisObject
                        mainHandler = XposedHelpers.getStaticObjectField(mgrCls, "E") as? Handler

                        if (!File(WEATHER_FLAG).exists()) {
                            XposedBridge.log("[$TAG] Flag file absent, skipping")
                            return
                        }

                        deployTemplate()
                        uiHandler.postDelayed({ injectCard() }, 3_000)
                        val periodic = object : Runnable {
                            override fun run() {
                                injectCard()
                                uiHandler.postDelayed(this, UPDATE_INTERVAL_MS)
                            }
                        }
                        uiHandler.postDelayed(periodic, UPDATE_INTERVAL_MS)
                        XposedBridge.log("[$TAG] Template deployed, card scheduled")
                    }
                },
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookManagerInit: ${e.message}")
        }
    }

    private fun deployTemplate() {
        try {
            val userId = Process.myUid() / 100_000
            val mrcPath = TEMPLATE_PATH.replace("\$user_id", userId.toString())
            val mrcFile = File(mrcPath)
            if (mrcFile.isDirectory) mrcFile.deleteRecursively()
            if (mrcFile.isFile) mrcFile.delete()
            mrcFile.parentFile?.mkdirs()

            ZipOutputStream(FileOutputStream(mrcFile)).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.xml"))
                zip.write(MANIFEST_XML.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            mrcFile.setReadable(true, false)
            mrcFile.parentFile?.setReadable(true, false)
            mrcFile.parentFile?.setExecutable(true, false)
            XposedBridge.log("[$TAG] ZIP → $mrcPath (${mrcFile.length()} bytes)")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] deployTemplate: ${e.message}")
        }
    }

    private fun injectCard() {
        val mgr = manager ?: return
        val h = mainHandler ?: return
        try {
            val rearParam = JSONObject().apply {
                put("business", WEATHER_BUSINESS)
                put("index", 0)
                put("priority", 100)
            }
            val bundle = Bundle().apply {
                putString("miui.rear.param", rearParam.toString())
            }
            val runnableCls = XposedHelpers.findClass("Z1.m", mgr::class.java.classLoader)
            val mgrCls = XposedHelpers.findClass("Z1.d0", mgr::class.java.classLoader)
            val ctor = XposedHelpers.findConstructorExact(
                runnableCls, mgrCls,
                Int::class.javaPrimitiveType, String::class.java,
                String::class.java, Bundle::class.java,
            )
            h.post(ctor.newInstance(mgr, NOTIFICATION_ID, JANUS_PKG, "janus_weather", bundle) as Runnable)
            XposedBridge.log("[$TAG] Card injected")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] injectCard: ${e.message}")
        }
    }

    // ── Original MAML weather template ──────────────────────────────────
    // Clean, minimal design with ContentProviderBinder for weather data.
    // Camera on left ~37%, content on right. Supports AOD and dark mode.

    @Suppress("MaxLineLength")
    private val MANIFEST_XML = """
<Widget frameRate="0" scaleByDensity="false" screenWidth="1080" version="2" useVariableUpdater="HyperMaterial,DateTime.Minute">
  <Var name="s" type="number" expression="(#view_width / 336)"/>
  <Var name="cx" type="number" expression="(128 * #s)"/>
  <Var name="rw" type="number" expression="(#view_width - #cx - 10*#s)"/>
  <Var name="is_pause" type="number" expression="0" const="true"/>
  <Var name="is_aod" type="number" expression="ifelse(eqs(@aod_desk_state,'1'),1,0)" const="true"/>

  <!-- Weather data -->
  <Var name="has_data" type="number" expression="0" const="true"/>
  <Var name="city" type="string" expression="''" const="true"/>
  <Var name="temp" type="string" expression="''" const="true"/>
  <Var name="desc" type="string" expression="''" const="true"/>
  <Var name="hi" type="string" expression="''" const="true"/>
  <Var name="lo" type="string" expression="''" const="true"/>
  <Var name="humid" type="string" expression="''" const="true"/>
  <Var name="aqi_raw" type="string" expression="''" const="true"/>
  <Var name="aqi_n" type="number" expression="0" const="true"/>
  <Var name="aqi_lbl" type="string" expression="''" const="true"/>
  <Var name="wind" type="string" expression="''" const="true"/>

  <!-- Hourly -->
  <Var name="has_hourly" type="number" expression="0" const="true"/>
  <Var name="h_time" type="string[]" values="'','','',''" const="true"/>
  <Var name="h_temp" type="string[]" values="'','','',''" const="true"/>

  <Function name="fetch">
    <BinderCommand name="wb" command="refresh"/>
    <BinderCommand name="hb" command="refresh"/>
  </Function>

  <Function name="calc">
    <VariableCommand name="aqi_n" expression="ifelse(strIsEmpty(@aqi_raw),0,strToReal(@aqi_raw))" type="number"/>
    <VariableCommand name="aqi_lbl" expression="ifelse(#aqi_n{1,'',#aqi_n{51,'优',#aqi_n{101,'良',#aqi_n{151,'轻度',#aqi_n{201,'中度','重度')))" type="string"/>
  </Function>

  <!-- ── City ── -->
  <Text textExp="@city"
        x="#cx" y="(24*#s)" w="(110*#s)" h="(16*#s)"
        size="(13*#s)" color="#ddffffff" textAlign="left"
        visibility="ifelse(#has_data,1,0)"/>

  <!-- ── Temperature ── -->
  <Text textExp="@temp"
        x="#cx" y="(44*#s)" w="(90*#s)" h="(48*#s)"
        size="(42*#s)" color="#ffffffff" textAlign="left"
        visibility="ifelse(#has_data,1,0)"/>

  <!-- ── Degree symbol (smaller, offset up) ── -->
  <Text text="°"
        x="(#cx + 78*#s)" y="(44*#s)" w="(20*#s)" h="(24*#s)"
        size="(20*#s)" color="#ccffffff" textAlign="left"
        visibility="ifelse(#has_data,1,0)"/>

  <!-- ── Condition + range (right of temp) ── -->
  <Text textExp="@desc+'  '+@hi+'°/'+@lo+'°'"
        x="(#cx + 98*#s)" y="(52*#s)" w="(100*#s)" h="(14*#s)"
        size="(11*#s)" color="#bbffffff" textAlign="left"
        visibility="ifelse(strIsEmpty(@desc),0,1)"/>

  <!-- ── AQI ── -->
  <Text textExp="'空气'+@aqi_lbl+' '+@aqi_raw"
        x="(#cx + 98*#s)" y="(68*#s)" w="(100*#s)" h="(14*#s)"
        size="(11*#s)" color="#99ffffff" textAlign="left"
        visibility="ifelse(strIsEmpty(@aqi_lbl),0,1)"/>

  <!-- ── Divider line ── -->
  <Rectangle x="#cx" y="(100*#s)" w="#rw" h="(0.5*#s)" fillColor="#22ffffff"
             visibility="ifelse(#has_hourly,1,0)"/>

  <!-- ── Hourly forecast (4 slots, right of camera) ── -->
  <!-- date_time is ms-since-midnight, /3600000 = hour -->
  <Group visibility="ifelse(#has_hourly,1,0)">
    <Text textExp="ifelse(floor(int(@h_time[0])/3600000){10,'0','')+floor(int(@h_time[0])/3600000)+':00'"
          x="(#cx)"         y="(108*#s)" w="(48*#s)" h="(12*#s)" size="(9*#s)" color="#88ffffff" textAlign="center"/>
    <Text textExp="@h_temp[0]+'°'"
          x="(#cx)"         y="(124*#s)" w="(48*#s)" h="(12*#s)" size="(10*#s)" color="#ccffffff" textAlign="center"/>

    <Text textExp="ifelse(floor(int(@h_time[1])/3600000){10,'0','')+floor(int(@h_time[1])/3600000)+':00'"
          x="(#cx+52*#s)"  y="(108*#s)" w="(48*#s)" h="(12*#s)" size="(9*#s)" color="#88ffffff" textAlign="center"/>
    <Text textExp="@h_temp[1]+'°'"
          x="(#cx+52*#s)"  y="(124*#s)" w="(48*#s)" h="(12*#s)" size="(10*#s)" color="#ccffffff" textAlign="center"/>

    <Text textExp="ifelse(floor(int(@h_time[2])/3600000){10,'0','')+floor(int(@h_time[2])/3600000)+':00'"
          x="(#cx+104*#s)" y="(108*#s)" w="(48*#s)" h="(12*#s)" size="(9*#s)" color="#88ffffff" textAlign="center"/>
    <Text textExp="@h_temp[2]+'°'"
          x="(#cx+104*#s)" y="(124*#s)" w="(48*#s)" h="(12*#s)" size="(10*#s)" color="#ccffffff" textAlign="center"/>

    <Text textExp="ifelse(floor(int(@h_time[3])/3600000){10,'0','')+floor(int(@h_time[3])/3600000)+':00'"
          x="(#cx+156*#s)" y="(108*#s)" w="(48*#s)" h="(12*#s)" size="(9*#s)" color="#88ffffff" textAlign="center"/>
    <Text textExp="@h_temp[3]+'°'"
          x="(#cx+156*#s)" y="(124*#s)" w="(48*#s)" h="(12*#s)" size="(10*#s)" color="#ccffffff" textAlign="center"/>
  </Group>

  <!-- ── Wind + Humidity (bottom right) ── -->
  <Text textExp="@wind+'  |  '+'湿度 '+@humid+'%'"
        x="#cx" y="(144*#s)" w="#rw" h="(12*#s)"
        size="(9*#s)" color="#66ffffff" textAlign="left"
        visibility="ifelse(#has_data,1,0)"/>

  <!-- ── No-data placeholder ── -->
  <Text text="天气加载中..."
        x="#cx" y="(60*#s)" w="#rw" h="(30*#s)"
        size="(14*#s)" color="#55ffffff" textAlign="left"
        visibility="ifelse(#has_data,0,1)"/>

  <VariableBinders>
    <!-- Daily weather (row 0 = today) -->
    <ContentProviderBinder name="wb"
        uriExp="'content://weather/actualWeatherData/curPosition'"
        columns="city_name,temperature,description,tmphighs,tmplows,humidity,aqilevel,wind"
        countName="has_data">
      <Variable name="city" column="city_name" type="string"/>
      <Variable name="temp" column="temperature" type="string"/>
      <Variable name="desc" column="description" type="string"/>
      <Variable name="hi" column="tmphighs" type="string"/>
      <Variable name="lo" column="tmplows" type="string"/>
      <Variable name="humid" column="humidity" type="string"/>
      <Variable name="aqi_raw" column="aqilevel" type="string"/>
      <Variable name="wind" column="wind" type="string"/>
      <Trigger>
        <FunctionCommand target="calc"/>
      </Trigger>
    </ContentProviderBinder>

    <!-- Hourly forecast -->
    <ContentProviderBinder name="hb"
        uriExp="'content://weather/hourlyData/curPosition'"
        columns="date_time,temperature"
        countName="has_hourly">
      <Variable name="h_time" column="date_time" type="string[]"/>
      <Variable name="h_temp" column="temperature" type="string[]"/>
    </ContentProviderBinder>
  </VariableBinders>

  <ExternalCommands>
    <Trigger action="init">
      <FunctionCommand target="fetch"/>
    </Trigger>
    <Trigger action="notification_received">
      <FunctionCommand target="fetch"/>
    </Trigger>
    <Trigger action="params_transferred">
      <FunctionCommand target="fetch"/>
    </Trigger>
    <Trigger action="resume">
      <VariableCommand name="is_pause" expression="0" type="number"/>
      <FunctionCommand target="fetch"/>
    </Trigger>
    <Trigger action="pause">
      <VariableCommand name="is_pause" expression="1" type="number"/>
      <FrameRateCommand rate="0"/>
    </Trigger>
    <Trigger action="enterAod">
      <VariableCommand name="is_aod" expression="1" type="number"/>
    </Trigger>
    <Trigger action="exitAod">
      <VariableCommand name="is_aod" expression="0" type="number"/>
      <FunctionCommand target="fetch"/>
    </Trigger>
  </ExternalCommands>
</Widget>
""".trimIndent()
}
