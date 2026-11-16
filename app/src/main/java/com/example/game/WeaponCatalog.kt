package com.example.game

enum class WeaponCategory(val labelVi: String) {
    MELEE("Cận chiến"),
    PISTOL("Súng lục"),
    SMG("Tiểu liên"),
    SHOTGUN("Shotgun"),
    AR("Súng trường"),
    SNIPER("Súng ngắm"),
}

data class WeaponSpec(
    val id: String,
    val name: String,
    val category: WeaponCategory,
    val cost: Int,
    val damage: Int,
    val headshotMultiplier: Float,
    val fireIntervalSec: Float,
    val magSize: Int,
    val reserveAmmo: Int,
    val reloadTimeSec: Float,
    val spreadDeg: Float,
    val adsSpreadDeg: Float,
    val adsFov: Float,
    val pellets: Int = 1,
    val range: Float = 55f,
    val recoilPitch: Float = 1.4f,
    val primaryColor: Int = 0xFF3E3B38.toInt(),
    val accentColor: Int = 0xFFD9B382.toInt(),
    val barrelLength: Float = 0.55f,
) {
    val isPrimary: Boolean
        get() = category == WeaponCategory.SMG ||
            category == WeaponCategory.SHOTGUN ||
            category == WeaponCategory.AR ||
            category == WeaponCategory.SNIPER

    val isSecondary: Boolean
        get() = category == WeaponCategory.PISTOL
}

object WeaponCatalog {
    val KNIFE = WeaponSpec(
        id = "knife",
        name = "Dao Quân Dụng",
        category = WeaponCategory.MELEE,
        cost = 0,
        damage = 60,
        headshotMultiplier = 1.5f,
        fireIntervalSec = 0.45f,
        magSize = 1,
        reserveAmmo = 0,
        reloadTimeSec = 0f,
        spreadDeg = 0f,
        adsSpreadDeg = 0f,
        adsFov = 68f,
        pellets = 1,
        range = 2.6f,
        recoilPitch = 0.3f,
        primaryColor = 0xFF78909C.toInt(),
        accentColor = 0xFF4E6E46.toInt(),
        barrelLength = 0.34f,
    )

    val G18 = WeaponSpec(
        id = "g18",
        name = "G18-Pixel",
        category = WeaponCategory.PISTOL,
        cost = 0,
        damage = 22,
        headshotMultiplier = 2.0f,
        fireIntervalSec = 0.16f,
        magSize = 15,
        reserveAmmo = 75,
        reloadTimeSec = 1.2f,
        spreadDeg = 2.2f,
        adsSpreadDeg = 0.8f,
        adsFov = 58f,
        pellets = 1,
        range = 40f,
        recoilPitch = 1.1f,
        primaryColor = 0xFF37474F.toInt(),
        accentColor = 0xFFFF8C1A.toInt(),
        barrelLength = 0.36f,
    )

    val DESERT_EAGLE = WeaponSpec(
        id = "deagle",
        name = "D-Eagle Cát",
        category = WeaponCategory.PISTOL,
        cost = 600,
        damage = 48,
        headshotMultiplier = 2.2f,
        fireIntervalSec = 0.36f,
        magSize = 7,
        reserveAmmo = 42,
        reloadTimeSec = 1.45f,
        spreadDeg = 2.6f,
        adsSpreadDeg = 0.5f,
        adsFov = 54f,
        pellets = 1,
        range = 48f,
        recoilPitch = 2.6f,
        primaryColor = 0xFFD9B382.toInt(),
        accentColor = 0xFF263238.toInt(),
        barrelLength = 0.42f,
    )

    val MP5 = WeaponSpec(
        id = "mp5",
        name = "MP5-Sand",
        category = WeaponCategory.SMG,
        cost = 1200,
        damage = 21,
        headshotMultiplier = 1.9f,
        fireIntervalSec = 0.085f,
        magSize = 30,
        reserveAmmo = 150,
        reloadTimeSec = 1.55f,
        spreadDeg = 3.0f,
        adsSpreadDeg = 1.1f,
        adsFov = 52f,
        pellets = 1,
        range = 42f,
        recoilPitch = 0.9f,
        primaryColor = 0xFF2D2A26.toInt(),
        accentColor = 0xFFC29B61.toInt(),
        barrelLength = 0.56f,
    )

    val M1014 = WeaponSpec(
        id = "m1014",
        name = "M1014-Cube",
        category = WeaponCategory.SHOTGUN,
        cost = 1400,
        damage = 16,
        headshotMultiplier = 1.6f,
        fireIntervalSec = 0.65f,
        magSize = 6,
        reserveAmmo = 30,
        reloadTimeSec = 1.9f,
        spreadDeg = 7.5f,
        adsSpreadDeg = 5.0f,
        adsFov = 58f,
        pellets = 6,
        range = 16f,
        recoilPitch = 3.4f,
        primaryColor = 0xFF453A31.toInt(),
        accentColor = 0xFFEF5350.toInt(),
        barrelLength = 0.68f,
    )

    val AK47 = WeaponSpec(
        id = "ak47",
        name = "AK47-Voxel",
        category = WeaponCategory.AR,
        cost = 1800,
        damage = 34,
        headshotMultiplier = 2.15f,
        fireIntervalSec = 0.125f,
        magSize = 30,
        reserveAmmo = 120,
        reloadTimeSec = 1.85f,
        spreadDeg = 2.8f,
        adsSpreadDeg = 0.7f,
        adsFov = 46f,
        pellets = 1,
        range = 60f,
        recoilPitch = 1.6f,
        primaryColor = 0xFF373532.toInt(),
        accentColor = 0xFF8D5524.toInt(),
        barrelLength = 0.72f,
    )

    val AWM = WeaponSpec(
        id = "awm",
        name = "AWM-Hẻm Núi",
        category = WeaponCategory.SNIPER,
        cost = 2600,
        damage = 112,
        headshotMultiplier = 2.5f,
        fireIntervalSec = 1.15f,
        magSize = 5,
        reserveAmmo = 25,
        reloadTimeSec = 2.2f,
        spreadDeg = 5.5f,
        adsSpreadDeg = 0.08f,
        adsFov = 24f,
        pellets = 1,
        range = 80f,
        recoilPitch = 4.2f,
        primaryColor = 0xFF4E6E46.toInt(),
        accentColor = 0xFF1F241D.toInt(),
        barrelLength = 0.92f,
    )

    val shopWeapons: List<WeaponSpec> = listOf(DESERT_EAGLE, MP5, M1014, AK47, AWM)
    val allWeapons: List<WeaponSpec> = listOf(G18, DESERT_EAGLE, MP5, M1014, AK47, AWM, KNIFE)
}

enum class TacticalItem(
    val id: String,
    val title: String,
    val description: String,
    val cost: Int,
) {
    ARMOR_LV1("armor_1", "Giáp Cát Lv.1", "+50 Giáp chiến đấu", 400),
    ARMOR_LV2("armor_2", "Giáp Quân Sự Lv.2", "+90 Giáp bảo vệ", 750),
    ARMOR_LV3("armor_3", "Giáp Đặc Nhiệm Lv.3", "+130 Giáp tối đa", 1100),
    MEDKIT("medkit", "Túi Cứu Thương", "Hồi 75 HP khi giao tranh (Tối đa 3)", 200),
    GLOO_BLOCK("gloo", "Bom Keo Khối Vuông", "Đặt tường đá sa thạch 3x2 chắn đạn (Tối đa 3)", 300),
}
