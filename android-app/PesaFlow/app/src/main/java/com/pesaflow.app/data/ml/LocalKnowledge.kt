package com.pesaflow.app.data.ml

/**
 * LocalKnowledge — bundled offline catalogue (spec Layer D).
 *
 * Sourced from web research 2026-09-30, every fact carries provenance +
 * verification date. Stale data is labelled, never presented as live:
 * - Universities: CUE approved list Nov 2025 (35 public chartered,
 *   30 private chartered) + county where published.
 * - Food prices: KU mess pricelist memo Aug 2023 (Tuko, Kenyans.co.ke),
 *   MMU eatery prices Sep 2023 (Citizen Digital), campus meal bands
 *   May 2025 (Mwingi Times), UoN eCitizen meal payment Feb 2024 (Tuko).
 * - Fare bands: KenyaHub 2026, KNBS CPI Dec 2024, CBD stage guide 2025.
 * - Paybills: paybillke verified 2026, KPLC vendor list, Safaricom bank
 *   codes PDF. NOTE: NHIF 200222 is DEFUNCT — replaced by SHA Oct 2024.
 */
object LocalKnowledge {
    data class University(val name: String, val short: String, val county: String, val public: Boolean)
    data class FoodPrice(val item: String, val priceKes: Double, val venue: String, val verified: String)
    data class FareBand(val route: String, val lowKes: Double, val highKes: Double, val source: String)
    data class Paybill(val owner: String, val number: String, val accountHint: String, val status: String)

    val universities: List<University> = listOf(
        University("University of Nairobi", "UoN", "Nairobi", true),
        University("Kenyatta University", "KU", "Nairobi", true),
        University("Moi University", "MU", "Uasin Gishu", true),
        University("Egerton University", "EU", "Nakuru", true),
        University("Jomo Kenyatta University of Agriculture and Technology", "JKUAT", "Kiambu", true),
        University("Maseno University", "MSU", "Kisumu", true),
        University("Masinde Muliro University of Science and Technology", "MMUST", "Kakamega", true),
        University("Dedan Kimathi University of Technology", "DKUT", "Nyeri", true),
        University("Chuka University", "CU", "Tharaka Nithi", true),
        University("Technical University of Kenya", "TUK", "Nairobi", true),
        University("Technical University of Mombasa", "TUM", "Mombasa", true),
        University("Pwani University", "PU", "Kilifi", true),
        University("Kisii University", "KSU", "Kisii", true),
        University("University of Eldoret", "UoE", "Uasin Gishu", true),
        University("Maasai Mara University", "MMara", "Narok", true),
        University("Jaramogi Oginga Odinga University of Science and Technology", "JOOUST", "Siaya", true),
        University("Laikipia University", "LU", "Laikipia", true),
        University("South Eastern Kenya University", "SEKU", "Kitui", true),
        University("Meru University of Science and Technology", "MUST", "Meru", true),
        University("Multimedia University of Kenya", "MMU", "Nairobi", true),
        University("University of Kabianga", "UoK", "Kericho", true),
        University("Karatina University", "KarU", "Nyeri", true),
        University("Kibabii University", "KIBU", "Bungoma", true),
        University("Rongo University", "RU", "Migori", true),
        University("Co-operative University of Kenya", "CUK", "Nairobi", true),
        University("Taita Taveta University", "TTU", "Taita Taveta", true),
        University("Murang'a University of Technology", "MUT", "Murang'a", true),
        University("University of Embu", "UoEm", "Embu", true),
        University("Machakos University", "MachU", "Machakos", true),
        University("Kirinyaga University", "KyU", "Kirinyaga", true),
        University("Garissa University", "GU", "Garissa", true),
        University("Alupe University", "AU", "Busia", true),
        University("Kaimosi Friends University", "KAFU", "Vihiga", true),
        University("Tom Mboya University", "TMU", "Homa Bay", true),
        University("Tharaka University", "THU", "Tharaka Nithi", true),
        University("Strathmore University", "SU", "Nairobi", false),
        University("Daystar University", "DU", "Nairobi", false),
        University("Mount Kenya University", "MKU", "Kiambu", false),
        University("KCA University", "KCA", "Nairobi", false),
        University("Zetech University", "ZU", "Nairobi", false),
        University("Catholic University of Eastern Africa", "CUEA", "Nairobi", false),
        University("United States International University", "USIU", "Nairobi", false),
        University("Kabarak University", "KABU", "Nakuru", false)
    )

    /** Verified mess/eatery prices — venue + date attached, never claimed live. */
    val foodPrices: List<FoodPrice> = listOf(
        FoodPrice("Chapati", 15.0, "KU mess", "2023-08 KU registrar memo"),
        FoodPrice("Mandazi", 10.0, "KU mess", "2023-08 KU registrar memo"),
        FoodPrice("African tea", 20.0, "KU mess", "2023-08 KU registrar memo"),
        FoodPrice("Ugali", 25.0, "KU mess", "2023-08 KU registrar memo"),
        FoodPrice("Sukuma wiki", 10.0, "KU mess", "2023-08 KU registrar memo"),
        FoodPrice("Beans", 25.0, "KU mess", "2023-08 KU registrar memo"),
        FoodPrice("Rice", 25.0, "KU mess", "2023-08 KU registrar memo"),
        FoodPrice("Githeri", 40.0, "KU mess", "2023-08 KU registrar memo"),
        FoodPrice("Beef", 70.0, "KU mess", "2023-08 KU registrar memo"),
        FoodPrice("Chicken + fries", 100.0, "KU mess", "2023-08 KU registrar memo"),
        FoodPrice("Milk packet", 60.0, "KU mess", "2023-08 KU registrar memo"),
        FoodPrice("Ugali pambana", 100.0, "KU", "2023-10 poster"),
        FoodPrice("Chips", 100.0, "MMU eateries", "2023-09 Citizen"),
        FoodPrice("Chapati + beans/ndengu", 90.0, "MMU eateries", "2023-09 Citizen"),
        FoodPrice("Ugali + sukuma + eggs/omena", 110.0, "MMU eateries", "2023-09 Citizen"),
        FoodPrice("Soda", 50.0, "MMU eateries", "2023-09 Citizen"),
        FoodPrice("Chapo choma (2 chapati + beans)", 40.0, "Nairobi campuses", "2023-10 Pulse"),
        FoodPrice("Smocha", 70.0, "Campus gates", "2023-10 Pulse"),
        FoodPrice("Full mess lunch", 150.0, "Typical public mess", "2025-05 Mwingi Times band 80-150"),
        FoodPrice("Cheapest cafeteria meal", 70.0, "Meru University", "2023-08 student leader")
    )

    val fareBands: List<FareBand> = listOf(
        FareBand("CBD-Westlands", 50.0, 100.0, "KenyaHub 2026"),
        FareBand("CBD-Thika Rd (Githurai)", 80.0, 150.0, "matatu guide 2025"),
        FareBand("CBD-Mombasa Rd (Embakasi)", 70.0, 120.0, "matatu guide 2025"),
        FareBand("CBD-Ngong Rd/Karen", 60.0, 100.0, "matatu guide 2025"),
        FareBand("CBD-Eastlands", 50.0, 80.0, "matatu guide 2025"),
        FareBand("CBD-Rongai", 80.0, 100.0, "AfroTools 2026"),
        FareBand("CBD-Kikuyu", 60.0, 80.0, "AfroTools 2026"),
        FareBand("Pipeline-CBD off-peak", 30.0, 70.0, "BusinessThisDay 2022"),
        FareBand("Pipeline-CBD rush", 80.0, 120.0, "BusinessThisDay 2022"),
        FareBand("Nairobi-Eldoret shuttle", 1200.0, 1550.0, "Mnetizen 2025"),
        FareBand("Nairobi-Nakuru shuttle", 600.0, 600.0, "Mnetizen 2025"),
        FareBand("Daily CBD return (student)", 100.0, 300.0, "AfroTools 2026")
    )

    val paybills: List<Paybill> = listOf(
        Paybill("KPLC Prepaid tokens", "888880", "meter number", "VERIFIED KPLC vendor list"),
        Paybill("KPLC Postpaid", "888888", "account number", "VERIFIED KPLC vendor list"),
        Paybill("KPLC New connection", "888899", "application ref", "VERIFIED KPLC vendor list"),
        Paybill("eCitizen / Government single", "222222", "invoice ref", "VERIFIED Gazette 16008/2022"),
        Paybill("SHA health", "200222", "national ID", "VERIFIED replaces NHIF Oct 2024"),
        Paybill("HELB repayment", "200800", "national ID / HELB no", "VERIFIED paybillke 2026"),
        Paybill("NSSF pension", "333300", "NSSF number", "VERIFIED paybillke 2026"),
        Paybill("Huduma Kenya", "191919", "Huduma ref", "VERIFIED paybillke 2026"),
        Paybill("Safaricom Postpaid", "200200", "phone number", "VERIFIED KulmiPay"),
        Paybill("Safaricom Postpay bundles", "898998", "phone number", "VERIFIED paybillke 2026"),
        Paybill("KCB M-Pesa", "522522", "account", "VERIFIED Safaricom bank codes PDF"),
        Paybill("Airtel airtime", "220220", "phone number", "VERIFIED transfer.co.ke"),
        Paybill("UoN meals", "222222", "OUTLETID-name e.g. NUK1-name", "VERIFIED UoN memo Feb 2024"),
        Paybill("NHIF", "200222", "DEFUNCT", "DEFUNCT replaced by SHA 2024-10-01")
    )

    fun findUniversity(query: String): University? {
        val q = query.lowercase()
        return universities.firstOrNull {
            it.name.lowercase().contains(q) || it.short.lowercase() == q || q.contains(it.short.lowercase())
        }
    }

    fun mealBudgetOptions(maxKes: Double): List<FoodPrice> =
        foodPrices.filter { it.priceKes <= maxKes }.sortedBy { it.priceKes }
}
