package com.pesaflow.app.data.ml

/**
 * Expense CATEGORY classifier. Label set = spec section 8 Model 2
 * adapted to app-canonical strings (Electricity, Water, School kept from
 * MpesaParser.inferCategory; UTILITIES/EDUCATION folded in to avoid
 * sibling-label overlap) plus Kenyan specializations (Clothes, Kujibamba,
 * HELB, Parent, Business) and new spec labels (GROCERIES split from
 * SHOPPING, SUBSCRIPTIONS, FEES, ENTERTAINMENT).
 *
 * MerchantDictionary lookup runs FIRST (deterministic, high precision);
 * this classifier handles the tail. Prediction never writes to the ledger.
 *
 * Model card: CAT_CLF v2, TF-IDF unigrams+bigrams + NB. Metrics gate in
 * MlMetricsTest.
 */
object CategoryClassifier {
    private val classifier = TfIdfClassifier()
    private var ready = false

    val trainingData: List<TfIdfClassifier.Labeled> = listOf(
        // FOOD — cooked / eaten out (verified mess prices 2023-2025)
        TfIdfClassifier.Labeled("Java House lunch chips sausage 120", "FOOD"),
        TfIdfClassifier.Labeled("smocha chapati madondo kibanda 70", "FOOD"),
        TfIdfClassifier.Labeled("pilau ugali sukuma githeri mess 40", "FOOD"),
        TfIdfClassifier.Labeled("restaurant dinner nyama choma", "FOOD"),
        TfIdfClassifier.Labeled("breakfast mandazi chai kiosk 10", "FOOD", true),
        TfIdfClassifier.Labeled("chafua chapati beans 90 MMU", "FOOD"),
        TfIdfClassifier.Labeled("chapo choma nylon 40", "FOOD"),
        TfIdfClassifier.Labeled("ugali pambana bones soup 100 KU", "FOOD"),
        TfIdfClassifier.Labeled("soda 50 eatery", "FOOD"),
        TfIdfClassifier.Labeled("kula chakula sherehe lunch", "FOOD", true),
        // GROCERIES — raw / cook-at-home (split from SHOPPING)
        TfIdfClassifier.Labeled("Mama Mboga mboga sukuma 10", "GROCERIES"),
        TfIdfClassifier.Labeled("butchery beef 70 minced 50", "GROCERIES"),
        TfIdfClassifier.Labeled("market tomatoes onions kachumbari", "GROCERIES", true),
        TfIdfClassifier.Labeled("unga maize flour cooking oil", "GROCERIES", true),
        TfIdfClassifier.Labeled("duka rice beans cooking", "GROCERIES", true),
        // TRANSPORT — grounded fare bands
        TfIdfClassifier.Labeled("matatu fare stage Embassava 80", "TRANSPORT"),
        TfIdfClassifier.Labeled("boda fare 150 nauli", "TRANSPORT"),
        TfIdfClassifier.Labeled("Uber ride Westlands 450", "TRANSPORT"),
        TfIdfClassifier.Labeled("Bolt trip 300", "TRANSPORT"),
        TfIdfClassifier.Labeled("Easy Coach Nairobi Eldoret 1550", "TRANSPORT"),
        TfIdfClassifier.Labeled("parking fee 100", "TRANSPORT", true),
        TfIdfClassifier.Labeled("Ndebe faster matatu 120 rush", "TRANSPORT", true),
        TfIdfClassifier.Labeled("shuttle Great Rift 1200", "TRANSPORT"),
        TfIdfClassifier.Labeled("SGR Madaraka express ticket", "TRANSPORT", true),
        // SHOPPING — supermarket / retail non-food
        TfIdfClassifier.Labeled("Naivas supermarket shopping 2300", "SHOPPING"),
        TfIdfClassifier.Labeled("Quickmart shopping nunua hotdog", "SHOPPING"),
        TfIdfClassifier.Labeled("Carrefour electronics GTC", "SHOPPING"),
        TfIdfClassifier.Labeled("China Square household", "SHOPPING", true),
        TfIdfClassifier.Labeled("Panda Mart Garden City", "SHOPPING", true),
        // AIRTIME / DATA
        TfIdfClassifier.Labeled("Safaricom airtime credo 50", "AIRTIME"),
        TfIdfClassifier.Labeled("Okoa Jahazi emergency credit", "AIRTIME"),
        TfIdfClassifier.Labeled("bonga points redeem", "AIRTIME", true),
        TfIdfClassifier.Labeled("Airtel 220220 airtime topup", "AIRTIME"),
        TfIdfClassifier.Labeled("Faiba bundles 1GB data 330330", "DATA"),
        TfIdfClassifier.Labeled("Zuku wifi fibre monthly", "DATA"),
        TfIdfClassifier.Labeled("unliminet net modem", "DATA", true),
        TfIdfClassifier.Labeled("Safaricom Postpay bundles 898998", "DATA"),
        // RENT / UTILITIES + Kenyan splits
        TfIdfClassifier.Labeled("hostel rent pango nyumba 4500", "RENT"),
        TfIdfClassifier.Labeled("landlord caretaker house agent bedsitter", "RENT"),
        TfIdfClassifier.Labeled("Meru hostel 5500 semester", "RENT"),
        TfIdfClassifier.Labeled("rent bedsitter 8000 monthly Donholm", "RENT", true),
        TfIdfClassifier.Labeled("KPLC tokens stima 500 888880", "Electricity"),
        TfIdfClassifier.Labeled("KPLC postpaid 888888 bill", "Electricity"),
        TfIdfClassifier.Labeled("electricity bill token meter", "Electricity", true),
        TfIdfClassifier.Labeled("stima prepaid vending", "Electricity", true),
        TfIdfClassifier.Labeled("Nairobi Water bill maji plot", "Water"),
        TfIdfClassifier.Labeled("water vendor bill county", "Water", true),
        TfIdfClassifier.Labeled("water bowser refill 500 estate", "Water", true),
        TfIdfClassifier.Labeled("water kiosk 20 litres 50", "Water", true),
        // EDUCATION (School is the app-canonical label; eCitizen invoicing folds in)
        TfIdfClassifier.Labeled("school fees tuition exam shule", "School"),
        TfIdfClassifier.Labeled("books kalamu stationery assignment", "School"),
        TfIdfClassifier.Labeled("eCitizen 222222 university invoice", "School", true),
        TfIdfClassifier.Labeled("cyber printing photocopy assignment", "Printing"),
        TfIdfClassifier.Labeled("exam registration KNEC fee", "School", true),
        TfIdfClassifier.Labeled("attachment fee industrial training", "School", true),
        TfIdfClassifier.Labeled("photocopy notes 200 pages cyber", "Printing", true),
        TfIdfClassifier.Labeled("library fine overdue book", "School", true),
        // HEALTH
        TfIdfClassifier.Labeled("hospital clinic pharmacy dawa SHA 200222", "Health"),
        TfIdfClassifier.Labeled("chemist medicine daktari", "Health", true),
        TfIdfClassifier.Labeled("consultation fee clinic 500", "Health", true),
        TfIdfClassifier.Labeled("lab test malaria typhoid 800", "Health", true),
        // CLOTHES / KUJIBAMBA / ENTERTAINMENT
        TfIdfClassifier.Labeled("shirt jeans nguo kiatu clothes Maasai Market", "Clothes"),
        TfIdfClassifier.Labeled("mitumba secondhand jacket Gikomba", "Clothes", true),
        TfIdfClassifier.Labeled("tailor repair stitching 200", "Clothes", true),
        TfIdfClassifier.Labeled("mitumba dress 350 Gikomba", "Clothes", true),
        TfIdfClassifier.Labeled("salon kinyozi hair nails plot", "Kujibamba"),
        TfIdfClassifier.Labeled("nywele retouch blowdry 800", "Kujibamba", true),
        TfIdfClassifier.Labeled("sherehe plot weekend plan", "Kujibamba", true),
        TfIdfClassifier.Labeled("manicure pedicure 600", "Kujibamba", true),
        TfIdfClassifier.Labeled("movie Two Rivers cinema", "ENTERTAINMENT", true),
        TfIdfClassifier.Labeled("game football viewing 100", "ENTERTAINMENT", true),
        TfIdfClassifier.Labeled("concert ticket 1500", "ENTERTAINMENT", true),
        TfIdfClassifier.Labeled("swimming pool entry 300", "ENTERTAINMENT", true),
        // SUBSCRIPTIONS — new spec label
        TfIdfClassifier.Labeled("Netflix monthly subscription 1500", "SUBSCRIPTIONS"),
        TfIdfClassifier.Labeled("Spotify premium 400", "SUBSCRIPTIONS", true),
        TfIdfClassifier.Labeled("Showmax 450", "SUBSCRIPTIONS", true),
        TfIdfClassifier.Labeled("DSTV Gotv monthly bouquet", "SUBSCRIPTIONS", true),
        TfIdfClassifier.Labeled("Starlink monthly 6500", "SUBSCRIPTIONS", true),
        TfIdfClassifier.Labeled("Mdundo VIP music 200", "SUBSCRIPTIONS", true),
        // DEBT / SAVINGS / FEES
        TfIdfClassifier.Labeled("Tala loan repayment Branch Zenka", "Debt"),
        TfIdfClassifier.Labeled("Fuliza outstanding deni Hustler Fund", "Debt"),
        TfIdfClassifier.Labeled("HELB repayment 200800", "Debt"),
        TfIdfClassifier.Labeled("loan repayment deadline Tala Branch", "Debt", true),
        TfIdfClassifier.Labeled("chama sacco mshwari save", "Savings"),
        TfIdfClassifier.Labeled("NSSF 333300 pension", "Savings", true),
        TfIdfClassifier.Labeled("savings group contribution weekly", "Savings", true),
        TfIdfClassifier.Labeled("eCitizen convenience fee 50 meals", "FEES"),
        TfIdfClassifier.Labeled("transaction cost service charge", "FEES", true),
        TfIdfClassifier.Labeled("ATM withdrawal charge", "FEES", true),
        // INCOME-side labels
        TfIdfClassifier.Labeled("mum dad upkeep allowance parent", "Parent"),
        TfIdfClassifier.Labeled("HELB upkeep disbursed semester", "HELB"),
        TfIdfClassifier.Labeled("Pochi business income", "Business"),
        TfIdfClassifier.Labeled("unknown miscellaneous", "OTHER"),
        TfIdfClassifier.Labeled("sponsor upkeep guardian support", "Parent", true),
        TfIdfClassifier.Labeled("HELB loan batch arriving", "HELB", true),
        TfIdfClassifier.Labeled("biashara profit chama payout", "Business", true),
        TfIdfClassifier.Labeled("random unclassified entry", "OTHER", true),
        TfIdfClassifier.Labeled("wazazi upkeep monthly support", "Parent", true),
        TfIdfClassifier.Labeled("HELB upkeep comrades disbursement", "HELB", true),
        TfIdfClassifier.Labeled("hustle income kibarua payout", "Business", true),
        TfIdfClassifier.Labeled("miscellaneous uncategorized spend", "OTHER", true),
        // v3 growth: groceries/food boundary, subscriptions, fees, Sheng transport, health
        TfIdfClassifier.Labeled("kiosk sukuma spinach tomatoes 80", "GROCERIES", true),
        TfIdfClassifier.Labeled("cereals beans 2kg duka", "GROCERIES", true),
        TfIdfClassifier.Labeled("maziwa milk 60 duka", "GROCERIES", true),
        TfIdfClassifier.Labeled("lunch pilau beans 150 kibanda", "FOOD", true),
        TfIdfClassifier.Labeled("supper chips mayai 120", "FOOD", true),
        TfIdfClassifier.Labeled("youtube premium monthly 600", "SUBSCRIPTIONS", true),
        TfIdfClassifier.Labeled("netflix shared 300 monthly", "SUBSCRIPTIONS", true),
        TfIdfClassifier.Labeled("paybill transaction fee 23 deducted", "FEES", true),
        TfIdfClassifier.Labeled("agent withdrawal fee 32", "FEES", true),
        TfIdfClassifier.Labeled("nimetuma nauli 100 roysambu", "TRANSPORT", true),
        TfIdfClassifier.Labeled("fare githurai 60 offpeak", "TRANSPORT", true),
        TfIdfClassifier.Labeled("dispensary prescription drugs 350", "Health", true),
        // v4 growth: single-sample labels to >=8 (DATA, Debt, ENTERTAINMENT, Health, SHOPPING, Savings)
        TfIdfClassifier.Labeled("safaricom data bundles 500MB 99", "DATA", true),
        TfIdfClassifier.Labeled("airtel smart connect data 2GB", "DATA", true),
        TfIdfClassifier.Labeled("telkom monthly data 5GB", "DATA", true),
        TfIdfClassifier.Labeled("faiba 8GB weekly 300", "DATA", true),
        TfIdfClassifier.Labeled("starlink data topup voucher", "DATA", true),
        TfIdfClassifier.Labeled("branch loan due 1500 repay", "Debt", true),
        TfIdfClassifier.Labeled("deni ya fuliza 700 lipa", "Debt", true),
        TfIdfClassifier.Labeled("tala repayment overdue notice", "Debt", true),
        TfIdfClassifier.Labeled("hustler fund loan balance 900", "Debt", true),
        TfIdfClassifier.Labeled("zenka loan installment 1200", "Debt", true),
        TfIdfClassifier.Labeled("cinema ticket 800 imax", "ENTERTAINMENT", true),
        TfIdfClassifier.Labeled("ps5 gaming lounge 200 hour", "ENTERTAINMENT", true),
        TfIdfClassifier.Labeled("concert early bird 2500", "ENTERTAINMENT", true),
        TfIdfClassifier.Labeled("netflix watch party snacks 500", "ENTERTAINMENT", true),
        TfIdfClassifier.Labeled("karaoke night entry 300", "ENTERTAINMENT", true),
        TfIdfClassifier.Labeled("agakhan hospital bill 2500", "Health", true),
        TfIdfClassifier.Labeled("nhif sha contribution receipt", "Health", true),
        TfIdfClassifier.Labeled("optician glasses 4000", "Health", true),
        TfIdfClassifier.Labeled("dental cleaning 3000", "Health", true),
        TfIdfClassifier.Labeled("maternity clinic visit 1500", "Health", true),
        TfIdfClassifier.Labeled("naivas home appliances 12000", "SHOPPING", true),
        TfIdfClassifier.Labeled("textiles mattress blankets 8000", "SHOPPING", true),
        TfIdfClassifier.Labeled("phone cover charger ole serai", "SHOPPING", true),
        TfIdfClassifier.Labeled("kitchenware sufuria set 3500", "SHOPPING", true),
        TfIdfClassifier.Labeled("shoes bata back to school", "SHOPPING", true),
        TfIdfClassifier.Labeled("piggy bank deposit 500 locked", "Savings", true),
        TfIdfClassifier.Labeled("equity goal account transfer", "Savings", true),
        TfIdfClassifier.Labeled("kcb cub account junior save", "Savings", true),
        TfIdfClassifier.Labeled("mali savings interest payout", "Savings", true),
        TfIdfClassifier.Labeled("lock savings 3000 december", "Savings", true),
        // v4 growth: FOOD vs GROCERIES boundary (cooked markers vs raw markers)
        TfIdfClassifier.Labeled("fried rice chips masala 200 served", "FOOD", true),
        TfIdfClassifier.Labeled("waiter bill nyama choma platter", "FOOD", true),
        TfIdfClassifier.Labeled("takeaway burger fries 450", "FOOD", true),
        TfIdfClassifier.Labeled("hotel breakfast buffet 600", "FOOD", true),
        TfIdfClassifier.Labeled("raw maize beans sack 90kg", "GROCERIES", true),
        TfIdfClassifier.Labeled("fresh tilapia market 400", "GROCERIES", true),
        TfIdfClassifier.Labeled("spices dhania pilipili market", "GROCERIES", true),
        TfIdfClassifier.Labeled("eggs tray 30 pieces 450", "GROCERIES", true),
        // v4 growth: TRANSPORT canonical + Sheng, FEES tails, AIRTIME topups
        TfIdfClassifier.Labeled("matatu fare kayole 70", "TRANSPORT", true),
        TfIdfClassifier.Labeled("boda nauli dagoretti 200", "TRANSPORT", true),
        TfIdfClassifier.Labeled("mpesa send money charge 28", "FEES", true),
        TfIdfClassifier.Labeled("bank ledger fee 150 monthly", "FEES", true),
        TfIdfClassifier.Labeled("okoa jahazi 100 topup", "AIRTIME", true),
        TfIdfClassifier.Labeled("safaricom airtime 20 please", "AIRTIME", true),
        TfIdfClassifier.Labeled("bonga points redeem airtime", "AIRTIME", true),
        // v5 growth: residual zeros (Health/SHOPPING) + FOOD-precision + SUBSCRIPTIONS
        TfIdfClassifier.Labeled("eye test optician 1200", "Health", true),
        TfIdfClassifier.Labeled("yellow fever vaccination certificate", "Health", true),
        TfIdfClassifier.Labeled("physiotherapy session 2000", "Health", true),
        TfIdfClassifier.Labeled("blood pressure checkup clinic", "Health", true),
        TfIdfClassifier.Labeled("laptop charger replacement 2500", "SHOPPING", true),
        TfIdfClassifier.Labeled("bedsheet duvet set 4500", "SHOPPING", true),
        TfIdfClassifier.Labeled("plastic chairs set home", "SHOPPING", true),
        TfIdfClassifier.Labeled("washing machine repair 3000", "SHOPPING", true),
        TfIdfClassifier.Labeled("disney plus monthly 1100", "SUBSCRIPTIONS", true),
        TfIdfClassifier.Labeled("prime video 900 monthly", "SUBSCRIPTIONS", true),
        TfIdfClassifier.Labeled("boomplay subscription 250", "SUBSCRIPTIONS", true)
    )

    private fun ensureTrained() {
        if (!ready) {
            classifier.train(trainingData)
            ready = true
        }
    }

    /**
     * Versioned weight import (spec §18 retrain path). See TransactionClassifier.
     */
    fun loadWeights(blob: String) {
        classifier.deserialize(blob)
        ready = true
    }

    /**
     * Dictionary first (precision), classifier second (recall).
     * Returns (category, confidence, source).
     */
    fun classify(merchant: String, text: String): Triple<String, Double, String> {
        MerchantDictionary.lookup(merchant)?.let { return Triple(it.category, 0.95, "MERCHANT_DICT") }
        MerchantDictionary.fuzzy(merchant)?.let { return Triple(it.category, 0.80, "MERCHANT_FUZZY") }
        ensureTrained()
        val top = classifier.predict("$merchant $text").firstOrNull()
        return if (top != null) Triple(top.label, top.probability, "CAT_CLF_v1")
        else Triple("Other", 0.0, "FALLBACK")
    }
}
