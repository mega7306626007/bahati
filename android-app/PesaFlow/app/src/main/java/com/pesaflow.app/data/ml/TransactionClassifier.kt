package com.pesaflow.app.data.ml

/**
 * Transaction TYPE classifier: EXPENSE / INCOME / TRANSFER / REFUND /
 * REVERSAL / FEE / WITHDRAWAL / DEPOSIT / BILL / SAVING / OTHER.
 *
 * Training data: ~100 labelled examples from the parser audit
 * (MpesaParser routing order) + M-Pesa SMS templates verified via web
 * (Safaricom docs, reply.cash, Medium kaluka wanjala, paybillke 2026,
 * eCitizen fee table Feb 2024). synthetic=true marks paraphrases written
 * for augmentation, never claimed as real traffic.
 *
 * BILL vs EXPENSE: paybill-with-account (KPLC 888880, eCitizen 222222,
 * SHA 200222, HELB 200800) is a BILL; till/merchant without account is
 * EXPENSE. FEE vs EXPENSE: transaction-cost tails are FEE. WITHDRAWAL vs
 * EXPENSE: agent/ATM cash-out is WITHDRAWAL. DEPOSIT vs INCOME: agent
 * cash-in is DEPOSIT. REFUND vs REVERSAL: merchant cashback is REFUND,
 * network-level undo is REVERSAL.
 *
 * Model card: TYPE_CLF v2, TF-IDF unigrams+bigrams + NB, lazy-trained.
 * Metrics gate in MlMetricsTest (macro-F1 + confusion review).
 */
object TransactionClassifier {
    private val classifier = TfIdfClassifier()
    private var ready = false

    val trainingData: List<TfIdfClassifier.Labeled> = listOf(
        // EXPENSE — verified SMS templates (P2P, till, merchant, airtime variants)
        TfIdfClassifier.Labeled("You have sent KSh 500 to John Doe on 12/9/26", "EXPENSE"),
        TfIdfClassifier.Labeled("KSh 250 paid to Java House", "EXPENSE"),
        TfIdfClassifier.Labeled("You bought KSh 50 of Airtime", "EXPENSE"),
        TfIdfClassifier.Labeled("KSh 150 paid to Till 54321 Mama Mboga", "EXPENSE"),
        TfIdfClassifier.Labeled("KSh 100 sent to Mama Mboga Pochi", "EXPENSE"),
        TfIdfClassifier.Labeled("sent KSh 20 worth of airtime to 0712345678", "EXPENSE"),
        TfIdfClassifier.Labeled("bought 1GB bundles for KSh 100", "EXPENSE"),
        TfIdfClassifier.Labeled("transferred KSh 5000 to KCB", "EXPENSE"),
        TfIdfClassifier.Labeled("Okoa Jahazi KSh 50 emergency airtime", "EXPENSE"),
        TfIdfClassifier.Labeled("purchased KSh 20 airtime", "EXPENSE"),
        TfIdfClassifier.Labeled("Successfully sent KSh 100 to 0734567890", "EXPENSE"),
        TfIdfClassifier.Labeled("Payment of KSh 300 to Naivas", "EXPENSE"),
        TfIdfClassifier.Labeled("Lipa fare matatu KSh 80", "EXPENSE", true),
        TfIdfClassifier.Labeled("nimetuma fare ya boda 150", "EXPENSE", true),
        TfIdfClassifier.Labeled("paid chips 120 at gate kiosk", "EXPENSE", true),
        TfIdfClassifier.Labeled("checkout Quickmart groceries 2300", "EXPENSE", true),
        TfIdfClassifier.Labeled("topup Safaricom bundles 100", "EXPENSE", true),
        TfIdfClassifier.Labeled("debited KSh 200 purchase at Carrefour", "EXPENSE"),
        TfIdfClassifier.Labeled("paid out KSh 450 Bolt trip", "EXPENSE", true),
        TfIdfClassifier.Labeled("kununua lunch 180 campus", "EXPENSE", true),
        // INCOME — verified templates (receive, bank-in, HELB, loans, salary)
        TfIdfClassifier.Labeled("You have received KSh 1000 from Jane Mum", "INCOME"),
        TfIdfClassifier.Labeled("transferred KSh 5000 from KCB to M-PESA", "INCOME"),
        TfIdfClassifier.Labeled("borrowed KSh 500 Fuliza", "INCOME"),
        TfIdfClassifier.Labeled("HELB upkeep KSh 5000 disbursed", "INCOME"),
        TfIdfClassifier.Labeled("credited KSh 5000 salary", "INCOME"),
        TfIdfClassifier.Labeled("Received KSh 100 from Airtel", "INCOME"),
        TfIdfClassifier.Labeled("Tala disbursed KSh 2000 loan", "INCOME"),
        TfIdfClassifier.Labeled("nimetumiwa upkeep 3000 na mum", "INCOME", true),
        TfIdfClassifier.Labeled("bursary received 10000", "INCOME", true),
        TfIdfClassifier.Labeled("Branch approved loan KSh 3000 advanced", "INCOME"),
        TfIdfClassifier.Labeled("Hustler Fund sent you KSh 1000", "INCOME"),
        TfIdfClassifier.Labeled("Equity bank credit KSh 8000 deposited", "INCOME"),
        TfIdfClassifier.Labeled("gift received 500 from dad", "INCOME", true),
        // SAVING — M-Shwari, chama, MMF
        TfIdfClassifier.Labeled("transferred KSh 500 to M-SHWARI", "SAVING"),
        TfIdfClassifier.Labeled("locked savings KSh 1000 chama", "SAVING", true),
        TfIdfClassifier.Labeled("nimeweka 500 kwa savings pot", "SAVING", true),
        TfIdfClassifier.Labeled("ziidi MMF deposit 2000", "SAVING", true),
        TfIdfClassifier.Labeled("transferred to Equity savings account", "SAVING", true),
        TfIdfClassifier.Labeled("sacco deduction KSh 1000", "SAVING", true),
        TfIdfClassifier.Labeled("fixed deposit locked 5000 six months", "SAVING", true),
        TfIdfClassifier.Labeled("emergency fund topup 1500", "SAVING", true),
        // TRANSFER — neutral ledger moves (own accounts, float)
        TfIdfClassifier.Labeled("agent float advance till transfer", "TRANSFER", true),
        TfIdfClassifier.Labeled("till to paybill merchant transfer", "TRANSFER", true),
        TfIdfClassifier.Labeled("balance transfer between own accounts", "TRANSFER", true),
        TfIdfClassifier.Labeled("M-Pesa to bank own account move", "TRANSFER", true),
        TfIdfClassifier.Labeled("Confirmed transfer KSh 2000 from M-Pesa to KCB own account", "TRANSFER"),
        TfIdfClassifier.Labeled("move money till to paybill float topup business", "TRANSFER", true),
        TfIdfClassifier.Labeled("transfer between savings and current same bank", "TRANSFER", true),
        TfIdfClassifier.Labeled("sweep leftover airtime float to main till", "TRANSFER", true),
        // REFUND — merchant cashback / returned goods
        TfIdfClassifier.Labeled("refund KSh 150 cashback Naivas", "REFUND"),
        TfIdfClassifier.Labeled("cashback KSh 45 loyalty points redeemed", "REFUND"),
        TfIdfClassifier.Labeled("returned goods refund KSh 800 Quickmart", "REFUND", true),
        TfIdfClassifier.Labeled("overcharge refunded 200 by shop", "REFUND", true),
        TfIdfClassifier.Labeled("bonga points cashback credited 120", "REFUND", true),
        TfIdfClassifier.Labeled("refund for cancelled order KSh 650 Jumia", "REFUND", true),
        TfIdfClassifier.Labeled("money back guarantee refund 300", "REFUND", true),
        TfIdfClassifier.Labeled("cashback reward 90 loyalty payout", "REFUND", true),
        TfIdfClassifier.Labeled("airtime cashback bonus 30 credited back", "REFUND", true),
        TfIdfClassifier.Labeled("shop returned my change overcharge cashback", "REFUND", true),
        // REVERSAL — network-level undo of a prior transaction
        TfIdfClassifier.Labeled("Reversed KSh 500 M-Pesa reversal", "REVERSAL"),
        TfIdfClassifier.Labeled("reversal of transaction SH12AB34CD KSh 300", "REVERSAL"),
        TfIdfClassifier.Labeled("transaction REVERSAL confirmed KSh 1000 returned", "REVERSAL"),
        TfIdfClassifier.Labeled("wrong till reversed 250 back to wallet", "REVERSAL", true),
        TfIdfClassifier.Labeled("reversal successful funds restored KSh 750", "REVERSAL", true),
        TfIdfClassifier.Labeled("mistaken payment reversed in full", "REVERSAL", true),
        TfIdfClassifier.Labeled("Safaricom reversal reference complete", "REVERSAL", true),
        // FEE — cost tails, never the principal
        TfIdfClassifier.Labeled("Transaction cost KSh 22", "FEE"),
        TfIdfClassifier.Labeled("service charge KSh 15 deducted", "FEE"),
        TfIdfClassifier.Labeled("withdrawal charge KSh 30 Agent", "FEE"),
        TfIdfClassifier.Labeled("transfer charge KSh 10", "FEE"),
        TfIdfClassifier.Labeled("eCitizen convenience fee KSh 50 meals", "FEE"),
        TfIdfClassifier.Labeled("access fee KSh 5 deducted", "FEE"),
        TfIdfClassifier.Labeled("levy KSh 8 fuel", "FEE", true),
        // WITHDRAWAL — cash-out at agent / ATM (verbs: withdraw/cash out; FEE owns charge/cost)
        TfIdfClassifier.Labeled("Withdraw KSh 2000 from Equity Agent 12345", "WITHDRAWAL"),
        TfIdfClassifier.Labeled("ATM cash out KSh 5000 PIN", "WITHDRAWAL"),
        TfIdfClassifier.Labeled("withdraw cash 1500 agent Watu", "WITHDRAWAL", true),
        TfIdfClassifier.Labeled("Give cash out KSh 3000 agent queue", "WITHDRAWAL", true),
        TfIdfClassifier.Labeled("cash out savings wallet 4000 agent", "WITHDRAWAL", true),
        TfIdfClassifier.Labeled("toa pesa kwa agent 2500", "WITHDRAWAL", true),
        // DEPOSIT — cash-in at agent
        TfIdfClassifier.Labeled("deposited KSh 3000 to Agent 777", "DEPOSIT"),
        TfIdfClassifier.Labeled("Give KSh 2000 cash to agent deposit", "DEPOSIT"),
        TfIdfClassifier.Labeled("agent deposit 5000 float", "DEPOSIT", true),
        TfIdfClassifier.Labeled("cash deposit confirmed agent 1200", "DEPOSIT", true),
        TfIdfClassifier.Labeled("weka pesa kwa agent 800 deposit", "DEPOSIT", true),
        // BILL — paybill WITH account reference (grounded numbers)
        TfIdfClassifier.Labeled("KSh 200 paybill sent to KPLC for account 12345 paybill 888880", "BILL"),
        TfIdfClassifier.Labeled("KSh 500 paybill KPLC Postpaid 888888 account 987", "BILL"),
        TfIdfClassifier.Labeled("KSh 150 UoN meals paybill eCitizen 222222 account NUK1-Emma", "BILL"),
        TfIdfClassifier.Labeled("KSh 400 paybill SHA 200222 national ID contribution", "BILL"),
        TfIdfClassifier.Labeled("KSh 1000 paybill HELB repayment 200800", "BILL"),
        TfIdfClassifier.Labeled("KSh 300 paybill NSSF 333300 contribution", "BILL"),
        TfIdfClassifier.Labeled("KSh 250 paybill Safaricom postpaid 200200 bill", "BILL"),
        TfIdfClassifier.Labeled("KSh 120 paybill Nairobi Water account plot 12", "BILL", true),
        TfIdfClassifier.Labeled("KSh 2000 paybill rent landlord account bedsitter", "BILL", true),
        TfIdfClassifier.Labeled("KSh 100 paybill Huduma 191919 certificate", "BILL", true),
        TfIdfClassifier.Labeled("paybill KSh 800 DSTV Gotv bouquet account 123", "BILL", true),
        TfIdfClassifier.Labeled("paybill KSh 1500 school fees account admission 456", "BILL", true),
        TfIdfClassifier.Labeled("KSh 90 paybill water vendor account plot 7", "BILL", true),
        TfIdfClassifier.Labeled("paybill sent KSh 600 chama welfare account member", "BILL", true),
        // OTHER — balance, promo, unparseable
        TfIdfClassifier.Labeled("M-PESA balance is KSh 1234", "OTHER"),
        TfIdfClassifier.Labeled("dial star promo win congratulations subscribe", "OTHER"),
        TfIdfClassifier.Labeled("Fuliza limit available loan balance", "OTHER"),
        TfIdfClassifier.Labeled("transaction confirmed receipt notice", "OTHER", true),
        TfIdfClassifier.Labeled("your statement is ready download app", "OTHER", true),
        TfIdfClassifier.Labeled("reminder pay your bill soon due", "OTHER", true),
        // v3 growth: own-account moves vs spending; reversal completions vs notices
        TfIdfClassifier.Labeled("sweep balance from till to paybill float own shop", "TRANSFER", true),
        TfIdfClassifier.Labeled("move upkeep from savings to mpesa spending wallet", "TRANSFER", true),
        TfIdfClassifier.Labeled("shift chama payout to personal mpesa same owner", "TRANSFER", true),
        TfIdfClassifier.Labeled("reversal completed funds back to mpesa wallet ref", "REVERSAL", true),
        TfIdfClassifier.Labeled("wrong paybill reversed full amount returned", "REVERSAL", true),
        TfIdfClassifier.Labeled("lock chama contribution weekly savings group", "SAVING", true),
        TfIdfClassifier.Labeled("move 1000 to mshwari lock savings", "SAVING", true),
        TfIdfClassifier.Labeled("pesaflow weekly spending summary ready view", "OTHER", true),
        // v4 growth: DEPOSIT/WITHDRAWAL cash-agent diversity (Sheng + agent variants)
        TfIdfClassifier.Labeled("weka 3000 kwa agent deposit float", "DEPOSIT", true),
        TfIdfClassifier.Labeled("cash in 4500 at equity agent deposit confirmed", "DEPOSIT", true),
        TfIdfClassifier.Labeled("deposit 1200 to till agent float topup", "DEPOSIT", true),
        TfIdfClassifier.Labeled("nimeweka 800 kwa duka agent deposit", "DEPOSIT", true),
        TfIdfClassifier.Labeled("agent cash deposit 7000 received", "DEPOSIT", true),
        TfIdfClassifier.Labeled("saka deposit 2500 kwa agent jirani", "DEPOSIT", true),
        TfIdfClassifier.Labeled("toa 3000 kwa atm withdrawal charges apply", "WITHDRAWAL", true),
        TfIdfClassifier.Labeled("cash out 2000 agent line queue", "WITHDRAWAL", true),
        TfIdfClassifier.Labeled("withdraw 1500 from kcb atm night", "WITHDRAWAL", true),
        TfIdfClassifier.Labeled("nimetoa 1000 kwa agent tao", "WITHDRAWAL", true),
        TfIdfClassifier.Labeled("agent withdrawal 6000 id required", "WITHDRAWAL", true),
        TfIdfClassifier.Labeled("to withdraw 3500 coop agent", "WITHDRAWAL", true),
        // v4 growth: SAVING locks, INCOME upkeep, BILL accounts, TRANSFER own-account
        TfIdfClassifier.Labeled("sacco monthly deduction 2000 savings", "SAVING", true),
        TfIdfClassifier.Labeled("fixed savings lock 10000 three months", "SAVING", true),
        TfIdfClassifier.Labeled("kcb goal savings transfer 1500 locked", "SAVING", true),
        TfIdfClassifier.Labeled("mshwari lock 2000 emergency fund", "SAVING", true),
        TfIdfClassifier.Labeled("chama payout received 8000 upkeep", "SAVING", true),
        TfIdfClassifier.Labeled("salary credited 45000 end month", "INCOME", true),
        TfIdfClassifier.Labeled("upkeep 4000 received from guardian", "INCOME", true),
        TfIdfClassifier.Labeled("freelance payout 6500 received", "INCOME", true),
        TfIdfClassifier.Labeled("nimetumiwa 2000 na bro", "INCOME", true),
        TfIdfClassifier.Labeled("paybill KSh 450 KPLC tokens account meter 54123", "BILL", true),
        TfIdfClassifier.Labeled("lipa bill ya maji 300 account plot 9", "BILL", true),
        TfIdfClassifier.Labeled("paybill 200800 HELB loan repayment account ID", "BILL", true),
        TfIdfClassifier.Labeled("dstv subscription paybill 799 account smartcard", "BILL", true),
        TfIdfClassifier.Labeled("shift salary to savings wallet same owner", "TRANSFER", true),
        TfIdfClassifier.Labeled("move float from mpesa to till own business", "TRANSFER", true),
        TfIdfClassifier.Labeled("transfer upkeep to school fees wallet self", "TRANSFER", true),
        // v4 growth: OTHER notices vs money movement
        TfIdfClassifier.Labeled("your fuliza limit increased to 2000", "OTHER", true),
        TfIdfClassifier.Labeled("claim cashback reward dial star", "OTHER", true),
        TfIdfClassifier.Labeled("mpesa statement download ready", "OTHER", true),
        TfIdfClassifier.Labeled("rate our agent service reply", "OTHER", true)
    )

    private fun ensureTrained() {
        if (!ready) {
            classifier.train(trainingData)
            ready = true
        }
    }

    /**
     * Versioned weight import (spec §18 retrain path): replaces bundled
     * weights with an offline-trained blob from TfIdfClassifier.serialize.
     * Throws on bad header — caller must catch and keep bundled weights.
     */
    fun loadWeights(blob: String) {
        classifier.deserialize(blob)
        ready = true
    }

    fun predict(text: String): List<TfIdfClassifier.Prediction> {
        ensureTrained()
        return classifier.predict(text)
    }

    fun predictTop(text: String): TfIdfClassifier.Prediction? = predict(text).firstOrNull()
}
