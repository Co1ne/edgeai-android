package com.edgeaivoice.pipeline

object PromptBuilder {
    private const val SYSTEM_PROMPT = """
你是一个端侧离线语音助手。
请使用简洁、准确的中文回答。
如果用户输入为空，请直接提示“我没有听清，请再说一次”。
"""

    fun buildSingleTurn(asrText: String): String {
        val user = asrText.trim()
        return buildString {
            append("<|system|>\n")
            append(SYSTEM_PROMPT.trim())
            append("\n<|user|>\n")
            append(user)
            append("\n<|assistant|>\n")
        }
    }
}
