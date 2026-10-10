package com.pesaflow.app.data.finance

// Campus transport atlas: rental areas, typical one-way fares and route
// leads per university, distilled from the Kenya University Matatu &
// Student Rental Transport Research (Oct 2026). Every figure is a
// *published lead*, not a verified fare — the UI must say so, and field
// verification beats this file wherever they disagree.
//
// Two consumers:
// - rent matching: rentalAreas feed the directory's home-name keywords,
//   so "GREENVIEW COURT" near KU reads as rent (category-gated, so a
//   bookshop in the same town never does);
// - onboarding fare box: fare band shown as "typical near X" when the
//   user hasn't typed their own figure. Typed figures always win.
data class CampusTransport(
    val campus: String,
    val keys: List<String>,
    val rentalAreas: List<String>,
    // Typical one-way fare band (KSh). Null where the report gives no
    // usable stage-to-stage figure.
    val fareOneWayMin: Int = 0,
    val fareOneWayMax: Int = 0,
    val routes: List<String> = emptyList()
)

val CAMPUS_TRANSPORT: List<CampusTransport> = listOf(
    CampusTransport(
        campus = "UoN",
        keys = listOf("nairobi", "uon", "chiromo"),
        rentalAreas = listOf("Ngara", "Pangani", "Parklands", "Nairobi West", "Madaraka", "South B", "South C", "Kilimani"),
        fareOneWayMin = 50,
        fareOneWayMax = 90,
        routes = listOf("11A/11B Ngara–Parklands", "14A CBD–Madaraka", "CBD city services")
    ),
    CampusTransport(
        campus = "KU",
        keys = listOf("kenyatta", "ku", "kahawa", "ruiru"),
        rentalAreas = listOf("Kahawa Wendani", "Kahawa Sukari", "Githurai", "Zimmerman", "Roysambu", "Kasarani", "Mwihoko", "Ruiru"),
        fareOneWayMin = 30,
        fareOneWayMax = 80,
        routes = listOf("237 CBD–Thika via KU", "145 CBD–Ruiru", "146 CBD–Ruiru", "44/45 Githurai")
    ),
    CampusTransport(
        campus = "JKUAT",
        keys = listOf("jkuat", "juja", "kimbo"),
        rentalAreas = listOf("Juja", "Witeithie", "Kalimoni", "Murera", "Theta", "Ruiru", "Kimbo", "Thika"),
        fareOneWayMin = 20,
        fareOneWayMax = 100,
        routes = listOf("237 CBD–Thika via JKUAT gate", "145 CBD–Ruiru")
    ),
    CampusTransport(
        campus = "Maseno",
        keys = listOf("maseno", "siriba", "luanda", "kisumu"),
        rentalAreas = listOf("Maseno", "Siriba", "College", "Luanda", "Kisumu"),
        fareOneWayMin = 20,
        fareOneWayMax = 150,
        routes = listOf("Kisumu–Maseno highway", "West Bunyore Kima–Sibira")
    ),
    CampusTransport(
        campus = "Egerton",
        keys = listOf("egerton", "njoro", "nakuru"),
        rentalAreas = listOf("Njoro", "Ngata", "Nakuru", "Mau Narok"),
        fareOneWayMin = 20,
        fareOneWayMax = 100,
        routes = listOf("NK6 Nakuru–Njoro–Egerton", "Njoro Line SACCO")
    ),
    CampusTransport(
        campus = "Kirinyaga",
        keys = listOf("kirinyaga", "kutus", "kerugoya", "kagio"),
        rentalAreas = listOf("Kutus", "Kerugoya", "Kagio", "Sagana"),
        fareOneWayMin = 30,
        fareOneWayMax = 70,
        routes = listOf("Kerugoya–Kutus–Embu", "Kerugoya–Kutus–Mwea")
    ),
    CampusTransport(
        campus = "Strathmore",
        keys = listOf("strathmore", "madaraka"),
        rentalAreas = listOf("Madaraka", "Nairobi West", "South B", "South C"),
        fareOneWayMin = 50,
        fareOneWayMax = 90,
        routes = listOf("14A CBD–Madaraka–Strathmore gate")
    ),
    CampusTransport(
        campus = "Daystar",
        keys = listOf("daystar", "athi river", "mlolongo"),
        rentalAreas = listOf("Athi River", "Mlolongo", "Syokimau", "Greenpark", "Kitengela", "Syokimau"),
        fareOneWayMin = 50,
        fareOneWayMax = 100,
        routes = listOf("110/110A CBD–Athi River")
    ),
    // ---- Report campuses 07–22 (rental + fare audits, Oct 2026) ----
    CampusTransport(
        campus = "Moi",
        keys = listOf("moi", "kesses", "annex"),
        rentalAreas = listOf("Kesses", "Eldoret"),
        fareOneWayMin = 20,
        fareOneWayMax = 150,
        routes = listOf("Eldoret Main Stage–Kesses", "Uganda Road–Main Campus")
    ),
    CampusTransport(
        campus = "UoE",
        keys = listOf("eldoret", "chepkoilel", "ziwa", "iten", "junction", "kuiwet", "mti moja"),
        rentalAreas = listOf("Junction", "Mti Moja", "Kuiwet", "Eldoret"),
        fareOneWayMin = 20,
        fareOneWayMax = 50,
        routes = listOf("Eldoret–Ziwa Gate A", "CHEP SACCO Eldoret–Iten Rd")
    ),
    CampusTransport(
        campus = "MMUST",
        keys = listOf("mmust", "muliro", "kakamega", "webuye"),
        rentalAreas = listOf("Kakamega", "Webuye"),
        fareOneWayMin = 20,
        fareOneWayMax = 120,
        routes = listOf("Kakamega–Webuye", "Kakamega–Bungoma")
    ),
    CampusTransport(
        campus = "Kisii",
        keys = listOf("kisii", "kilgoris", "nyamira"),
        rentalAreas = listOf("Kisii", "Kilgoris", "Nyamira"),
        fareOneWayMin = 20,
        fareOneWayMax = 60,
        routes = listOf("Kisii–Kilgoris", "Kisii–Nyamira")
    ),
    CampusTransport(
        campus = "MKU",
        keys = listOf("mount kenya", "mku", "thika", "general kago", "jomoko"),
        rentalAreas = listOf("Thika", "Juja", "Ruiru"),
        fareOneWayMin = 20,
        fareOneWayMax = 100,
        routes = listOf("237 CBD–Thika", "Thika–Juja")
    ),
    CampusTransport(
        campus = "USIU",
        keys = listOf("usiu", "garden estate", "mirema"),
        rentalAreas = listOf("Kasarani", "Roysambu", "Garden Estate", "Mirema", "Zimmerman"),
        fareOneWayMin = 20,
        fareOneWayMax = 60,
        routes = listOf("237 Thika Rd trunk", "145/146 Ruiru")
    ),
    CampusTransport(
        campus = "KCA",
        keys = listOf("kca", "ruaraka"),
        rentalAreas = listOf("Ruaraka", "Kasarani", "Roysambu", "Zimmerman"),
        fareOneWayMin = 20,
        fareOneWayMax = 60,
        routes = listOf("237 CBD–Thika (KCA stop)", "145/146")
    ),
    CampusTransport(
        campus = "Zetech",
        keys = listOf("zetech"),
        rentalAreas = listOf("Ruiru", "Kimbo", "Kahawa", "Githurai"),
        fareOneWayMin = 20,
        fareOneWayMax = 70,
        routes = listOf("145 CBD–Ruiru", "237 via Ruiru")
    ),
    CampusTransport(
        campus = "TUK",
        keys = listOf("technical university", "tuk", "haile selassie"),
        rentalAreas = listOf("Ngara", "Pangani", "Nairobi West", "South B"),
        fareOneWayMin = 30,
        fareOneWayMax = 70,
        routes = listOf("CBD city services")
    ),
    CampusTransport(
        campus = "DeKUT",
        keys = listOf("dedan", "dekut", "nyeri", "mweiga", "nyahururu"),
        rentalAreas = listOf("Nyeri", "Mweiga"),
        fareOneWayMin = 20,
        fareOneWayMax = 60,
        routes = listOf("Nyeri–Nyahururu via DeKUT", "Nyeri–Mweiga")
    ),
    CampusTransport(
        campus = "Chuka",
        keys = listOf("chuka", "igembe", "maua", "chogoria", "tharaka", "gatunga"),
        rentalAreas = listOf("Chuka", "Embu", "Meru"),
        fareOneWayMin = 20,
        fareOneWayMax = 40,
        routes = listOf("Chuka–Embu", "Chuka–Meru")
    ),
    CampusTransport(
        campus = "Machakos",
        keys = listOf("machakos", "mumbuni", "konza", "wote"),
        rentalAreas = listOf("Machakos", "Mumbuni"),
        fareOneWayMin = 30,
        fareOneWayMax = 50,
        routes = listOf("Nairobi–Machakos", "Machakos–Wote")
    ),
    CampusTransport(
        campus = "SEKU",
        keys = listOf("seku", "south eastern", "kitui", "kwa vonza", "vonza"),
        rentalAreas = listOf("Kwa Vonza", "Kitui"),
        fareOneWayMin = 30,
        fareOneWayMax = 80,
        routes = listOf("Kitui–Machakos", "Kitui local")
    ),
    CampusTransport(
        campus = "Embu",
        keys = listOf("embu", "njukiri", "kiritiri", "kubukubu"),
        rentalAreas = listOf("Embu", "Njukiri"),
        fareOneWayMin = 20,
        fareOneWayMax = 50,
        routes = listOf("Embu local", "Embu–Nairobi", "Embu–Meru")
    ),
    CampusTransport(
        campus = "Pwani",
        keys = listOf("pwani", "kilifi", "malindi"),
        rentalAreas = listOf("Kilifi", "Mombasa", "Malindi"),
        fareOneWayMin = 20,
        fareOneWayMax = 40,
        routes = listOf("Kilifi local", "Mombasa–Kilifi", "Malindi–Kilifi")
    )
)

private fun matchCampus(universityName: String): CampusTransport? {
    val low = universityName.trim().lowercase()
    if (low.isEmpty()) return null
    val tokens = low.split(Regex("[^a-z0-9]+")).toSet()
    return CAMPUS_TRANSPORT.firstOrNull { c ->
        c.keys.any { k -> if (' ' in k) k in low else k.trim() in tokens }
    }
}

/** Rental areas + routes + fare band for a university, or null if unknown. */
fun transportFor(universityName: String): CampusTransport? = matchCampus(universityName)

/** One-line fare hint for the onboarding fare box, or null. */
fun campusFareHint(universityName: String): String? {
    val c = matchCampus(universityName) ?: return null
    if (c.fareOneWayMin <= 0) return null
    return "Typical near ${c.campus}: KSh ${c.fareOneWayMin}–${c.fareOneWayMax} one-way (2026 research, verify locally)"
}
