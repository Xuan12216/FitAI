package com.xuan.fitai.nutrition

/**
 * Approximate recipe examples (not verified brand or laboratory data) for common Taiwanese composite meals, street foods,
 * breakfast shop items, bento boxes, and popular chain restaurant items that are
 * absent from the raw-ingredient TFDA dataset.
 *
 * All macro/calorie fields are normalized per 100g edible portion (matching TFDA convention
 * and [NutritionPortionCalculator]), while [NutritionFood.description] explicitly states
 * the standard 1-serving weight and total serving nutrition so the local AI can accurately
 * estimate both "1份/1碗" and explicit gram weights.
 */
object TaiwaneseCommonFoods {
    private fun dish(
        id: String,
        name: String,
        aliases: String,
        servingDesc: String,
        caloriesPer100g: Float,
        proteinPer100g: Float,
        carbsPer100g: Float,
        fatPer100g: Float,
        category: String = "內建配方估算"
    ) = NutritionFood(
        id = id,
        name = name,
        aliases = aliases,
        description = "【來源：內建配方估算，非官方／品牌營養標示】$servingDesc；表列數值為每100g換算",
        calories = caloriesPer100g,
        protein = proteinPer100g,
        carbs = carbsPer100g,
        fat = fatPer100g,
        category = category
    )

    // Beverages with only ml-based portions are omitted: volume does not establish edible mass.
    val ALL: List<NutritionFood> = listOf(
        // ── 麵食與湯麵 ──────────────────────────────────────────
        dish("TW-001", "紅燒牛肉麵", "牛肉麵", "標準1碗(約680g含湯)約760kcal(P 44g/C 78g/F 29g)", 112f, 6.5f, 11.5f, 4.3f),
        dish("TW-002", "清燉牛肉麵", "牛肉麵,清湯牛肉麵", "標準1碗(約680g含湯)約650kcal(P 46g/C 76g/F 18g)", 96f, 6.8f, 11.2f, 2.6f),
        dish("TW-003", "牛肉湯麵", "", "標準1碗(約580g無肉)約490kcal(P 16g/C 70g/F 16g)", 85f, 2.8f, 12.0f, 2.8f),
        dish("TW-004", "牛肉乾拌麵", "牛肉乾麵", "標準1碗(約380g)約620kcal(P 38g/C 68g/F 22g)", 163f, 10.0f, 17.9f, 5.8f),
        dish("TW-005", "陽春麵", "陽春湯麵", "標準1碗(約450g含湯)約360kcal(P 11g/C 58g/F 9g)", 80f, 2.4f, 12.9f, 2.0f),
        dish("TW-006", "乾麵", "乾拌麵", "標準1碗(約260g)約440kcal(P 12g/C 62g/F 16g)", 169f, 4.6f, 23.8f, 6.2f),
        dish("TW-007", "麻醬麵", "麻醬乾麵", "標準1碗(約280g)約560kcal(P 16g/C 68g/F 25g)", 200f, 5.7f, 24.3f, 8.9f),
        dish("TW-008", "炸醬麵", "醡醬麵", "標準1碗(約300g)約520kcal(P 18g/C 66g/F 20g)", 173f, 6.0f, 22.0f, 6.7f),
        dish("TW-009", "大腸麵線", "", "標準1碗(約400g)約420kcal(P 15g/C 58g/F 14g)", 105f, 3.8f, 14.5f, 3.5f),
        dish("TW-010", "肉羹麵", "", "標準1碗(約480g)約530kcal(P 20g/C 72g/F 18g)", 110f, 4.2f, 15.0f, 3.8f),
        dish("TW-011", "鍋燒意麵", "", "標準1碗(約550g)約580kcal(P 24g/C 65g/F 25g)", 105f, 4.4f, 11.8f, 4.5f),
        dish("TW-012", "黑胡椒鐵板麵", "", "標準1份(約320g含蛋)約610kcal(P 18g/C 74g/F 27g)", 191f, 5.6f, 23.1f, 8.4f),
        dish("TW-013", "炒麵", "台式炒麵", "標準1份(約320g)約580kcal(P 16g/C 72g/F 25g)", 181f, 5.0f, 22.5f, 7.8f),
        dish("TW-014", "義大利肉醬麵", "", "標準1份(約380g)約640kcal(P 24g/C 78g/F 26g)", 168f, 6.3f, 20.5f, 6.8f),
        dish("TW-015", "豚骨拉麵", "日式豚骨拉麵", "標準1碗(約650g)約780kcal(P 32g/C 82g/F 36g)", 120f, 4.9f, 12.6f, 5.5f),

        // ── 早餐店：蛋餅、吐司、漢堡、中式早點 ────────────────────────
        dish("TW-016", "原味蛋餅", "蛋餅,傳統原味蛋餅", "標準1份(約125g)約263kcal(P 9.4g/C 28.8g/F 12g)", 210f, 7.5f, 23.0f, 9.6f),
        dish("TW-017", "起司蛋餅", "起士蛋餅,乳酪蛋餅,芝士蛋餅", "標準1份(約145g)約345kcal(P 14.8g/C 29.7g/F 18.6g)", 238f, 10.2f, 20.5f, 12.8f),
        dish("TW-018", "玉米蛋餅", "", "標準1份(約155g)約307kcal(P 10.5g/C 38g/F 12.6g)", 198f, 6.8f, 24.5f, 8.1f),
        dish("TW-019", "鮪魚蛋餅", "", "標準1份(約160g)約344kcal(P 17.9g/C 31.7g/F 16.2g)", 215f, 11.2f, 19.8f, 10.1f),
        dish("TW-020", "培根蛋餅", "", "標準1份(約140g)約357kcal(P 12g/C 28.3g/F 21.8g)", 255f, 8.6f, 20.2f, 15.6f),
        dish("TW-021", "火腿蛋餅", "", "標準1份(約140g)約297kcal(P 12.6g/C 30.1g/F 13.7g)", 212f, 9.0f, 21.5f, 9.8f),
        dish("TW-022", "薯餅蛋餅", "", "標準1份(約185g)約459kcal(P 12g/C 49.6g/F 23.7g)", 248f, 6.5f, 26.8f, 12.8f),
        dish("TW-023", "豬排蛋餅", "里肌豬排蛋餅", "標準1份(約170g)約383kcal(P 21.3g/C 32.6g/F 18.4g)", 225f, 12.5f, 19.2f, 10.8f),
        dish("TW-024", "肉鬆蛋餅", "", "標準1份(約140g)約339kcal(P 13.3g/C 33.6g/F 16.8g)", 242f, 9.5f, 24.0f, 12.0f),
        dish("TW-025", "蔥抓餅加蛋", "", "標準1份(約160g)約410kcal(P 12g/C 48g/F 19g)", 256f, 7.5f, 30.0f, 11.9f),
        dish("TW-026", "燒餅油條", "", "標準1套(約150g)約520kcal(P 12g/C 58g/F 27g)", 347f, 8.0f, 38.7f, 18.0f),
        dish("TW-027", "台式飯糰", "傳統台式飯糰", "標準1顆(約200g)約460kcal(P 13g/C 66g/F 16g)", 230f, 6.5f, 33.0f, 8.0f),
        dish("TW-028", "御飯糰", "三角飯糰,超商飯糰", "標準1顆(約110g)約205kcal(P 5.5g/C 36g/F 4.2g)", 186f, 5.0f, 32.7f, 3.8f),
        dish("TW-029", "火腿蛋吐司", "", "標準1份(約140g)約350kcal(P 14g/C 38g/F 15.5g)", 250f, 10.0f, 27.1f, 11.1f),
        dish("TW-030", "總匯三明治", "俱樂部三明治", "標準1份(約240g)約560kcal(P 24g/C 52g/F 28g)", 233f, 10.0f, 21.7f, 11.7f),
        dish("TW-031", "蘿蔔糕", "", "標準1份2片(約130g)約230kcal(P 4.5g/C 34g/F 8.5g)", 177f, 3.5f, 26.2f, 6.5f),
        dish("TW-032", "水煎包", "", "標準1顆(約80g)約180kcal(P 6.5g/C 22g/F 7.5g)", 225f, 8.1f, 27.5f, 9.4f),
        dish("TW-033", "鍋貼", "煎餃", "標準10顆(約280g)約620kcal(P 24g/C 66g/F 29g)", 221f, 8.6f, 23.6f, 10.4f),
        dish("TW-034", "水餃", "熟水餃", "標準10顆(約260g)約520kcal(P 22g/C 60g/F 21g)", 200f, 8.5f, 23.1f, 8.1f),

        // ── 飯類與便當 ──────────────────────────────────────────
        dish("TW-035", "滷肉飯", "魯肉飯", "標準1碗(約240g)約440kcal(P 13g/C 56g/F 18g)", 183f, 5.4f, 23.3f, 7.5f),
        dish("TW-036", "雞肉飯", "", "標準1碗(約240g)約380kcal(P 18g/C 54g/F 10g)", 158f, 7.5f, 22.5f, 4.2f),
        dish("TW-037", "控肉飯", "爌肉飯", "標準1碗(約320g)約680kcal(P 26g/C 65g/F 35g)", 213f, 8.1f, 20.3f, 10.9f),
        dish("TW-038", "炸雞腿便當", "酥炸雞腿便當", "標準1個(約500g)約880kcal(P 38g/C 95g/F 38g)", 176f, 7.6f, 19.0f, 7.6f),
        dish("TW-039", "滷雞腿便當", "", "標準1個(約480g)約730kcal(P 38g/C 90g/F 24g)", 152f, 7.9f, 18.8f, 5.0f),
        dish("TW-040", "排骨便當", "", "標準1個(約480g)約820kcal(P 32g/C 94g/F 35g)", 171f, 6.7f, 19.6f, 7.3f),
        dish("TW-041", "控肉便當", "爌肉便當", "標準1個(約480g)約850kcal(P 30g/C 90g/F 41g)", 177f, 6.3f, 18.8f, 8.5f),
        dish("TW-042", "三寶飯", "燒臘三寶飯", "標準1個(約500g)約920kcal(P 42g/C 92g/F 42g)", 184f, 8.4f, 18.4f, 8.4f),
        dish("TW-043", "健康餐盒", "", "標準1盒(約420g)約520kcal(P 38g/C 62g/F 13g)", 124f, 9.0f, 14.8f, 3.1f),
        dish("TW-044", "蛋炒飯", "", "標準1盤(約320g)約640kcal(P 18g/C 82g/F 26g)", 200f, 5.6f, 25.6f, 8.1f),
        dish("TW-045", "咖哩飯", "", "標準1盤(約450g)約720kcal(P 22g/C 96g/F 27g)", 160f, 4.9f, 21.3f, 6.0f),
        dish("TW-046", "燴飯", "", "標準1盤(約450g)約680kcal(P 24g/C 88g/F 25g)", 151f, 5.3f, 19.6f, 5.6f),
        dish("TW-047", "糙米飯", "", "標準1碗(約200g)約350kcal(P 7.2g/C 74g/F 2.4g)", 175f, 3.6f, 37.0f, 1.2f),

        // ── 健身常備與單點蛋白質 ──────────────────────────────────
        dish("TW-048", "舒肥雞胸肉", "", "標準1包(約115g)約135kcal(P 26g/C 1.5g/F 2.5g)", 117f, 22.6f, 1.3f, 2.2f),
        dish("TW-049", "水煮蛋", "白煮蛋", "標準1顆(約55g)約75kcal(P 6.8g/C 0.8g/F 5.0g)", 136f, 12.4f, 1.5f, 9.1f),
        dish("TW-050", "乾煎鮭魚", "乾煎鮭魚排", "標準1片(約150g)約330kcal(P 31g/C 0g/F 22g)", 220f, 20.7f, 0.0f, 14.7f),
        dish("TW-051", "滷牛腱", "", "標準1份(約100g)約168kcal(P 28g/C 2g/F 5.2g)", 168f, 28.0f, 2.0f, 5.2f),
        dish("TW-052", "滷豆干", "滷豆乾", "標準1份(約100g)約175kcal(P 16g/C 5.5g/F 10g)", 175f, 16.0f, 5.5f, 10.0f),
        dish("TW-053", "烤地瓜", "", "標準1條(約150g)約186kcal(P 2.1g/C 42g/F 0.5g)", 124f, 1.4f, 28.0f, 0.3f),

        // ── 台灣小吃、夜市與炸物 ──────────────────────────────────
        dish("TW-054", "鹹酥雞", "鹽酥雞,無骨鹹酥雞", "標準1份(約180g)約540kcal(P 32g/C 26g/F 34g)", 300f, 17.8f, 14.4f, 18.9f),
        dish("TW-055", "炸雞排", "酥炸雞排", "標準1片(約230g)約630kcal(P 38g/C 32g/F 39g)", 274f, 16.5f, 13.9f, 17.0f),
        dish("TW-056", "甜不辣", "", "標準1份(約150g)約330kcal(P 14g/C 38g/F 13.5g)", 220f, 9.3f, 25.3f, 9.0f),
        dish("TW-057", "炸臭豆腐", "", "標準1份5塊(約200g)約510kcal(P 22g/C 32g/F 33g)", 255f, 11.0f, 16.0f, 16.5f),
        dish("TW-058", "蚵仔煎", "", "標準1份(約250g)約510kcal(P 14g/C 56g/F 25g)", 204f, 5.6f, 22.4f, 10.0f),
        dish("TW-059", "肉圓", "", "標準1顆(約180g)約380kcal(P 11g/C 48g/F 16g)", 211f, 6.1f, 26.7f, 8.9f),
        dish("TW-060", "肉粽", "", "標準1顆(約200g)約520kcal(P 16g/C 66g/F 21g)", 260f, 8.0f, 33.0f, 10.5f),
        dish("TW-061", "刈包", "虎咬豬,割包", "標準1個(約160g)約420kcal(P 15g/C 44g/F 20g)", 263f, 9.4f, 27.5f, 12.5f),

        // ── 飲品與手搖飲 ────────────────────────────────────────

        // ── 常見連鎖速食 ────────────────────────────────────────
        dish("TW-068", "麥當勞大麥克", "大麥克,BigMac", "標準1個(約215g)約534kcal(P 27g/C 43g/F 28g)", 248f, 12.6f, 20.0f, 13.0f),
        dish("TW-069", "麥當勞勁辣雞腿堡", "勁辣雞腿堡", "標準1個(約195g)約493kcal(P 23g/C 44g/F 25g)", 253f, 11.8f, 22.6f, 12.8f),
        dish("TW-070", "麥克雞塊", "麥當勞麥克雞塊", "標準6塊(約96g)約270kcal(P 15g/C 16g/F 16g)", 281f, 15.6f, 16.7f, 16.7f),
        dish("TW-071", "豬肉滿福堡加蛋", "", "標準1個(約165g)約390kcal(P 22g/C 29g/F 20g)", 236f, 13.3f, 17.6f, 12.1f),
        dish("TW-072", "肯德基原味蛋撻", "肯德基蛋撻", "標準1顆(約55g)約182kcal(P 3.1g/C 18.5g/F 10.6g)", 331f, 5.6f, 33.6f, 19.3f)
    )
}
