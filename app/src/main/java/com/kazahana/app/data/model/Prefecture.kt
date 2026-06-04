package com.kazahana.app.data.model

/**
 * 47都道府県。`rawValue` は BSAF `target` および避難所データの `prefecture` と突き合わせる
 * `jp-xxxx` 形式。マッピングは bsaf-kikikuru-bot の bot-definition.json および
 * build-shelters.py の PREFECTURE_MAP と完全一致させること。
 */
enum class Prefecture(val rawValue: String, val displayName: String, val japaneseName: String) {
    HOKKAIDO("jp-hokkaido", "北海道", "北海道"),
    AOMORI("jp-aomori", "青森県", "青森県"),
    IWATE("jp-iwate", "岩手県", "岩手県"),
    MIYAGI("jp-miyagi", "宮城県", "宮城県"),
    AKITA("jp-akita", "秋田県", "秋田県"),
    YAMAGATA("jp-yamagata", "山形県", "山形県"),
    FUKUSHIMA("jp-fukushima", "福島県", "福島県"),
    IBARAKI("jp-ibaraki", "茨城県", "茨城県"),
    TOCHIGI("jp-tochigi", "栃木県", "栃木県"),
    GUNMA("jp-gunma", "群馬県", "群馬県"),
    SAITAMA("jp-saitama", "埼玉県", "埼玉県"),
    CHIBA("jp-chiba", "千葉県", "千葉県"),
    TOKYO("jp-tokyo", "東京都", "東京都"),
    KANAGAWA("jp-kanagawa", "神奈川県", "神奈川県"),
    NIIGATA("jp-niigata", "新潟県", "新潟県"),
    TOYAMA("jp-toyama", "富山県", "富山県"),
    ISHIKAWA("jp-ishikawa", "石川県", "石川県"),
    FUKUI("jp-fukui", "福井県", "福井県"),
    YAMANASHI("jp-yamanashi", "山梨県", "山梨県"),
    NAGANO("jp-nagano", "長野県", "長野県"),
    GIFU("jp-gifu", "岐阜県", "岐阜県"),
    SHIZUOKA("jp-shizuoka", "静岡県", "静岡県"),
    AICHI("jp-aichi", "愛知県", "愛知県"),
    MIE("jp-mie", "三重県", "三重県"),
    SHIGA("jp-shiga", "滋賀県", "滋賀県"),
    KYOTO("jp-kyoto", "京都府", "京都府"),
    OSAKA("jp-osaka", "大阪府", "大阪府"),
    HYOGO("jp-hyogo", "兵庫県", "兵庫県"),
    NARA("jp-nara", "奈良県", "奈良県"),
    WAKAYAMA("jp-wakayama", "和歌山県", "和歌山県"),
    TOTTORI("jp-tottori", "鳥取県", "鳥取県"),
    SHIMANE("jp-shimane", "島根県", "島根県"),
    OKAYAMA("jp-okayama", "岡山県", "岡山県"),
    HIROSHIMA("jp-hiroshima", "広島県", "広島県"),
    YAMAGUCHI("jp-yamaguchi", "山口県", "山口県"),
    TOKUSHIMA("jp-tokushima", "徳島県", "徳島県"),
    KAGAWA("jp-kagawa", "香川県", "香川県"),
    EHIME("jp-ehime", "愛媛県", "愛媛県"),
    KOCHI("jp-kochi", "高知県", "高知県"),
    FUKUOKA("jp-fukuoka", "福岡県", "福岡県"),
    SAGA("jp-saga", "佐賀県", "佐賀県"),
    NAGASAKI("jp-nagasaki", "長崎県", "長崎県"),
    KUMAMOTO("jp-kumamoto", "熊本県", "熊本県"),
    OITA("jp-oita", "大分県", "大分県"),
    MIYAZAKI("jp-miyazaki", "宮崎県", "宮崎県"),
    KAGOSHIMA("jp-kagoshima", "鹿児島県", "鹿児島県"),
    OKINAWA("jp-okinawa", "沖縄県", "沖縄県"),
    ;

    companion object {
        fun fromRawValue(raw: String?): Prefecture? =
            entries.firstOrNull { it.rawValue == raw }

        /**
         * 逆ジオコーディングで得た都道府県名（"東京都" / "北海道札幌市" 等）から解決する。
         * Geocoder の administrativeArea は末尾に都道府県名を含むため前方一致で判定。
         */
        fun fromJapaneseName(name: String?): Prefecture? {
            if (name.isNullOrBlank()) return null
            // 京都府は「京都」で他県と衝突しないよう完全な都道府県名を含むか優先判定
            return entries.firstOrNull { name.contains(it.japaneseName) }
        }
    }
}
