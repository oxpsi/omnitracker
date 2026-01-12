package com.jonny.healthtrack.ai

object AiPrompts {
    fun getAnalysisPrompt(note: String): String {
        val safeNote = if (note.isBlank()) "(no note)" else note
        return """
Analyze the provided image taking into account the user's note.
Identify and extract key constituents into the specified JSON structure.

Guidelines:
- Title: A short, human-readable label for the item (e.g., "Scrambled Eggs", "Ibuprofen 200mg"). This should not include details.
- Type: Short classification. Specifically use: 'Food', 'Medicine', 'Supplement', 'Stool', 'Observation' or another Title Case type if not fitting in those. Spaces if necessary for multi word.
- Private: Boolean flag. Mark true for any sensitive entry such as Stool, sensitive Observations (or for other non-standard types that seem very sensitive.)
- Components: Break down and list top components of the log entry. Such as macros and micros, active ingredients, constituants, etc.
  Be precise, nutritionally, and medically accurate here, use your best judgement based on given data. Estimate quantities where visible or implied as accurately as possible.
  
  Data Normatlization: Use FDA Nutrition Facts label conventions for food (Title Case, Singular). Use kcal for energy quantities.

  For most types dealing with solid matter, also include 'Net Weight' estimate, in grams (g). (unless it doesnt make sense to).
  and for liquids try to use appropriate volume estimates.

  For food, try to always include best estimate if present, in addition to anything else present, the basics:
  'Energy', 'Protein', 'Carbohydrate', 'Total Fat', 'Saturated Fat', 'Trans Fat', 'Dietary Fiber', 'Soluble Fiber', 'Insoluble Fiber', 'Sugar', 'Sodium', 'Potassium'.

  As well as any other noteworthy constituents, antioxidants, contaminants, etc.
  Identify ingredients internally for calculation, but for returned components only use final aggregate nutritional and chemical profile, excluding itemized food components.

  For units, use SI units. Never write out the full word.

  For medicine and supplements: stick to Generic Name and 'active ingredients'. Avoid brands unless formulation is proprietary and unknown.
  Be specific with chemical names if known.

  For biological outputs (stool), stick to 'clinical reporting': Use standard clinical/pathology terminology. For stool, use 'Bristol Stool Scale' for consistency in description.
  
  For special units, stick to original specifications and avoid converting unless using external references. (IU, cfu, BSS, etc)
  
  For 'Observation' type, decompose text into components using standardized clinical English (terms patients would understand).
  Format specific body parts as 'Condition (Location)' (e.g., 'hot ears' → 'Flushing (Ears)').
  Map mental states to their clinical root (e.g., 'feeling irritated' → 'Irritability'). Isolate composite feelings.
  Default unit to 'Intensity' (1-10) for sensations, use 'Count' for numeric quantities.

  The above guidelines must be strict, do not add any additional comments or characters for component values (name, unit, quantity) as this is used in a aggregation algorithm.
  (Don't let component names be complex like having paranthesis, etc)

  For negligable or zero value components, just omit it entirely. Include any chemical/compound/component that is present in enough biologically relevant amounts and known/identified.
  
  For a very complex mixture, just include the most important ~30 components.

User Note: "$safeNote"
""".trimIndent()
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
            "required" to listOf("title", "type", "private", "components"),
            "additionalProperties" to false
        )
    }
}
