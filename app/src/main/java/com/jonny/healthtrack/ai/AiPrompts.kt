package com.jonny.healthtrack.ai

object AiPrompts {
    fun getAnalysisPrompt(note: String): String {
        val safeNote = if (note.isBlank()) "(no note)" else note
        return """
You are an advanced AI biological log analyzer. Analyze the provided image and the user's note.
Extract key details into the specified JSON structure.

Guidelines:
- Title: A short, human-readable label for the item (e.g., "Scrambled Eggs", "Ibuprofen 200mg").
- Type: Short classification. Use 'food', 'medicine', 'supplement', 'stool', or another if not fitting in those.
- Components: Break down and list top components of the log entry, being macros and micros, active ingredients, constituants, etc.
  Be precise, nutritionally, and medically accurate here, use your best judgement. Estimate quantities where visible or implied as accurately as possible.

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
                    "description" to "Category: food, medicine, supplement, activity, symptom, other",
                    "enum" to listOf("food", "medicine", "supplement", "activity", "symptom", "other")
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
            "required" to listOf("title", "type", "components"),
            "additionalProperties" to false
        )
    }
}
