package com.example

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MotoModelSpec(
    val modelo: String,
    val cilindraje: String,
    val categoria: String? = null
)

@JsonClass(generateAdapter = true)
data class BrandCatalogResponse(
    val marca: String? = null,
    val modelos: List<MotoModelSpec>? = null
)

object MotorcycleCatalog {

    val MARCAS: List<String> = listOf(
        "Yamaha",
        "Bajaj",
        "AKT",
        "Suzuki",
        "Honda",
        "KTM",
        "TVS",
        "Hero",
        "Victory",
        "Kawasaki",
        "Royal Enfield",
        "BMW",
        "Benelli",
        "Husqvarna",
        "CFMoto",
        "Ducati",
        "Triumph",
        "Kymco",
        "SYM"
    )

    private val VERIFIED_CATALOG: Map<String, List<MotoModelSpec>> = mapOf(
        "Yamaha" to listOf(
            MotoModelSpec("Crypton FI", "114cc", "Urbana / Semiautomática"),
            MotoModelSpec("YC-Z 110", "110cc", "Urbana / Trabajo"),
            MotoModelSpec("YBR 125Z", "124cc", "Urbana / Trabajo"),
            MotoModelSpec("SZ-RR 150", "149cc", "Urbana / Street"),
            MotoModelSpec("FZ Version 2.0", "149cc", "Naked / Street"),
            MotoModelSpec("FZ-S FI 3.0 ABS", "149cc", "Naked / Street"),
            MotoModelSpec("FZ25 ABS", "249cc", "Naked / Street"),
            MotoModelSpec("XTZ 125", "124cc", "Doble Propósito"),
            MotoModelSpec("XTZ 150 FI", "149cc", "Doble Propósito"),
            MotoModelSpec("XTZ 250 ABS", "249cc", "Doble Propósito"),
            MotoModelSpec("NMAX Connected", "155cc", "Scooter"),
            MotoModelSpec("Aerox 155", "155cc", "Scooter"),
            MotoModelSpec("XMAX 300", "292cc", "Maxi Scooter"),
            MotoModelSpec("TMAX 560", "562cc", "Maxi Scooter"),
            MotoModelSpec("MT-15", "155cc", "Hyper Naked"),
            MotoModelSpec("MT-03 ABS", "321cc", "Hyper Naked"),
            MotoModelSpec("MT-07", "689cc", "Hyper Naked"),
            MotoModelSpec("MT-09", "890cc", "Hyper Naked"),
            MotoModelSpec("MT-09 SP", "890cc", "Hyper Naked"),
            MotoModelSpec("MT-10", "998cc", "Hyper Naked"),
            MotoModelSpec("YZF-R15 V4", "155cc", "Supersport"),
            MotoModelSpec("YZF-R3", "321cc", "Supersport"),
            MotoModelSpec("YZF-R7", "689cc", "Supersport"),
            MotoModelSpec("YZF-R1", "998cc", "Supersport"),
            MotoModelSpec("Ténéré 700", "689cc", "Adventure"),
            MotoModelSpec("Tracer 9 GT", "890cc", "Sport Touring"),
            MotoModelSpec("Super Ténéré 1200ZE", "1199cc", "Adventure")
        ),
        "Bajaj" to listOf(
            MotoModelSpec("Boxer CT 100 KS", "102cc", "Trabajo"),
            MotoModelSpec("Boxer CT 100 ES", "102cc", "Trabajo"),
            MotoModelSpec("Boxer S", "102cc", "Trabajo"),
            MotoModelSpec("Boxer CT 125", "124cc", "Trabajo"),
            MotoModelSpec("Boxer 150X", "144cc", "Trabajo / Doble Propósito"),
            MotoModelSpec("Platino 100", "102cc", "Urbana"),
            MotoModelSpec("Platino 125", "124cc", "Urbana"),
            MotoModelSpec("Discover 125 ST-R", "124cc", "Urbana"),
            MotoModelSpec("Pulsar NS 125", "124cc", "Naked / Street"),
            MotoModelSpec("Pulsar 150 Neon", "149cc", "Street"),
            MotoModelSpec("Pulsar NS 160 FI ABS", "160cc", "Naked / Street"),
            MotoModelSpec("Pulsar N160", "164cc", "Naked / Street"),
            MotoModelSpec("Pulsar 180 Fi Neon", "178cc", "Street"),
            MotoModelSpec("Pulsar NS200", "199cc", "Naked / Street"),
            MotoModelSpec("Pulsar NS 200 FI ABS", "199cc", "Naked / Street"),
            MotoModelSpec("Pulsar RS 200", "199cc", "Deportiva"),
            MotoModelSpec("Pulsar N250", "249cc", "Naked / Street"),
            MotoModelSpec("Dominar 250", "248cc", "Sport Touring"),
            MotoModelSpec("Dominar 400 Touring", "373cc", "Sport Touring"),
            MotoModelSpec("Avenger 220 Cruise", "220cc", "Cruiser")
        ),
        "AKT" to listOf(
            MotoModelSpec("Special 110 X", "107cc", "Semiautomática"),
            MotoModelSpec("Flex 125", "124cc", "Semiautomática"),
            MotoModelSpec("NKD 125", "124cc", "Urbana / Clásica"),
            MotoModelSpec("NKD 125 Mastech", "124cc", "Urbana / Clásica"),
            MotoModelSpec("CR4 125", "124cc", "Street"),
            MotoModelSpec("CR4 162", "162cc", "Street"),
            MotoModelSpec("CR4 200 Pro", "197cc", "Street"),
            MotoModelSpec("TTR 125", "124cc", "Enduro / Doble Propósito"),
            MotoModelSpec("TTR 200", "197cc", "Enduro / Doble Propósito"),
            MotoModelSpec("TT Dual Sport 200", "197cc", "Doble Propósito"),
            MotoModelSpec("TT 250 Adventour", "250cc", "Adventure"),
            MotoModelSpec("Dynamic Pro 125", "124cc", "Scooter"),
            MotoModelSpec("Dynamic R 125", "124cc", "Scooter"),
            MotoModelSpec("Jet 125", "124cc", "Scooter"),
            MotoModelSpec("Voge 300 DS", "292cc", "Adventure"),
            MotoModelSpec("Voge 300 Rally", "292cc", "Enduro / Rally"),
            MotoModelSpec("Voge 500 DS", "471cc", "Adventure")
        ),
        "Suzuki" to listOf(
            MotoModelSpec("AX4 115", "113cc", "Trabajo"),
            MotoModelSpec("GN 125", "124cc", "Urbana / Custom"),
            MotoModelSpec("GSX 125R", "124cc", "Urbana / Street"),
            MotoModelSpec("Address 115 FI", "113cc", "Scooter"),
            MotoModelSpec("Avenis 125", "124cc", "Scooter"),
            MotoModelSpec("Burgman Street 125", "124cc", "Scooter"),
            MotoModelSpec("Gixxer 150 FI ABS", "155cc", "Naked / Street"),
            MotoModelSpec("Gixxer SF 150 FI", "155cc", "Deportiva"),
            MotoModelSpec("GSX-R150", "147cc", "Supersport"),
            MotoModelSpec("GSX-S150", "147cc", "Naked"),
            MotoModelSpec("Gixxer 250", "249cc", "Naked"),
            MotoModelSpec("Gixxer SF 250", "249cc", "Deportiva"),
            MotoModelSpec("DR 150 FI ABS", "149cc", "Doble Propósito"),
            MotoModelSpec("DR-Z400S", "398cc", "Doble Propósito"),
            MotoModelSpec("DR 650 SE", "644cc", "Doble Propósito"),
            MotoModelSpec("V-Strom 160", "160cc", "Adventure"),
            MotoModelSpec("V-Strom 250 SX", "249cc", "Adventure"),
            MotoModelSpec("V-Strom 650 XT", "645cc", "Adventure"),
            MotoModelSpec("V-Strom 800DE", "776cc", "Adventure"),
            MotoModelSpec("V-Strom 1050DE", "1037cc", "Adventure"),
            MotoModelSpec("GSX-8S", "776cc", "Naked"),
            MotoModelSpec("Hayabusa GSX1300R", "1340cc", "Hyperbike")
        ),
        "Honda" to listOf(
            MotoModelSpec("Navi", "109cc", "Urbana / Automática"),
            MotoModelSpec("Wave 110S", "109cc", "Semiautomática"),
            MotoModelSpec("Dio 110", "109cc", "Scooter"),
            MotoModelSpec("PCX 160 ABS", "156cc", "Scooter"),
            MotoModelSpec("ADV 350", "330cc", "Maxi Scooter"),
            MotoModelSpec("X-ADV 750", "745cc", "Adventure Scooter"),
            MotoModelSpec("CB 100", "99cc", "Trabajo"),
            MotoModelSpec("CB 125F Twister", "124cc", "Urbana"),
            MotoModelSpec("CB 160F", "162cc", "Street"),
            MotoModelSpec("CB190R", "184cc", "Naked / Street"),
            MotoModelSpec("CB 300F Twister", "293cc", "Naked"),
            MotoModelSpec("CB 500F", "471cc", "Naked"),
            MotoModelSpec("CB 650R", "649cc", "Neo Sports Café"),
            MotoModelSpec("CB 1000R", "998cc", "Neo Sports Café"),
            MotoModelSpec("XR 150L", "149cc", "Doble Propósito"),
            MotoModelSpec("XR 190L", "184cc", "Doble Propósito"),
            MotoModelSpec("XR 250 Tornado", "249cc", "Doble Propósito"),
            MotoModelSpec("XRE 190 ABS", "184cc", "Doble Propósito"),
            MotoModelSpec("XRE 300 ABS", "291cc", "Doble Propósito"),
            MotoModelSpec("Sahara 300", "293cc", "Adventure / Trail"),
            MotoModelSpec("CB 500X", "471cc", "Adventure"),
            MotoModelSpec("NX 500", "471cc", "Adventure"),
            MotoModelSpec("XL 750 Transalp", "755cc", "Adventure"),
            MotoModelSpec("CRF 1100L Africa Twin", "1084cc", "Adventure")
        ),
        "KTM" to listOf(
            MotoModelSpec("Duke 200", "199cc", "Naked"),
            MotoModelSpec("Duke 200 NG ABS", "199cc", "Naked"),
            MotoModelSpec("Duke 250", "249cc", "Naked"),
            MotoModelSpec("Duke 390", "373cc", "Naked"),
            MotoModelSpec("Duke 390 Gen 3", "399cc", "Naked"),
            MotoModelSpec("RC 200", "199cc", "Supersport"),
            MotoModelSpec("RC 390", "373cc", "Supersport"),
            MotoModelSpec("250 Adventure", "248cc", "Adventure"),
            MotoModelSpec("390 Adventure", "373cc", "Adventure"),
            MotoModelSpec("690 Enduro R", "693cc", "Enduro"),
            MotoModelSpec("790 Duke", "799cc", "Naked"),
            MotoModelSpec("790 Adventure", "799cc", "Adventure"),
            MotoModelSpec("890 Duke R", "889cc", "Naked"),
            MotoModelSpec("890 Adventure R", "889cc", "Adventure"),
            MotoModelSpec("1290 Super Adventure S", "1301cc", "Adventure"),
            MotoModelSpec("1390 Super Duke R", "1350cc", "Hyper Naked")
        ),
        "TVS" to listOf(
            MotoModelSpec("Sport 100 ELS", "99cc", "Trabajo"),
            MotoModelSpec("Stryker 125", "124cc", "Urbana"),
            MotoModelSpec("Raider 125", "124cc", "Street"),
            MotoModelSpec("Raider 125 FI", "124cc", "Street"),
            MotoModelSpec("Ntorq 125 Race Edition", "124cc", "Scooter"),
            MotoModelSpec("Apache RTR 160 2V", "159cc", "Street"),
            MotoModelSpec("Apache RTR 160 4V FI ABS", "159cc", "Naked / Street"),
            MotoModelSpec("Apache RTR 200 4V FI ABS", "197cc", "Naked / Street"),
            MotoModelSpec("Apache RR 310", "312cc", "Supersport"),
            MotoModelSpec("Apache RTR 310", "312cc", "Hyper Naked"),
            MotoModelSpec("Ronin 225", "225cc", "Neo-Retro")
        ),
        "Hero" to listOf(
            MotoModelSpec("Eco 100", "97cc", "Trabajo"),
            MotoModelSpec("Eco Deluxe", "97cc", "Trabajo"),
            MotoModelSpec("Eco 150 Cargo", "149cc", "Trabajo"),
            MotoModelSpec("Splendor XPro", "109cc", "Urbana"),
            MotoModelSpec("Ignitor 125", "124cc", "Urbana"),
            MotoModelSpec("Dash 125 FI", "124cc", "Scooter"),
            MotoModelSpec("Thriller Pro 150", "149cc", "Street"),
            MotoModelSpec("Hunk 150", "149cc", "Street"),
            MotoModelSpec("Hunk 160R", "163cc", "Naked / Street"),
            MotoModelSpec("Hunk 160R 4V", "163cc", "Naked / Street"),
            MotoModelSpec("Xpulse 200", "199cc", "Doble Propósito"),
            MotoModelSpec("Xpulse 200 4V", "199cc", "Doble Propósito"),
            MotoModelSpec("Xpulse 200T", "199cc", "Touring")
        ),
        "Victory" to listOf(
            MotoModelSpec("One ST 100", "97cc", "Semiautomática"),
            MotoModelSpec("Shock 115", "113cc", "Semiautomática"),
            MotoModelSpec("Life 125", "124cc", "Scooter"),
            MotoModelSpec("ZS 125", "124cc", "Scooter"),
            MotoModelSpec("Black", "171cc", "Maxi Scooter"),
            MotoModelSpec("Bet ABS 150", "149cc", "Maxi Scooter"),
            MotoModelSpec("Nitro 125", "124cc", "Urbana"),
            MotoModelSpec("Switch 150", "149cc", "Street"),
            MotoModelSpec("Bomber 125", "124cc", "Café Racer / Clásica"),
            MotoModelSpec("Bomber 150", "149cc", "Café Racer / Clásica"),
            MotoModelSpec("Bomber 250", "249cc", "Café Racer / Clásica"),
            MotoModelSpec("Venom 14", "149cc", "Naked"),
            MotoModelSpec("Venom 18", "177cc", "Naked"),
            MotoModelSpec("Venom 250", "249cc", "Naked"),
            MotoModelSpec("Venom 400", "378cc", "Naked"),
            MotoModelSpec("MRX 125", "124cc", "Enduro / Doble Propósito"),
            MotoModelSpec("MRX 150", "149cc", "Enduro / Doble Propósito"),
            MotoModelSpec("MRX 200", "199cc", "Enduro / Doble Propósito"),
            MotoModelSpec("MRX Arizona 200", "199cc", "Adventure")
        ),
        "Kawasaki" to listOf(
            MotoModelSpec("KLX 150", "144cc", "Enduro / Doble Propósito"),
            MotoModelSpec("KLX 230", "233cc", "Enduro / Doble Propósito"),
            MotoModelSpec("KLX 300", "292cc", "Enduro / Doble Propósito"),
            MotoModelSpec("Z400", "399cc", "Supernaked"),
            MotoModelSpec("Z500", "451cc", "Supernaked"),
            MotoModelSpec("Z650", "649cc", "Supernaked"),
            MotoModelSpec("Z900", "948cc", "Supernaked"),
            MotoModelSpec("Z H2", "998cc", "Hypernaked"),
            MotoModelSpec("Ninja 400", "399cc", "Supersport"),
            MotoModelSpec("Ninja 500", "451cc", "Supersport"),
            MotoModelSpec("Ninja 650", "649cc", "Sport"),
            MotoModelSpec("Ninja ZX-4RR", "399cc", "Supersport"),
            MotoModelSpec("Ninja ZX-6R", "636cc", "Supersport"),
            MotoModelSpec("Ninja ZX-10R", "998cc", "Superbike"),
            MotoModelSpec("Versys-X 300", "296cc", "Adventure"),
            MotoModelSpec("Versys 650", "649cc", "Adventure Touring"),
            MotoModelSpec("Versys 1000 SE", "1043cc", "Adventure Touring"),
            MotoModelSpec("KLR 650", "652cc", "Adventure"),
            MotoModelSpec("Vulcan S 650", "649cc", "Cruiser")
        ),
        "Royal Enfield" to listOf(
            MotoModelSpec("Hunter 350", "349cc", "Roadster"),
            MotoModelSpec("Classic 350", "349cc", "Clásica"),
            MotoModelSpec("Meteor 350", "349cc", "Cruiser"),
            MotoModelSpec("Bullet 350", "349cc", "Clásica"),
            MotoModelSpec("Scram 411", "411cc", "Scrambler / Crossover"),
            MotoModelSpec("Himalayan 411", "411cc", "Adventure"),
            MotoModelSpec("Himalayan 450", "452cc", "Adventure"),
            MotoModelSpec("Guerrilla 450", "452cc", "Roadster"),
            MotoModelSpec("Interceptor 650", "648cc", "Twin Roadster"),
            MotoModelSpec("Continental GT 650", "648cc", "Café Racer"),
            MotoModelSpec("Super Meteor 650", "648cc", "Cruiser"),
            MotoModelSpec("Shotgun 650", "648cc", "Custom Bobber")
        ),
        "BMW" to listOf(
            MotoModelSpec("G 310 R", "313cc", "Roadster"),
            MotoModelSpec("G 310 GS", "313cc", "Adventure"),
            MotoModelSpec("C 400 X", "350cc", "Maxi Scooter"),
            MotoModelSpec("C 400 GT", "350cc", "Maxi Scooter"),
            MotoModelSpec("F 750 GS", "853cc", "Adventure"),
            MotoModelSpec("F 800 GS", "895cc", "Adventure"),
            MotoModelSpec("F 850 GS", "853cc", "Adventure"),
            MotoModelSpec("F 900 R", "895cc", "Roadster"),
            MotoModelSpec("F 900 XR", "895cc", "Sport Touring"),
            MotoModelSpec("F 900 GS", "895cc", "Adventure"),
            MotoModelSpec("S 1000 R", "999cc", "Hyper Naked"),
            MotoModelSpec("S 1000 RR", "999cc", "Superbike"),
            MotoModelSpec("S 1000 XR", "999cc", "Sport Touring"),
            MotoModelSpec("R 1250 GS", "1254cc", "Adventure"),
            MotoModelSpec("R 1250 GS Adventure", "1254cc", "Adventure"),
            MotoModelSpec("R 1300 GS", "1300cc", "Adventure"),
            MotoModelSpec("R 18", "1802cc", "Heritage Cruiser")
        ),
        "Benelli" to listOf(
            MotoModelSpec("TNT 135", "135cc", "Mini Naked"),
            MotoModelSpec("TNT 150i", "149cc", "Street"),
            MotoModelSpec("180S", "175cc", "Naked"),
            MotoModelSpec("TNT 25N", "249cc", "Naked"),
            MotoModelSpec("BN 302S", "300cc", "Naked"),
            MotoModelSpec("Leoncino 250", "249cc", "Scrambler"),
            MotoModelSpec("Leoncino 500", "500cc", "Scrambler"),
            MotoModelSpec("Leoncino 800", "754cc", "Scrambler"),
            MotoModelSpec("TRK 251", "249cc", "Adventure"),
            MotoModelSpec("TRK 502", "500cc", "Adventure"),
            MotoModelSpec("TRK 502X", "500cc", "Adventure"),
            MotoModelSpec("TRK 702X", "698cc", "Adventure"),
            MotoModelSpec("502C", "500cc", "Cruiser"),
            MotoModelSpec("752S", "754cc", "Naked")
        ),
        "Husqvarna" to listOf(
            MotoModelSpec("Svartpilen 200", "199cc", "Urban Explorer"),
            MotoModelSpec("Svartpilen 250", "248cc", "Urban Explorer"),
            MotoModelSpec("Svartpilen 401", "373cc", "Urban Explorer"),
            MotoModelSpec("Svartpilen 401 Gen 2", "399cc", "Urban Explorer"),
            MotoModelSpec("Vitpilen 401", "373cc", "Café Racer"),
            MotoModelSpec("701 Enduro", "693cc", "Enduro"),
            MotoModelSpec("701 Supermoto", "693cc", "Supermoto"),
            MotoModelSpec("Norden 901", "889cc", "Adventure")
        ),
        "CFMoto" to listOf(
            MotoModelSpec("250 NK", "249cc", "Naked"),
            MotoModelSpec("300 NK", "292cc", "Naked"),
            MotoModelSpec("450 NK", "449cc", "Naked"),
            MotoModelSpec("650 NK", "649cc", "Naked"),
            MotoModelSpec("800 NK", "799cc", "Naked"),
            MotoModelSpec("250 SR", "249cc", "Supersport"),
            MotoModelSpec("300 SR", "292cc", "Supersport"),
            MotoModelSpec("450 SR", "449cc", "Supersport"),
            MotoModelSpec("450 MT", "449cc", "Adventure"),
            MotoModelSpec("650 MT", "649cc", "Adventure Touring"),
            MotoModelSpec("700 MT", "693cc", "Adventure Touring"),
            MotoModelSpec("800 MT Explore", "799cc", "Adventure"),
            MotoModelSpec("700 CL-X Heritage", "693cc", "Neo-Retro")
        ),
        "Ducati" to listOf(
            MotoModelSpec("Scrambler Icon 800", "803cc", "Scrambler"),
            MotoModelSpec("Monster 937", "937cc", "Naked"),
            MotoModelSpec("Hypermotard 698 Mono", "659cc", "Supermotard"),
            MotoModelSpec("Hypermotard 950", "937cc", "Supermotard"),
            MotoModelSpec("Multistrada V2", "937cc", "Adventure Touring"),
            MotoModelSpec("Multistrada V4", "1158cc", "Adventure Touring"),
            MotoModelSpec("DesertX", "937cc", "Adventure Rally"),
            MotoModelSpec("Panigale V2", "955cc", "Superbike"),
            MotoModelSpec("Panigale V4", "1103cc", "Superbike"),
            MotoModelSpec("Streetfighter V2", "955cc", "Hyper Naked"),
            MotoModelSpec("Streetfighter V4", "1103cc", "Hyper Naked"),
            MotoModelSpec("Diavel V4", "1158cc", "Power Cruiser")
        ),
        "Triumph" to listOf(
            MotoModelSpec("Speed 400", "398cc", "Roadster"),
            MotoModelSpec("Scrambler 400 X", "398cc", "Scrambler"),
            MotoModelSpec("Trident 660", "660cc", "Roadster"),
            MotoModelSpec("Tiger Sport 660", "660cc", "Adventure Sport"),
            MotoModelSpec("Street Triple 765 R", "765cc", "Roadster"),
            MotoModelSpec("Street Triple 765 RS", "765cc", "Roadster"),
            MotoModelSpec("Tiger 850 Sport", "888cc", "Adventure"),
            MotoModelSpec("Tiger 900 GT Pro", "888cc", "Adventure"),
            MotoModelSpec("Tiger 900 Rally Pro", "888cc", "Adventure"),
            MotoModelSpec("Tiger 1200 Rally Explorer", "1160cc", "Adventure"),
            MotoModelSpec("Bonneville T100", "900cc", "Modern Classic"),
            MotoModelSpec("Bonneville T120", "1200cc", "Modern Classic"),
            MotoModelSpec("Scrambler 900", "900cc", "Scrambler"),
            MotoModelSpec("Scrambler 1200 XE", "1200cc", "Scrambler"),
            MotoModelSpec("Rocket 3 R", "2458cc", "Power Roadster")
        ),
        "Kymco" to listOf(
            MotoModelSpec("Agility 125 Digital 3.0", "124cc", "Scooter"),
            MotoModelSpec("Agility Go 125", "124cc", "Scooter"),
            MotoModelSpec("Twist 125", "124cc", "Scooter"),
            MotoModelSpec("Fly 125", "124cc", "Scooter"),
            MotoModelSpec("Like 125", "124cc", "Scooter Clásico"),
            MotoModelSpec("Super 8 150", "149cc", "Scooter"),
            MotoModelSpec("Downtown 350i", "321cc", "Maxi Scooter"),
            MotoModelSpec("Xciting S 400", "400cc", "Maxi Scooter"),
            MotoModelSpec("AK 550", "550cc", "Maxi Scooter")
        ),
        "SYM" to listOf(
            MotoModelSpec("Crox 125", "124cc", "Scooter"),
            MotoModelSpec("CroxR 150", "149cc", "Scooter"),
            MotoModelSpec("Orbit 125", "124cc", "Scooter"),
            MotoModelSpec("Jet 14 200i ABS", "169cc", "Scooter"),
            MotoModelSpec("NHX 190i", "183cc", "Naked"),
            MotoModelSpec("NHT 200", "183cc", "Adventure"),
            MotoModelSpec("Citycom S 300i", "278cc", "Maxi Scooter"),
            MotoModelSpec("Joymax Z+ 300", "278cc", "Maxi Scooter"),
            MotoModelSpec("Maxsym TL 508", "508cc", "Maxi Scooter")
        )
    )

    fun baseModelsForBrand(marca: String): List<MotoModelSpec> {
        val clean = marca.trim()
        val key = VERIFIED_CATALOG.keys.firstOrNull { it.equals(clean, ignoreCase = true) }
        return if (key != null) VERIFIED_CATALOG[key].orEmpty() else emptyList()
    }

    fun normalizeCilindraje(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.isNotEmpty()) "${digits}cc" else raw.trim()
    }

    fun mergeModels(base: List<MotoModelSpec>, dynamic: List<MotoModelSpec>): List<MotoModelSpec> {
        val map = LinkedHashMap<String, MotoModelSpec>()
        for (item in base) {
            val key = item.modelo.trim().lowercase().replace(Regex("\\s+"), " ")
            if (key.isNotEmpty() && item.cilindraje.isNotBlank()) {
                map[key] = item.copy(cilindraje = normalizeCilindraje(item.cilindraje))
            }
        }
        for (item in dynamic) {
            val cleanModel = item.modelo.trim()
            val cleanCc = normalizeCilindraje(item.cilindraje)
            val key = cleanModel.lowercase().replace(Regex("\\s+"), " ")
            if (key.isNotEmpty() && cleanCc.any { it.isDigit() } && !map.containsKey(key)) {
                map[key] = MotoModelSpec(
                    modelo = cleanModel,
                    cilindraje = cleanCc,
                    categoria = item.categoria?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
        }
        return map.values.toList()
    }

    fun findDisplacement(marca: String, modelo: String, currentModels: List<MotoModelSpec> = emptyList()): String? {
        val normModel = modelo.trim().lowercase().replace(Regex("\\s+"), " ")
        if (normModel.isEmpty()) return null
        val fromCurrent = currentModels.firstOrNull {
            it.modelo.trim().lowercase().replace(Regex("\\s+"), " ") == normModel
        }
        if (fromCurrent != null) return normalizeCilindraje(fromCurrent.cilindraje)

        val base = baseModelsForBrand(marca).firstOrNull {
            it.modelo.trim().lowercase().replace(Regex("\\s+"), " ") == normModel
        }
        return base?.let { normalizeCilindraje(it.cilindraje) }
    }

    fun parseBrandCatalogJson(
        text: String,
        adapter: JsonAdapter<BrandCatalogResponse>
    ): List<MotoModelSpec> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val parsed = runCatching { adapter.fromJson(text.substring(start, end + 1)) }.getOrNull()
        return parsed?.modelos.orEmpty().mapNotNull { spec ->
            val mod = spec.modelo.trim()
            val cc = normalizeCilindraje(spec.cilindraje)
            if (mod.isEmpty() || !cc.any { it.isDigit() }) null
            else MotoModelSpec(mod, cc, spec.categoria?.trim()?.takeIf { it.isNotEmpty() })
        }
    }
}
