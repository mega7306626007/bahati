package com.pesaflow.app.data.ml

/**
 * Dataset manifests (spec section 16). Every training dataset declares
 * schema version, source, collection date, label provenance, preprocessing
 * version, synthetic-data flagging, training eligibility and privacy class.
 * Synthetic examples are permitted for augmentation but must be identified —
 * they are, via Labeled.synthetic, and real-only test size is reported.
 */
data class DatasetManifest(
    val id: String,
    val schemaVersion: Int,
    val source: String,
    val collectionDate: String,
    val labelProvenance: String,
    val preprocessingVersion: String,
    val totalExamples: Int,
    val syntheticExamples: Int,
    val trainingEligible: Boolean,
    val privacyClass: String
) {
    val realExamples: Int get() = totalExamples - syntheticExamples
}

object DatasetManifests {
    fun typeDataset(examples: List<TfIdfClassifier.Labeled>) = DatasetManifest(
        id = "A_transaction_classification",
        schemaVersion = 2,
        source = "parser_audit_MpesaParser_routing + Safaricom_docs + reply.cash + Medium_kaluka_wanjala + paybillke_verified_paybills",
        collectionDate = "2026-09-30",
        labelProvenance = "RULE_DERIVED_FROM_PARSER_ORDER + HUMAN_VERIFIED_TEMPLATES",
        preprocessingVersion = "uni_bigram_lower_alnum_v2",
        totalExamples = examples.size,
        syntheticExamples = examples.count { it.synthetic },
        trainingEligible = true,
        privacyClass = "NO_PII_TEMPLATES_ONLY"
    )

    fun categoryDataset(examples: List<TfIdfClassifier.Labeled>) = DatasetManifest(
        id = "B_expense_category_classification",
        schemaVersion = 2,
        source = "parser_audit_inferCategory + NLP_catKeywords + Cytonn_Retail_2024 + campus_food_prices_Tuko_Citizen_2023_2025",
        collectionDate = "2026-09-30",
        labelProvenance = "RULE_DERIVED + HUMAN_VERIFIED_MERCHANTS",
        preprocessingVersion = "uni_bigram_lower_alnum_v2",
        totalExamples = examples.size,
        syntheticExamples = examples.count { it.synthetic },
        trainingEligible = true,
        privacyClass = "NO_PII_TEMPLATES_ONLY"
    )

    fun intentDataset(examples: List<TfIdfClassifier.Labeled>) = DatasetManifest(
        id = "I_intent_classification",
        schemaVersion = 2,
        source = "spec_section_17_examples + Sheng_Swahili_paraphrases",
        collectionDate = "2026-09-30",
        labelProvenance = "SPEC_ANNOTATION + HUMAN_PARAPHRASE",
        preprocessingVersion = "uni_bigram_lower_alnum_v2",
        totalExamples = examples.size,
        syntheticExamples = examples.count { it.synthetic },
        trainingEligible = true,
        privacyClass = "NO_PII_TEMPLATES_ONLY"
    )

    val merchantCatalogue = DatasetManifest(
        id = "C_merchant_normalization",
        schemaVersion = 2,
        source = "parser_audit + Cytonn_Retail_2024 + Tuko_2025 + paybillke_verified + Safaricom_bank_codes_PDF + KNBS_CPI_Dec2024 + KenyaHub_fares",
        collectionDate = "2026-09-30",
        labelProvenance = "PUBLIC_DATASET + HUMAN_VERIFIED",
        preprocessingVersion = "alias_index_exact_plus_levenshtein2",
        totalExamples = MerchantDictionary.entries.sumOf { it.aliases.size },
        syntheticExamples = 0,
        trainingEligible = true,
        privacyClass = "PUBLIC_ONLY"
    )

    val localKnowledge = DatasetManifest(
        id = "D_local_knowledge",
        schemaVersion = 1,
        source = "CUE_2025_university_list + KU_mess_pricelist_2023 + Citizen_MMU_prices_2023 + MwingiTimes_2025 + eCitizen_UoN_memo_2024",
        collectionDate = "2026-09-30",
        labelProvenance = "PUBLIC_DATASET_NEWS_VERIFIED",
        preprocessingVersion = "v1",
        totalExamples = LocalKnowledge.universities.size + LocalKnowledge.foodPrices.size + LocalKnowledge.fareBands.size,
        syntheticExamples = 0,
        trainingEligible = false,
        privacyClass = "PUBLIC_ONLY"
    )
}
