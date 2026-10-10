import dev.eppopkaaddons.utils.pets.PetXpHistory
import dev.eppopkaaddons.utils.pets.PetXpModel
import kotlin.test.*

class PetXpModelTest {
    private fun dragon(total: Double, level: Int = 147) = requireNotNull(PetXpModel.read(
        "[Lvl $level] Golden Dragon", emptyList(),
        """{"type":"GOLDEN_DRAGON","tier":"LEGENDARY","uuid":"pet-a","exp":$total,"active":true}"""))

    @Test fun suffixesAndCommasAreParsedWithoutDroppingTheirMagnitude() {
        assertEquals(42000.0, PetXpModel.amount("42k"))
        assertEquals(1200000.0, PetXpModel.amount("1.2M"))
        assertEquals(1234567.0, PetXpModel.amount("1,234,567"))
        assertEquals(2500000000.0, PetXpModel.amount("2.5b"))
        assertNull(PetXpModel.amount("NaN")); assertNull(PetXpModel.amount("-20"))
    }
    @Test fun legendaryStandardCurveHasCorrectFirstCostAndTotal() {
        val costs = requireNotNull(PetXpModel.requirements("BEE", "LEGENDARY"))
        assertEquals(99, costs.size); assertEquals(660L, costs.first())
        assertEquals(25_353_230L, costs.sum())
    }
    @Test fun metadataUsesCurrentLevelXpNotLifetimeXp() {
        val start = requireNotNull(PetXpModel.totalBeforeLevel("GOLDEN_DRAGON", "LEGENDARY", 147))
        val r = dragon(start + 1_886_700 * .25)
        assertEquals(PetXpModel.Source.METADATA, r.source)
        assertEquals(.25, requireNotNull(r.progress), 1e-9)
        assertEquals(471675.0, r.intoLevel)
        assertEquals(1886700.0, r.required)
        assertFalse(r.maxed)
    }
    @Test fun goldenDragonAtLevel100IsNotMaxed() {
        val r = dragon(25_353_230.0, 100)
        assertFalse(r.maxed); assertEquals(200, r.maxLevel); assertEquals(0.0, r.progress)
        assertEquals(1886700.0, r.required)
    }
    @Test fun allThreeDragonTypesHaveAnExtendedCurve() {
        for (type in listOf("GOLDEN_DRAGON", "JADE_DRAGON", "ROSE_DRAGON")) {
            val costs = requireNotNull(PetXpModel.requirements(type, "LEGENDARY"))
            assertEquals(199, costs.size); assertEquals(214_023_230L, costs.sum())
            assertEquals(1886700L, costs.last())
        }
    }
    @Test fun correctDragonMaxIsTheOnlyMetadataCaseWithAFullBar() {
        val r = dragon(214_023_230.0, 200)
        assertTrue(r.maxed); assertEquals(1.0, r.progress); assertNull(r.required)
    }
    @Test fun xpAtALevelBoundaryResetsToZeroForTheNewLevel() {
        val start = requireNotNull(PetXpModel.totalBeforeLevel("GOLDEN_DRAGON", "LEGENDARY", 148))
        val r = dragon(start, 148)
        assertEquals(0.0, r.progress); assertEquals(0.0, r.intoLevel); assertFalse(r.maxed)
    }
    @Test fun tierBoostUsesTheEffectiveRarity() {
        assertEquals("EPIC", PetXpModel.effectiveTier("RARE", "PET_ITEM_TIER_BOOST"))
        assertEquals("MYTHIC", PetXpModel.effectiveTier("MYTHIC", "PET_ITEM_TIER_BOOST"))
        assertEquals("RARE", PetXpModel.effectiveTier("RARE", null))
    }
    @Test fun bingoDoesNotUseTheStandardRarityOffset() {
        assertEquals(100L, PetXpModel.requirements("BINGO", "EPIC")!!.first())
        assertEquals(0.0, PetXpModel.level("BINGO", "EPIC", 0.0)!!.into)
    }
    @Test fun loreSuffixesGiveTheCorrectFraction() {
        val r = requireNotNull(PetXpModel.read("[Lvl 42] Tiger",
            listOf("Progress to Level 43: 3.5%", "──────── 42k / 1.2M", "LEGENDARY"), null))
        assertEquals(.035, requireNotNull(r.progress), 1e-9)
        assertEquals(42000.0, r.intoLevel); assertEquals(1200000.0, r.required)
        assertEquals(PetXpModel.Source.LORE, r.source)
    }
    @Test fun candyAndOtherSlashValuesAreNeverPetXp() {
        val r = requireNotNull(PetXpModel.read("[Lvl 42] Tiger",
            listOf("Candy Used: 10/10", "LEGENDARY"), null))
        assertNull(r.progress); assertFalse(r.maxed)
    }
    @Test fun malformedOrMissingDataIsUnknownNotFull() {
        for (json in listOf(null, "{bad json}", """{"exp":1e999,"tier":"LEGENDARY"}""", """{"exp":-1,"tier":"LEGENDARY"}""")) {
            val r = requireNotNull(PetXpModel.read("[Lvl 42] Tiger", emptyList(), json))
            assertNull(r.progress); assertFalse(r.maxed)
        }
    }
    @Test fun staleOrWrongLevelMetadataCannotProduceFullProgress() {
        val r = requireNotNull(PetXpModel.read("[Lvl 42] Tiger", emptyList(),
            """{"type":"TIGER","tier":"LEGENDARY","exp":25353230}"""))
        assertNull(r.progress); assertFalse(r.maxed)
    }
    @Test fun anInvalidNonMaxRatioIsNotClampedToFull() {
        val r = requireNotNull(PetXpModel.read("[Lvl 42] Tiger",
            listOf("Progress to Level 43: 100%", "999M / 1M"), null))
        assertNull(r.progress); assertFalse(r.maxed)
    }
    @Test fun percentOnlyLoreIsUsableButDoesNotInventXpCounts() {
        val r = requireNotNull(PetXpModel.read("[Lvl 42] Tiger", listOf("Progress to Level 43: 12.4%"), null))
        assertEquals(.124, requireNotNull(r.progress), 1e-9)
        assertNull(r.intoLevel); assertNull(r.required)
        assertEquals(PetXpModel.Source.PERCENTAGE, r.source)
    }
    @Test fun explicitServerMaxLoreAllowsAFullBar() {
        val r = requireNotNull(PetXpModel.read("[Lvl 100] Tiger", listOf("MAX LEVEL", "LEGENDARY"), null))
        assertTrue(r.maxed); assertEquals(1.0, r.progress)
    }
    @Test fun aVisibleFutureLevelVetoesAnIncompatibleMaxCurve() {
        val r = requireNotNull(PetXpModel.read("[Lvl 100] Future Pet",
            listOf("Progress to Level 101: 10%", "100k / 1M"),
            """{"type":"FUTURE_PET","tier":"LEGENDARY","exp":25353230}"""))
        assertFalse(r.maxed); assertEquals(.1, requireNotNull(r.progress), 1e-9)
    }
    @Test fun formattedNamesAndMetadataUuidRemainIntact() {
        val r = requireNotNull(PetXpModel.read("§7[Lvl 1] §aBee ✦", emptyList(),
            """{"type":"BEE","tier":"UNCOMMON","uuid":"unique-b","exp":0,"active":true}"""))
        assertEquals("Bee", r.name); assertEquals("unique-b", r.uuid); assertTrue(r.active)
    }
    @Test fun ratesRequireTwoRealObservationsAndIncludeIdleTime() {
        val h = PetXpHistory(); h.record(1000.0, 0, 5)
        assertNull(h.perHour(10000, 5))
        h.record(1100.0, 10000, 5)
        assertEquals(36000.0, h.perHour(10000, 5))
        assertEquals(18000.0, h.perHour(20000, 5))
        assertNull(h.perHour(310001, 5))
    }
    @Test fun negativeXpOrDifferentPetResetCannotCreateAFakeRate() {
        val h = PetXpHistory(); h.record(1000.0, 0, 5); h.record(900.0, 10000, 5)
        assertNull(h.perHour(10000, 5))
        h.clear(); h.record(4000.0, 20000, 5); assertNull(h.perHour(30000, 5))
    }
}
