package com.oxpsi.omnitracker.ai

object AiPrompts {
    /**
     * The built-in analysis instructions. When a custom prompt is configured in
     * Settings it replaces this text; the user's log note is always appended
     * after it (images are attached as separate content parts either way).
     */
    const val DEFAULT_ANALYSIS_PROMPT = """Analyze the provided image(s) and notes. Identify the entry type and extract its constituents into the JSON structure below.

Image roles: "Batch" images describe an aggregate (recipe, packaged food label, formulation). "Entry/Log" images describe a single consumption or observation event. Images are labeled 1-based (e.g. "Image 1: Batch") above the note.

Output schema:
{
  "title": "",
  "type": "",
  "private": false,
  "food_items": [],
  "components": []
}

Field rules:
- title: Short human-readable label (e.g. "Scrambled Eggs", "Ibuprofen 200mg"). Not to be super descriptive, but medications for example can include amounts if short.
- type: One of "Food", "Medicine", "Supplement", "Stool", "Observation", or another Title Case type if none fit (spaces allowed for multi-word).
- private: Boolean. Mark true for sensitive entries (Stool, sensitive Observations, or other non-standard sensitive types).
- food_items: Higher-level list of total logged items/amounts (e.g. "Pork Tenderloin, 40 g", "Watermelon Juice, 200 ml", "1 tsp salt", brand products). Condenses the log before component analysis.
- components: Complete breakdown — macros, micros, active ingredients, constituents, etc. Be nutritionally and medically accurate; estimate quantities from visible or implied data.

Component rules (universal):
- Baseline nutrient set to include for Food when applicable (extend with any noteworthy extras; omit any not biologically relevant). Use the exact names below — do not expand, abbreviate, or rephrase (e.g. "Omega-3", not "Omega-3 Fatty Acids"). Varying the name breaks downstream aggregation by unit type.
  Energy (kcal), Protein (g), Carbohydrate (g), Total Fat (g), Saturated Fat (g), Trans Fat (g), Dietary Fiber (g), Soluble Fiber (g), Insoluble Fiber (g), Sugar (g), Added Sugars (g),
  Sodium (mg), Potassium (mg), Calcium (mg), Iron (mg), Magnesium (mg), Phosphorus (mg), Zinc (mg), Selenium (mcg), Choline (mg),
  Vitamin A (mcg RAE), Vitamin C (mg), Vitamin D (mcg), Vitamin E (mg), Vitamin K (mcg), Vitamin B1 (mg), Vitamin B2 (mg), Vitamin B3 (mg), Vitamin B6 (mg), Folate (mcg DFE), Vitamin B12 (mcg),
  Omega-3 (g), Omega-6 (g)
  (These extend beyond a standard FDA label. Use FDA label conventions where one is available; fill gaps by estimation.)
- Data normalization: FDA Nutrition Facts label naming for food components (Title Case, Singular). Energy in kcal.
- Solids: include a "Net Weight" estimate in grams (g) where it makes sense. Liquids: use appropriate volume estimates.
- Units: short abbreviations only, all lowercase, no special characters — g, mg, mcg, kcal, iu, cfu. Always "mcg" for micrograms (never μg or ug). Never write out the full unit word. Preserve native/special units verbatim (iu, cfu, etc.); do not convert.
- Sanity checks: Soluble + Insoluble Fiber should equal Dietary Fiber; macro-derived energy should largely sum to total Energy. Apply only when both sides are independently estimated.
- For zero or negligible components, omit entirely. Include any compound present in biologically relevant, identifiable amounts.
- Nutrition labels are often incomplete — don't omit a baseline nutrient just because any provided label omits it, unless the label explicitly states zero.
- Component structure must be plain: name, unit, quantity — no parentheticals, no extra comments, no extra characters. This feeds an aggregation algorithm.

Per-type rules:
- Medicine / Supplement: Use generic name and active ingredients. Avoid brands unless the formulation is proprietary/unknown. Use specific chemical names when known.
- Stool: Use standard clinical/pathology terminology. Describe stool using the Bristol Stool Scale (BSS 1-7).
- Observation: Decompose text into standardized clinical English (terms a patient would understand). Format body-location signs as "Condition (Location)" (e.g. "Flushing (Ears)"). Map mental states to their clinical root (e.g. "Irritability") and isolate composite feelings. Default unit for sensations is "Intensity" (1-10); use "Count" for numeric quantities.

Final strictness: Output only the specified fields and component name/unit/quantity triples. No commentary, no extra keys, no characters beyond what each rule requires."""

    fun getAnalysisPrompt(note: String, customPrompt: String? = null): String {
        val safeNote = if (note.isBlank()) "(no note)" else note
        val body = customPrompt?.takeIf { it.isNotBlank() } ?: DEFAULT_ANALYSIS_PROMPT
        return "$body\n\nUser Note: \"$safeNote\""
    }

    /**
     * Returns the common schema structure for the analysis result.
     * Providers can wrap this in their specific API envelopes (e.g., "additionalProperties": false for OpenAI).
     */
    fun getAnalysisSchemaStructure(): Map<String, Any> {
        return mapOf(
            "type" to "object",
            "properties" to mapOf(
                "title" to mapOf(
                    "type" to "string",
                    "description" to "Short title for the item"
                ),
                "type" to mapOf(
                    "type" to "string",
                    "description" to "Category: Food, Medicine, Supplement, Stool, State, Observation, or another Title Case type"
                ),
                "private" to mapOf(
                    "type" to "boolean",
                    "description" to "True if the log should be hidden from the gallery"
                ),
                "food_items" to mapOf(
                    "type" to "array",
                    "description" to "Higher-level list of total logged items/amounts, condensing the log before component analysis",
                    "items" to mapOf("type" to "string")
                ),
                "components" to mapOf(
                    "type" to "array",
                    "description" to "Primary components or ingredients",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "name" to mapOf("type" to "string"),
                            "unit" to mapOf("type" to "string"),
                            "quantity" to mapOf("type" to "number")
                        ),
                        "required" to listOf("name", "unit", "quantity"),
                        "additionalProperties" to false
                    )
                )
            ),
            "required" to listOf("title", "type", "private", "food_items", "components"),
            "additionalProperties" to false
        )
    }
}
