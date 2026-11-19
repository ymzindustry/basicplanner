package com.ymz.basicplanner.model.module

import com.ymz.basicplanner.model.ItemType
import kotlinx.serialization.Serializable

/** What a module can be attached to. */
enum class ModuleTargetKind { DAY, SESSION, EXERCISE, SET }

@Serializable
data class ModuleInputSpec(
    /** Input name used by the module, e.g. "form". */
    val name: String,
    /** Type label shown in the UI (int, float, string, boolean). */
    val type: String = "int"
)

@Serializable
data class ModuleInputBinding(
    val specName: String,
    /** Entity whose items feed this input (the target itself or one of its ancestors). */
    val entityId: Long,
    val entityLabel: String = ""
)

/**
 * A Python function `apply(item)` that receives exactly one item value and returns four deltas for
 * every set it covers: `return sets, reps, percent, time`.
 *
 * [itemFamilyId] is the single item the function may read, picked when the module is created; the
 * engine resolves its value on the target (or the closest ancestor that carries it).
 */
@Serializable
data class ModuleDefinition(
    val id: Long,
    val name: String,
    val description: String = "",
    val code: String = DEFAULT_CODE,
    val itemFamilyId: Long? = null,
    val itemName: String = ""
) {
    companion object {
        /**
         * The signature a module carries: named after the module, its single parameter named and
         * typed after the chosen item — `def test(max: int):`.
         */
        fun signature(moduleName: String, itemName: String, itemType: ItemType? = null): String {
            val fn = pythonFunctionName(moduleName)
            val param = pythonParameterName(itemName)
            return "def $fn($param: ${pythonTypeName(itemType)}):"
        }

        fun pythonTypeName(itemType: ItemType?): String = when (itemType) {
            ItemType.INT -> "int"
            ItemType.FLOAT -> "float"
            ItemType.BOOLEAN -> "bool"
            ItemType.STRING, ItemType.VIDEO, ItemType.IMAGE, null -> "str"
        }

        /** A new module starts with its signature, then this body. */
        fun defaultCodeFor(moduleName: String, itemName: String, itemType: ItemType? = null): String {
            val param = pythonParameterName(itemName)
            return signature(moduleName, itemName, itemType) + "\n" +
                "    # deltas, starting at zero\n" +
                "    sets = 0\n" +
                "    reps = 0\n" +
                "    percent = 0.0\n" +
                "    time = 0\n" +
                "\n" +
                "    if $param >= 8:\n" +
                "        # drained: lighter session\n" +
                "        sets = -1\n" +
                "        reps = -1\n" +
                "        percent = -10.0\n" +
                "        time = 60\n" +
                "\n" +
                "    return sets, reps, percent, time\n"
        }

        /** The function takes the module's own name, so `module 1` → `def module_1(item):`. */
        fun pythonFunctionName(moduleName: String): String {
            val slug = moduleName.trim().lowercase()
                .replace(Regex("[^a-z0-9_]+"), "_")
                .trim('_')
            if (slug.isEmpty() || slug.first().isDigit() || slug in PYTHON_KEYWORDS) return "apply"
            return slug
        }

        /** `max effort` → `max_effort`; falls back to `item` for keywords and unusable names. */
        fun pythonParameterName(itemName: String): String {
            val slug = itemName.trim().lowercase()
                .replace(Regex("[^a-z0-9_]+"), "_")
                .trim('_')
            if (slug.isEmpty() || slug.first().isDigit() || slug in PYTHON_KEYWORDS) return "item"
            return slug
        }

        val PYTHON_KEYWORDS = setOf(
            "def", "return", "if", "elif", "else", "for", "while", "in", "not", "and", "or",
            "None", "True", "False", "import", "from", "as", "class", "try", "except", "finally",
            "raise", "with", "pass", "break", "continue", "is", "lambda", "global", "assert", "del",
            "yield", "async", "await", "nonlocal"
        )

        const val DEFAULT_CODE: String =
            "def apply(item):\n" +
                "    # deltas, starting at zero\n" +
                "    sets = 0\n" +
                "    reps = 0\n" +
                "    percent = 0.0\n" +
                "    time = 0\n" +
                "\n" +
                "    if item >= 8:\n" +
                "        # drained: lighter session\n" +
                "        sets = -1\n" +
                "        reps = -1\n" +
                "        percent = -10.0\n" +
                "        time = 60\n" +
                "\n" +
                "    return sets, reps, percent, time\n"

        /**
         * True for the bodies written before the four deltas were declared: they returned bare
         * numbers, so `sets`, `reps`, `percent` and `time` never appeared in the file.
         */
        fun isLegacyTemplate(code: String): Boolean {
            val declares = listOf("sets =", "reps =", "percent =", "time =").all { it in code }
            if (declares) return false
            return "return -1, -1, -10.0, 60" in code ||
                "return 0, 0, 0.0, 0" in code ||
                "\"sets\":" in code
        }

        /** What `apply` returns, in order. */
        const val RETURN_KEYS = "sets, reps, percent, time"
    }
}

/**
 * The four values a module returns — `sets, reps, percent, time` — read as **deltas** added to the
 * plan. They start at zero for every session and are inherited from module to module: each one adds
 * its own numbers on top of what the previous ones asked for.
 *
 * Zero means "no change", which is why the baseline is zero and not null.
 */
@Serializable
data class ModulePrescription(
    val sets: Int = 0,
    val reps: Int = 0,
    val percent: Double = 0.0,
    val time: Int = 0
) {
    fun isIdentity(): Boolean = sets == 0 && reps == 0 && percent == 0.0 && time == 0

    /** Inheritance: the next module starts from what the previous ones already asked for. */
    operator fun plus(other: ModulePrescription) = ModulePrescription(
        sets = sets + other.sets,
        reps = reps + other.reps,
        percent = percent + other.percent,
        time = time + other.time
    )
}

/**
 * Links a module to one target of one program. [programId] is what keeps two programs that share an
 * exercise apart: the same entity can carry different modules in different programs.
 */
@Serializable
data class ModuleAttachment(
    val id: Long,
    val moduleId: Long,
    val programId: Long,
    val targetKind: ModuleTargetKind,
    val targetId: Long,
    val bindings: List<ModuleInputBinding> = emptyList()
)

/** One set as the module sees it — plan values only, never the plan itself. */
@Serializable
data class ModuleSetSnapshot(
    val id: Long,
    val exerciseId: Long,
    val exercise: String,
    val movement: String,
    val reps: Int,
    val intensity: Int,
    val rest: Int,
    val weight_kg: Double
)

@Serializable
data class ModuleTargetInfo(
    val kind: String,
    val id: Long,
    val name: String
)

/** The one item a module reads: its family name, its type and the value found on the target or an ancestor. */
@Serializable
data class ModuleItemInfo(
    val name: String,
    val type: String = "string",
    val value: String
)

/** The JSON payload handed to `apply(ctx)`. */
@Serializable
data class ModuleContext(
    val date: String,
    val program_id: Long,
    val target: ModuleTargetInfo,
    /** The module's single item — what `apply` receives as its argument. */
    val item: ModuleItemInfo? = null,
    /** Item family name → value (kept for modules that still read the whole map). */
    val items: Map<String, String> = emptyMap(),
    val sets: List<ModuleSetSnapshot> = emptyList()
)

/** Adjustments a module asks for on one set; deltas so several modules can stack. */
data class SetAdjustment(
    val repsDelta: Int = 0,
    val percentDelta: Double = 0.0,
    val kgDelta: Double = 0.0,
    val restDelta: Int = 0
) {
    fun isIdentity(): Boolean =
        repsDelta == 0 && percentDelta == 0.0 && kgDelta == 0.0 && restDelta == 0
}

data class ModuleRunIssue(val moduleId: Long, val moduleName: String, val message: String)

data class ModuleOutcome(
    val adjustments: Map<Long, SetAdjustment> = emptyMap(),
    /** Prescription per exercise, so a module asking for 3 sets can be shown as such. */
    val prescriptions: Map<Long, ModulePrescription> = emptyMap(),
    val applied: List<Long> = emptyList(),
    val issues: List<ModuleRunIssue> = emptyList()
) {
    fun adjustmentFor(setEntryId: Long): SetAdjustment? = adjustments[setEntryId]
    fun isEmpty(): Boolean = adjustments.isEmpty() && issues.isEmpty()
}

/** Runs a module's Python source with the JSON context and returns its JSON output. */
fun interface ModuleExecutor {
    fun run(module: ModuleDefinition, contextJson: String): String
}
