package com.zyn.chat.app.data.chat

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.zyn.chat.app.data.providers.AiProvider
import com.zyn.chat.app.data.providers.ModelCatalog
import com.zyn.chat.app.data.providers.ProviderAuth
import com.zyn.chat.app.data.providers.ProviderEndpoint
import com.zyn.chat.app.domain.chat.ChatImage
import com.zyn.chat.app.domain.chat.ChatRequest
import com.zyn.chat.app.domain.chat.ChatRole
import com.zyn.chat.app.domain.chat.ChatTurn
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A question with pictures still has to reach every OpenAI-compatible provider, so the wire shape is
 * pinned here: pictures as a `data:` URL inside an `image_url` content part, and untouched plain text
 * for the turns that carry none.
 */
class OpenAiProtocolTest {

    @Test
    fun `a turn without pictures is still a plain string`() {
        val body = bodyOf(ChatTurn(role = ChatRole.User, content = "hello"))

        assertTrue(body, body.contains("""{"role":"user","content":"hello"}"""))
    }

    @Test
    fun `a picture is sent as an inline data URL`() {
        val body = bodyOf(
            ChatTurn(
                role = ChatRole.User,
                content = "what is this",
                images = listOf(ChatImage(mimeType = "image/jpeg", base64 = "QUJD")),
            ),
        )

        assertTrue(body, body.contains("""{"type":"text","text":"what is this"}"""))
        assertTrue(
            body,
            body.contains("""{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,QUJD"}}"""),
        )
    }

    @Test
    fun `a picture with no text is sent on its own`() {
        val body = bodyOf(
            ChatTurn(
                role = ChatRole.User,
                content = "",
                images = listOf(ChatImage(mimeType = "image/png", base64 = "QQ==")),
            ),
        )

        assertTrue(
            body,
            body.contains("""[{"type":"image_url","image_url":{"url":"data:image/png;base64,QQ=="}}]"""),
        )
    }

    @Test
    fun `every picture of a turn is sent in order`() {
        val body = bodyOf(
            ChatTurn(
                role = ChatRole.User,
                content = "two of them",
                images = listOf(
                    ChatImage(mimeType = "image/jpeg", base64 = "FIRST"),
                    ChatImage(mimeType = "image/jpeg", base64 = "SECOND"),
                ),
            ),
        )

        assertTrue(body, body.indexOf("FIRST") < body.indexOf("SECOND"))
    }

    @Test
    fun `the body still carries the model, the stream flag and the token limit`() {
        val body = bodyOf(ChatTurn(role = ChatRole.User, content = "hi"), maxTokens = 256)

        assertTrue(body, body.contains(""""model":"test-model""""))
        assertTrue(body, body.contains(""""stream":true"""))
        assertTrue(body, body.contains(""""max_tokens":256"""))
    }

    private fun bodyOf(turn: ChatTurn, maxTokens: Int? = null): String = textOf(
        PROTOCOL.requestBody(request = request(turn, maxTokens), stream = true),
    )

    private fun request(turn: ChatTurn, maxTokens: Int?): ChatRequest = ChatRequest(
        conversationId = "conversation",
        provider = PROVIDER,
        model = MODEL,
        apiKey = "test-key",
        systemPrompt = null,
        messages = listOf(turn),
        maxOutputTokens = maxTokens,
    )

    private fun textOf(body: RequestBody): String = Buffer().also { buffer -> body.writeTo(buffer) }.readUtf8()
}

private val MOSHI = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

private val PROTOCOL = OpenAiProtocol(MOSHI)

private val MODEL = ModelCatalog.enrich("test", "test-model")

private val PROVIDER = AiProvider(
    id = "test",
    name = "Test",
    endpoint = ProviderEndpoint(baseUrl = "https://example.com/v1", auth = ProviderAuth.Bearer),
)
