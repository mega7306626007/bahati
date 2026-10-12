package com.pesaflow.app.data.campus

// Campus + year-of-study habits: researched living patterns per campus and
// per study year, used to personalize onboarding, budgets and Buddy advice.
// Residence/food/commute notes are starter estimates - verify locally.
// Year habits encode the one reliable rhythm: freshers eat out and stay in
// hostels; seniors rent, cook and hustle. Nothing here invents prices.
data class CampusHabit(
    val campus: String,
    val keys: List<String>,
    val residence: String,
    val food: String,
    val commute: String
)

val CAMPUS_HABITS: List<CampusHabit> = listOf(
    CampusHabit(
        campus = "UoN Main",
        keys = listOf("uon", "nairobi", "uon main", "main campus", "university way", "uonbi"),
        residence = "CBD walkable; Ngara/Pangani bedsitters; Chiromo hostels nearby.",
        food = "Klabu (Club 36): basic plate KSh 30, smocha 50, beef ugali 100.",
        commute = "Walk the CBD; matatus only for late nights."
    ),
    CampusHabit(
        campus = "UoN Kabete",
        keys = listOf("kabete", "lower kabete", "upper kabete", "wangige"),
        residence = "On-campus hostels first; Wangige and Kangemi rentals beyond.",
        food = "Campus mess first; Wangige market rewards those who cook.",
        commute = "Waiyaki Way matatus (Kangemi corridor); long ride to CBD."
    ),
    CampusHabit(
        campus = "UoN Chiromo",
        keys = listOf("chiromo", "qejani", "riverside"),
        residence = "Qejani and Chiromo hostels; Westlands adjacent.",
        food = "Klabu prices carry over; Westlands eateries cost double.",
        commute = "Walk Westlands; matatu for CBD."
    ),
    CampusHabit(
        campus = "KU Main",
        keys = listOf("kenyatta", "ku", "kahawa", "kahawa wendani", "kahawa sukari", "ruiru"),
        residence = "Kahawa hostels and rentals; Roysambu for night life.",
        food = "Nyayo/Eastern/Western mess: chapati 15, sukuma 10, githeri 40.",
        commute = "Thika Road corridor; 237/145/146."
    ),
    CampusHabit(
        campus = "JKUAT Juja",
        keys = listOf("jkuat", "juja", "juja farm", "kalimoni", "kimbo"),
        residence = "Hostels plus Juja town rentals; Juja Farm cheapest.",
        food = "Main mess + Tuck Shop eggs; Cafe Aticas top-rated cheap.",
        commute = "237 Thika corridor; Juja stage for town runs."
    ),
    CampusHabit(
        campus = "Maseno",
        keys = listOf("maseno", "siriba", "college", "luanda", "kisumu"),
        residence = "Siriba/College hostels; Maseno town; Kisumu 38km - commute rarely pays.",
        food = "College set meal 60; Student Special 30; chafua plates 90-110.",
        commute = "Walk campus; Kisumu runs are day trips, not daily."
    ),
    CampusHabit(
        campus = "Kirinyaga",
        keys = listOf("kirinyaga", "kutus", "kerugoya", "kagio", "sagana"),
        residence = "Kutus and Kerugoya bedsitters; Kagio cheapest.",
        food = "Mess halls first; Kutus market rewards those who cook.",
        commute = "Walk or short matatu; Kutus is the campus town."
    ),
    CampusHabit(
        campus = "Strathmore",
        keys = listOf("strathmore", "madaraka"),
        residence = "Madaraka, Nairobi West, South B and South C.",
        food = "Madaraka eateries cost more than public mess halls - budget for it.",
        commute = "Walk Madaraka; short matatu beyond."
    ),
    CampusHabit(
        campus = "Daystar Athi River",
        keys = listOf("daystar", "athi river", "mlolongo", "syokimau", "kitengela", "greenpark"),
        residence = "Athi River and Mlolongo; Syokimau and Kitengela for quieter stays.",
        food = "Campus mess first; Mlolongo eateries for variety.",
        commute = "Mombasa Road traffic is real - leave early, verify stage fares."
    ),
    CampusHabit(
        campus = "Moi Kesses",
        keys = listOf("moi", "kesses", "annex"),
        residence = "Kesses hostels first; Eldoret is a trip, not a daily commute.",
        food = "Mess halls first; Kesses market for cooks.",
        commute = "Campus stages; Eldoret runs are weekend trips."
    ),
    CampusHabit(
        campus = "UoEldoret",
        keys = listOf("uoe", "eldoret", "chepkoilel", "ziwa", "iten", "junction", "kuiwet", "mti moja"),
        residence = "Junction, Mti Moja and Kuiwet; Eldoret town beyond.",
        food = "Mess halls first; Eldoret eateries for weekends.",
        commute = "Eldoret corridor matatus; walk the campus."
    ),
    CampusHabit(
        campus = "MMUST",
        keys = listOf("mmust", "muliro", "kakamega", "webuye"),
        residence = "Kakamega town and Webuye rentals; hostels first.",
        food = "Mess halls first; Kakamega market rewards cooks.",
        commute = "Walk or short matatu around Kakamega."
    ),
    CampusHabit(
        campus = "Kisii",
        keys = listOf("kisii", "kilgoris", "nyamira"),
        residence = "Kisii town bedsitters; Nyamira for quieter stays.",
        food = "Mess halls first; Kisii market is kind to cooks.",
        commute = "Walk or short matatu; hills punish late starts."
    ),
    CampusHabit(
        campus = "MKU Thika",
        keys = listOf("mount kenya", "mku", "thika", "general kago", "jomoko"),
        residence = "Thika town and General Kago; Juja and Ruiru spill over.",
        food = "Mess halls first; Thika eateries for variety.",
        commute = "Thika Road corridor; walk where you can."
    ),
    CampusHabit(
        campus = "USIU",
        keys = listOf("usiu", "garden estate", "mirema"),
        residence = "Garden Estate, Mirema, Kasarani and Roysambu.",
        food = "Campus cafeteria plus Thika Road malls - mall prices bite, budget for it.",
        commute = "Short Thika Road matatus; Zimmerman spill over."
    ),
    CampusHabit(
        campus = "KCA",
        keys = listOf("kca", "ruaraka"),
        residence = "Ruaraka, Kasarani, Roysambu and Zimmerman.",
        food = "Mess halls first; Ruaraka eateries are fair.",
        commute = "Short matatus on the Ruaraka corridor."
    ),
    CampusHabit(
        campus = "Zetech",
        keys = listOf("zetech"),
        residence = "Ruiru, Kimbo, Kahawa and Githurai bedsitters.",
        food = "Mess halls first; Ruiru eateries for variety.",
        commute = "Thika Road corridor; Kimbo stage for town runs."
    ),
    CampusHabit(
        campus = "TUK",
        keys = listOf("technical university", "tuk", "haile selassie"),
        residence = "Ngara and Pangani bedsitters; Nairobi West beyond.",
        food = "CBD eateries plus mess; CBD prices need a daily cap.",
        commute = "Walk the CBD; matatus only for late nights."
    ),
    CampusHabit(
        campus = "DeKUT",
        keys = listOf("dedan", "dekut", "nyeri", "mweiga", "nyahururu"),
        residence = "Nyeri town and Mweiga rentals; hostels first.",
        food = "Mess halls first; Nyeri market rewards cooks.",
        commute = "Walk or short matatu around Nyeri."
    ),
    CampusHabit(
        campus = "Embu",
        keys = listOf("embu", "njukiri", "kiritiri", "kubukubu"),
        residence = "Embu town and Njukiri bedsitters.",
        food = "Mess halls first; Embu market rewards cooks.",
        commute = "Walk or short matatu in town."
    ),
    CampusHabit(
        campus = "Chuka",
        keys = listOf("chuka", "igembe", "maua", "chogoria", "tharaka", "gatunga"),
        residence = "Chuka town bedsitters; Embu and Meru corridor beyond.",
        food = "Mess halls first; Chuka market is kind to cooks.",
        commute = "Walk the campus town; Meru and Embu are trips, not commutes."
    ),
    CampusHabit(
        campus = "Machakos",
        keys = listOf("machakos", "mumbuni", "konza", "wote"),
        residence = "Machakos town and Mumbuni bedsitters.",
        food = "Mess halls first; Machakos market rewards cooks.",
        commute = "Walk or short matatu in town."
    ),
    CampusHabit(
        campus = "SEKU",
        keys = listOf("seku", "south eastern", "kitui", "kwa vonza", "vonza"),
        residence = "Kwa Vonza hostels first; Kitui town beyond.",
        food = "Mess halls first; stock up in Kitui - Vonza shops are thin.",
        commute = "Kitui link matatus; plan trips, do not drift."
    ),
    CampusHabit(
        campus = "Pwani Kilifi",
        keys = listOf("pwani", "kilifi", "malindi"),
        residence = "Kilifi town bedsitters; Malindi for the coast mood.",
        food = "Mess halls first; Kilifi eateries are fair.",
        commute = "Walk or short matatu; Mombasa is a trip, not a commute."
    ),
    CampusHabit(
        campus = "Egerton Njoro",
        keys = listOf("egerton", "njoro", "ngata", "nakuru"),
        residence = "Hostels plus Njoro town; Nakuru 25km for weekends.",
        food = "Chui/Kiboko mess; boiled githeri cheapest; Njoro eateries moderate.",
        commute = "NK6 Nakuru corridor; walk the campus."
    )
)

// Shared residence/food/commute memory: sibling campuses (Kabete/Main)
// resolve through the same matchers as food and transport data.
fun campusHabitsFor(query: String): CampusHabit? {
    val low = query.trim().lowercase()
    if (low.isEmpty()) return null
    val tokens = low.split(Regex("[^a-z0-9]+")).toSet()
    return CAMPUS_HABITS.firstOrNull { c ->
        c.keys.any { k -> if (' ' in k) k in low else k in tokens }
    }
}

// Year-of-study habits: what changes, what to watch, one top tip.
data class YearHabit(
    val year: Int,
    val pattern: String,
    val watch: String,
    val topTip: String
)

fun yearHabitFor(yearOfStudy: String): YearHabit {
    val year = yearOfStudy.filter { it.isDigit() }.toIntOrNull() ?: 0
    return when (year) {
        1 -> YearHabit(
            year = 1,
            pattern = "Freshers eat out most, live in hostels, learn HELB the hard way.",
            watch = "Food-out spending and orientation-week splurges.",
            topTip = "Eat at the mess for the first month; sign nothing yearly in week one."
        )
        2 -> YearHabit(
            year = 2,
            pattern = "Rentals begin, cooking starts, first real budgets appear.",
            watch = "Solo rentals that swallow upkeep.",
            topTip = "A cooking starter kit pays for itself in three weeks; split rentals beat solos."
        )
        3 -> YearHabit(
            year = 3,
            pattern = "Attachments and hustles: transport spikes, side income appears.",
            watch = "Attachment-season fare bleed and lifestyle creep from hustle cash.",
            topTip = "Route hustle income 50/30/20 on arrival day - before it melts."
        )
        else -> if (year >= 4) YearHabit(
            year = year,
            pattern = "Final stretch: project costs, job-hunt spend, stable rent.",
            watch = "Project levies and interview-season transport.",
            topTip = "Keep a project buffer and a separate interview fare fund."
        ) else YearHabit(
            year = 0,
            pattern = "Year unknown: hostel habits assumed, food-out watched.",
            watch = "Untracked food and fare spending.",
            topTip = "Log food and fares for two weeks - the pattern names itself."
        )
    }
}
