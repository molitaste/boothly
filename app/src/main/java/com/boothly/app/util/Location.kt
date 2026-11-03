package com.boothly.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.boothly.app.data.Supplier

/** Finds the supplier's city (or province / country) from address, company name or card text. */
object CityDetector {
    // English name to Chinese spellings. Order matters: more specific first.
    private val CITIES: List<Pair<String, List<String>>> = listOf(
        "Guangzhou" to listOf("广州", "canton city", "panyu", "番禺", "baiyun", "白云", "huadu", "花都"),
        "Shenzhen" to listOf("深圳", "bao'an", "baoan", "宝安", "longgang", "龙岗", "longhua", "龙华"),
        "Foshan" to listOf("佛山", "shunde", "顺德", "nanhai", "南海"),
        "Dongguan" to listOf("东莞"),
        "Zhongshan" to listOf("中山", "guzhen", "古镇"),
        "Zhuhai" to listOf("珠海"),
        "Huizhou" to listOf("惠州"),
        "Jiangmen" to listOf("江门"),
        "Shantou" to listOf("汕头", "chenghai", "澄海"),
        "Chaozhou" to listOf("潮州"),
        "Jieyang" to listOf("揭阳"),
        "Zhaoqing" to listOf("肇庆"),
        "Qingyuan" to listOf("清远"),
        "Xiamen" to listOf("厦门"),
        "Jinjiang" to listOf("晋江"),
        "Quanzhou" to listOf("泉州"),
        "Fuzhou" to listOf("福州"),
        "Putian" to listOf("莆田"),
        "Yiwu" to listOf("义乌"),
        "Yongkang" to listOf("永康"),
        "Ningbo" to listOf("宁波", "cixi", "慈溪", "yuyao", "余姚"),
        "Hangzhou" to listOf("杭州"),
        "Wenzhou" to listOf("温州", "yueqing", "乐清", "ruian", "瑞安"),
        "Taizhou" to listOf("台州", "泰州"),
        "Jinhua" to listOf("金华"),
        "Shaoxing" to listOf("绍兴"),
        "Huzhou" to listOf("湖州"),
        "Jiaxing" to listOf("嘉兴"),
        "Shanghai" to listOf("上海"),
        "Suzhou" to listOf("苏州", "kunshan", "昆山"),
        "Wuxi" to listOf("无锡"),
        "Changzhou" to listOf("常州"),
        "Nanjing" to listOf("南京"),
        "Nantong" to listOf("南通"),
        "Yangzhou" to listOf("扬州"),
        "Beijing" to listOf("北京"),
        "Tianjin" to listOf("天津"),
        "Qingdao" to listOf("青岛"),
        "Jinan" to listOf("济南"),
        "Yantai" to listOf("烟台"),
        "Linyi" to listOf("临沂"),
        "Weifang" to listOf("潍坊"),
        "Shijiazhuang" to listOf("石家庄"),
        "Baoding" to listOf("保定"),
        "Zhengzhou" to listOf("郑州"),
        "Wuhan" to listOf("武汉"),
        "Changsha" to listOf("长沙"),
        "Chengdu" to listOf("成都"),
        "Chongqing" to listOf("重庆"),
        "Xi'an" to listOf("西安", "xian"),
        "Hefei" to listOf("合肥"),
        "Nanchang" to listOf("南昌"),
        "Shenyang" to listOf("沈阳"),
        "Dalian" to listOf("大连"),
        "Hong Kong" to listOf("香港", "hongkong", "kowloon", "九龙"),
        "Macau" to listOf("澳门", "macao"),
        "Taipei" to listOf("台北"),
        "Dubai" to listOf("دبي"),
        "Sharjah" to listOf("الشارقة"),
        "Amman" to listOf("عمان"),
        "Riyadh" to listOf("الرياض"),
        "Jeddah" to listOf("جدة"),
        "Istanbul" to listOf("إسطنبول"),
    )

    private val PROVINCES: List<Pair<String, List<String>>> = listOf(
        "Guangdong" to listOf("广东"),
        "Zhejiang" to listOf("浙江"),
        "Fujian" to listOf("福建"),
        "Jiangsu" to listOf("江苏"),
        "Shandong" to listOf("山东"),
        "Hebei" to listOf("河北"),
        "Henan" to listOf("河南"),
        "Hubei" to listOf("湖北"),
        "Hunan" to listOf("湖南"),
        "Anhui" to listOf("安徽"),
        "Jiangxi" to listOf("江西"),
        "Sichuan" to listOf("四川"),
        "Liaoning" to listOf("辽宁"),
        "Guangxi" to listOf("广西"),
        "Taiwan" to listOf("台湾"),
        "India" to listOf(),
        "Turkey" to listOf("türkiye", "تركيا"),
        "Vietnam" to listOf("viet nam"),
        "Jordan" to listOf("الأردن"),
        "UAE" to listOf("united arab emirates", "الإمارات"),
        "Saudi Arabia" to listOf("السعودية"),
    )

    private fun find(text: String, table: List<Pair<String, List<String>>>): String? {
        val low = text.lowercase()
        for ((name, others) in table) {
            if (Regex("(?<![a-z])" + Regex.escape(name.lowercase()) + "(?![a-z])").containsMatchIn(low)) return name
            if (others.any { o -> if (o.any { it in 'a'..'z' }) Regex("(?<![a-z])" + Regex.escape(o) + "(?![a-z])").containsMatchIn(low) else low.contains(o) }) return name
        }
        return null
    }

    /** Looks in each text in order (address first, then company, then card text). */
    fun detect(texts: List<String>): String {
        val list = texts.filter { it.isNotBlank() }
        for (t in list) find(t, CITIES)?.let { return it }
        for (t in list) find(t, PROVINCES)?.let { return it }
        return ""
    }

    fun cityOf(s: Supplier): String = s.city.ifBlank { detect(listOf(s.address, s.company, s.ocrText)) }

    /** "9.2E15" → "Hall 9.2" (Canton Fair style booth numbers). */
    fun hallOf(s: Supplier): String {
        val b = s.booth.trim()
        if (b.isEmpty()) return ""
        Regex("^(\\d{1,2}\\.\\d)").find(b)?.let { return "Hall " + it.groupValues[1] }
        Regex("^(\\d{1,2})[A-Za-z]").find(b)?.let { return "Hall " + it.groupValues[1] }
        return "Booth " + b.take(3)
    }
}

/** Opens a place in any map app installed on the phone (Amap, Baidu, Tencent, Petal, Google…). */
object MapApps {
    data class MapApp(val name: String, val pkg: String?, val intent: (String) -> Intent)

    private fun view(uri: String, pkg: String?) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
        if (pkg != null) setPackage(pkg)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    private val ALL = listOf(
        MapApp("Amap 高德地图", "com.autonavi.minimap") { q ->
            view("androidamap://poi?sourceApplication=Boothly&keywords=${Uri.encode(q)}&dev=0", "com.autonavi.minimap")
        },
        MapApp("Baidu Maps 百度地图", "com.baidu.BaiduMap") { q ->
            view("baidumap://map/geocoder?src=andr.boothly.app&address=${Uri.encode(q)}", "com.baidu.BaiduMap")
        },
        MapApp("Tencent Maps 腾讯地图", "com.tencent.map") { q ->
            view("qqmap://map/search?keyword=${Uri.encode(q)}&referer=Boothly", "com.tencent.map")
        },
        MapApp("Petal Maps (Huawei)", "com.huawei.maps.app") { q ->
            view("geo:0,0?q=${Uri.encode(q)}", "com.huawei.maps.app")
        },
        MapApp("Google Maps", "com.google.android.apps.maps") { q ->
            view("geo:0,0?q=${Uri.encode(q)}", "com.google.android.apps.maps")
        },
        MapApp("Waze", "com.waze") { q ->
            view("https://waze.com/ul?q=${Uri.encode(q)}", "com.waze")
        },
        MapApp("Apple Maps (web)", null) { q ->
            view("https://maps.apple.com/?q=${Uri.encode(q)}", null)
        },
        MapApp("Other map app…", null) { q ->
            Intent.createChooser(view("geo:0,0?q=${Uri.encode(q)}", null), "Open with")
        },
    )

    private fun installed(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    /** Installed map apps first, then the web / chooser options. */
    fun available(ctx: Context): List<MapApp> = ALL.filter { it.pkg == null || installed(ctx, it.pkg) }

    fun open(ctx: Context, app: MapApp, query: String) {
        try {
            ctx.startActivity(app.intent(query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // fall back to the app's main screen if it doesn't accept the link
            val launch = app.pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
            if (launch != null) {
                Contact.copy(ctx, "Address", query)
                toast(ctx, "Address copied – paste it in the search box")
                ctx.startActivity(launch)
            } else {
                toast(ctx, "Could not open ${app.name}")
            }
        }
    }

    fun queryFor(s: Supplier): String {
        if (s.address.isNotBlank()) return s.address
        val city = CityDetector.cityOf(s)
        return listOf(s.company, city).filter { it.isNotBlank() }.joinToString(" ")
    }
}
