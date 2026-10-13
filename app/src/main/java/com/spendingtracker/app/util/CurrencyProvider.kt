package com.spendingtracker.app.util

object CurrencyProvider {
    data class CurrencyInfo(
        val code: String,
        val name: String,
        val symbol: String
    )

    val allCurrencies = listOf(
        CurrencyInfo("USD", "US Dollar", "\$"),
        CurrencyInfo("EUR", "Euro", "€"),
        CurrencyInfo("GBP", "British Pound", "£"),
        CurrencyInfo("JPY", "Japanese Yen", "¥"),
        CurrencyInfo("ILS", "Israeli New Shekel", "₪"),
        CurrencyInfo("AUD", "Australian Dollar", "A\$"),
        CurrencyInfo("CAD", "Canadian Dollar", "C\$"),
        CurrencyInfo("CHF", "Swiss Franc", "CHF"),
        CurrencyInfo("CNY", "Chinese Yuan", "¥"),
        CurrencyInfo("SEK", "Swedish Krona", "kr"),
        CurrencyInfo("NZD", "New Zealand Dollar", "NZ\$"),
        CurrencyInfo("MXN", "Mexican Peso", "\$"),
        CurrencyInfo("SGD", "Singapore Dollar", "S\$"),
        CurrencyInfo("HKD", "Hong Kong Dollar", "HK\$"),
        CurrencyInfo("NOK", "Norwegian Krone", "kr"),
        CurrencyInfo("KRW", "South Korean Won", "₩"),
        CurrencyInfo("TRY", "Turkish Lira", "₺"),
        CurrencyInfo("INR", "Indian Rupee", "₹"),
        CurrencyInfo("BRL", "Brazilian Real", "R\$"),
        CurrencyInfo("ZAR", "South African Rand", "R"),
        CurrencyInfo("RUB", "Russian Ruble", "₽"),
        CurrencyInfo("PLN", "Polish Złoty", "zł"),
        CurrencyInfo("DKK", "Danish Krone", "kr"),
        CurrencyInfo("THB", "Thai Baht", "฿"),
        CurrencyInfo("AED", "UAE Dirham", "AED"),
        CurrencyInfo("AFN", "Afghan Afghani", "؋"),
        CurrencyInfo("ALL", "Albanian Lek", "L"),
        CurrencyInfo("AMD", "Armenian Dram", "֏"),
        CurrencyInfo("ANG", "Netherlands Antillean Guilder", "ƒ"),
        CurrencyInfo("AOA", "Angolan Kwanza", "Kz"),
        CurrencyInfo("ARS", "Argentine Peso", "\$"),
        CurrencyInfo("AWG", "Aruban Florin", "ƒ"),
        CurrencyInfo("AZN", "Azerbaijani Manat", "₼"),
        CurrencyInfo("BAM", "Bosnia-Herzegovina Mark", "KM"),
        CurrencyInfo("BBD", "Barbadian Dollar", "\$"),
        CurrencyInfo("BDT", "Bangladeshi Taka", "৳"),
        CurrencyInfo("BGN", "Bulgarian Lev", "лв"),
        CurrencyInfo("BHD", "Bahraini Dinar", ".د.ب"),
        CurrencyInfo("BIF", "Burundian Franc", "FBu"),
        CurrencyInfo("BMD", "Bermudan Dollar", "\$"),
        CurrencyInfo("BND", "Brunei Dollar", "\$"),
        CurrencyInfo("BOB", "Bolivian Boliviano", "Bs."),
        CurrencyInfo("BSD", "Bahamian Dollar", "\$"),
        CurrencyInfo("BTN", "Bhutanese Ngultrum", "Nu."),
        CurrencyInfo("BWP", "Botswanan Pula", "P"),
        CurrencyInfo("BYN", "Belarusian Ruble", "Br"),
        CurrencyInfo("BZD", "Belize Dollar", "BZ\$"),
        CurrencyInfo("CDF", "Congolese Franc", "FC"),
        CurrencyInfo("CLP", "Chilean Peso", "\$"),
        CurrencyInfo("COP", "Colombian Peso", "\$"),
        CurrencyInfo("CRC", "Costa Rican Colón", "₡"),
        CurrencyInfo("CUP", "Cuban Peso", "₱"),
        CurrencyInfo("CVE", "Cape Verdean Escudo", "\$"),
        CurrencyInfo("CZK", "Czech Koruna", "Kč"),
        CurrencyInfo("DJF", "Djiboutian Franc", "Fdj"),
        CurrencyInfo("DOP", "Dominican Peso", "RD\$"),
        CurrencyInfo("DZD", "Algerian Dinar", "دج"),
        CurrencyInfo("EGP", "Egyptian Pound", "£"),
        CurrencyInfo("ERN", "Eritrean Nakfa", "Nfk"),
        CurrencyInfo("ETB", "Ethiopian Birr", "Br"),
        CurrencyInfo("FJD", "Fijian Dollar", "\$"),
        CurrencyInfo("FKP", "Falkland Islands Pound", "£"),
        CurrencyInfo("GEL", "Georgian Lari", "₾"),
        CurrencyInfo("GHS", "Ghanaian Cedi", "₵"),
        CurrencyInfo("GIP", "Gibraltar Pound", "£"),
        CurrencyInfo("GMD", "Gambian Dalasi", "D"),
        CurrencyInfo("GNF", "Guinean Franc", "FG"),
        CurrencyInfo("GTQ", "Guatemalan Quetzal", "Q"),
        CurrencyInfo("GYD", "Guyanaese Dollar", "\$"),
        CurrencyInfo("HNL", "Honduran Lempira", "L"),
        CurrencyInfo("HRK", "Croatian Kuna", "kn"),
        CurrencyInfo("HTG", "Haitian Gourde", "G"),
        CurrencyInfo("HUF", "Hungarian Forint", "Ft"),
        CurrencyInfo("IDR", "Indonesian Rupiah", "Rp"),
        CurrencyInfo("IQD", "Iraqi Dinar", "ع.د"),
        CurrencyInfo("IRR", "Iranian Rial", "﷼"),
        CurrencyInfo("ISK", "Icelandic Króna", "kr"),
        CurrencyInfo("JMD", "Jamaican Dollar", "J\$"),
        CurrencyInfo("JOD", "Jordanian Dinar", "JD"),
        CurrencyInfo("KES", "Kenyan Shilling", "KSh"),
        CurrencyInfo("KGS", "Kyrgystani Som", "лв"),
        CurrencyInfo("KHR", "Cambodian Riel", "៛"),
        CurrencyInfo("KMF", "Comorian Franc", "CF"),
        CurrencyInfo("KPW", "North Korean Won", "₩"),
        CurrencyInfo("KWD", "Kuwaiti Dinar", "KD"),
        CurrencyInfo("KYD", "Cayman Islands Dollar", "\$"),
        CurrencyInfo("KZT", "Kazakhstani Tenge", "₸"),
        CurrencyInfo("LAK", "Laotian Kip", "₭"),
        CurrencyInfo("LBP", "Lebanese Pound", "£"),
        CurrencyInfo("LKR", "Sri Lankan Rupee", "₨"),
        CurrencyInfo("LRD", "Liberian Dollar", "\$"),
        CurrencyInfo("LSL", "Lesotho Loti", "M"),
        CurrencyInfo("LYD", "Libyan Dinar", "LD"),
        CurrencyInfo("MAD", "Moroccan Dirham", "MAD"),
        CurrencyInfo("MDL", "Moldovan Leu", "L"),
        CurrencyInfo("MGA", "Malagasy Ariary", "Ar"),
        CurrencyInfo("MKD", "Macedonian Denar", "ден"),
        CurrencyInfo("MMK", "Myanmar Kyat", "K"),
        CurrencyInfo("MNT", "Mongolian Tugrik", "₮"),
        CurrencyInfo("MOP", "Macanese Pataca", "MOP\$"),
        CurrencyInfo("MRU", "Mauritanian Ouguiya", "UM"),
        CurrencyInfo("MUR", "Mauritian Rupee", "₨"),
        CurrencyInfo("MVR", "Maldivian Rufiyaa", "Rf"),
        CurrencyInfo("MWK", "Malawian Kwacha", "MK"),
        CurrencyInfo("MYR", "Malaysian Ringgit", "RM"),
        CurrencyInfo("MZN", "Mozambican Metical", "MT"),
        CurrencyInfo("NAD", "Namibian Dollar", "\$"),
        CurrencyInfo("NGN", "Nigerian Naira", "₦"),
        CurrencyInfo("NIO", "Nicaraguan Córdoba", "C\$"),
        CurrencyInfo("NPR", "Nepalese Rupee", "₨"),
        CurrencyInfo("OMR", "Omani Rial", "﷼"),
        CurrencyInfo("PAB", "Panamanian Balboa", "B/."),
        CurrencyInfo("PEN", "Peruvian Sol", "S/."),
        CurrencyInfo("PGK", "Papua New Guinean Kina", "K"),
        CurrencyInfo("PHP", "Philippine Peso", "₱"),
        CurrencyInfo("PKR", "Pakistani Rupee", "₨"),
        CurrencyInfo("PYG", "Paraguayan Guarani", "Gs"),
        CurrencyInfo("QAR", "Qatari Rial", "﷼"),
        CurrencyInfo("RON", "Romanian Leu", "lei"),
        CurrencyInfo("RSD", "Serbian Dinar", "Дин."),
        CurrencyInfo("RWF", "Rwandan Franc", "R₣"),
        CurrencyInfo("SAR", "Saudi Riyal", "﷼"),
        CurrencyInfo("SBD", "Solomon Islands Dollar", "\$"),
        CurrencyInfo("SCR", "Seychellois Rupee", "₨"),
        CurrencyInfo("SDG", "Sudanese Pound", "ج.س."),
        CurrencyInfo("SHP", "Saint Helena Pound", "£"),
        CurrencyInfo("SLL", "Sierra Leonean Leone", "Le"),
        CurrencyInfo("SOS", "Somali Shilling", "S"),
        CurrencyInfo("SRD", "Surinamese Dollar", "\$"),
        CurrencyInfo("STN", "São Tomé & Príncipe Dobra", "Db"),
        CurrencyInfo("SVC", "Salvadoran Colón", "\$"),
        CurrencyInfo("SYP", "Syrian Pound", "£"),
        CurrencyInfo("SZL", "Swazi Lilangeni", "E"),
        CurrencyInfo("TJS", "Tajikistani Somoni", "SM"),
        CurrencyInfo("TMT", "Turkmenistani Manat", "T"),
        CurrencyInfo("TND", "Tunisian Dinar", "د.ت"),
        CurrencyInfo("TOP", "Tongan Paʻanga", "T\$"),
        CurrencyInfo("TTD", "Trinidad & Tobago Dollar", "TT\$"),
        CurrencyInfo("TWD", "New Taiwan Dollar", "NT\$"),
        CurrencyInfo("TZS", "Tanzanian Shilling", "TSh"),
        CurrencyInfo("UAH", "Ukrainian Hryvnia", "₴"),
        CurrencyInfo("UGX", "Ugandan Shilling", "USh"),
        CurrencyInfo("UYU", "Uruguayan Peso", "\$U"),
        CurrencyInfo("UZS", "Uzbekistani Som", "лв"),
        CurrencyInfo("VES", "Venezuelan Bolívar", "Bs."),
        CurrencyInfo("VND", "Vietnamese Dong", "₫"),
        CurrencyInfo("VUV", "Vanuatu Vatu", "VT"),
        CurrencyInfo("WST", "Samoan Tala", "WS\$"),
        CurrencyInfo("XAF", "Central African CFA Franc", "FCFA"),
        CurrencyInfo("XCD", "East Caribbean Dollar", "\$"),
        CurrencyInfo("XDR", "Special Drawing Rights", "XDR"),
        CurrencyInfo("XOF", "West African CFA Franc", "CFA"),
        CurrencyInfo("XPF", "CFP Franc", "₣"),
        CurrencyInfo("YER", "Yemeni Rial", "﷼"),
        CurrencyInfo("ZMW", "Zambian Kwacha", "ZK")
    )

    fun getSymbol(code: String): String {
        return allCurrencies.find { it.code == code }?.symbol ?: code
    }

    fun search(query: String): List<CurrencyInfo> {
        if (query.isBlank()) return allCurrencies
        val q = query.lowercase()
        return allCurrencies.filter {
            it.code.lowercase().contains(q) || it.name.lowercase().contains(q)
        }
    }

    // Parse JSON array like ["USD","EUR","ILS"] - simple parser without external deps
    fun parseJsonArray(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            // Very simple parser: remove brackets, quotes, split by comma
            json.trim().removePrefix("[").removeSuffix("]")
                .split(",")
                .map { it.trim().trim('"', '\'', ' ') }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toJsonArray(codes: List<String>): String {
        return codes.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
    }

    fun getOrderedCurrencies(
        sortMode: String = "ALPHABETICAL",
        customOrderJson: String? = null,
        favoriteJson: String? = null,
        recentJson: String? = null,
        balanceCurrency: String? = null
    ): List<CurrencyInfo> {
        val customOrder = parseJsonArray(customOrderJson)
        val favorites = parseJsonArray(favoriteJson).toSet()
        val recents = parseJsonArray(recentJson)

        // Base list
        var list = allCurrencies.toList()

        // Apply sort mode
        list = when (sortMode) {
            "CUSTOM" -> {
                if (customOrder.isNotEmpty()) {
                    val orderMap = customOrder.withIndex().associate { it.value to it.index }
                    list.sortedWith(compareBy(
                        { orderMap[it.code] ?: Int.MAX_VALUE },
                        { it.code }
                    ))
                } else {
                    list.sortedBy { it.code }
                }
            }
            "RECENT" -> {
                if (recents.isNotEmpty()) {
                    val recentMap = recents.withIndex().associate { it.value to it.index }
                    list.sortedWith(compareBy(
                        { recentMap[it.code] ?: Int.MAX_VALUE },
                        { it.code }
                    ))
                } else {
                    list.sortedBy { it.code }
                }
            }
            "FAVORITES" -> {
                list.sortedWith(compareBy(
                    { if (it.code in favorites) 0 else 1 },
                    { it.code }
                ))
            }
            else -> { // ALPHABETICAL
                list.sortedBy { it.code }
            }
        }

        // Always put balance currency first, then favorites, then rest (if not already handled)
        if (balanceCurrency != null && sortMode != "CUSTOM" && sortMode != "RECENT") {
            val balanceFirst = mutableListOf<CurrencyInfo>()
            val favList = mutableListOf<CurrencyInfo>()
            val rest = mutableListOf<CurrencyInfo>()

            for (c in list) {
                when {
                    c.code == balanceCurrency -> balanceFirst.add(c)
                    c.code in favorites -> favList.add(c)
                    else -> rest.add(c)
                }
            }
            // If balance currency is also favorite, keep it first only once
            return balanceFirst + favList + rest
        }

        // For custom/recent, if favorites exist, put favorites at top within that order? No, respect custom order but favorites first
        if (favorites.isNotEmpty() && (sortMode == "CUSTOM" || sortMode == "RECENT")) {
            val favOrdered = list.filter { it.code in favorites }
            val nonFav = list.filter { it.code !in favorites }
            return favOrdered + nonFav
        }

        return list
    }

    fun addRecentCurrency(recentJson: String?, currencyCode: String, maxSize: Int = 10): String {
        val current = parseJsonArray(recentJson).toMutableList()
        current.remove(currencyCode)
        current.add(0, currencyCode)
        return toJsonArray(current.take(maxSize))
    }
}
